package br.com.supermercados.prices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import br.com.supermercados.prices.price.PriceObservation;
import br.com.supermercados.prices.price.PriceService;
import br.com.supermercados.prices.price.StockAvailability;
import br.com.supermercados.prices.support.PostgresTestDatabase;

/** Synthetic prices in a rolled-back, isolated PostgreSQL schema. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Sql("/fixtures/catalog.sql")
class StoreListingIntegrationTests {

    private static final String CITY = "623614c4-674b-4e6d-a252-2eafbd98be97";
    private static final UUID SOURCE = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID STORE_A = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID STORE_B = UUID.fromString("00000000-0000-0000-0000-000000000202");

    @Autowired private MockMvc mvc;
    @Autowired private PriceService prices;
    @Autowired private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry);
    }

    @Test
    void onlyMarketsWithACurrentPriceAreListed() throws Exception {
        price(STORE_A, Instant.now().minus(Duration.ofHours(2)));
        price(STORE_B, Instant.now().minus(Duration.ofDays(5)));

        for (var request : new org.springframework.test.web.servlet.RequestBuilder[] {
                get("/api/v1/stores"), get("/api/v1/stores").param("cityId", CITY)}) {
            mvc.perform(request).andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[*].id", contains(STORE_A.toString())))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }
    }

    @Test
    void marketsWithoutAnOnlineStoreAreRetiredWithoutLosingTheirHistory() throws IOException {
        // A fresh database already ran V34 over the Supermarket directory entry from V25.
        assertThat(jdbc.queryForObject("""
                SELECT s.active FROM stores s JOIN data_sources d ON d.id = s.source_id
                WHERE d.code = 'SUPERMARKET_OFFICIAL_DIRECTORY'""", Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject(
                "SELECT enabled FROM data_sources WHERE code = 'SUPERMARKET_OFFICIAL_DIRECTORY'", Boolean.class)).isFalse();

        // Vila Rica only exists where the flyer importer ran, so it is recreated with a price and the migration
        // replayed: that unit is hidden, São Geraldo stays, and nothing is deleted.
        UUID flyers = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO data_sources (id, code, name, base_url, enabled, verified_at, created_at)
                VALUES (?, 'atacadao_reviewed_flyers', 'Encartes oficiais Atacadão — revisão manual',
                        'https://www.atacadao.com.br', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)""", flyers);
        UUID vilaRica = store(flyers, "atacadao:store:289", "Atacadão Vila Rica");
        UUID saoGeraldo = store(flyers, "atacadao:store:815", "Atacadão São Geraldo");
        price(vilaRica, Instant.now().minus(Duration.ofHours(1)));

        jdbc.execute(new ClassPathResource("db/migration/V34__retire_markets_without_online_store.sql")
                .getContentAsString(StandardCharsets.UTF_8));

        assertThat(jdbc.queryForObject("SELECT active FROM stores WHERE id = ?", Boolean.class, vilaRica)).isFalse();
        assertThat(jdbc.queryForObject("SELECT active FROM stores WHERE id = ?", Boolean.class, saoGeraldo)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM price_records WHERE store_id = ?", Integer.class, vilaRica))
                .isOne();
        assertThat(jdbc.queryForObject("SELECT name FROM data_sources WHERE id = ?", String.class, flyers))
                .isEqualTo("Diretório oficial de lojas Atacadão");
    }

    private UUID store(UUID source, String reference, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO stores (id, supermarket_chain_id, city_id, name, active, source_id, source_reference,
                                    collected_at, updated_at)
                SELECT ?, '00000000-0000-0000-0000-000000000151', id, ?, true, ?, ?, CURRENT_TIMESTAMP,
                       CURRENT_TIMESTAMP
                FROM cities WHERE name = 'Volta Redonda'""", id, name, source, reference);
        return id;
    }

    private void price(UUID store, Instant collectedAt) {
        UUID product = jdbc.queryForObject("SELECT id FROM products ORDER BY id LIMIT 1", UUID.class);
        prices.appendObservation(new PriceObservation(product, store, SOURCE, "listing-" + UUID.randomUUID(),
                new BigDecimal("5.00"), null, "BRL", collectedAt, null, null, StockAvailability.AVAILABLE));
    }
}

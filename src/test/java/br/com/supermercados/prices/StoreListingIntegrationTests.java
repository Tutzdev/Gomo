package br.com.supermercados.prices;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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

    private void price(UUID store, Instant collectedAt) {
        UUID product = jdbc.queryForObject("SELECT id FROM products ORDER BY id LIMIT 1", UUID.class);
        prices.appendObservation(new PriceObservation(product, store, SOURCE, "listing-" + UUID.randomUUID(),
                new BigDecimal("5.00"), null, "BRL", collectedAt, null, null, StockAvailability.AVAILABLE));
    }
}

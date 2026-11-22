package br.com.supermercados.prices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import br.com.supermercados.prices.common.ApiException;
import br.com.supermercados.prices.price.PriceObservation;
import br.com.supermercados.prices.price.PriceService;
import br.com.supermercados.prices.price.StockAvailability;
import br.com.supermercados.prices.store.StorePriceChannel;
import br.com.supermercados.prices.store.StorePriceStatus;
import br.com.supermercados.prices.store.StoreResponse;
import br.com.supermercados.prices.store.StoreService;
import br.com.supermercados.prices.storevisit.StoreVisitRequest;
import br.com.supermercados.prices.storevisit.StoreVisitService;
import br.com.supermercados.prices.support.PostgresTestDatabase;

/** Shelf prices of markets without an online store, against the real schema (V52). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Sql("/fixtures/catalog.sql")
class StoreVisitIntegrationTests {

    private static final String BARRA_MANSA = "2f6cba12-af5c-4af6-96a6-da7184148f88";
    private static final UUID SUPERMARKET_BARRA_MANSA = UUID.fromString("0b1c2d3e-4f50-4a61-8b72-9c83d4e5f601");
    private static final UUID ONLINE_STORE = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID SOURCE = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID ALPHA = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final UUID BETA = UUID.fromString("00000000-0000-0000-0000-000000000302");

    @Autowired private MockMvc mvc;
    @Autowired private StoreVisitService visits;
    @Autowired private PriceService prices;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private StoreService stores;
    @Autowired private jakarta.persistence.EntityManager entityManager;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry);
    }

    @Test
    void barraMansaMarketsWithoutAnOnlineStoreStayOffThePublicListUntilTheirFirstVisit() throws Exception {
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM stores WHERE city_id = ?::uuid AND price_channel = 'IN_STORE' AND active""",
                Integer.class, BARRA_MANSA)).isEqualTo(5);

        mvc.perform(get("/api/v1/stores/{id}", SUPERMARKET_BARRA_MANSA)).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Supermarket Barra Mansa Centro"))
                .andExpect(jsonPath("$.priceChannel").value("IN_STORE"))
                .andExpect(jsonPath("$.priceStatus").value("NO_PRICES"));
        mvc.perform(get("/api/v1/stores").param("cityId", BARRA_MANSA).param("size", "50")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.priceChannel == 'IN_STORE')]", empty()));
        assertThat(stores.findStoresWithStatus(UUID.fromString(BARRA_MANSA), PageRequest.of(0, 50)).getContent())
                .filteredOn(store -> store.priceChannel() == StorePriceChannel.IN_STORE)
                .hasSize(5)
                .allMatch(store -> store.priceStatus() == StorePriceStatus.NO_PRICES);
    }

    @Test
    void aShelfPriceStaysComparableForAWeekWhileAScrapedOneExpiresInTwoDays() throws Exception {
        Instant fourDaysAgo = Instant.now().minus(Duration.ofDays(4)).truncatedTo(ChronoUnit.SECONDS);
        prices.appendObservation(new PriceObservation(ALPHA, ONLINE_STORE, SOURCE, "test-online-alpha",
                new BigDecimal("9.90"), null, "BRL", fourDaysAgo, null, null, StockAvailability.AVAILABLE));

        var recorded = visits.record(admin(), SUPERMARKET_BARRA_MANSA, new StoreVisitRequest(fourDaysAgo,
                List.of(new StoreVisitRequest.Item(BETA, new BigDecimal("7.49")))));

        assertThat(recorded.recordedPrices()).isOne();
        assertThat(recorded.currentUntil()).isEqualTo(fourDaysAgo.plus(Duration.ofDays(7)));
        assertThat(jdbc.queryForObject("SELECT origin_type FROM price_records WHERE store_id = ?",
                String.class, SUPERMARKET_BARRA_MANSA)).isEqualTo("STORE_VISIT");
        mvc.perform(get("/api/v1/stores/{id}", SUPERMARKET_BARRA_MANSA))
                .andExpect(jsonPath("$.priceStatus").value("CURRENT"));
        mvc.perform(get("/api/v1/stores/{id}", ONLINE_STORE))
                .andExpect(jsonPath("$.priceStatus").value("OUTDATED"));
        mvc.perform(get("/api/v1/stores").param("cityId", BARRA_MANSA).param("size", "50"))
                .andExpect(jsonPath("$.content[?(@.priceChannel == 'IN_STORE')].id",
                        contains(SUPERMARKET_BARRA_MANSA.toString())));
        // Price ordering runs in SQL: the four-day-old shelf price still counts, so Beta comes before Alpha.
        mvc.perform(get("/api/v1/products").param("query", "FICTÍCIO").param("sort", "price_asc")
                        .param("storeId", SUPERMARKET_BARRA_MANSA.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(BETA.toString()));
    }

    @Test
    void aMarketCanHaveShorterShelfPriceValidityThanTheDefault() throws Exception {
        UUID admin = admin();
        visits.changeValidity(admin, SUPERMARKET_BARRA_MANSA, 3);
        Instant fourDaysAgo = Instant.now().minus(Duration.ofDays(4)).truncatedTo(ChronoUnit.SECONDS);
        Instant twoDaysAgo = Instant.now().minus(Duration.ofDays(2)).truncatedTo(ChronoUnit.SECONDS);

        assertThatThrownBy(() -> visits.record(admin, SUPERMARKET_BARRA_MANSA, new StoreVisitRequest(fourDaysAgo,
                List.of(new StoreVisitRequest.Item(BETA, new BigDecimal("7.49"))))))
                .isInstanceOf(ApiException.class).hasMessageContaining("antiga demais");
        var recorded = visits.record(admin, SUPERMARKET_BARRA_MANSA, new StoreVisitRequest(twoDaysAgo,
                List.of(new StoreVisitRequest.Item(BETA, new BigDecimal("7.49")))));

        assertThat(recorded.currentUntil()).isEqualTo(twoDaysAgo.plus(Duration.ofDays(3)));
        assertThat(jdbc.queryForObject("SELECT valid_until FROM price_records WHERE store_id = ?",
                java.sql.Timestamp.class, SUPERMARKET_BARRA_MANSA).toInstant()).isEqualTo(recorded.currentUntil());
        mvc.perform(get("/api/v1/stores/{id}", SUPERMARKET_BARRA_MANSA))
                .andExpect(jsonPath("$.inStorePriceValidDays").value(3))
                .andExpect(jsonPath("$.priceStatus").value("CURRENT"));

        assertThatThrownBy(() -> visits.changeValidity(admin, SUPERMARKET_BARRA_MANSA, 8))
                .isInstanceOf(ApiException.class).hasMessageContaining("1 a 7 dias");
        assertThatThrownBy(() -> visits.changeValidity(admin, ONLINE_STORE, 3))
                .isInstanceOf(ApiException.class).hasMessageContaining("sem loja online");
        visits.changeValidity(admin, SUPERMARKET_BARRA_MANSA, null);
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT in_store_price_valid_days FROM stores WHERE id = ?", Integer.class,
                SUPERMARKET_BARRA_MANSA)).isNull();
    }

    @Test
    void aMarketWhoseShelfPricesExpiredLeavesThePublicListAndTheComparisons() throws Exception {
        Instant twoDaysAgo = Instant.now().minus(Duration.ofDays(2)).truncatedTo(ChronoUnit.SECONDS);
        visits.record(admin(), SUPERMARKET_BARRA_MANSA, new StoreVisitRequest(twoDaysAgo,
                List.of(new StoreVisitRequest.Item(BETA, new BigDecimal("7.49")))));

        assertThat(stores.findAllActiveStores(UUID.fromString(BARRA_MANSA), 500))
                .extracting(StoreResponse::id).contains(SUPERMARKET_BARRA_MANSA);

        jdbc.update("""
                UPDATE price_records
                SET collected_at = collected_at - interval '6 days', recorded_at = recorded_at - interval '6 days',
                    valid_until = NULL
                WHERE store_id = ?""", SUPERMARKET_BARRA_MANSA);

        assertThat(stores.findAllActiveStores(UUID.fromString(BARRA_MANSA), 500))
                .extracting(StoreResponse::id).doesNotContain(SUPERMARKET_BARRA_MANSA);
        mvc.perform(get("/api/v1/stores").param("cityId", BARRA_MANSA).param("size", "50"))
                .andExpect(jsonPath("$.content[?(@.priceChannel == 'IN_STORE')]", empty()));
    }

    @Test
    void resendingTheSameVisitRecordsNothingNew() {
        Instant observedAt = Instant.now().minus(Duration.ofHours(1)).truncatedTo(ChronoUnit.SECONDS);
        var visit = new StoreVisitRequest(observedAt, List.of(new StoreVisitRequest.Item(ALPHA, new BigDecimal("5.00"))));
        UUID admin = admin();

        visits.record(admin, SUPERMARKET_BARRA_MANSA, visit);
        visits.record(admin, SUPERMARKET_BARRA_MANSA, visit);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM price_records WHERE store_id = ?",
                Integer.class, SUPERMARKET_BARRA_MANSA)).isOne();
    }

    @Test
    void aMarketWithAnOnlineStoreDoesNotAcceptShelfPrices() {
        var visit = new StoreVisitRequest(Instant.now().minusSeconds(60),
                List.of(new StoreVisitRequest.Item(ALPHA, new BigDecimal("5.00"))));

        assertThatThrownBy(() -> visits.record(admin(), ONLINE_STORE, visit))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("sem loja online");
    }

    private UUID admin() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO app_users (id, name, email, password_hash, created_at, updated_at, role, email_verified_at)
                VALUES (?, 'Admin fictício', ?, 'x', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'ADMIN', CURRENT_TIMESTAMP)""",
                id, "admin-" + id + "@example.test");
        return id;
    }
}

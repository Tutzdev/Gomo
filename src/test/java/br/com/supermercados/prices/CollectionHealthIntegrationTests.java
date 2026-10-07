package br.com.supermercados.prices;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import br.com.supermercados.prices.collection.CollectionHealthService;
import br.com.supermercados.prices.collection.CollectionHealthService.MarketStatus;
import br.com.supermercados.prices.price.PriceObservation;
import br.com.supermercados.prices.price.PriceService;
import br.com.supermercados.prices.price.StockAvailability;
import br.com.supermercados.prices.support.PostgresTestDatabase;

/** Synthetic runs and prices in a rolled-back, isolated PostgreSQL schema. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Sql("/fixtures/catalog.sql")
class CollectionHealthIntegrationTests {

    private static final UUID SOURCE = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID STORE_A = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID STORE_B = UUID.fromString("00000000-0000-0000-0000-000000000202");
    /** A regional collector registered in every context; the synthetic runs borrow its code. */
    private static final String COLLECTOR = "bramil_santo_agostinho";

    @Autowired private CollectionHealthService health;
    @Autowired private PriceService prices;
    @Autowired private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry);
    }

    @Test
    void aCollectionInProgressKeepsItsMarketAndAddsNoPhantomRow() {
        price(STORE_A, Instant.now().minus(Duration.ofHours(1)));
        run("SUCCESS", STORE_A, Instant.now().minus(Duration.ofHours(1)));
        run("RUNNING", null, Instant.now());

        var report = health.report();

        var market = report.markets().stream().filter(item -> STORE_A.equals(item.storeId())).findFirst().orElseThrow();
        assertThat(market.status()).isEqualTo(MarketStatus.OK);
        assertThat(market.currentProducts()).isOne();
        assertThat(market.collectors()).singleElement().satisfies(collector -> assertThat(collector.lastStatus().name()).isEqualTo("RUNNING"));
        assertThat(report.markets()).noneMatch(item -> item.storeId() == null
                && item.collectors().stream().anyMatch(collector -> collector.code().equals(COLLECTOR)));
    }

    @Test
    void aMarketWhoseCollectionsStoppedIsLateThenDown() {
        price(STORE_A, Instant.now().minus(Duration.ofHours(20)));
        run("SUCCESS", STORE_A, Instant.now().minus(Duration.ofHours(20)));
        run("FAILED", null, Instant.now().minus(Duration.ofHours(2)));

        assertThat(statusOf(STORE_A)).isEqualTo(MarketStatus.STALE);
        assertThat(health.report().healthy()).isFalse();

        jdbc.update("UPDATE price_records SET collected_at = collected_at - interval '3 days', recorded_at = recorded_at - interval '3 days' WHERE store_id = ?", STORE_A);
        assertThat(statusOf(STORE_A)).isEqualTo(MarketStatus.DOWN);
    }

    @Test
    void aStoreNoCollectorFeedsHasNoSourceAndDoesNotRaiseAnAlert() {
        price(STORE_A, Instant.now().minus(Duration.ofMinutes(30)));
        run("SUCCESS", STORE_A, Instant.now().minus(Duration.ofMinutes(30)));

        assertThat(statusOf(STORE_B)).isEqualTo(MarketStatus.NO_SOURCE);
    }

    private MarketStatus statusOf(UUID storeId) {
        return health.report().markets().stream().filter(item -> storeId.equals(item.storeId())).findFirst().orElseThrow().status();
    }

    private void price(UUID store, Instant collectedAt) {
        UUID product = jdbc.queryForObject("SELECT id FROM products ORDER BY id LIMIT 1", UUID.class);
        prices.appendObservation(new PriceObservation(product, store, SOURCE, "health-" + UUID.randomUUID(),
                new BigDecimal("5.00"), null, "BRL", collectedAt, null, null, StockAvailability.AVAILABLE));
    }

    private void run(String status, UUID store, Instant startedAt) {
        jdbc.update("""
                INSERT INTO collection_runs (id, collector_code, supermarket_name, store_name, store_id, started_at,
                    finished_at, status)
                VALUES (?, ?, 'Bramil', 'Bramil Santo Agostinho', ?, ?, ?, ?)
                """, UUID.randomUUID(), COLLECTOR, store, Timestamp.from(startedAt),
                "RUNNING".equals(status) ? null : Timestamp.from(startedAt.plusSeconds(60)), status);
    }
}

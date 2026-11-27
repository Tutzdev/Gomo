package br.com.supermercados.prices;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.supermercados.prices.price.PriceHistoryCompactor;
import br.com.supermercados.prices.price.PriceObservation;
import br.com.supermercados.prices.price.PriceService;
import br.com.supermercados.prices.price.StockAvailability;
import br.com.supermercados.prices.support.PostgresTestDatabase;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
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

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Sql("/fixtures/catalog.sql")
class PriceHistoryCompactionIntegrationTests {

    private static final UUID SOURCE = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID STORE_A = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID PRODUCT_A = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final UUID PRODUCT_B = UUID.fromString("00000000-0000-0000-0000-000000000302");

    @Autowired PriceHistoryCompactor compactor;
    @Autowired PriceService prices;
    @Autowired JdbcTemplate jdbc;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry);
    }

    @Test
    void oldRepeatsGoWhileEveryPriceChangeAndTheRecentDaysStay() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        observe(PRODUCT_A, "d10", "3.00", now.minus(Duration.ofDays(10)));
        observe(PRODUCT_A, "d9", "3.00", now.minus(Duration.ofDays(9)));
        observe(PRODUCT_A, "d8", "3.00", now.minus(Duration.ofDays(8)));
        observe(PRODUCT_A, "d7", "3.50", now.minus(Duration.ofDays(7)));
        observe(PRODUCT_A, "d6", "3.50", now.minus(Duration.ofDays(6)));
        observe(PRODUCT_A, "d5", "3.00", now.minus(Duration.ofDays(5)));
        observe(PRODUCT_A, "h12", "3.00", now.minus(Duration.ofHours(12)));
        observe(PRODUCT_A, "h6", "3.00", now.minus(Duration.ofHours(6)));
        Map<String, Object> dailyLowestBefore = dailyLowest();

        long removed = compactor.compact();

        assertThat(removed).isEqualTo(3);
        assertThat(references(PRODUCT_A)).containsExactly("d10", "d7", "d5", "h12", "h6");
        assertThat(dailyLowest()).isEqualTo(dailyLowestBefore);
        assertThat(compactor.compact()).isZero();
    }

    @Test
    void theNewestObservationStaysEvenWhenItIsOldAndRepeated() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        observe(PRODUCT_B, "old-1", "4.00", now.minus(Duration.ofDays(30)));
        observe(PRODUCT_B, "old-2", "4.00", now.minus(Duration.ofDays(20)));
        observe(PRODUCT_B, "old-3", "4.00", now.minus(Duration.ofDays(10)));

        compactor.compact();

        assertThat(references(PRODUCT_B)).containsExactly("old-1", "old-3");
        assertThat(jdbc.queryForObject("""
                SELECT r.source_reference FROM latest_prices l JOIN price_records r ON r.id = l.price_record_id
                WHERE l.store_id = ? AND l.product_id = ?
                """, String.class, STORE_A, PRODUCT_B)).isEqualTo("old-3");
    }

    @Test
    void aPromotionStartingOrEndingCountsAsAChange() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        observe(PRODUCT_A, "regular", "5.00", null, now.minus(Duration.ofDays(9)));
        observe(PRODUCT_A, "promo", "5.00", "4.00", now.minus(Duration.ofDays(8)));
        observe(PRODUCT_A, "promo-again", "5.00", "4.00", now.minus(Duration.ofDays(7)));
        observe(PRODUCT_A, "regular-back", "5.00", null, now.minus(Duration.ofDays(6)));
        observe(PRODUCT_A, "latest", "5.00", null, now.minus(Duration.ofDays(5)));

        compactor.compact();

        assertThat(references(PRODUCT_A)).containsExactly("regular", "promo", "regular-back", "latest");
    }

    private void observe(UUID product, String reference, String regular, Instant collectedAt) {
        observe(product, reference, regular, null, collectedAt);
    }

    private void observe(UUID product, String reference, String regular, String promotional, Instant collectedAt) {
        prices.appendObservation(new PriceObservation(product, STORE_A, SOURCE, reference, new BigDecimal(regular),
                promotional == null ? null : new BigDecimal(promotional), "BRL", collectedAt, null, null,
                StockAvailability.UNKNOWN));
    }

    private List<String> references(UUID product) {
        return jdbc.queryForList("SELECT source_reference FROM price_records WHERE product_id = ? ORDER BY collected_at",
                String.class, product);
    }

    private Map<String, Object> dailyLowest() {
        return Map.of("rows", jdbc.queryForList(
                "SELECT price_day, lowest_price FROM daily_lowest_prices WHERE product_id = ? ORDER BY price_day",
                PRODUCT_A));
    }
}

package br.com.supermercados.prices.price;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Keeps price_records from filling the server's disk. The collectors write every product of every store four
 * times a day and almost all of those rows repeat the one before (in 11/2026 that was ~480 MB a day, 92% of it
 * repeated). Rows older than {@code app.price-history.detail-retention} are thinned to the moments a price
 * changed: of each run of equal observations (same regular and promotional price, availability and promotion
 * condition) only the first one stays, and so does the newest observation of each store and product, which
 * latest_prices points to.
 * <p>
 * Nothing the site shows is lost. The history still has every change, and the daily lowest prices behind the
 * usual price, the alerts, the deals and the stock-up hint live in daily_lowest_prices, which the insert trigger
 * fills and nothing recomputes from here. Store visits and user contributions are never touched, and neither are
 * rows an alert or a contribution points to.
 */
@Service
public class PriceHistoryCompactor {

    private static final Logger log = LoggerFactory.getLogger(PriceHistoryCompactor.class);

    /**
     * Each store is compacted in slices of product ids (by the first hex digit), each one its own statement and
     * transaction: the window over a big store's whole history stays small, and a failure keeps what was done.
     */
    static final int PRODUCT_SLICES = 16;

    private static final String COMPACT_SLICE = """
            DELETE FROM price_records r
            USING (
                SELECT id, collected_at,
                       (regular_price, promotional_price, availability, promotion_condition)
                           IS NOT DISTINCT FROM
                           lag((regular_price, promotional_price, availability, promotion_condition)) OVER w
                           AS repeats_previous,
                       lead(id) OVER w IS NULL AS newest
                FROM price_records
                WHERE store_id = ? AND product_id BETWEEN ? AND ? AND origin_type = 'SOURCE'
                WINDOW w AS (PARTITION BY source_id, product_id ORDER BY collected_at, recorded_at, id)
            ) observation
            WHERE r.id = observation.id
              AND observation.repeats_previous
              AND NOT observation.newest
              AND observation.collected_at < ?
              AND NOT EXISTS (SELECT 1 FROM alert_notifications a WHERE a.price_record_id = r.id)
              AND NOT EXISTS (SELECT 1 FROM price_contributions c WHERE c.price_record_id = r.id)
            """;

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final Duration detailRetention;

    public PriceHistoryCompactor(JdbcTemplate jdbc, Clock clock,
            @Value("${app.price-history.detail-retention:P3D}") Duration detailRetention) {
        // The collection monitor counts the products collected in the last hours: at least a day stays whole.
        if (detailRetention.compareTo(Duration.ofDays(1)) < 0) {
            throw new IllegalArgumentException("app.price-history.detail-retention must be at least one day");
        }
        this.jdbc = jdbc;
        this.clock = clock;
        this.detailRetention = detailRetention;
    }

    @Scheduled(cron = "${app.price-history.compaction-cron:0 0 2 * * *}", zone = "${app.collection.zone:America/Sao_Paulo}")
    public void scheduledCompaction() {
        try {
            long started = System.nanoTime();
            long removed = compact();
            log.info("Price history compacted: {} repeated observations removed in {} s", removed,
                    Duration.ofNanos(System.nanoTime() - started).toSeconds());
        } catch (RuntimeException exception) {
            log.error("Price history compaction failed", exception);
        }
    }

    /** Removes the repeated observations older than the detail window and returns how many went. */
    public long compact() {
        Timestamp cutoff = Timestamp.from(clock.instant().minus(detailRetention));
        List<UUID> stores = jdbc.queryForList("SELECT id FROM stores ORDER BY id", UUID.class);
        long removed = 0;
        for (UUID store : stores) {
            for (int slice = 0; slice < PRODUCT_SLICES; slice++) {
                removed += jdbc.update(COMPACT_SLICE, store, lowestId(slice), highestId(slice), cutoff);
            }
        }
        return removed;
    }

    static UUID lowestId(int slice) {
        return UUID.fromString(Integer.toHexString(slice) + "0000000-0000-0000-0000-000000000000");
    }

    static UUID highestId(int slice) {
        return UUID.fromString(Integer.toHexString(slice) + "fffffff-ffff-ffff-ffff-ffffffffffff");
    }
}

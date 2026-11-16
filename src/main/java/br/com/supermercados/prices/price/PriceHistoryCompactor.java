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

@Service
public class PriceHistoryCompactor {

    private static final Logger log = LoggerFactory.getLogger(PriceHistoryCompactor.class);

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

    public PriceHistoryCompactor(
        JdbcTemplate jdbc,
        Clock clock,
        @Value("${app.price-history.detail-retention:P3D}") Duration detailRetention) {

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

            log.info(
                "Price history compacted: {} repeated observations removed in {} s",
                removed,
                Duration.ofNanos(System.nanoTime() - started).toSeconds());
        } catch (RuntimeException exception) {
            log.error("Price history compaction failed", exception);
        }
    }

    public long compact() {
        Timestamp cutoff = Timestamp.from(clock.instant().minus(detailRetention));
        List<UUID> stores = jdbc.queryForList("SELECT id FROM stores ORDER BY id", UUID.class);
        long removed = 0;

        for (UUID store : stores) {
            for (int slice = 0; slice < PRODUCT_SLICES; slice++) {
                removed += jdbc.update(
                    COMPACT_SLICE,
                    store,
                    lowestId(slice),
                    highestId(slice),
                    cutoff);
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

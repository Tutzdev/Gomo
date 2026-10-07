package br.com.supermercados.prices.collection;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import br.com.supermercados.prices.price.PricePolicy;

/**
 * Whether each market still has current prices. A collector that breaks (a store redesigns its site) only
 * shows up in the logs, and two days later its market silently leaves every comparison; this report makes
 * that visible to the admin and to an external uptime monitor while the prices are still current.
 */
@Service
public class CollectionHealthService {

    private final CollectionCoordinator coordinator;
    private final JdbcTemplate jdbc;
    private final PricePolicy pricePolicy;
    private final Clock clock;
    private final Duration staleAfter;

    public CollectionHealthService(CollectionCoordinator coordinator, JdbcTemplate jdbc, PricePolicy pricePolicy, Clock clock,
            @Value("${app.collection.stale-after:PT12H}") Duration staleAfter) {
        if (staleAfter.isNegative() || staleAfter.isZero()) {
            throw new IllegalArgumentException("app.collection.stale-after deve ser positivo");
        }
        this.coordinator = coordinator;
        this.jdbc = jdbc;
        this.pricePolicy = pricePolicy;
        this.clock = clock;
        this.staleAfter = staleAfter;
    }

    public CollectionHealthReport report() {
        Instant now = clock.instant();
        Instant oldestCurrent = now.minus(pricePolicy.maxAge());
        Map<String, LastRun> lastRuns = lastRuns();
        Map<String, Instant> lastSuccesses = lastSuccesses();
        Map<UUID, List<CollectorHealth>> collectorsByStore = new LinkedHashMap<>();
        Map<UUID, String> storeNames = new HashMap<>();
        for (var metadata : coordinator.collectors()) {
            LastRun run = lastRuns.get(metadata.code());
            boolean scheduled = coordinator.isScheduled(metadata.code());
            if (run == null && !scheduled) continue;
            UUID storeId = run == null ? null : run.storeId();
            var collector = new CollectorHealth(metadata.code(), scheduled, run == null ? null : run.status(),
                    run == null ? null : run.startedAt(), lastSuccesses.get(metadata.code()),
                    run == null ? null : run.errorMessage());
            collectorsByStore.computeIfAbsent(storeId, ignored -> new ArrayList<>()).add(collector);
            storeNames.putIfAbsent(storeId, metadata.storeName());
        }

        List<MarketHealth> markets = new ArrayList<>();
        for (var store : activeStores()) {
            List<CollectorHealth> collectors = collectorsByStore.getOrDefault(store.id(), List.of());
            boolean hasSource = collectors.stream().anyMatch(CollectorHealth::scheduled);
            Instant lastPrice = store.lastPriceAt();
            MarketStatus status;
            if (!hasSource) status = MarketStatus.NO_SOURCE;
            else if (lastPrice == null || !lastPrice.isAfter(oldestCurrent)) status = MarketStatus.DOWN;
            else if (!lastPrice.isAfter(now.minus(staleAfter))) status = MarketStatus.STALE;
            else status = MarketStatus.OK;
            long currentProducts = lastPrice == null || !lastPrice.isAfter(oldestCurrent) ? 0 : currentProducts(store.id(), oldestCurrent);
            markets.add(new MarketHealth(store.id(), store.name(), status, lastPrice, currentProducts, collectors));
        }
        // A collector whose store was never registered (its first run failed) is still a market that is down.
        collectorsByStore.forEach((storeId, collectors) -> {
            if (storeId == null && collectors.stream().anyMatch(CollectorHealth::scheduled)) {
                markets.add(new MarketHealth(null, storeNames.get(null), MarketStatus.DOWN, null, 0, collectors));
            }
        });
        boolean healthy = markets.stream().noneMatch(market -> market.status() == MarketStatus.DOWN || market.status() == MarketStatus.STALE);
        return new CollectionHealthReport(now, healthy, staleAfter, pricePolicy.maxAge(), markets);
    }

    private Map<String, LastRun> lastRuns() {
        Map<String, LastRun> runs = new HashMap<>();
        jdbc.query("""
                SELECT DISTINCT ON (collector_code) collector_code, store_id, status, started_at, error_message
                FROM collection_runs ORDER BY collector_code, started_at DESC, id DESC
                """, row -> {
            runs.put(row.getString("collector_code"), new LastRun(row.getObject("store_id", UUID.class),
                    CollectionStatus.valueOf(row.getString("status")), row.getTimestamp("started_at").toInstant(),
                    row.getString("error_message")));
        });
        return runs;
    }

    private Map<String, Instant> lastSuccesses() {
        Map<String, Instant> successes = new HashMap<>();
        jdbc.query("""
                SELECT collector_code, max(finished_at) AS finished_at FROM collection_runs
                WHERE status IN ('SUCCESS', 'PARTIAL') GROUP BY collector_code
                """, row -> {
            successes.put(row.getString("collector_code"), row.getTimestamp("finished_at").toInstant());
        });
        return successes;
    }

    private List<StoreFreshness> activeStores() {
        return jdbc.query("""
                SELECT s.id, s.name, (SELECT max(p.collected_at) FROM price_records p WHERE p.store_id = s.id) AS last_price
                FROM stores s WHERE s.active ORDER BY s.name, s.id
                """, (row, index) -> {
            Timestamp lastPrice = row.getTimestamp("last_price");
            return new StoreFreshness(row.getObject("id", UUID.class), row.getString("name"),
                    lastPrice == null ? null : lastPrice.toInstant());
        });
    }

    private long currentProducts(UUID storeId, Instant oldestCurrent) {
        Long count = jdbc.queryForObject("""
                SELECT count(DISTINCT product_id) FROM price_records
                WHERE store_id = ? AND collected_at > ? AND availability <> 'UNAVAILABLE'
                """, Long.class, storeId, Timestamp.from(oldestCurrent));
        return count == null ? 0 : count;
    }

    public enum MarketStatus {
        /** Prices collected within {@code app.collection.stale-after}. */
        OK,
        /** Prices still shown, but the last collections failed; fix before they expire. */
        STALE,
        /** No current price: the market is hidden from searches and comparisons. */
        DOWN,
        /** No public price source; the market is hidden and nothing is expected to collect it. */
        NO_SOURCE
    }

    public record CollectionHealthReport(Instant checkedAt, boolean healthy, Duration staleAfter, Duration priceMaxAge,
            List<MarketHealth> markets) {
    }

    public record MarketHealth(UUID storeId, String storeName, MarketStatus status, Instant lastPriceAt,
            long currentProducts, List<CollectorHealth> collectors) {
    }

    public record CollectorHealth(String code, boolean scheduled, CollectionStatus lastStatus, Instant lastRunAt,
            Instant lastSuccessAt, String lastError) {
    }

    private record LastRun(UUID storeId, CollectionStatus status, Instant startedAt, String errorMessage) {
    }

    private record StoreFreshness(UUID id, String name, Instant lastPriceAt) {
    }
}

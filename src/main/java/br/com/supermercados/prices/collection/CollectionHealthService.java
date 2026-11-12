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

    /** A market with prices written down at the shelf needs a visit when they expire within this window. */
    static final Duration VISIT_DUE_BEFORE = Duration.ofDays(2);

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
        Map<String, UUID> storeOfCollector = storesOfCollectors();
        Map<UUID, List<CollectorHealth>> collectorsByStore = new LinkedHashMap<>();
        List<MarketHealth> neverRegistered = new ArrayList<>();
        for (var metadata : coordinator.collectors()) {
            LastRun run = lastRuns.get(metadata.code());
            boolean scheduled = coordinator.isScheduled(metadata.code());
            if (run == null && !scheduled) continue;
            // A run in progress (or one that failed before reaching the store) has no store yet.
            UUID storeId = storeOfCollector.get(metadata.code());
            var collector = new CollectorHealth(metadata.code(), scheduled, run == null ? null : run.status(),
                    run == null ? null : run.startedAt(), lastSuccesses.get(metadata.code()),
                    run == null ? null : run.errorMessage());
            if (storeId != null) {
                collectorsByStore.computeIfAbsent(storeId, ignored -> new ArrayList<>()).add(collector);
            } else if (scheduled && (run == null || run.status() != CollectionStatus.RUNNING)) {
                // Its store was never registered (every run failed before reaching it): a market that is down.
                neverRegistered.add(new MarketHealth(null, metadata.storeName(), MarketStatus.DOWN, null, null, 0,
                        List.of(collector)));
            }
        }

        List<MarketHealth> markets = new ArrayList<>();
        for (var store : activeStores()) {
            List<CollectorHealth> collectors = collectorsByStore.getOrDefault(store.id(), List.of());
            if (store.inStore()) {
                markets.add(inStoreMarket(store, collectors, now));
                continue;
            }
            boolean hasSource = collectors.stream().anyMatch(CollectorHealth::scheduled);
            Instant lastPrice = store.lastPriceAt();
            MarketStatus status;
            if (!hasSource) status = MarketStatus.NO_SOURCE;
            else if (lastPrice == null || !lastPrice.isAfter(oldestCurrent)) status = MarketStatus.DOWN;
            else if (!lastPrice.isAfter(now.minus(staleAfter))) status = MarketStatus.STALE;
            else status = MarketStatus.OK;
            long currentProducts = lastPrice == null || !lastPrice.isAfter(oldestCurrent) ? 0 : currentProducts(store.id(), oldestCurrent);
            Instant expiresAt = lastPrice == null ? null : lastPrice.plus(pricePolicy.maxAge());
            markets.add(new MarketHealth(store.id(), store.name(), status, lastPrice, expiresAt, currentProducts, collectors));
        }
        markets.addAll(neverRegistered);
        boolean healthy = markets.stream().noneMatch(market -> market.status() == MarketStatus.DOWN || market.status() == MarketStatus.STALE);
        return new CollectionHealthReport(now, healthy, staleAfter, pricePolicy.maxAge(), markets);
    }

    /**
     * A market without an online store: the Gomo team writes its prices down at the shelf and they stay current
     * for {@code app.prices.in-store-max-age}. Nothing collects it, so instead of NO_SOURCE it reports when the
     * next visit is due. It never makes the report unhealthy: a visit that is late is a chore, not an outage.
     */
    private MarketHealth inStoreMarket(StoreFreshness store, List<CollectorHealth> collectors, Instant now) {
        Instant lastVisit = store.lastPriceAt();
        Instant expiresAt = lastVisit == null ? null : lastVisit.plus(pricePolicy.inStoreMaxAge());
        MarketStatus status;
        if (expiresAt == null || !expiresAt.isAfter(now)) status = MarketStatus.VISIT_OVERDUE;
        else if (!expiresAt.isAfter(now.plus(VISIT_DUE_BEFORE))) status = MarketStatus.VISIT_DUE;
        else status = MarketStatus.VISITED;
        long currentProducts = status == MarketStatus.VISIT_OVERDUE ? 0
                : currentProducts(store.id(), now.minus(pricePolicy.inStoreMaxAge()));
        return new MarketHealth(store.id(), store.name(), status, lastVisit, expiresAt, currentProducts, collectors);
    }

    private Map<String, LastRun> lastRuns() {
        Map<String, LastRun> runs = new HashMap<>();
        jdbc.query("""
                SELECT DISTINCT ON (collector_code) collector_code, status, started_at, error_message
                FROM collection_runs ORDER BY collector_code, started_at DESC, id DESC
                """, row -> {
            runs.put(row.getString("collector_code"), new LastRun(CollectionStatus.valueOf(row.getString("status")),
                    row.getTimestamp("started_at").toInstant(), row.getString("error_message")));
        });
        return runs;
    }

    private Map<String, UUID> storesOfCollectors() {
        Map<String, UUID> stores = new HashMap<>();
        jdbc.query("""
                SELECT DISTINCT ON (collector_code) collector_code, store_id
                FROM collection_runs WHERE store_id IS NOT NULL ORDER BY collector_code, started_at DESC, id DESC
                """, row -> {
            stores.put(row.getString("collector_code"), row.getObject("store_id", UUID.class));
        });
        return stores;
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
                SELECT s.id, s.name, s.price_channel = 'IN_STORE' AS in_store,
                    (SELECT max(p.collected_at) FROM price_records p WHERE p.store_id = s.id) AS last_price
                FROM stores s WHERE s.active ORDER BY s.name, s.id
                """, (row, index) -> {
            Timestamp lastPrice = row.getTimestamp("last_price");
            return new StoreFreshness(row.getObject("id", UUID.class), row.getString("name"),
                    row.getBoolean("in_store"), lastPrice == null ? null : lastPrice.toInstant());
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
        NO_SOURCE,
        /** No online store; the prices written down at the last visit are current. */
        VISITED,
        /** No online store; the prices from the last visit expire within {@link #VISIT_DUE_BEFORE}. */
        VISIT_DUE,
        /** No online store and no current price (never visited, or the visit expired): the market is hidden. */
        VISIT_OVERDUE;

        /** Statuses of markets whose prices come from store visits, not from a collector. */
        public boolean inStore() {
            return this == VISITED || this == VISIT_DUE || this == VISIT_OVERDUE;
        }
    }

    public record CollectionHealthReport(Instant checkedAt, boolean healthy, Duration staleAfter, Duration priceMaxAge,
            List<MarketHealth> markets) {
    }

    /** {@code priceExpiresAt}: when the newest price stops counting as current (null without a price). */
    public record MarketHealth(UUID storeId, String storeName, MarketStatus status, Instant lastPriceAt,
            Instant priceExpiresAt, long currentProducts, List<CollectorHealth> collectors) {
    }

    public record CollectorHealth(String code, boolean scheduled, CollectionStatus lastStatus, Instant lastRunAt,
            Instant lastSuccessAt, String lastError) {
    }

    private record LastRun(CollectionStatus status, Instant startedAt, String errorMessage) {
    }

    private record StoreFreshness(UUID id, String name, boolean inStore, Instant lastPriceAt) {
    }
}

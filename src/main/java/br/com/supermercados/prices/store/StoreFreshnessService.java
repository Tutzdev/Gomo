package br.com.supermercados.prices.store;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.supermercados.prices.price.PriceOriginType;
import br.com.supermercados.prices.price.PricePolicy;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreFreshnessService {

    private static final String LAST_COLLECTIONS = """
            SELECT s.id, (SELECT max(r.collected_at) FROM price_records r WHERE r.store_id = s.id) AS last_collected
            FROM stores s
            WHERE s.id IN (:storeIds)
            """;

    private static final String STORES_OUT_OF_STOCK = """
            SELECT DISTINCT latest.store_id
            FROM latest_prices latest
            WHERE latest.store_id IN (:storeIds)
              AND latest.product_id IN (:productIds)
              AND latest.availability = 'UNAVAILABLE'
              AND latest.collected_at > :freshAfter
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final PricePolicy policy;

    public List<MissingStoreResponse> explainMissing(
        Collection<StoreResponse> missing,
        Collection<UUID> productIds,
        Instant now) {

        if (missing.isEmpty()) {return List.of();}

        List<UUID> storeIds = missing.stream().map(StoreResponse::id).toList();
        Map<UUID, Instant> lastCollected = lastCollections(storeIds);
        Set<UUID> outOfStock = storesOutOfStock(storeIds, productIds, now);

        return missing.stream()
                .map(store -> new MissingStoreResponse(
                    store.id(),
                    store.name(),
                    reason(store, lastCollected.get(store.id()), outOfStock.contains(store.id()), now),
                    lastCollected.get(store.id())))
                .toList();
    }

    private MissingPriceReason reason(StoreResponse store, Instant lastCollected, boolean outOfStock, Instant now) {
        if (store.priceChannel() == StorePriceChannel.IN_STORE) {return MissingPriceReason.NOT_CHECKED_IN_STORE;}

        if (lastCollected == null || lastCollected.isBefore(now.minus(policy.maxAge(PriceOriginType.SOURCE)))) {
            return MissingPriceReason.OUTDATED_PRICES;
        }

        return outOfStock ? MissingPriceReason.OUT_OF_STOCK : MissingPriceReason.NOT_SOLD_ONLINE;
    }

    private Map<UUID, Instant> lastCollections(List<UUID> storeIds) {
        Map<UUID, Instant> lastCollected = new HashMap<>();

        jdbc.query(LAST_COLLECTIONS, new MapSqlParameterSource("storeIds", storeIds), row -> {
            Timestamp collected = row.getTimestamp("last_collected");

            if (collected != null) {lastCollected.put(row.getObject("id", UUID.class), collected.toInstant());}
        });

        return lastCollected;
    }

    private Set<UUID> storesOutOfStock(List<UUID> storeIds, Collection<UUID> productIds, Instant now) {
        if (productIds.isEmpty()) {return Set.of();}

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("storeIds", storeIds)
                .addValue("productIds", productIds)
                .addValue("freshAfter", Timestamp.from(now.minus(policy.maxAge(PriceOriginType.SOURCE))));

        Set<UUID> outOfStock = new HashSet<>();

        jdbc.query(STORES_OUT_OF_STOCK, parameters, row -> {
            outOfStock.add(row.getObject("store_id", UUID.class));
        });

        return outOfStock;
    }
}

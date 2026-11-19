package br.com.supermercados.prices.store;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    private final NamedParameterJdbcTemplate jdbc;
    private final PricePolicy policy;

    public List<MissingStoreResponse> explainMissing(Collection<StoreResponse> missing, Instant now) {
        if (missing.isEmpty()) {return List.of();}

        Map<UUID, Instant> lastCollected = lastCollections(missing.stream().map(StoreResponse::id).toList());

        return missing.stream()
                .map(store -> new MissingStoreResponse(
                    store.id(),
                    store.name(),
                    reason(store, lastCollected.get(store.id()), now),
                    lastCollected.get(store.id())))
                .toList();
    }

    private MissingPriceReason reason(StoreResponse store, Instant lastCollected, Instant now) {
        boolean inStore = store.priceChannel() == StorePriceChannel.IN_STORE;
        PriceOriginType origin = inStore ? PriceOriginType.STORE_VISIT : PriceOriginType.SOURCE;
        boolean outdated = lastCollected == null || lastCollected.isBefore(now.minus(policy.maxAge(origin)));

        if (inStore) {return MissingPriceReason.NOT_CHECKED_IN_STORE;}

        return outdated ? MissingPriceReason.OUTDATED_PRICES : MissingPriceReason.NOT_SOLD_ONLINE;
    }

    private Map<UUID, Instant> lastCollections(List<UUID> storeIds) {
        Map<UUID, Instant> lastCollected = new HashMap<>();

        jdbc.query(LAST_COLLECTIONS, new MapSqlParameterSource("storeIds", storeIds), row -> {
            Timestamp collected = row.getTimestamp("last_collected");

            if (collected != null) {lastCollected.put(row.getObject("id", UUID.class), collected.toInstant());}
        });

        return lastCollected;
    }
}

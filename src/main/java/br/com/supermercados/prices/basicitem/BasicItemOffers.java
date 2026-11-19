package br.com.supermercados.prices.basicitem;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.supermercados.prices.price.PricePolicy;
import br.com.supermercados.prices.price.PriceQuote;
import br.com.supermercados.prices.price.PriceRecord;
import br.com.supermercados.prices.price.PriceRecordRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BasicItemOffers {

    private static final String LINKED_PRODUCTS = """
            SELECT product_id, basic_item, package_amount
            FROM basic_item_products
            WHERE basic_item IN (:items)
            """;

    private static final String ITEM_OF_CATALOG_ITEM = """
            SELECT link.basic_item
            FROM catalog_item_products catalog
            JOIN basic_item_products link ON link.product_id = catalog.product_id
            WHERE catalog.catalog_item_id = :catalogItemId
            GROUP BY link.basic_item
            ORDER BY count(*) DESC, link.basic_item
            LIMIT 1
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final PriceRecordRepository prices;
    private final PricePolicy policy;

    public Map<UUID, BasicItemOffer> cheapestByStore(BasicItem item, Collection<UUID> storeIds, Instant now) {
        return cheapestByStore(List.of(item), storeIds, now).getOrDefault(item.slug(), Map.of());
    }

    public Map<String, Map<UUID, BasicItemOffer>> cheapestByStore(
        Collection<BasicItem> items,
        Collection<UUID> storeIds,
        Instant now) {

        if (items.isEmpty() || storeIds.isEmpty()) {return Map.of();}

        Map<UUID, LinkedProduct> linked = linkedProducts(items);

        if (linked.isEmpty()) {return Map.of();}

        Map<String, Map<UUID, BasicItemOffer>> cheapest = new HashMap<>();

        for (PriceRecord record : prices.findLatestForStoresAndProducts(storeIds, linked.keySet())) {
            PriceQuote quote = policy.quote(record, now);

            if (quote.unitPrice() == null) {continue;}

            LinkedProduct product = linked.get(record.getProductId());
            BasicItemOffer offer = BasicItemOffer.of(product.item(), record, quote, product.packageAmount());

            cheapest.computeIfAbsent(product.item().slug(), ignored -> new HashMap<>())
                    .merge(record.getStoreId(), offer, BasicItemOffer::cheaper);
        }

        return cheapest;
    }

    public Optional<BasicItem> itemOfCatalogItem(UUID catalogItemId) {
        List<String> found = jdbc.queryForList(
            ITEM_OF_CATALOG_ITEM,
            new MapSqlParameterSource("catalogItemId", catalogItemId),
            String.class);

        return found.stream().findFirst().flatMap(BasicItems::find);
    }

    public Optional<UUID> anyLinkedProduct(BasicItem item, Collection<UUID> excluded) {
        return linkedProducts(List.of(item)).keySet().stream()
                .filter(productId -> !excluded.contains(productId))
                .sorted()
                .findFirst();
    }

    private Map<UUID, LinkedProduct> linkedProducts(Collection<BasicItem> items) {
        Map<String, BasicItem> bySlug = new HashMap<>();
        items.forEach(item -> bySlug.put(item.slug(), item));

        Map<UUID, LinkedProduct> linked = new HashMap<>();

        jdbc.query(LINKED_PRODUCTS, new MapSqlParameterSource("items", bySlug.keySet()), row -> {
            linked.put(row.getObject("product_id", UUID.class), new LinkedProduct(
                bySlug.get(row.getString("basic_item")),
                row.getBigDecimal("package_amount")));
        });

        return linked;
    }

    private record LinkedProduct(BasicItem item, BigDecimal packageAmount) {}
}

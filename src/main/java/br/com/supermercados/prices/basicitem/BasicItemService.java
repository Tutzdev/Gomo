package br.com.supermercados.prices.basicitem;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.supermercados.prices.common.ApiException;
import br.com.supermercados.prices.product.Product;
import br.com.supermercados.prices.product.ProductRepository;
import br.com.supermercados.prices.store.StoreFreshnessService;
import br.com.supermercados.prices.store.StoreResponse;
import br.com.supermercados.prices.store.StoreService;
import br.com.supermercados.prices.subscription.ComparisonAccess;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BasicItemService {

    private static final int MAXIMUM_STORES = 5000;

    private static final String CATALOG_ITEMS_OF_PRODUCTS = """
            SELECT link.product_id, link.catalog_item_id
            FROM catalog_item_products link
            JOIN catalog_items item ON item.id = link.catalog_item_id AND item.active
            WHERE link.product_id IN (:productIds)
            """;

    private static final Comparator<BasicItemOffer> STORE_RANKING = Comparator
            .comparing(BasicItemOffer::pricePerMeasure, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(offer -> offer.price().unitPrice());

    private final BasicItemOffers offers;
    private final StoreService stores;
    private final StoreFreshnessService freshness;
    private final ProductRepository products;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public static BasicItem require(String slug) {
        return BasicItems.find(slug)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Item básico não encontrado."));
    }

    public List<BasicItemResponse> search(String query, UUID cityId) {
        List<BasicItem> found = BasicItems.search(query);

        if (cityId == null) {return found.stream().map(BasicItemService::withoutPrices).toList();}

        Map<UUID, String> storeNames = storeNames(allStores(cityId));

        Map<String, Map<UUID, BasicItemOffer>> cheapest = offers.cheapestByStore(
            found,
            storeNames.keySet(),
            clock.instant());

        return found.stream()
                .map(item -> summary(item, cheapest.getOrDefault(item.slug(), Map.of()), storeNames))
                .toList();
    }

    public Optional<BasicItemResponse> forCatalogItem(UUID catalogItemId, UUID cityId) {
        Optional<BasicItem> item = offers.itemOfCatalogItem(catalogItemId);

        if (item.isEmpty() || cityId == null) {return item.map(BasicItemService::withoutPrices);}

        Map<UUID, String> storeNames = storeNames(allStores(cityId));
        Map<UUID, BasicItemOffer> byStore = offers.cheapestByStore(item.get(), storeNames.keySet(), clock.instant());

        return Optional.of(summary(item.get(), byStore, storeNames));
    }

    public BasicItemComparisonResponse compare(
        String slug,
        UUID cityId,
        List<UUID> storeIds,
        ComparisonAccess access) {

        BasicItem item = require(slug);
        Instant now = clock.instant();
        List<StoreResponse> selected = stores.selectComparisonStores(cityId, storeIds, MAXIMUM_STORES);
        List<StoreResponse> ranked = access.premium() ? selected : allStores(cityId);
        Map<UUID, String> storeNames = storeNames(ranked);
        Map<UUID, BasicItemOffer> byStore = offers.cheapestByStore(item, storeNames.keySet(), now);

        Set<UUID> selectedIds = selected.stream().map(StoreResponse::id).collect(Collectors.toSet());
        List<BasicItemOffer> rankedOffers = byStore.values().stream().sorted(STORE_RANKING).toList();

        List<BasicItemOffer> selectedOffers = rankedOffers.stream()
                .filter(offer -> selectedIds.contains(offer.storeId()))
                .toList();

        List<BasicItemOffer> visibleOffers = access.visibleOf(rankedOffers).stream()
                .filter(offer -> selectedIds.contains(offer.storeId()))
                .toList();

        List<StoreResponse> missing = selected.stream().filter(store -> !byStore.containsKey(store.id())).toList();

        return new BasicItemComparisonResponse(
            summary(item, byStore, storeNames(selected)),
            now,
            storeOffers(item, visibleOffers, storeNames),
            freshness.explainMissing(missing, offers.productIdsOf(item), now),
            access.withLockedStores(selectedOffers.size() - visibleOffers.size()));
    }

    public BasicItemCoverageResponse coverage(UUID cityId) {
        List<StoreResponse> selected = allStores(cityId);
        Map<UUID, String> storeNames = storeNames(selected);

        Map<String, Map<UUID, BasicItemOffer>> cheapest = offers.cheapestByStore(
            BasicItems.all(),
            storeNames.keySet(),
            clock.instant());

        List<BasicItemCoverageResponse.Item> items = BasicItems.all().stream()
                .map(item -> new BasicItemCoverageResponse.Item(
                    item.slug(),
                    item.name(),
                    item.aisle(),
                    List.copyOf(cheapest.getOrDefault(item.slug(), Map.of()).keySet())))
                .toList();

        List<BasicItemCoverageResponse.Store> coveredStores = selected.stream()
                .map(store -> new BasicItemCoverageResponse.Store(
                    store.id(),
                    store.name(),
                    (int) items.stream().filter(item -> item.pricedStoreIds().contains(store.id())).count()))
                .toList();

        return new BasicItemCoverageResponse(coveredStores, items);
    }

    private BasicItemResponse summary(BasicItem item, Map<UUID, BasicItemOffer> byStore, Map<UUID, String> storeNames) {
        BasicItemOffer cheapest = byStore.values().stream().min(STORE_RANKING).orElse(null);

        return BasicItemResponse.of(
            item,
            byStore.size(),
            storeNames.size(),
            cheapest,
            cheapest == null ? null : storeNames.get(cheapest.storeId()));
    }

    private List<BasicItemComparisonResponse.StoreOffer> storeOffers(
        BasicItem item,
        List<BasicItemOffer> ranked,
        Map<UUID, String> storeNames) {

        List<UUID> productIds = ranked.stream().map(BasicItemOffer::productId).distinct().toList();

        Map<UUID, Product> byId = products.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        Map<UUID, UUID> catalogItems = catalogItemsOf(productIds);

        return ranked.stream()
                .map(offer -> new BasicItemComparisonResponse.StoreOffer(
                    offer.storeId(),
                    storeNames.get(offer.storeId()),
                    offer.productId(),
                    catalogItems.get(offer.productId()),
                    productName(offer, byId.get(offer.productId())),
                    offer.packageAmount() == null ? null : item.measure().amountLabel(offer.packageAmount()),
                    offer.pricePerMeasure(),
                    offer.typicalPackage(),
                    offer.record().getOriginUrl(),
                    offer.price()))
                .toList();
    }

    private Map<UUID, UUID> catalogItemsOf(Collection<UUID> productIds) {
        if (productIds.isEmpty()) {return Map.of();}

        Map<UUID, UUID> catalogItems = new HashMap<>();

        jdbc.query(CATALOG_ITEMS_OF_PRODUCTS, new MapSqlParameterSource("productIds", productIds), row -> {
            catalogItems.put(row.getObject("product_id", UUID.class), row.getObject("catalog_item_id", UUID.class));
        });

        return catalogItems;
    }

    private static String productName(BasicItemOffer offer, Product product) {
        String sourceName = offer.record().getSourceProductName();

        if (sourceName != null && !sourceName.isBlank()) {return sourceName;}

        return product == null ? null : product.getName();
    }

    private List<StoreResponse> allStores(UUID cityId) {
        return stores.selectComparisonStores(cityId, null, MAXIMUM_STORES);
    }

    private static BasicItemResponse withoutPrices(BasicItem item) {
        return BasicItemResponse.of(item, 0, 0, null, null);
    }

    private static Map<UUID, String> storeNames(List<StoreResponse> selected) {
        return selected.stream().collect(Collectors.toMap(StoreResponse::id, StoreResponse::name));
    }
}

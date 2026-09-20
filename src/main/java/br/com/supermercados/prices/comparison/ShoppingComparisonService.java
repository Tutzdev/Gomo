package br.com.supermercados.prices.comparison;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.supermercados.prices.common.PageResponse;
import br.com.supermercados.prices.location.LocationService;
import br.com.supermercados.prices.price.PricePolicy;
import br.com.supermercados.prices.product.ProductResponse;
import br.com.supermercados.prices.product.ProductService;
import br.com.supermercados.prices.shoppinglist.ShoppingListItemResponse;
import br.com.supermercados.prices.shoppinglist.ShoppingListService;
import br.com.supermercados.prices.store.StoreResponse;
import br.com.supermercados.prices.store.StoreService;

@Service
@Transactional(readOnly = true)
public class ShoppingComparisonService {

    private static final String CURRENCY = "BRL";
    private static final int MAXIMUM_ALLOWED_RECOMMENDATION_STORES = 5000;
    private static final int MAXIMUM_CLOSEST_MATCHES = 10;

    private final LocationService locations;
    private final StoreService stores;
    private final ProductService products;
    private final ShoppingListService shoppingLists;
    private final EquivalentPriceService prices;
    private final PricePolicy pricePolicy;
    private final ShoppingPriceCalculator calculator;
    private final Clock clock;
    private final int maximumRecommendationStores;

    public ShoppingComparisonService(
            LocationService locations,
            StoreService stores,
            ProductService products,
            ShoppingListService shoppingLists,
            EquivalentPriceService prices,
            PricePolicy pricePolicy,
            ShoppingPriceCalculator calculator,
            Clock clock,
            @Value("${app.recommendations.max-stores:500}") int maximumRecommendationStores) {
        if (maximumRecommendationStores < 1
                || maximumRecommendationStores > MAXIMUM_ALLOWED_RECOMMENDATION_STORES) {
            throw new IllegalArgumentException("app.recommendations.max-stores must be between 1 and 5000");
        }

        this.locations = locations;
        this.stores = stores;
        this.products = products;
        this.shoppingLists = shoppingLists;
        this.prices = prices;
        this.pricePolicy = pricePolicy;
        this.calculator = calculator;
        this.clock = clock;
        this.maximumRecommendationStores = maximumRecommendationStores;
    }

    public ProductComparisonResponse compareProduct(UUID productId, UUID cityId, Pageable pageable) {
        return compareProduct(productId, cityId, pageable, null);
    }

    public ProductComparisonResponse compareProduct(UUID productId, UUID cityId, Pageable pageable, List<UUID> storeIds) {
        var product = products.requireProduct(productId);
        locations.requireCity(cityId);
        Instant comparedAt = clock.instant();
        Page<StoreResponse> availableStores = comparisonStores(cityId, pageable, storeIds);
        var offers = prices.find(List.of(productId), availableStores.stream().map(StoreResponse::id).toList(), comparedAt);
        var latestPricesByStore = offers.byStore();
        Page<ProductStoreComparison> comparisons = availableStores.map(store -> {
            var record = latestPricesByStore.getOrDefault(store.id(), Map.of()).get(productId);
            var quote = pricePolicy.quote(record, comparedAt);
            var matched = offers.matchedProduct(productId, record);
            return new ProductStoreComparison(store.id(), store.name(), quote,
                    offers.measurementPrice(productId, record, quote.unitPrice()),
                    matched == null ? null : ProductResponse.from(matched), store.priceSourceNote());
        });

        return new ProductComparisonResponse(productId, products.comparisonName(product), cityId, CURRENCY, comparedAt,
                PageResponse.from(comparisons), offers.possible(productId).stream().map(ProductResponse::from).toList());
    }

    public ShoppingListComparisonResponse compareShoppingList(
            UUID userId, UUID listId, UUID cityId, Pageable pageable) {
        return compareShoppingList(userId, listId, cityId, pageable, null);
    }

    public ShoppingListComparisonResponse compareShoppingList(
            UUID userId, UUID listId, UUID cityId, Pageable pageable, List<UUID> storeIds) {
        var shoppingList = shoppingLists.getOwnedList(userId, listId);
        locations.requireCity(cityId);
        Instant comparedAt = clock.instant();
        Page<StoreResponse> availableStores = comparisonStores(cityId, pageable, storeIds);
        List<UUID> productIds = shoppingList.items().stream()
                .map(ShoppingListItemResponse::productId).toList();
        var offers = prices.find(productIds, availableStores.stream().map(StoreResponse::id).toList(), comparedAt);
        Page<ShoppingStoreComparison> comparisons = availableStores.map(store ->
                calculateStore(store, shoppingList.items(), offers, comparedAt));

        return new ShoppingListComparisonResponse(listId, cityId, CURRENCY, comparedAt,
                PageResponse.from(comparisons));
    }

    public ShoppingRecommendationResponse recommendShoppingList(UUID userId, UUID listId, UUID cityId) {
        return recommendShoppingList(userId, listId, cityId, null);
    }

    public ShoppingRecommendationResponse recommendShoppingList(
            UUID userId, UUID listId, UUID cityId, List<UUID> storeIds) {
        var shoppingList = shoppingLists.getOwnedList(userId, listId);
        Instant comparedAt = clock.instant();
        List<StoreResponse> availableStores = stores.selectComparisonStores(cityId, storeIds, maximumRecommendationStores);
        List<UUID> productIds = shoppingList.items().stream()
                .map(ShoppingListItemResponse::productId).toList();
        var offers = prices.find(productIds, availableStores.stream().map(StoreResponse::id).toList(), comparedAt);
        List<ShoppingStoreComparison> comparisons = availableStores.stream()
                .map(store -> calculateStore(store, shoppingList.items(), offers, comparedAt)).toList();
        List<StoreRecommendationCandidate> candidates = comparisons.stream()
                .map(StoreRecommendationCandidate::from).toList();

        StoreRecommendationCandidate recommendation = findCompleteRecommendation(candidates);
        ShoppingCombinationResponse combination = calculator.combine(
                shoppingList.items(), comparisons, recommendation);
        if (recommendation != null) {
            return new ShoppingRecommendationResponse(listId, cityId, CURRENCY, comparedAt, candidates.size(),
                    RecommendationStatus.COMPLETE_STORE_FOUND, recommendation, List.of(), combination);
        }

        List<StoreRecommendationCandidate> closestMatches = findClosestMatches(candidates);
        return new ShoppingRecommendationResponse(listId, cityId, CURRENCY, comparedAt, candidates.size(),
                RecommendationStatus.NO_COMPLETE_STORE, null, closestMatches, combination);
    }

    private StoreRecommendationCandidate findCompleteRecommendation(
            List<StoreRecommendationCandidate> candidates) {
        Comparator<StoreRecommendationCandidate> completeOrder = Comparator
                .comparing(StoreRecommendationCandidate::total)
                .thenComparing(StoreRecommendationCandidate::storeName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(StoreRecommendationCandidate::storeId);
        return candidates.stream()
                .filter(StoreRecommendationCandidate::completeShoppingList)
                .min(completeOrder)
                .orElse(null);
    }

    private List<StoreRecommendationCandidate> findClosestMatches(
            List<StoreRecommendationCandidate> candidates) {
        int bestCoverage = candidates.stream().mapToInt(StoreRecommendationCandidate::pricedItems).max().orElse(0);
        if (bestCoverage == 0) {
            return List.of();
        }
        Comparator<StoreRecommendationCandidate> coverageOrder = Comparator
                .comparing(StoreRecommendationCandidate::total,
                        Comparator.nullsLast(BigDecimal::compareTo))
                .thenComparing(StoreRecommendationCandidate::storeName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(StoreRecommendationCandidate::storeId);
        return candidates.stream()
                .filter(candidate -> candidate.pricedItems() == bestCoverage)
                .sorted(coverageOrder)
                .limit(MAXIMUM_CLOSEST_MATCHES)
                .toList();
    }

    private ShoppingStoreComparison calculateStore(StoreResponse store, List<ShoppingListItemResponse> items,
            EquivalentPriceService.Result offers, Instant comparedAt) {
        var storePrices = offers.byStore().getOrDefault(store.id(), Map.of());
        var calculation = calculator.calculate(store.id(), store.name(), items, storePrices, comparedAt);
        var detailedItems = calculation.items().stream().map(item -> {
            var matched = offers.matchedProduct(item.productId(), storePrices.get(item.productId()));
            return new ComparisonItemResponse(item.productId(), item.productName(), item.quantity(),
                    item.price(), item.lineTotal(), matched == null ? null : ProductResponse.from(matched),
                    !offers.possible(item.productId()).isEmpty());
        }).toList();
        return new ShoppingStoreComparison(store.id(), store.name(), calculation.requestedItems(),
                calculation.pricedItems(), calculation.missingItems(), calculation.subtotalKnown(),
                calculation.completeShoppingList(), detailedItems, store.priceSourceNote());
    }

    private Page<StoreResponse> comparisonStores(UUID cityId, Pageable pageable, List<UUID> storeIds) {
        if (storeIds == null || storeIds.isEmpty()) return stores.findActiveStores(cityId, pageable);
        List<StoreResponse> selected = stores.selectComparisonStores(cityId, storeIds, maximumRecommendationStores);
        int from = (int) Math.min(pageable.getOffset(), selected.size());
        int to = Math.min(from + pageable.getPageSize(), selected.size());
        return new PageImpl<>(selected.subList(from, to), pageable, selected.size());
    }
}

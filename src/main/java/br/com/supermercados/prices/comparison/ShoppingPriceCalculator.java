package br.com.supermercados.prices.comparison;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Component;

import br.com.supermercados.prices.price.PricePolicy;
import br.com.supermercados.prices.price.PriceQuote;
import br.com.supermercados.prices.price.PriceRecord;
import br.com.supermercados.prices.shoppinglist.ShoppingListItemResponse;

@Component
public class ShoppingPriceCalculator {

    /** Bounds the pairs tried (n² / 2) when a city has many stores; the best pairs come from the best stores. */
    private static final int MAXIMUM_PAIR_CANDIDATES = 40;
    /** What a second stop must save, on the same items, to be worth the trip. */
    static final BigDecimal MINIMUM_SECOND_STOP_SAVINGS = new BigDecimal("5.00");

    private final PricePolicy pricePolicy;

    public ShoppingPriceCalculator(PricePolicy pricePolicy) {
        this.pricePolicy = pricePolicy;
    }

    public ShoppingStoreComparison calculate(
            UUID storeId, String storeName, List<ShoppingListItemResponse> requestedItems,
            Map<UUID, PriceRecord> productPrices, Instant comparedAt) {
        List<ComparisonItemResponse> items = requestedItems.stream()
                .map(item -> calculateItem(item, productPrices.get(item.productId()), comparedAt))
                .toList();
        int pricedItems = (int) items.stream().filter(item -> item.lineTotal() != null).count();
        BigDecimal subtotal = pricedItems == 0 ? null : items.stream()
                .map(ComparisonItemResponse::lineTotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        return new ShoppingStoreComparison(storeId, storeName, items.size(), pricedItems,
                items.size() - pricedItems, subtotal, !items.isEmpty() && pricedItems == items.size(), items);
    }

    private ComparisonItemResponse calculateItem(
            ShoppingListItemResponse item, PriceRecord record, Instant comparedAt) {
        PriceQuote quote = pricePolicy.quote(record, comparedAt);
        // Round each priced line to cents before summing, including fractional requested quantities.
        BigDecimal lineTotal = quote.unitPrice() == null ? null
                : quote.unitPrice().multiply(item.quantity()).setScale(2, RoundingMode.HALF_UP);
        return new ComparisonItemResponse(item.productId(), item.productName(), item.quantity(),
                quote, lineTotal);
    }

    public ShoppingCombinationResponse combine(List<ShoppingListItemResponse> requestedItems,
            List<ShoppingStoreComparison> comparisons, StoreRecommendationCandidate completeStore) {
        Map<UUID, StoreItem> cheapestItems = new LinkedHashMap<>();
        for (ShoppingStoreComparison store : comparisons) {
            for (ComparisonItemResponse item : store.items()) {
                if (item.lineTotal() == null) {
                    continue;
                }
                StoreItem previous = cheapestItems.get(item.productId());
                boolean preferCompleteStore = completeStore != null && store.storeId().equals(completeStore.storeId());
                if (previous == null || item.lineTotal().compareTo(previous.item().lineTotal()) < 0
                        || (item.lineTotal().compareTo(previous.item().lineTotal()) == 0 && preferCompleteStore)) {
                    cheapestItems.put(item.productId(), new StoreItem(store.storeId(), store.storeName(), item));
                }
            }
        }

        Map<UUID, List<ComparisonItemResponse>> itemsByStore = new LinkedHashMap<>();
        Map<UUID, String> storeNames = new LinkedHashMap<>();
        List<UUID> missing = new ArrayList<>();
        for (ShoppingListItemResponse requested : requestedItems) {
            StoreItem chosen = cheapestItems.get(requested.productId());
            if (chosen == null) {
                missing.add(requested.productId());
            } else {
                itemsByStore.computeIfAbsent(chosen.storeId(), ignored -> new ArrayList<>()).add(chosen.item());
                storeNames.put(chosen.storeId(), chosen.storeName());
            }
        }
        List<ShoppingCombinationResponse.StorePurchase> purchases = itemsByStore.entrySet().stream()
                .map(entry -> new ShoppingCombinationResponse.StorePurchase(entry.getKey(), storeNames.get(entry.getKey()),
                        entry.getValue().stream().map(ComparisonItemResponse::lineTotal)
                                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add), entry.getValue()))
                .toList();
        BigDecimal total = purchases.isEmpty() ? null : purchases.stream()
                .map(ShoppingCombinationResponse.StorePurchase::subtotal)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        boolean complete = !requestedItems.isEmpty() && missing.isEmpty();
        BigDecimal savings = complete && completeStore != null
                ? completeStore.total().subtract(total) : null;
        return new ShoppingCombinationResponse(requestedItems.size(), cheapestItems.size(), missing,
                total, complete, savings, purchases);
    }

    /**
     * The best purchase split across at most two stores: the one that prices the most items, then the lowest
     * total, then the fewest stores. Buying each item where it is cheapest can mean a trip to every market; two
     * stops is what people actually do, so this is the split the screen recommends first. A second stop that
     * only adds items already priced has to save at least {@link #MINIMUM_SECOND_STOP_SAVINGS}: crossing town
     * for R$ 0,40 is not "more practical".
     */
    public ShoppingCombinationResponse combineInTwoStores(List<ShoppingListItemResponse> requestedItems,
            List<ShoppingStoreComparison> comparisons, StoreRecommendationCandidate completeStore) {
        List<ShoppingStoreComparison> candidates = comparisons.stream()
                .filter(store -> store.pricedItems() > 0)
                .sorted(Comparator.comparing(ShoppingStoreComparison::pricedItems, Comparator.reverseOrder())
                        .thenComparing(ShoppingStoreComparison::subtotalKnown,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ShoppingStoreComparison::storeName, String.CASE_INSENSITIVE_ORDER))
                .limit(MAXIMUM_PAIR_CANDIDATES)
                .toList();
        ShoppingCombinationResponse single = null;
        ShoppingCombinationResponse pair = null;
        for (int first = 0; first < candidates.size(); first++) {
            single = better(single, combine(requestedItems, List.of(candidates.get(first)), completeStore));
            for (int second = first + 1; second < candidates.size(); second++) {
                pair = better(pair, combine(requestedItems,
                        List.of(candidates.get(first), candidates.get(second)), completeStore));
            }
        }
        if (single == null) {
            return combine(requestedItems, List.of(), completeStore);
        }
        // A pair whose cheapest lines all come from one store is that store: the single one already covers it.
        if (pair == null || pair.stores().size() < 2 || better(single, pair) == single) {
            return single;
        }
        boolean coversMore = pair.pricedItems() > single.pricedItems();
        boolean savesEnough = single.subtotalKnown().subtract(pair.subtotalKnown())
                .compareTo(MINIMUM_SECOND_STOP_SAVINGS) >= 0;
        return coversMore || savesEnough ? pair : single;
    }

    private static ShoppingCombinationResponse better(ShoppingCombinationResponse current,
            ShoppingCombinationResponse candidate) {
        if (current == null) {
            return candidate;
        }
        Comparator<ShoppingCombinationResponse> order = Comparator
                .comparing(ShoppingCombinationResponse::pricedItems, Comparator.reverseOrder())
                .thenComparing(ShoppingCombinationResponse::subtotalKnown,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(combination -> combination.stores().size());
        return order.compare(candidate, current) < 0 ? candidate : current;
    }

    private record StoreItem(UUID storeId, String storeName, ComparisonItemResponse item) {
    }
}

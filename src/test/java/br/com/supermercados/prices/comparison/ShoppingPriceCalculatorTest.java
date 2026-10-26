package br.com.supermercados.prices.comparison;

import br.com.supermercados.prices.price.PriceFixtures;
import br.com.supermercados.prices.price.PricePolicy;
import br.com.supermercados.prices.price.PriceRecord;
import br.com.supermercados.prices.shoppinglist.ShoppingListItemResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ShoppingPriceCalculatorTest {

    private final Instant now = Instant.parse("2026-09-10T12:00:00Z");
    private final UUID storeId = UUID.randomUUID();
    private final ShoppingPriceCalculator calculator = new ShoppingPriceCalculator(
            new PricePolicy(Duration.ofDays(2)));

    @Test
    void multipliesQuantitiesWithDecimalPrecisionAndRoundsEachLineHalfUp() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        List<ShoppingListItemResponse> items = List.of(item(first, "3.000"), item(second, "1.234"),
                item(third, "0.500"));
        Map<UUID, PriceRecord> prices = Map.of(
                first, PriceFixtures.regular(first, storeId, "0.10", now),
                second, PriceFixtures.regular(second, storeId, "1.25", now),
                third, PriceFixtures.regular(third, storeId, "0.01", now));

        ShoppingStoreComparison result = calculator.calculate(storeId, "Synthetic test store", items, prices, now);

        assertThat(result.subtotalKnown()).isEqualByComparingTo("1.85");
        assertThat(result.subtotalKnown().scale()).isEqualTo(2);
        assertThat(result.items()).extracting(ComparisonItemResponse::lineTotal)
                .containsExactly(new BigDecimal("0.30"), new BigDecimal("1.54"), new BigDecimal("0.01"));
        assertThat(result.completeShoppingList()).isTrue();
        assertThat(result.pricedItems()).isEqualTo(3);
    }

    @Test
    void missingProductIsNeverFreeAndMakesTheListIncomplete() {
        UUID known = UUID.randomUUID();
        UUID missing = UUID.randomUUID();
        var items = List.of(item(known, "2.000"), item(missing, "3.000"));
        var prices = Map.of(known, PriceFixtures.regular(known, storeId, "12.35", now));

        ShoppingStoreComparison result = calculator.calculate(storeId, "Synthetic test store", items, prices, now);

        assertThat(result.subtotalKnown()).isEqualByComparingTo("24.70");
        assertThat(result.completeShoppingList()).isFalse();
        assertThat(result.requestedItems()).isEqualTo(2);
        assertThat(result.pricedItems()).isEqualTo(1);
        assertThat(result.missingItems()).isEqualTo(1);
        assertThat(result.items().get(1).lineTotal()).isNull();
        assertThat(result.items().get(1).price().unitPrice()).isNull();
    }

    @Test
    void entirelyMissingPricesProduceNullSubtotal() {
        var items = List.of(item(UUID.randomUUID(), "2.000"));

        ShoppingStoreComparison result = calculator.calculate(
                storeId, "Synthetic test store", items, Map.of(), now);

        assertThat(result.subtotalKnown()).isNull();
        assertThat(result.completeShoppingList()).isFalse();
        assertThat(result.missingItems()).isEqualTo(1);
    }

    @Test
    void emptyListIsNotPresentedAsACompleteFreePurchase() {
        ShoppingStoreComparison result = calculator.calculate(
                storeId, "Synthetic test store", List.of(), Map.of(), now);

        assertThat(result.subtotalKnown()).isNull();
        assertThat(result.completeShoppingList()).isFalse();
        assertThat(result.requestedItems()).isZero();
    }

    @Test
    void expiredPriceDoesNotContributeToSubtotal() {
        UUID productId = UUID.randomUUID();
        var prices = Map.of(productId, PriceFixtures.regular(
                productId, storeId, "10.00", now.minus(Duration.ofDays(3))));

        ShoppingStoreComparison result = calculator.calculate(storeId, "Synthetic test store",
                List.of(item(productId, "1.000")), prices, now);

        assertThat(result.subtotalKnown()).isNull();
        assertThat(result.pricedItems()).isZero();
        assertThat(result.missingItems()).isEqualTo(1);
    }

    private ShoppingListItemResponse item(UUID productId, String quantity) {
        return new ShoppingListItemResponse(UUID.randomUUID(), productId, "Synthetic test product",
                new BigDecimal(quantity));
    }

    @Test
    void combinesCheapestItemsWithQuantitiesAndComputesSavingsAgainstACompleteStore() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID otherStore = UUID.randomUUID();
        var items = List.of(item(first, "2.000"), item(second, "1.500"));
        var complete = calculator.calculate(storeId, "Complete", items, Map.of(
                first, PriceFixtures.regular(first, storeId, "4.00", now),
                second, PriceFixtures.regular(second, storeId, "6.00", now)), now);
        var partial = calculator.calculate(otherStore, "Partial", items, Map.of(
                second, PriceFixtures.regular(second, otherStore, "2.00", now)), now);
        var winner = new StoreRecommendationCandidate(storeId, "Complete", new BigDecimal("17.00"),
                2, 2, 0, List.of(), true, false);

        var result = calculator.combine(items, List.of(complete, partial), winner);

        assertThat(result.completeShoppingList()).isTrue();
        assertThat(result.subtotalKnown()).isEqualByComparingTo("11.00");
        assertThat(result.savingsAgainstCompleteStore()).isEqualByComparingTo("6.00");
        assertThat(result.stores()).hasSize(2);
        assertThat(result.stores().get(1).items()).singleElement().satisfies(item -> {
            assertThat(item.productId()).isEqualTo(second);
            assertThat(item.quantity()).isEqualByComparingTo("1.500");
            assertThat(item.lineTotal()).isEqualByComparingTo("3.00");
        });
    }

    @Test
    void combinationRetainsMissingItemsAndNeverClaimsPartialSavings() {
        UUID first = UUID.randomUUID();
        UUID missing = UUID.randomUUID();
        var items = List.of(item(first, "2.000"), item(missing, "1.000"));
        var partial = calculator.calculate(storeId, "Partial", items, Map.of(
                first, PriceFixtures.regular(first, storeId, "3.00", now),
                missing, PriceFixtures.regular(missing, storeId, "1.00", now.minus(Duration.ofDays(3)))), now);

        var result = calculator.combine(items, List.of(partial), null);

        assertThat(result.completeShoppingList()).isFalse();
        assertThat(result.subtotalKnown()).isEqualByComparingTo("6.00");
        assertThat(result.missingProductIds()).containsExactly(missing);
        assertThat(result.savingsAgainstCompleteStore()).isNull();
        var noPrices = calculator.combine(items, List.of(), null);
        assertThat(noPrices.subtotalKnown()).isNull();
        assertThat(noPrices.missingProductIds()).containsExactly(first, missing);
        assertThat(calculator.combine(List.of(), List.of(), null).completeShoppingList()).isFalse();
    }

    @Test
    void twoStoreCombinationPicksThePairWithTheLowestTotalInsteadOfVisitingEveryStore() {
        UUID rice = UUID.randomUUID();
        UUID beans = UUID.randomUUID();
        UUID oil = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        var items = List.of(item(rice, "1.000"), item(beans, "1.000"), item(oil, "1.000"));
        var complete = calculator.calculate(storeId, "Complete", items, Map.of(
                rice, PriceFixtures.regular(rice, storeId, "20.00", now),
                beans, PriceFixtures.regular(beans, storeId, "8.00", now),
                oil, PriceFixtures.regular(oil, storeId, "7.00", now)), now);
        var cheapRice = calculator.calculate(second, "Cheap rice", items, Map.of(
                rice, PriceFixtures.regular(rice, second, "15.00", now)), now);
        var cheapOil = calculator.calculate(third, "Cheap oil", items, Map.of(
                oil, PriceFixtures.regular(oil, third, "6.50", now)), now);
        var winner = new StoreRecommendationCandidate(storeId, "Complete", new BigDecimal("35.00"),
                3, 3, 0, List.of(), true, false);

        var everyStore = calculator.combine(items, List.of(complete, cheapRice, cheapOil), winner);
        var pair = calculator.combineInTwoStores(items, List.of(complete, cheapRice, cheapOil), winner);

        assertThat(everyStore.stores()).hasSize(3);
        assertThat(everyStore.subtotalKnown()).isEqualByComparingTo("29.50");
        assertThat(pair.stores()).extracting(ShoppingCombinationResponse.StorePurchase::storeName)
                .containsExactly("Cheap rice", "Complete");
        assertThat(pair.completeShoppingList()).isTrue();
        assertThat(pair.subtotalKnown()).isEqualByComparingTo("30.00");
        assertThat(pair.savingsAgainstCompleteStore()).isEqualByComparingTo("5.00");
    }

    @Test
    void twoStoreCombinationPrefersCoveringMoreItemsOverAPartialCheaperPair() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID cheapStore = UUID.randomUUID();
        UUID otherStore = UUID.randomUUID();
        var items = List.of(item(first, "1.000"), item(second, "1.000"));
        var cheap = calculator.calculate(cheapStore, "Cheap", items, Map.of(
                first, PriceFixtures.regular(first, cheapStore, "1.00", now)), now);
        var other = calculator.calculate(otherStore, "Other", items, Map.of(
                first, PriceFixtures.regular(first, otherStore, "1.50", now)), now);
        var covering = calculator.calculate(storeId, "Covering", items, Map.of(
                second, PriceFixtures.regular(second, storeId, "9.00", now)), now);

        var pair = calculator.combineInTwoStores(items, List.of(cheap, other, covering), null);

        assertThat(pair.pricedItems()).isEqualTo(2);
        assertThat(pair.stores()).extracting(ShoppingCombinationResponse.StorePurchase::storeName)
                .containsExactly("Cheap", "Covering");
        assertThat(pair.savingsAgainstCompleteStore()).isNull();
    }

    @Test
    void twoStoreCombinationKeepsOneStoreWhenSplittingSavesNothing() {
        UUID first = UUID.randomUUID();
        UUID otherStore = UUID.randomUUID();
        var items = List.of(item(first, "1.000"));
        var best = calculator.calculate(storeId, "Best", items, Map.of(
                first, PriceFixtures.regular(first, storeId, "3.00", now)), now);
        var pricier = calculator.calculate(otherStore, "Pricier", items, Map.of(
                first, PriceFixtures.regular(first, otherStore, "4.00", now)), now);

        var pair = calculator.combineInTwoStores(items, List.of(pricier, best), null);

        assertThat(pair.stores()).extracting(ShoppingCombinationResponse.StorePurchase::storeName)
                .containsExactly("Best");
        assertThat(calculator.combineInTwoStores(items, List.of(), null).stores()).isEmpty();
    }
}

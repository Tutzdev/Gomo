package br.com.supermercados.prices.comparison;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@code combination} buys each item where it is cheapest, in as many stores as that takes;
 * {@code twoStoreCombination} is the best split that needs at most two stops. {@code splitSavings} and
 * {@code twoStoreSavings} say what each one saves against the same best single store, also when no store has
 * the whole list.
 */
public record ShoppingRecommendationResponse(
        UUID shoppingListId,
        UUID cityId,
        String currency,
        Instant comparedAt,
        int evaluatedStores,
        RecommendationStatus status,
        StoreRecommendationCandidate recommendation,
        List<StoreRecommendationCandidate> closestMatches,
        ShoppingCombinationResponse combination,
        SplitSavings splitSavings,
        ShoppingCombinationResponse twoStoreCombination,
        SplitSavings twoStoreSavings) {

    public ShoppingRecommendationResponse {
        closestMatches = List.copyOf(closestMatches);
    }
}

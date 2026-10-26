package br.com.supermercados.prices.comparison;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * {@code combination} buys each item where it is cheapest, in as many stores as that takes;
 * {@code twoStoreCombination} is the best split that needs at most two stops.
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
        ShoppingCombinationResponse twoStoreCombination) {

    public ShoppingRecommendationResponse {
        closestMatches = List.copyOf(closestMatches);
    }
}

package br.com.supermercados.prices.comparison;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
        SplitSavings splitSavings) {

    public ShoppingRecommendationResponse {
        closestMatches = List.copyOf(closestMatches);
    }
}

package br.com.supermercados.prices.comparison;

import java.util.UUID;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.supermercados.prices.auth.AuthenticatedUser;
import br.com.supermercados.prices.common.PageRequests;
import br.com.supermercados.prices.subscription.ComparisonUsageService;

@RestController
@RequestMapping("/api/v1/comparisons")
public class ComparisonController {

    private final ShoppingComparisonService comparisons;
    private final ComparisonUsageService usage;

    public ComparisonController(ShoppingComparisonService comparisons, ComparisonUsageService usage) {
        this.comparisons = comparisons;
        this.usage = usage;
    }

    @GetMapping("/products")
    public ProductComparisonResponse product(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam UUID productId,
            @RequestParam UUID cityId,
            @RequestParam(required = false) List<UUID> storeIds,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return comparisons.compareProduct(productId, cityId,
                PageRequests.create(page, size, Sort.by("name", "id")), storeIds, usage.startComparison(user, productId));
    }

    @GetMapping("/shopping-lists/{id}")
    public ShoppingListComparisonResponse shoppingList(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestParam UUID cityId,
            @RequestParam(required = false) List<UUID> storeIds,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return comparisons.compareShoppingList(user.id(), id, cityId,
                PageRequests.create(page, size, Sort.by("name", "id")), storeIds, user.premium());
    }

    @GetMapping("/shopping-lists/{id}/recommendation")
    public ShoppingRecommendationResponse recommendation(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id,
            @RequestParam UUID cityId,
            @RequestParam(required = false) List<UUID> storeIds) {
        return comparisons.recommendShoppingList(user.id(), id, cityId, storeIds, user.premium());
    }
}

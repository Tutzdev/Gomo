package br.com.supermercados.prices.basicitem;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.supermercados.prices.auth.AuthenticatedUser;
import br.com.supermercados.prices.subscription.ComparisonUsageService;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class BasicItemController {

    private final BasicItemService basicItems;
    private final BasicItemLinker linker;
    private final ComparisonUsageService usage;

    @GetMapping("/api/v1/basic-items")
    public List<BasicItemResponse> search(
        @RequestParam(required = false) String query,
        @RequestParam(required = false) UUID cityId) {

        return basicItems.search(query, cityId);
    }

    @GetMapping("/api/v1/basic-items/{slug}")
    public BasicItemComparisonResponse compare(
        @AuthenticationPrincipal AuthenticatedUser user,
        @PathVariable String slug,
        @RequestParam UUID cityId,
        @RequestParam(required = false) List<UUID> storeIds) {

        BasicItem item = BasicItemService.require(slug);

        return basicItems.compare(item.slug(), cityId, storeIds, usage.startComparison(user, item.id()));
    }

    @GetMapping("/api/v1/basic-items/of-catalog-item/{catalogItemId}")
    public ResponseEntity<BasicItemResponse> ofCatalogItem(
        @PathVariable UUID catalogItemId,
        @RequestParam(required = false) UUID cityId) {

        return basicItems.forCatalogItem(catalogItemId, cityId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/api/v1/admin/basic-items/coverage")
    public BasicItemCoverageResponse coverage(@RequestParam UUID cityId) {
        return basicItems.coverage(cityId);
    }

    @PostMapping("/api/v1/admin/basic-items/relink")
    public BasicItemLinker.Summary relink() {
        return linker.relink();
    }
}

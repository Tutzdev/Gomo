package br.com.supermercados.prices.suggestion;

import br.com.supermercados.prices.auth.AbuseProtectionService;
import br.com.supermercados.prices.auth.AuthenticatedUser;
import br.com.supermercados.prices.common.PageRequests;
import br.com.supermercados.prices.common.PageResponse;
import br.com.supermercados.prices.contribution.ContributionStatus;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ProductSuggestionController {

    private final ProductSuggestionService suggestions;
    private final AbuseProtectionService abuseProtection;

    public ProductSuggestionController(ProductSuggestionService suggestions, AbuseProtectionService abuseProtection) {
        this.suggestions = suggestions;
        this.abuseProtection = abuseProtection;
    }

    @PostMapping("/product-suggestions")
    public ResponseEntity<ProductSuggestionResponse> submit(@AuthenticationPrincipal AuthenticatedUser contributor,
            @Valid @RequestBody SubmitProductSuggestionRequest request) {
        abuseProtection.checkContribution(contributor.id());
        ProductSuggestionResponse response = suggestions.submit(contributor, request);
        return ResponseEntity.created(URI.create("/api/v1/product-suggestions/" + response.id())).body(response);
    }

    @GetMapping("/product-suggestions")
    public PageResponse<ProductSuggestionResponse> findMine(@AuthenticationPrincipal AuthenticatedUser contributor,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return suggestions.findMine(contributor.id(),
                PageRequests.create(page, size, Sort.by(Sort.Direction.DESC, "submittedAt", "id")));
    }

    @GetMapping("/admin/product-suggestions")
    public PageResponse<ProductSuggestionResponse> moderationQueue(
            @RequestParam(defaultValue = "PENDING") ContributionStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return suggestions.findForModeration(status,
                PageRequests.create(page, size, Sort.by(Sort.Direction.ASC, "submittedAt", "id")));
    }

    @PostMapping("/admin/product-suggestions/{id}/approval")
    public ProductSuggestionResponse approve(@AuthenticationPrincipal AuthenticatedUser moderator,
            @PathVariable UUID id) {
        return suggestions.approve(moderator.id(), id);
    }

    @PostMapping("/admin/product-suggestions/{id}/rejection")
    public ProductSuggestionResponse reject(@AuthenticationPrincipal AuthenticatedUser moderator,
            @PathVariable UUID id, @Valid @RequestBody RejectProductSuggestionRequest request) {
        return suggestions.reject(moderator.id(), id, request.reason());
    }
}

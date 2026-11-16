package br.com.supermercados.prices.contribution;

import br.com.supermercados.prices.auth.AuthenticatedUser;
import br.com.supermercados.prices.auth.AbuseProtectionService;
import br.com.supermercados.prices.common.PageRequests;
import br.com.supermercados.prices.common.PageResponse;
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
public class PriceContributionController {

    private final PriceContributionService contributions;
    private final AbuseProtectionService abuseProtection;

    public PriceContributionController(PriceContributionService contributions, AbuseProtectionService abuseProtection) {
        this.contributions = contributions;
        this.abuseProtection = abuseProtection;
    }

    @PostMapping("/contributions")
    public ResponseEntity<PriceContributionResponse> submit(
        @AuthenticationPrincipal AuthenticatedUser contributor,
        @Valid @RequestBody SubmitPriceContributionRequest request) {

        abuseProtection.checkContribution(contributor.id());
        PriceContributionResponse response = contributions.submit(contributor, request);

        return ResponseEntity.created(URI.create("/api/v1/contributions/" + response.id())).body(response);
    }

    @GetMapping("/contributions")
    public PageResponse<PriceContributionResponse> findMine(
        @AuthenticationPrincipal AuthenticatedUser contributor,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {

        Sort sort = Sort.by(Sort.Direction.DESC, "submittedAt", "id");

        return contributions.findMine(contributor.id(), PageRequests.create(page, size, sort));
    }

    @GetMapping("/contributions/{id}")
    public PriceContributionResponse findMine(
        @AuthenticationPrincipal AuthenticatedUser contributor,
        @PathVariable UUID id) {

        return contributions.findMine(contributor.id(), id);
    }

    @GetMapping("/admin/contributions")
    public PageResponse<PriceContributionResponse> moderationQueue(
        @RequestParam(defaultValue = "PENDING") ContributionStatus status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {

        Sort sort = Sort.by("submittedAt", "id");

        return contributions.findForModeration(status, PageRequests.create(page, size, sort));
    }

    @PostMapping("/admin/contributions/{id}/approval")
    public PriceContributionResponse approve(
        @AuthenticationPrincipal AuthenticatedUser moderator,
        @PathVariable UUID id) {

        return contributions.approve(moderator.id(), id);
    }

    @PostMapping("/admin/contributions/{id}/rejection")
    public PriceContributionResponse reject(
        @AuthenticationPrincipal AuthenticatedUser moderator,
        @PathVariable UUID id,
        @Valid @RequestBody RejectContributionRequest request) {

        return contributions.reject(moderator.id(), id, request);
    }
}

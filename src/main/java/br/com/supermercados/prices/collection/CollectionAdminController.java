package br.com.supermercados.prices.collection;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.supermercados.prices.admin.AdminAction;
import br.com.supermercados.prices.admin.AdminAuditService;
import br.com.supermercados.prices.auth.AuthenticatedUser;
import br.com.supermercados.prices.common.PageRequests;
import br.com.supermercados.prices.common.PageResponse;

@RestController
@RequestMapping("/api/v1/admin/collections")
public class CollectionAdminController {

    private final CollectionCoordinator coordinator;
    private final CollectionRunService runs;
    private final AdminAuditService audit;
    private final CollectionReviewRepository reviews;

    public CollectionAdminController(
            CollectionCoordinator coordinator,
            CollectionRunService runs,
            AdminAuditService audit,
            CollectionReviewRepository reviews) {
        this.coordinator = coordinator;
        this.runs = runs;
        this.audit = audit;
        this.reviews = reviews;
    }

    @PostMapping
    public List<CollectionRunResponse> collect(
            @RequestParam(required = false) String collectorCode,
            @RequestParam(defaultValue = "false") boolean existingOnly,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        List<CollectionRunResponse> results = existingOnly
                ? coordinator.refreshExisting(collectorCode) : coordinator.collectSelected(collectorCode);
        results.forEach(result -> audit.record(
                actor.id(), AdminAction.COLLECTION_TRIGGERED, "COLLECTION_RUN", result.id()));
        return results;
    }

    @GetMapping
    public PageResponse<CollectionRunResponse> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Sort sort = Sort.by(Sort.Direction.DESC, "startedAt", "id");
        return runs.findAll(PageRequests.create(page, size, sort));
    }

    @GetMapping("/{id}")
    public CollectionRunResponse find(@PathVariable UUID id) {
        return runs.find(id);
    }

    @GetMapping("/reviews")
    public PageResponse<CollectionReviewResponse> findReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return reviews.findAll(PageRequests.create(page, size, Sort.unsorted()));
    }
}

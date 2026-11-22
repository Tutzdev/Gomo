package br.com.supermercados.prices.storevisit;

import br.com.supermercados.prices.auth.AuthenticatedUser;
import br.com.supermercados.prices.common.PageRequests;
import br.com.supermercados.prices.common.PageResponse;
import br.com.supermercados.prices.store.StoreResponse;
import br.com.supermercados.prices.store.StoreService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/stores")
public class StoreVisitController {

    private final StoreVisitService visits;
    private final StoreService stores;

    public StoreVisitController(StoreVisitService visits, StoreService stores) {
        this.visits = visits;
        this.stores = stores;
    }

    @GetMapping
    public PageResponse<StoreResponse> findStores(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "100") int size) {

        return PageResponse.from(stores.findStoresWithStatus(
            null,
            PageRequests.create(page, size, Sort.by("name", "id"))));
    }

    @PutMapping("/{storeId}/visit-validity")
    public StoreResponse changeValidity(
        @AuthenticationPrincipal AuthenticatedUser actor,
        @PathVariable UUID storeId,
        @RequestBody StoreVisitValidityRequest request) {

        return visits.changeValidity(actor.id(), storeId, request.days());
    }

    @PostMapping("/{storeId}/visits")
    public StoreVisitResponse record(
        @AuthenticationPrincipal AuthenticatedUser actor,
        @PathVariable UUID storeId,
        @Valid @RequestBody StoreVisitRequest visit) {

        return visits.record(actor.id(), storeId, visit);
    }
}

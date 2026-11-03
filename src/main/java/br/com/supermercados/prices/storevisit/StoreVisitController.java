package br.com.supermercados.prices.storevisit;

import br.com.supermercados.prices.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/stores")
public class StoreVisitController {

    private final StoreVisitService visits;

    public StoreVisitController(StoreVisitService visits) {
        this.visits = visits;
    }

    @PostMapping("/{storeId}/visits")
    public StoreVisitResponse record(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID storeId,
            @Valid @RequestBody StoreVisitRequest visit) {
        return visits.record(actor.id(), storeId, visit);
    }
}

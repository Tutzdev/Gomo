package br.com.supermercados.prices.purchase;

import br.com.supermercados.prices.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PurchaseController {

    private final TripService trips;
    private final PurchaseService purchases;

    public PurchaseController(TripService trips, PurchaseService purchases) {
        this.trips = trips;
        this.purchases = purchases;
    }

    @GetMapping("/shopping-lists/{id}/trip")
    public TripResponse trip(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id,
            @RequestParam UUID storeId) {
        return trips.trip(user.id(), id, storeId);
    }

    @PostMapping("/shopping-lists/{id}/purchases")
    public PurchaseService.RecordedPurchase record(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id, @Valid @RequestBody RecordPurchaseRequest request) {
        return purchases.record(user.id(), id, request);
    }

    @GetMapping("/savings")
    public SavingsReportResponse savings(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String month) {
        return purchases.month(user.id(), user.premium(), month);
    }

    @DeleteMapping("/purchases/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        purchases.delete(user.id(), id);
        return ResponseEntity.noContent().build();
    }
}

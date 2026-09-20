package br.com.supermercados.prices.alert;

import br.com.supermercados.prices.auth.AuthenticatedUser;
import br.com.supermercados.prices.common.PageRequests;
import br.com.supermercados.prices.common.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class PriceAlertController {

    private final PriceAlertService alerts;
    private final AlertNotificationService notifications;

    public PriceAlertController(PriceAlertService alerts, AlertNotificationService notifications) {
        this.alerts = alerts;
        this.notifications = notifications;
    }

    @PostMapping("/alerts")
    public ResponseEntity<PriceAlertResponse> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreatePriceAlertRequest request) {
        PriceAlertResponse response = alerts.create(user, request);
        
        return ResponseEntity.created(URI.create("/api/v1/alerts/" + response.id())).body(response);
    }

    @GetMapping("/alerts")
    public PageResponse<PriceAlertResponse> findAlerts(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt", "id");

        return alerts.findMine(user.id(), PageRequests.create(page, size, sort));
    }

    @PatchMapping("/alerts/{id}/deactivation")
    public PriceAlertResponse deactivate(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id) {return alerts.deactivate(user.id(), id);}

    @GetMapping("/notifications")
    public PageResponse<AlertNotificationResponse> findNotifications(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt", "id");

        return notifications.findMine(user.id(), PageRequests.create(page, size, sort));
    }

    @PatchMapping("/notifications/{id}/read")
    public AlertNotificationResponse markRead(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID id) {return notifications.markRead(user.id(), id);}
}

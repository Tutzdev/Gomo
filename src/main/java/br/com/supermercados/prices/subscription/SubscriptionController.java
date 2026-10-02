package br.com.supermercados.prices.subscription;

import br.com.supermercados.prices.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/subscription")
public class SubscriptionController {

    private final SubscriptionService subscriptions;

    public SubscriptionController(SubscriptionService subscriptions) {
        this.subscriptions = subscriptions;
    }

    @GetMapping
    public SubscriptionStatusResponse status(@AuthenticationPrincipal AuthenticatedUser user) {
        return subscriptions.status(user.id());
    }

    @PostMapping("/trial")
    public SubscriptionStatusResponse startTrial(@AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody StartTrialRequest request) {
        return subscriptions.startTrial(user.id(), request);
    }

    @DeleteMapping("/trial")
    public SubscriptionStatusResponse cancelTrial(@AuthenticationPrincipal AuthenticatedUser user) {
        return subscriptions.cancelTrial(user.id());
    }

    @GetMapping("/premium-value")
    public PremiumValueResponse premiumValue(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam UUID cityId) {
        return subscriptions.premiumValue(user.id(), cityId);
    }
}

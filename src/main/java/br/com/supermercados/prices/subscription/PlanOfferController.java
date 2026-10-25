package br.com.supermercados.prices.subscription;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * What a new account gets, for visitors who have none yet: during the open beta a free account already sees
 * every market, so the site can say so instead of pointing to Premium.
 */
@RestController
public class PlanOfferController {

    private final PremiumAccess premiumAccess;

    public PlanOfferController(PremiumAccess premiumAccess) {
        this.premiumAccess = premiumAccess;
    }

    @GetMapping("/api/v1/plans/offer")
    public PlanOfferResponse offer() {
        return new PlanOfferResponse(premiumAccess.openBeta());
    }

    public record PlanOfferResponse(boolean openBeta) {
    }
}

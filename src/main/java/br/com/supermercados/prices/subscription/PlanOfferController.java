package br.com.supermercados.prices.subscription;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

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

    public record PlanOfferResponse(boolean openBeta) {}
}

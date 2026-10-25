package br.com.supermercados.prices.subscription;

import br.com.supermercados.prices.user.User;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolves an account's Premium access. While the open beta is on (there is no charging yet), every account
 * without Premium of its own gets {@link PremiumSource#BETA}; the free-plan limits stay in the code for when the
 * beta ends.
 */
@Component
public class PremiumAccess {

    private final boolean openBeta;

    public PremiumAccess(@Value("${app.subscription.open-beta:false}") boolean openBeta) {
        this.openBeta = openBeta;
    }

    public static PremiumAccess withoutBeta() {
        return new PremiumAccess(false);
    }

    public PremiumSource sourceOf(User user, Instant now) {
        PremiumSource source = user.premiumSource(now);
        return source == PremiumSource.NONE && openBeta ? PremiumSource.BETA : source;
    }

    public boolean hasPremium(User user, Instant now) {
        return sourceOf(user, now) != PremiumSource.NONE;
    }

    /** No trial is offered during the beta: the account already has everything the trial would unlock. */
    public boolean trialAvailable(User user, Instant now) {
        return !openBeta && user.isTrialAvailable(now);
    }
}

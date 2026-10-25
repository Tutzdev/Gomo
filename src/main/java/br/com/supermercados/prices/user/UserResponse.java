package br.com.supermercados.prices.user;

import br.com.supermercados.prices.subscription.PremiumAccess;
import br.com.supermercados.prices.subscription.PremiumSource;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String name,
        String email,
        UserRole role,
        boolean emailVerified,
        boolean subscriber,
        boolean premium,
        PremiumSource premiumSource,
        Instant trialEndsAt,
        boolean trialAvailable,
        Instant paidUntil,
        Instant createdAt,
        Instant updatedAt) {

    public static UserResponse from(User user, PremiumAccess access, Instant now) {
        PremiumSource premiumSource = access.sourceOf(user, now);
        Instant trialEndsAt = premiumSource == PremiumSource.TRIAL ? user.getPremiumTrialEndsAt() : null;
        Instant paidUntil = premiumSource == PremiumSource.SUBSCRIPTION ? user.getPremiumPaidUntil() : null;
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole(), user.isEmailVerified(),
                user.isSubscriber(), premiumSource != PremiumSource.NONE, premiumSource, trialEndsAt,
                access.trialAvailable(user, now), paidUntil, user.getCreatedAt(), user.getUpdatedAt());
    }
}

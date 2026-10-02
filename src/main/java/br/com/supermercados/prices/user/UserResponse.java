package br.com.supermercados.prices.user;

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
        Instant createdAt,
        Instant updatedAt) {

    public static UserResponse from(User user, Instant now) {
        PremiumSource premiumSource = user.premiumSource(now);
        Instant trialEndsAt = premiumSource == PremiumSource.TRIAL ? user.getPremiumTrialEndsAt() : null;
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole(), user.isEmailVerified(),
                user.isSubscriber(), premiumSource != PremiumSource.NONE, premiumSource, trialEndsAt,
                user.isTrialAvailable(now), user.getCreatedAt(), user.getUpdatedAt());
    }
}

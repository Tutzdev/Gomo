package br.com.supermercados.prices.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import br.com.supermercados.prices.subscription.BillingCycle;
import br.com.supermercados.prices.subscription.PremiumSource;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "app_users")
public class User {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UserRole role;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(nullable = false)
    private boolean subscriber;

    @Column(name = "premium_trial_started_at")
    private Instant premiumTrialStartedAt;

    @Column(name = "premium_trial_ends_at")
    private Instant premiumTrialEndsAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_billing_cycle", length = 16)
    private BillingCycle preferredBillingCycle;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected User() {
    }

    public User(String name, String email, String passwordHash, Instant now) {
        this.id = UUID.randomUUID();
        this.name = name.strip();
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
        this.role = UserRole.USER;
        this.subscriber = false;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    public void rename(String name, Instant now) {
        this.name = name.strip();
        this.updatedAt = now;
    }

    public void verifyEmail(Instant now) {
        if (emailVerifiedAt == null) {
            emailVerifiedAt = now;
            updatedAt = now;
        }
    }

    public void promoteToAdmin(Instant now) {
        role = UserRole.ADMIN;
        updatedAt = now;
    }

    public void activateSubscription(Instant now) {
        if (!subscriber) {
            subscriber = true;
            updatedAt = now;
        }
    }

    /** The trial is offered once per account and never to someone who already has Premium. */
    public boolean isTrialAvailable(Instant now) {
        return premiumTrialStartedAt == null && premiumSource(now) == PremiumSource.NONE;
    }

    public void startPremiumTrial(Duration length, BillingCycle billingCycle, Instant now) {
        if (!isTrialAvailable(now)) {
            throw new IllegalStateException("Premium trial is not available for this account");
        }
        premiumTrialStartedAt = now;
        premiumTrialEndsAt = now.plus(length);
        preferredBillingCycle = billingCycle;
        updatedAt = now;
    }

    /** Ends an active trial right away; the account returns to the free plan. */
    public void cancelPremiumTrial(Instant now) {
        if (isTrialActive(now)) {
            premiumTrialEndsAt = now;
            updatedAt = now;
        }
    }

    public PremiumSource premiumSource(Instant now) {
        if (role == UserRole.ADMIN) {
            return PremiumSource.ADMIN;
        }
        if (subscriber) {
            return PremiumSource.SUBSCRIPTION;
        }
        if (isTrialActive(now)) {
            return PremiumSource.TRIAL;
        }
        return PremiumSource.NONE;
    }

    public boolean hasPremiumAccess(Instant now) {
        return premiumSource(now) != PremiumSource.NONE;
    }

    private boolean isTrialActive(Instant now) {
        return premiumTrialEndsAt != null && premiumTrialEndsAt.isAfter(now);
    }

    public void changePassword(String passwordHash, Instant now) {
        this.passwordHash = passwordHash;
        updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UserRole getRole() {
        return role;
    }

    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    public boolean isSubscriber() {
        return subscriber;
    }

    public Instant getPremiumTrialEndsAt() {
        return premiumTrialEndsAt;
    }

    public BillingCycle getPreferredBillingCycle() {
        return preferredBillingCycle;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

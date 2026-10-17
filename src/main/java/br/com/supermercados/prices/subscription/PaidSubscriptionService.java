package br.com.supermercados.prices.subscription;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.supermercados.prices.common.ApiException;
import br.com.supermercados.prices.user.User;
import br.com.supermercados.prices.user.UserRepository;

/**
 * Paid Premium. The provider's webhook is the only thing that grants access: opening a checkout or coming back
 * from it proves nothing. Network calls to the provider happen outside database transactions.
 */
@Service
public class PaidSubscriptionService {

    /** A renewal refused on its due date is retried for three days; access lasts through those retries. */
    static final Duration RENEWAL_GRACE = Duration.ofDays(3);

    private static final Logger log = LoggerFactory.getLogger(PaidSubscriptionService.class);

    private final PaymentGateway gateway;
    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public PaidSubscriptionService(PaymentGateway gateway, UserRepository users, JdbcTemplate jdbc, Clock clock) {
        this.gateway = gateway;
        this.users = users;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public boolean checkoutAvailable() {
        return gateway.available();
    }

    public CheckoutResponse startCheckout(UUID userId, BillingCycle cycle) {
        if (!gateway.available()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "O pagamento online ainda não está disponível.");
        }
        User user = users.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Usuário não encontrado."));
        if (!user.isEmailVerified()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Confirme seu e-mail antes de assinar.");
        }
        if (activeSubscriptionId(userId).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "Sua conta já tem uma assinatura ativa.");
        }

        PaymentGateway.Checkout checkout = gateway.openSubscriptionCheckout(cycle);
        jdbc.update("INSERT INTO billing_checkouts (id, user_id, billing_cycle, created_at) VALUES (?, ?, ?, ?)",
                checkout.id(), userId, cycle.name(), Timestamp.from(clock.instant()));
        return new CheckoutResponse(checkout.url());
    }

    /** Stops renewals; the period already paid stays available until it ends. */
    public void cancel(UUID userId) {
        String subscriptionId = activeSubscriptionId(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "Não há assinatura ativa para cancelar."));
        gateway.cancelSubscription(subscriptionId);
        markCancelled(subscriptionId, clock.instant());
    }

    /** A deleted account must not keep being charged; if the provider cannot confirm, the deletion waits. */
    public void cancelBeforeAccountDeletion(UUID userId) {
        activeSubscriptionId(userId).ifPresent(subscriptionId -> {
            gateway.cancelSubscription(subscriptionId);
            markCancelled(subscriptionId, clock.instant());
        });
    }

    public Optional<String> activeSubscriptionId(UUID userId) {
        List<String> ids = jdbc.queryForList("""
                SELECT id FROM billing_subscriptions WHERE user_id = ? AND status = 'ACTIVE'
                ORDER BY created_at DESC
                """, String.class, userId);
        return ids.stream().findFirst();
    }

    /** Applies a webhook once; retries of an event already applied change nothing. */
    @Transactional
    public void apply(PaymentEvent event) {
        Instant now = clock.instant();
        int fresh = jdbc.update("""
                INSERT INTO billing_webhook_events (id, event_type, received_at) VALUES (?, ?, ?)
                ON CONFLICT DO NOTHING
                """, event.id(), event.type().name(), Timestamp.from(now));
        if (fresh == 0) {
            return;
        }
        switch (event.type()) {
            case SUBSCRIPTION_STARTED -> started(event, now);
            case SUBSCRIPTION_RENEWED -> renewed(event, now);
            case SUBSCRIPTION_CANCELLED -> cancelled(event, now);
            case PAYMENT_FAILED -> log.warn("Renovação recusada na assinatura {}; o provedor tenta de novo",
                    event.subscriptionId());
            case OTHER -> {
            }
        }
    }

    private void started(PaymentEvent event, Instant now) {
        if (event.subscriptionId() == null || event.checkoutId() == null) {
            log.warn("Assinatura iniciada sem id ou checkout no evento {}", event.id());
            return;
        }
        List<Owner> checkouts = jdbc.query("SELECT user_id, billing_cycle FROM billing_checkouts WHERE id = ?",
                (row, index) -> new Owner(row.getObject("user_id", UUID.class),
                        BillingCycle.valueOf(row.getString("billing_cycle"))), event.checkoutId());
        if (checkouts.isEmpty()) {
            // Another app on the same AbacatePay account, or a checkout from another environment.
            log.warn("Checkout {} do evento {} não foi aberto pelo Gomo", event.checkoutId(), event.id());
            return;
        }
        Owner owner = checkouts.getFirst();
        BillingCycle cycle = event.cycle() == null ? owner.cycle() : event.cycle();
        jdbc.update("""
                INSERT INTO billing_subscriptions (id, user_id, billing_cycle, status, created_at, updated_at)
                VALUES (?, ?, ?, 'ACTIVE', ?, ?)
                ON CONFLICT (id) DO NOTHING
                """, event.subscriptionId(), owner.userId(), cycle.name(), Timestamp.from(now), Timestamp.from(now));
        extend(owner.userId(), cycle, event.paidAt(), now);
    }

    private void renewed(PaymentEvent event, Instant now) {
        findSubscription(event).ifPresent(owner -> extend(owner.userId(),
                event.cycle() == null ? owner.cycle() : event.cycle(), event.paidAt(), now));
    }

    private void cancelled(PaymentEvent event, Instant now) {
        findSubscription(event).ifPresent(owner -> {
            markCancelled(event.subscriptionId(), now);
            if (event.paymentsExhausted()) {
                requireUser(owner.userId()).endPaidPremium(now);
            }
        });
    }

    private Optional<Owner> findSubscription(PaymentEvent event) {
        if (event.subscriptionId() == null) {
            return Optional.empty();
        }
        List<Owner> owners = jdbc.query("SELECT user_id, billing_cycle FROM billing_subscriptions WHERE id = ?",
                (row, index) -> new Owner(row.getObject("user_id", UUID.class),
                        BillingCycle.valueOf(row.getString("billing_cycle"))), event.subscriptionId());
        if (owners.isEmpty()) {
            log.warn("Assinatura {} do evento {} não é conhecida pelo Gomo", event.subscriptionId(), event.id());
        }
        return owners.stream().findFirst();
    }

    /** One cycle from the payment, counted on the shoppers' calendar, plus the renewal retry window. */
    private void extend(UUID userId, BillingCycle cycle, Instant paidAt, Instant now) {
        Instant base = paidAt == null || paidAt.isAfter(now) ? now : paidAt;
        ZonedDateTime start = base.atZone(FreePlan.USAGE_ZONE);
        Instant until = (cycle == BillingCycle.ANNUAL ? start.plusYears(1) : start.plusMonths(1)).toInstant()
                .plus(RENEWAL_GRACE);
        requireUser(userId).extendPaidPremium(until, now);
    }

    private void markCancelled(String subscriptionId, Instant now) {
        jdbc.update("""
                UPDATE billing_subscriptions SET status = 'CANCELLED', cancelled_at = ?, updated_at = ?
                WHERE id = ? AND status = 'ACTIVE'
                """, Timestamp.from(now), Timestamp.from(now), subscriptionId);
    }

    private User requireUser(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new IllegalStateException("Assinatura sem conta"));
    }

    private record Owner(UUID userId, BillingCycle cycle) {
    }
}

package br.com.supermercados.prices.alert;

import br.com.supermercados.prices.price.PricePolicy;
import br.com.supermercados.prices.price.PriceRecord;
import br.com.supermercados.prices.price.PriceRecordRepository;
import br.com.supermercados.prices.price.PriceStatus;
import br.com.supermercados.prices.store.Store;
import br.com.supermercados.prices.store.StoreService;
import br.com.supermercados.prices.user.User;
import br.com.supermercados.prices.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PriceAlertEvaluator {

    private final PriceAlertRepository alerts;
    private final AlertNotificationRepository notifications;
    private final PriceRecordRepository prices;
    private final StoreService stores;
    private final PricePolicy pricePolicy;
    private final UserRepository users;
    private final Clock clock;
    private final Duration repeatInterval;
    private final Duration premiumRepeatInterval;

    /**
     * Free alerts notify at most once per {@code repeatInterval} (a day by default). Premium alerts use a shorter
     * interval so every price collection (four a day) can notify.
     */
    public PriceAlertEvaluator(PriceAlertRepository alerts, AlertNotificationRepository notifications,
            PriceRecordRepository prices, StoreService stores, PricePolicy pricePolicy, UserRepository users,
            Clock clock,
            @Value("${app.alerts.repeat-interval:P1D}") Duration repeatInterval,
            @Value("${app.alerts.premium-repeat-interval:PT1H}") Duration premiumRepeatInterval) {
        requirePositive(repeatInterval, "app.alerts.repeat-interval");
        requirePositive(premiumRepeatInterval, "app.alerts.premium-repeat-interval");
        this.alerts = alerts;
        this.notifications = notifications;
        this.prices = prices;
        this.stores = stores;
        this.pricePolicy = pricePolicy;
        this.users = users;
        this.clock = clock;
        this.repeatInterval = repeatInterval;
        this.premiumRepeatInterval = premiumRepeatInterval;
    }

    @Transactional
    public void evaluate(UUID priceRecordId) {
        PriceRecord record = prices.findById(priceRecordId)
                .orElseThrow(() -> new IllegalStateException("Persisted price record was not found"));
        Store store = stores.requireStore(record.getStoreId());
        Instant now = clock.instant();
        var quote = pricePolicy.quote(record, now);
        if (quote.status() != PriceStatus.KNOWN) {
            return;
        }

        List<PriceAlert> candidates = alerts.findActiveForEvaluation(record.getProductId(), store.getCityId());
        Set<UUID> premiumUsers = findPremiumUsers(candidates, now);
        for (PriceAlert alert : candidates) {
            Duration interval = premiumUsers.contains(alert.getUserId()) ? premiumRepeatInterval : repeatInterval;
            if (quote.unitPrice().compareTo(alert.getTargetPrice()) > 0
                    || alert.getLastNotifiedAt() != null && alert.getLastNotifiedAt().isAfter(now.minus(interval))) {
                continue;
            }
            int inserted = notifications.insertIfAbsent(UUID.randomUUID(), alert.getUserId(), alert.getId(),
                    record.getId(), record.getStoreId(), quote.unitPrice(), now);
            if (inserted == 1) {
                alert.markNotified(now);
            }
        }
    }

    private Set<UUID> findPremiumUsers(List<PriceAlert> candidates, Instant now) {
        if (candidates.isEmpty()) {
            return Set.of();
        }
        Set<UUID> userIds = candidates.stream().map(PriceAlert::getUserId).collect(Collectors.toSet());
        return users.findAllById(userIds).stream()
                .filter(user -> user.hasPremiumAccess(now))
                .map(User::getId)
                .collect(Collectors.toSet());
    }

    private static void requirePositive(Duration interval, String property) {
        if (interval.isNegative() || interval.isZero()) {
            throw new IllegalArgumentException(property + " must be positive");
        }
    }
}

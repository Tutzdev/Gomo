package br.com.supermercados.prices.alert;

import br.com.supermercados.prices.price.PricePolicy;
import br.com.supermercados.prices.price.PriceRecord;
import br.com.supermercados.prices.price.PriceRecordRepository;
import br.com.supermercados.prices.price.PriceStatus;
import br.com.supermercados.prices.price.UsualPrice;
import br.com.supermercados.prices.store.Store;
import br.com.supermercados.prices.subscription.PremiumAccess;
import br.com.supermercados.prices.store.StoreService;
import br.com.supermercados.prices.user.User;
import br.com.supermercados.prices.user.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PriceAlertEvaluator {

    /** A price still at or below the last notice gets one reminder a week, not one per collection. */
    static final Duration REMINDER = Duration.ofDays(7);

    private final PriceAlertRepository alerts;
    private final AlertNotificationRepository notifications;
    private final PriceRecordRepository prices;
    private final StoreService stores;
    private final PricePolicy pricePolicy;
    private final UserRepository users;
    private final PremiumAccess premiumAccess;
    private final Clock clock;
    private final Duration repeatInterval;
    private final Duration premiumRepeatInterval;

    /**
     * Free alerts notify at most once per {@code repeatInterval} (a day by default). Premium alerts use a shorter
     * interval so every price collection (four a day) can notify.
     */
    public PriceAlertEvaluator(PriceAlertRepository alerts, AlertNotificationRepository notifications,
            PriceRecordRepository prices, StoreService stores, PricePolicy pricePolicy, UserRepository users,
            PremiumAccess premiumAccess, Clock clock,
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
        this.premiumAccess = premiumAccess;
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
        CityUsualPrice usualPrice = new CityUsualPrice(record.getProductId(), store.getCityId(), now);
        for (PriceAlert alert : candidates) {
            boolean premium = premiumUsers.contains(alert.getUserId());
            // Kept, not deleted, when Premium ends: the alert speaks again if the person comes back.
            if (alert.getKind() == PriceAlertKind.BELOW_AVERAGE && !premium) {
                continue;
            }
            Duration interval = premium ? premiumRepeatInterval : repeatInterval;
            if (!alert.shouldNotify(quote.unitPrice(), now, interval, REMINDER)) {
                continue;
            }
            BigDecimal threshold = threshold(alert, usualPrice);
            if (threshold == null || quote.unitPrice().compareTo(threshold) > 0) {
                continue;
            }
            int inserted = notifications.insertIfAbsent(UUID.randomUUID(), alert.getUserId(), alert.getId(),
                    record.getId(), record.getStoreId(), quote.unitPrice(), now);
            if (inserted == 1) {
                alert.markNotified(now, quote.unitPrice());
            }
        }
    }

    /** The highest price that fires the alert; {@code null} while there is no usual price to compare with. */
    private static BigDecimal threshold(PriceAlert alert, CityUsualPrice usualPrice) {
        if (alert.getKind() == PriceAlertKind.TARGET_PRICE) {
            return alert.getTargetPrice();
        }
        BigDecimal average = usualPrice.get();
        if (average == null) {
            return null;
        }
        BigDecimal share = BigDecimal.valueOf(100 - alert.getDiscountPercent()).movePointLeft(2);
        return average.multiply(share).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * The {@link UsualPrice} of the product in the whole city, the same one the price verdict shows. Loaded once
     * per evaluated price, and only if some alert needs it.
     */
    private final class CityUsualPrice {

        private final UUID productId;
        private final UUID cityId;
        private final LocalDate today;
        private Optional<BigDecimal> average;

        private CityUsualPrice(UUID productId, UUID cityId, Instant now) {
            this.productId = productId;
            this.cityId = cityId;
            this.today = LocalDate.ofInstant(now, UsualPrice.ZONE);
        }

        private BigDecimal get() {
            if (average == null) {
                average = prices.findDailyLowestAverage(productId, cityId, UsualPrice.windowStart(today), today,
                        UsualPrice.MINIMUM_DAYS);
            }
            return average.orElse(null);
        }
    }

    private Set<UUID> findPremiumUsers(List<PriceAlert> candidates, Instant now) {
        if (candidates.isEmpty()) {
            return Set.of();
        }
        Set<UUID> userIds = candidates.stream().map(PriceAlert::getUserId).collect(Collectors.toSet());
        return users.findAllById(userIds).stream()
                .filter(user -> premiumAccess.hasPremium(user, now))
                .map(User::getId)
                .collect(Collectors.toSet());
    }

    private static void requirePositive(Duration interval, String property) {
        if (interval.isNegative() || interval.isZero()) {
            throw new IllegalArgumentException(property + " must be positive");
        }
    }
}

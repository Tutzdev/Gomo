package br.com.supermercados.prices.alert;

import br.com.supermercados.prices.price.PricePolicy;
import br.com.supermercados.prices.price.PriceQuote;
import br.com.supermercados.prices.price.PriceRecord;
import br.com.supermercados.prices.price.PriceRecordRepository;
import br.com.supermercados.prices.price.PriceStatus;
import br.com.supermercados.prices.price.UsualPrice;
import br.com.supermercados.prices.store.Store;
import br.com.supermercados.prices.store.StoreService;
import br.com.supermercados.prices.subscription.PremiumAccess;
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

    public PriceAlertEvaluator(
        PriceAlertRepository alerts,
        AlertNotificationRepository notifications,
        PriceRecordRepository prices,
        StoreService stores,
        PricePolicy pricePolicy,
        UserRepository users,
        PremiumAccess premiumAccess,
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
        PriceQuote quote = pricePolicy.quote(record, now);

        if (quote.status() != PriceStatus.KNOWN) {return;}

        BigDecimal price = quote.unitPrice();
        List<PriceAlert> candidates = alerts.findActiveForEvaluation(record.getProductId(), store.getCityId());
        Set<UUID> premiumUsers = findPremiumUsers(candidates, now);
        CityUsualPrice usualPrice = new CityUsualPrice(record.getProductId(), store.getCityId(), now);

        for (PriceAlert alert : candidates) {
            boolean premium = premiumUsers.contains(alert.getUserId());

            if (shouldFire(alert, premium, price, usualPrice, now)) {sendNotification(alert, record, price, now);}
        }
    }

    private boolean shouldFire(
        PriceAlert alert,
        boolean premium,
        BigDecimal price,
        CityUsualPrice usualPrice,
        Instant now) {

        if (alert.getKind() == PriceAlertKind.BELOW_AVERAGE && !premium) {return false;}

        Duration interval = premium ? premiumRepeatInterval : repeatInterval;

        if (!alert.shouldNotify(price, now, interval, REMINDER)) {return false;}

        BigDecimal threshold = threshold(alert, usualPrice);

        return threshold != null && price.compareTo(threshold) <= 0;
    }

    private void sendNotification(
        PriceAlert alert,
        PriceRecord record,
        BigDecimal price,
        Instant now) {

        int inserted = notifications.insertIfAbsent(
            UUID.randomUUID(),
            alert.getUserId(),
            alert.getId(),
            record.getId(),
            record.getStoreId(),
            price,
            now);

        if (inserted == 1) {alert.markNotified(now, price);}
    }

    private static BigDecimal threshold(PriceAlert alert, CityUsualPrice usualPrice) {
        if (alert.getKind() == PriceAlertKind.TARGET_PRICE) {return alert.getTargetPrice();}

        BigDecimal average = usualPrice.get();

        if (average == null) {return null;}

        BigDecimal shareOfAverage = BigDecimal.valueOf(100 - alert.getDiscountPercent()).movePointLeft(2);

        return average.multiply(shareOfAverage).setScale(2, RoundingMode.HALF_UP);
    }

    private Set<UUID> findPremiumUsers(List<PriceAlert> candidates, Instant now) {
        if (candidates.isEmpty()) {return Set.of();}

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
                average = prices.findDailyLowestAverage(
                    productId,
                    cityId,
                    UsualPrice.windowStart(today),
                    today,
                    UsualPrice.MINIMUM_DAYS);
            }

            return average.orElse(null);
        }
    }
}

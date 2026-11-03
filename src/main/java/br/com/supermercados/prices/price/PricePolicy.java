package br.com.supermercados.prices.price;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

@Component
public class PricePolicy {

    private final Duration maxAge;
    private final Duration inStoreMaxAge;

    @Autowired
    public PricePolicy(@Value("${app.prices.max-age:P2D}") Duration maxAge,
            @Value("${app.prices.in-store-max-age:P7D}") Duration inStoreMaxAge) {
        this.maxAge = requirePositive(maxAge);
        this.inStoreMaxAge = requirePositive(inStoreMaxAge);
        // The SQL filters use the in-store window as their outer bound.
        if (inStoreMaxAge.compareTo(maxAge) < 0) {
            throw new IllegalArgumentException("O preço conferido na loja não pode vencer antes do preço coletado.");
        }
    }

    public PricePolicy(Duration maxAge) {
        this(maxAge, maxAge.compareTo(Duration.ofDays(7)) > 0 ? maxAge : Duration.ofDays(7));
    }

    private static Duration requirePositive(Duration duration) {
        Objects.requireNonNull(duration, "maxAge");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("A duração de frescor dos preços deve ser positiva.");
        }
        return duration;
    }

    /** How long a collected observation counts as the current price. */
    public Duration maxAge() {
        return maxAge;
    }

    /** How long a price written down at the shelf ({@link PriceOriginType#STORE_VISIT}) counts as current. */
    public Duration inStoreMaxAge() {
        return inStoreMaxAge;
    }

    public Duration maxAge(PriceOriginType origin) {
        return origin == PriceOriginType.STORE_VISIT ? inStoreMaxAge : maxAge;
    }

    public PriceQuote quote(PriceRecord record, Instant comparedAt) {
        if (record == null) {
            return PriceQuote.missing();
        }
        Instant expiresAt = observationExpiry(record);
        PriceRecordResponse observation = PriceRecordResponse.from(record);
        if (!expiresAt.isAfter(comparedAt) || record.getCollectedAt().isAfter(comparedAt)) {
            return new PriceQuote(PriceStatus.EXPIRED, StockAvailability.UNKNOWN,
                    null, false, expiresAt, observation);
        }
        if (record.getAvailability() == StockAvailability.UNAVAILABLE) {
            return new PriceQuote(PriceStatus.OUT_OF_STOCK, StockAvailability.UNAVAILABLE,
                    null, false, expiresAt, observation);
        }
        boolean promotionApplied = record.getPromotionalPrice() != null
                && record.getPromotionValidUntil() != null
                && record.getPromotionCondition() == null
                && record.getPromotionValidUntil().isAfter(comparedAt);
        if (promotionApplied && record.getPromotionValidUntil().isBefore(expiresAt)) {
            expiresAt = record.getPromotionValidUntil();
        }
        return new PriceQuote(PriceStatus.KNOWN, record.getAvailability(),
                promotionApplied ? record.getPromotionalPrice() : record.getRegularPrice(),
                promotionApplied, expiresAt, observation);
    }

    private Instant observationExpiry(PriceRecord record) {
        Instant freshnessLimit = record.getCollectedAt().plus(maxAge(record.getOriginType()));
        if (record.getValidUntil() != null && record.getValidUntil().isBefore(freshnessLimit)) {
            return record.getValidUntil();
        }
        return freshnessLimit;
    }
}

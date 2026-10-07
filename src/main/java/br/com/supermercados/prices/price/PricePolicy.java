package br.com.supermercados.prices.price;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

@Component
public class PricePolicy {

    private final Duration maxAge;

    public PricePolicy(@Value("${app.prices.max-age:P2D}") Duration maxAge) {
        this.maxAge = Objects.requireNonNull(maxAge, "maxAge");
        if (maxAge.isZero() || maxAge.isNegative()) {
            throw new IllegalArgumentException("A duração de frescor dos preços deve ser positiva.");
        }
    }

    /** How long an observation counts as the current price. */
    public Duration maxAge() {
        return maxAge;
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
        Instant freshnessLimit = record.getCollectedAt().plus(maxAge);
        if (record.getValidUntil() != null && record.getValidUntil().isBefore(freshnessLimit)) {
            return record.getValidUntil();
        }
        return freshnessLimit;
    }
}

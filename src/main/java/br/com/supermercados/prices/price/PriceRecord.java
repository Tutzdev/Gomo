package br.com.supermercados.prices.price;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "price_records")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PriceRecord {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private UUID storeId;

    @Column(nullable = false)
    private UUID sourceId;

    @Column(nullable = false, length = 500)
    private String sourceReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PriceOriginType originType;

    private UUID contributionId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal regularPrice;

    @Column(precision = 12, scale = 2)
    private BigDecimal promotionalPrice;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private Instant collectedAt;

    @Column(nullable = false)
    private Instant recordedAt;

    private Instant validUntil;

    private Instant promotionValidUntil;

    @Column(length = 500)
    private String promotionCondition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private StockAvailability availability;

    @Column(length = 2048)
    private String originUrl;

    @Column(length = 2048)
    private String sourceProductReference;

    @Column(length = 200)
    private String sourceProductName;

    static PriceRecord from(PriceObservation observation, Instant recordedAt) {
        return from(observation, recordedAt, PriceOriginType.SOURCE, null);
    }

    static PriceRecord fromContribution(
            PriceObservation observation, UUID contributionId, Instant recordedAt) {
        return from(observation, recordedAt, PriceOriginType.USER_CONTRIBUTION, contributionId);
    }

    private static PriceRecord from(
            PriceObservation observation, Instant recordedAt, PriceOriginType originType, UUID contributionId) {
        PriceRecord record = new PriceRecord();
        
        record.id = UUID.randomUUID();
        record.productId = observation.productId();
        record.storeId = observation.storeId();
        record.sourceId = observation.sourceId();
        record.sourceReference = observation.sourceReference().strip();
        record.originType = originType;
        record.contributionId = contributionId;
        record.regularPrice = observation.regularPrice().setScale(2, RoundingMode.UNNECESSARY);

        record.promotionalPrice = observation.promotionalPrice() == null ? null
                : observation.promotionalPrice().setScale(2, RoundingMode.UNNECESSARY);

        record.currency = observation.currency();
        record.collectedAt = toDatabasePrecision(observation.collectedAt());
        record.recordedAt = toDatabasePrecision(recordedAt);
        record.validUntil = toDatabasePrecision(observation.validUntil());
        record.promotionValidUntil = toDatabasePrecision(observation.promotionValidUntil());
        record.promotionCondition = cleanOptional(observation.promotionCondition());
        record.availability = observation.availability();
        record.originUrl = observation.originUrl();
        record.sourceProductReference = cleanOptional(observation.sourceProductReference());
        record.sourceProductName = cleanOptional(observation.sourceProductName());

        return record;
    }

    void enrichOrigin(String publicUrl) {
        if (originUrl == null) originUrl = publicUrl;
    }

    void enrichSourceDescription(String name) {
        if (sourceProductName == null) sourceProductName = name;
    }

    boolean hasSameObservation(PriceRecord other) {
        return productId.equals(other.productId)
                && storeId.equals(other.storeId)
                && sourceId.equals(other.sourceId)
                && sourceReference.equals(other.sourceReference)
                && originType == other.originType
                && Objects.equals(contributionId, other.contributionId)
                && regularPrice.equals(other.regularPrice)
                && Objects.equals(promotionalPrice, other.promotionalPrice)
                && currency.equals(other.currency)
                && collectedAt.equals(other.collectedAt)
                && Objects.equals(validUntil, other.validUntil)
                && Objects.equals(promotionValidUntil, other.promotionValidUntil)
                && Objects.equals(promotionCondition, other.promotionCondition)
                && availability == other.availability;
    }

    private static String cleanOptional(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static Instant toDatabasePrecision(Instant timestamp) {
        return timestamp == null ? null : timestamp.truncatedTo(ChronoUnit.MICROS);
    }
}

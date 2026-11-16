package br.com.supermercados.prices.contribution;

import br.com.supermercados.prices.price.StockAvailability;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "price_contributions")
class PriceContribution {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID contributorId;

    @Column(nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private UUID storeId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal regularPrice;

    @Column(precision = 12, scale = 2)
    private BigDecimal promotionalPrice;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private Instant observedAt;

    @Column(nullable = false)
    private Instant submittedAt;

    private Instant validUntil;

    private Instant promotionValidUntil;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private StockAvailability availability;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ContributionStatus status;

    private UUID moderatorId;

    private Instant decidedAt;

    @Column(length = 500)
    private String rejectionReason;

    private UUID priceRecordId;

    @Version
    private long version;

    protected PriceContribution() {}

    PriceContribution(UUID contributorId, SubmitPriceContributionRequest request, Instant submittedAt) {
        this.id = UUID.randomUUID();
        this.contributorId = contributorId;
        this.productId = request.productId();
        this.storeId = request.storeId();
        this.regularPrice = request.regularPrice().setScale(2, RoundingMode.UNNECESSARY);

        this.promotionalPrice = request.promotionalPrice() == null ? null
                : request.promotionalPrice().setScale(2, RoundingMode.UNNECESSARY);

        this.currency = request.currency();
        this.observedAt = databasePrecision(request.observedAt());
        this.submittedAt = databasePrecision(submittedAt);
        this.validUntil = databasePrecision(request.validUntil());
        this.promotionValidUntil = databasePrecision(request.promotionValidUntil());
        this.availability = request.availability();
        this.status = ContributionStatus.PENDING;
    }

    void approve(UUID moderatorId, UUID priceRecordId, Instant decidedAt) {
        status = ContributionStatus.APPROVED;
        this.moderatorId = moderatorId;
        this.priceRecordId = priceRecordId;
        this.decidedAt = databasePrecision(decidedAt);
    }

    void reject(UUID moderatorId, String reason, Instant decidedAt) {
        status = ContributionStatus.REJECTED;
        this.moderatorId = moderatorId;
        rejectionReason = reason.strip();
        this.decidedAt = databasePrecision(decidedAt);
    }

    UUID getId() {
        return id;
    }

    UUID getContributorId() {
        return contributorId;
    }

    UUID getProductId() {
        return productId;
    }

    UUID getStoreId() {
        return storeId;
    }

    BigDecimal getRegularPrice() {
        return regularPrice;
    }

    BigDecimal getPromotionalPrice() {
        return promotionalPrice;
    }

    String getCurrency() {
        return currency;
    }

    Instant getObservedAt() {
        return observedAt;
    }

    Instant getSubmittedAt() {
        return submittedAt;
    }

    Instant getValidUntil() {
        return validUntil;
    }

    Instant getPromotionValidUntil() {
        return promotionValidUntil;
    }

    StockAvailability getAvailability() {
        return availability;
    }

    ContributionStatus getStatus() {
        return status;
    }

    UUID getModeratorId() {
        return moderatorId;
    }

    Instant getDecidedAt() {
        return decidedAt;
    }

    String getRejectionReason() {
        return rejectionReason;
    }

    UUID getPriceRecordId() {
        return priceRecordId;
    }

    private static Instant databasePrecision(Instant instant) {
        return instant == null ? null : instant.truncatedTo(ChronoUnit.MICROS);
    }
}

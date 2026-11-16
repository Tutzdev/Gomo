package br.com.supermercados.prices.alert;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "price_alerts")
class PriceAlert {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private UUID cityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PriceAlertKind kind;

    @Column(precision = 12, scale = 2)
    private BigDecimal targetPrice;

    private Integer discountPercent;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant lastNotifiedAt;

    @Column(precision = 12, scale = 2)
    private BigDecimal lastNotifiedPrice;

    @Version
    private long version;

    protected PriceAlert() {}

    PriceAlert(UUID userId, CreatePriceAlertRequest request, Instant now) {
        id = UUID.randomUUID();
        this.userId = userId;
        productId = request.productId();
        cityId = request.cityId();
        kind = request.kind();
        targetPrice = inCents(request.targetPrice());
        discountPercent = request.discountPercent();
        active = true;
        createdAt = now;
        updatedAt = now;
    }

    void deactivate(Instant now) {
        active = false;
        updatedAt = now;
    }

    void markNotified(Instant now, BigDecimal price) {
        lastNotifiedAt = now;
        lastNotifiedPrice = price;
        updatedAt = now;
    }

    boolean shouldNotify(
        BigDecimal price,
        Instant now,
        Duration minimumInterval,
        Duration reminder) {

        if (lastNotifiedAt == null) {return true;}

        if (wasNotifiedWithin(minimumInterval, now)) {return false;}

        return isCheaperThanLastNotice(price) || !wasNotifiedWithin(reminder, now);
    }

    private boolean wasNotifiedWithin(Duration period, Instant now) {
        return lastNotifiedAt.isAfter(now.minus(period));
    }

    private boolean isCheaperThanLastNotice(BigDecimal price) {
        return lastNotifiedPrice == null || price.compareTo(lastNotifiedPrice) < 0;
    }

    private static BigDecimal inCents(BigDecimal price) {
        return price == null ? null : price.setScale(2, RoundingMode.UNNECESSARY);
    }

    UUID getId() {
        return id;
    }

    UUID getUserId() {
        return userId;
    }

    UUID getProductId() {
        return productId;
    }

    UUID getCityId() {
        return cityId;
    }

    PriceAlertKind getKind() {
        return kind;
    }

    BigDecimal getTargetPrice() {
        return targetPrice;
    }

    Integer getDiscountPercent() {
        return discountPercent;
    }

    boolean isActive() {
        return active;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }

    Instant getLastNotifiedAt() {
        return lastNotifiedAt;
    }
}

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

    protected PriceAlert() {
    }

    PriceAlert(UUID userId, CreatePriceAlertRequest request, Instant now) {
        id = UUID.randomUUID();
        this.userId = userId;
        productId = request.productId();
        cityId = request.cityId();
        kind = request.kind();
        targetPrice = request.targetPrice() == null ? null : request.targetPrice().setScale(2, RoundingMode.UNNECESSARY);
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

    /**
     * A promotion is collected four times a day for as long as it lasts; one notice is enough. After the first,
     * the alert speaks again only when the price drops below the one it announced, or when it is still there
     * {@code reminder} later. {@code minimumInterval} keeps the spacing of the plan either way.
     */
    boolean shouldNotify(BigDecimal price, Instant now, Duration minimumInterval, Duration reminder) {
        if (lastNotifiedAt == null) {
            return true;
        }
        if (lastNotifiedAt.isAfter(now.minus(minimumInterval))) {
            return false;
        }
        // Notified before the price was kept: the old rule, once, until this notice records it.
        return lastNotifiedPrice == null || price.compareTo(lastNotifiedPrice) < 0
                || !lastNotifiedAt.isAfter(now.minus(reminder));
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

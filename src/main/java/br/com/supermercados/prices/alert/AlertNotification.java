package br.com.supermercados.prices.alert;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "alert_notifications")
class AlertNotification {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private UUID alertId;

    @Column(nullable = false)
    private UUID priceRecordId;

    @Column(nullable = false)
    private UUID storeId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant readAt;

    protected AlertNotification() {}

    void markRead(Instant now) {
        if (readAt == null) {
            readAt = now;
        }
    }

    UUID getId() {
        return id;
    }

    UUID getAlertId() {
        return alertId;
    }

    UUID getPriceRecordId() {
        return priceRecordId;
    }

    UUID getStoreId() {
        return storeId;
    }

    BigDecimal getUnitPrice() {
        return unitPrice;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getReadAt() {
        return readAt;
    }
}

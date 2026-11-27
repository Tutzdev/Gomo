package br.com.supermercados.prices.purchase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "purchase_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class PurchaseItem {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID purchaseId;

    @Column(nullable = false)
    private UUID productId;

    @Column(nullable = false, length = 200)
    private String productName;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(precision = 12, scale = 2)
    private BigDecimal cityAveragePrice;

    @Column(nullable = false)
    private int cityStores;

    PurchaseItem(
        UUID purchaseId,
        UUID productId,
        String productName,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal cityAveragePrice,
        int cityStores) {

        this.id = UUID.randomUUID();
        this.purchaseId = purchaseId;
        this.productId = productId;
        this.productName = productName.length() > 200 ? productName.substring(0, 200) : productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.cityAveragePrice = cityAveragePrice;
        this.cityStores = cityStores;
    }

    BigDecimal linePaid() {
        return unitPrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
    }

    BigDecimal lineCityAverage() {
        return cityAveragePrice == null ? null : cityAveragePrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
    }
}

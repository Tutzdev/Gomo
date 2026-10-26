package br.com.supermercados.prices.purchase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    /** Where the price paid came from. */
    enum PriceSource {
        /** The current price Gomo had for the store. */
        COLLECTED,
        /** The price the person saw on the shelf and typed in the "No mercado" mode. */
        SHELF
    }

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PriceSource priceSource;

    /** Average current price among the city's stores; {@code null} when fewer than two had one. */
    @Column(precision = 12, scale = 2)
    private BigDecimal cityAveragePrice;

    @Column(nullable = false)
    private int cityStores;

    PurchaseItem(UUID purchaseId, UUID productId, String productName, BigDecimal quantity, BigDecimal unitPrice,
            PriceSource priceSource, BigDecimal cityAveragePrice, int cityStores) {
        this.id = UUID.randomUUID();
        this.purchaseId = purchaseId;
        this.productId = productId;
        this.productName = productName.length() > 200 ? productName.substring(0, 200) : productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.priceSource = priceSource;
        this.cityAveragePrice = cityAveragePrice;
        this.cityStores = cityStores;
    }

    BigDecimal linePaid() {
        return unitPrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
    }

    /** {@code null} when the item is not compared with the city. */
    BigDecimal lineCityAverage() {
        return cityAveragePrice == null ? null : cityAveragePrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
    }
}

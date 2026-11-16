package br.com.supermercados.prices.purchase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "purchases")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Purchase {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    private UUID shoppingListId;

    @Column(nullable = false, length = 120)
    private String listName;

    @Column(nullable = false)
    private UUID storeId;

    @Column(nullable = false)
    private UUID cityId;

    @Column(nullable = false)
    private Instant purchasedAt;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalPaid;

    @Column(nullable = false)
    private int comparedItems;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal comparedPaid;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal comparedCityAverage;

    @Column(nullable = false)
    private int itemsWithoutPrice;

    BigDecimal savings() {
        return comparedCityAverage.subtract(comparedPaid);
    }
}

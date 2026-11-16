package br.com.supermercados.prices.shoppinglist;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "shopping_list_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShoppingListItem {

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID shoppingListId;

    @Column(nullable = false, updatable = false)
    private UUID productId;

    @Column(updatable = false)
    private UUID catalogItemId;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    private Instant checkedAt;

    private UUID checkedBy;

    ShoppingListItem(UUID shoppingListId, UUID productId, BigDecimal quantity) {
        this(shoppingListId, productId, null, quantity);
    }

    ShoppingListItem(
        UUID shoppingListId,
        UUID productId,
        UUID catalogItemId,
        BigDecimal quantity) {

        this.id = UUID.randomUUID();
        this.shoppingListId = shoppingListId;
        this.productId = productId;
        this.catalogItemId = catalogItemId;
        this.quantity = quantity;
    }

    void changeQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    void check(UUID userId, Instant now) {
        if (checkedAt == null) {
            checkedAt = now.truncatedTo(ChronoUnit.MICROS);
            checkedBy = userId;
        }
    }

    void uncheck() {
        checkedAt = null;
        checkedBy = null;
    }
}

package br.com.supermercados.prices.shoppinglist;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "shopping_lists")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShoppingList {

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ShoppingType shoppingType;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    ShoppingList(
        UUID userId,
        String name,
        ShoppingType shoppingType,
        Instant now) {

        this.id = UUID.randomUUID();
        this.userId = userId;
        this.name = name.strip();
        this.shoppingType = shoppingType;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void rename(String name, ShoppingType shoppingType, Instant now) {
        this.name = name.strip();
        this.shoppingType = shoppingType;
        touch(now);
    }

    void touch(Instant now) {
        this.updatedAt = now;
    }
}

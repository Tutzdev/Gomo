package br.com.supermercados.prices.shoppinglist;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Someone the owner shared the list with. The owner is not a member: they are {@code shopping_lists.user_id}. */
@Entity
@Table(name = "shopping_list_members")
@IdClass(ShoppingListMember.Key.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ShoppingListMember {

    @Id
    private UUID shoppingListId;

    @Id
    private UUID userId;

    @Column(nullable = false, updatable = false)
    private Instant joinedAt;

    ShoppingListMember(UUID shoppingListId, UUID userId, Instant now) {
        this.shoppingListId = shoppingListId;
        this.userId = userId;
        this.joinedAt = now;
    }

    record Key(UUID shoppingListId, UUID userId) implements Serializable {

        Key() {
            this(null, null);
        }
    }
}

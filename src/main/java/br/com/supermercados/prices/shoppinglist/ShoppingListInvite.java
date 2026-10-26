package br.com.supermercados.prices.shoppinglist;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** An invite link to a list. Only the hash of its token is kept; the link itself is shown once, to the owner. */
@Entity
@Table(name = "shopping_list_invites")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ShoppingListInvite {

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID shoppingListId;

    @Column(nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(nullable = false, updatable = false)
    private UUID createdBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false, updatable = false)
    private Instant expiresAt;

    private Instant revokedAt;

    ShoppingListInvite(UUID shoppingListId, String tokenHash, UUID createdBy, Instant now, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.shoppingListId = shoppingListId;
        this.tokenHash = tokenHash;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    boolean isUsable(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }
}

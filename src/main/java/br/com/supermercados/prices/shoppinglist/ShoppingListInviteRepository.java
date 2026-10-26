package br.com.supermercados.prices.shoppinglist;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface ShoppingListInviteRepository extends JpaRepository<ShoppingListInvite, UUID> {

    Optional<ShoppingListInvite> findByTokenHash(String tokenHash);

    /** A new link replaces the old ones; "stop sharing" ends them all. */
    @Modifying(flushAutomatically = true)
    @Query("""
            update ShoppingListInvite invite set invite.revokedAt = :now
            where invite.shoppingListId = :listId and invite.revokedAt is null
            """)
    int revokeAll(UUID listId, Instant now);
}

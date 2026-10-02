package br.com.supermercados.prices.shoppinglist;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ShoppingListRepository extends JpaRepository<ShoppingList, UUID> {

    Page<ShoppingList> findByUserId(UUID userId, Pageable pageable);

    long countByUserId(UUID userId);

    Optional<ShoppingList> findByIdAndUserId(UUID id, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select list from ShoppingList list where list.id = :id and list.userId = :userId")
    Optional<ShoppingList> lockOwnedList(UUID id, UUID userId);
}

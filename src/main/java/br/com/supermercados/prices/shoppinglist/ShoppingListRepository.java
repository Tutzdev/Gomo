package br.com.supermercados.prices.shoppinglist;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ShoppingListRepository extends JpaRepository<ShoppingList, UUID> {

    /** The person's own lists and the ones shared with them. */
    String ACCESSIBLE = """
            (list.userId = :userId or exists (
                select member.userId from ShoppingListMember member
                where member.shoppingListId = list.id and member.userId = :userId))
            """;

    Page<ShoppingList> findByUserId(UUID userId, Pageable pageable);

    long countByUserId(UUID userId);

    Optional<ShoppingList> findByIdAndUserId(UUID id, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select list from ShoppingList list where list.id = :id and list.userId = :userId")
    Optional<ShoppingList> lockOwnedList(UUID id, UUID userId);

    /** Serialises the people joining a list at the same moment, so the member limit holds. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select list from ShoppingList list where list.id = :id")
    Optional<ShoppingList> lockById(UUID id);

    @Query(value = "select list from ShoppingList list where " + ACCESSIBLE,
            countQuery = "select count(list) from ShoppingList list where " + ACCESSIBLE)
    Page<ShoppingList> findAccessible(UUID userId, Pageable pageable);

    @Query("select list from ShoppingList list where list.id = :id and " + ACCESSIBLE)
    Optional<ShoppingList> findAccessible(UUID id, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select list from ShoppingList list where list.id = :id and " + ACCESSIBLE)
    Optional<ShoppingList> lockAccessibleList(UUID id, UUID userId);

    @Query("select list from ShoppingList list where list.shoppingType in :types and " + ACCESSIBLE
            + " order by list.name, list.id")
    List<ShoppingList> findAccessibleOfTypes(UUID userId, Collection<ShoppingType> types);
}

package br.com.supermercados.prices.shoppinglist;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ShoppingListItemRepository extends JpaRepository<ShoppingListItem, UUID> {

    @Query("""
            select new br.com.supermercados.prices.shoppinglist.ShoppingListItemResponse(
                item.id, item.productId, coalesce(catalog.displayName, product.name), item.quantity,
                item.catalogItemId, coalesce(catalog.imageUrl, product.imageUrl), item.checkedAt, item.checkedBy,
                checker.name)
            from ShoppingListItem item join Product product on product.id = item.productId
            left join CatalogItem catalog on catalog.id = item.catalogItemId
            left join User checker on checker.id = item.checkedBy
            where item.shoppingListId = :listId
            order by coalesce(catalog.displayName, product.name), item.id
            """)
    List<ShoppingListItemResponse> findItems(UUID listId);

    Optional<ShoppingListItem> findByIdAndShoppingListId(UUID id, UUID shoppingListId);

    boolean existsByShoppingListIdAndProductId(UUID shoppingListId, UUID productId);

    boolean existsByShoppingListIdAndCatalogItemId(UUID shoppingListId, UUID catalogItemId);

    long countByShoppingListId(UUID shoppingListId);

    long countByShoppingListIdAndCheckedAtIsNull(UUID shoppingListId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ShoppingListItem item set item.checkedAt = null, item.checkedBy = null where item.shoppingListId = :listId")
    int clearChecks(UUID listId);

    /** In the person's own lists and in the ones shared with them. */
    @Query("""
            select distinct item.catalogItemId from ShoppingListItem item, ShoppingList list
            where list.id = item.shoppingListId and item.catalogItemId is not null and
            """ + ShoppingListRepository.ACCESSIBLE)
    Set<UUID> findCatalogItemIdsOfUser(UUID userId);
}

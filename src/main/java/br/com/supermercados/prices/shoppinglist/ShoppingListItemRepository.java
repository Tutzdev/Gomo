package br.com.supermercados.prices.shoppinglist;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ShoppingListItemRepository extends JpaRepository<ShoppingListItem, UUID> {

    @Query("""
            select new br.com.supermercados.prices.shoppinglist.ShoppingListItemResponse(
                item.id, item.productId, coalesce(catalog.displayName, product.name), item.quantity,
                item.catalogItemId, coalesce(catalog.imageUrl, product.imageUrl))
            from ShoppingListItem item join Product product on product.id = item.productId
            left join CatalogItem catalog on catalog.id = item.catalogItemId
            where item.shoppingListId = :listId
            order by coalesce(catalog.displayName, product.name), item.id
            """)
    List<ShoppingListItemResponse> findItems(UUID listId);

    Optional<ShoppingListItem> findByIdAndShoppingListId(UUID id, UUID shoppingListId);

    boolean existsByShoppingListIdAndProductId(UUID shoppingListId, UUID productId);

    boolean existsByShoppingListIdAndCatalogItemId(UUID shoppingListId, UUID catalogItemId);

    long countByShoppingListId(UUID shoppingListId);
}

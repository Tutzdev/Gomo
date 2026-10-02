package br.com.supermercados.prices.catalog;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CatalogItemRepository extends JpaRepository<CatalogItem, UUID> {

    @Query(value = """
            SELECT item.* FROM catalog_items item
            JOIN catalog_item_products link ON link.catalog_item_id = item.id
            WHERE link.product_id = :productId AND item.active
            """, nativeQuery = true)
    java.util.Optional<CatalogItem> findActiveByProductId(@Param("productId") UUID productId);

    @Query(value = "SELECT product_id FROM catalog_item_products WHERE catalog_item_id = :itemId", nativeQuery = true)
    List<UUID> findProductIds(@Param("itemId") UUID itemId);

    @Query(value = """
            SELECT CAST(link.product_id AS text) AS requested, CAST(member.product_id AS text) AS member
            FROM catalog_item_products link
            JOIN catalog_items item ON item.id = link.catalog_item_id AND item.active
            JOIN catalog_item_products member ON member.catalog_item_id = link.catalog_item_id
            WHERE link.product_id IN (:productIds)
            """, nativeQuery = true)
    List<Object[]> findEquivalentProductIds(@Param("productIds") Collection<UUID> productIds);

    @Query(value = """
            SELECT CAST(catalog_item_id AS text), CAST(product_id AS text) FROM catalog_item_products
            WHERE catalog_item_id IN (:itemIds)
            """, nativeQuery = true)
    List<Object[]> findLinks(@Param("itemIds") Collection<UUID> itemIds);
}

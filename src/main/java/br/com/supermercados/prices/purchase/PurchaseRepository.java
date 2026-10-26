package br.com.supermercados.prices.purchase;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PurchaseRepository extends JpaRepository<Purchase, UUID> {

    @Query("""
            select purchase from Purchase purchase
            where purchase.userId = :userId and purchase.purchasedAt >= :since and purchase.purchasedAt < :until
            order by purchase.purchasedAt desc, purchase.id
            """)
    List<Purchase> findBetween(@Param("userId") UUID userId, @Param("since") Instant since,
            @Param("until") Instant until);

    Optional<Purchase> findByIdAndUserId(UUID id, UUID userId);

    /** The id comes from the client: the same purchase sent twice is written once. */
    @Modifying
    @Query(value = """
            INSERT INTO purchases (id, user_id, shopping_list_id, list_name, store_id, city_id, purchased_at,
                total_paid, compared_items, compared_paid, compared_city_average, items_without_price)
            VALUES (:id, :userId, :listId, :listName, :storeId, :cityId, :purchasedAt, :totalPaid, :comparedItems,
                :comparedPaid, :comparedCityAverage, :itemsWithoutPrice)
            ON CONFLICT (id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("userId") UUID userId, @Param("listId") UUID listId,
            @Param("listName") String listName, @Param("storeId") UUID storeId, @Param("cityId") UUID cityId,
            @Param("purchasedAt") Instant purchasedAt, @Param("totalPaid") BigDecimal totalPaid,
            @Param("comparedItems") int comparedItems, @Param("comparedPaid") BigDecimal comparedPaid,
            @Param("comparedCityAverage") BigDecimal comparedCityAverage,
            @Param("itemsWithoutPrice") int itemsWithoutPrice);
}

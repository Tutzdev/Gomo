package br.com.supermercados.prices.price;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface PriceRecordRepository extends Repository<PriceRecord, UUID> {

    Optional<PriceRecord> findById(UUID id);

    /**
     * The newest collection of each store; {@code collectedAt} is null for a store without prices. A max() per
     * store is one index lookup, where a GROUP BY over price_records read every observation the store ever had.
     */
    @Query("""
            SELECT new br.com.supermercados.prices.price.StorePriceUpdate(s.id,
                (SELECT max(p.collectedAt) FROM PriceRecord p WHERE p.storeId = s.id))
            FROM Store s WHERE s.id IN :storeIds
            """)
    List<StorePriceUpdate> findStoreUpdates(@Param("storeIds") Collection<UUID> storeIds);

    Page<PriceRecord> findByProductIdAndStoreId(UUID productId, UUID storeId, Pageable pageable);

    Page<PriceRecord> findByProductIdAndStoreIdAndCollectedAtGreaterThanEqual(
            UUID productId, UUID storeId, Instant since, Pageable pageable);

    Optional<PriceRecord> findBySourceIdAndSourceReference(UUID sourceId, String sourceReference);

    List<PriceRecord> findAllBySourceIdAndSourceReferenceIn(
            UUID sourceId, Collection<String> sourceReferences);

    /** The newest observation per store and product, read from latest_prices (kept by a trigger, see V54). */
    @Query(value = """
            SELECT r.*
            FROM latest_prices latest
            JOIN price_records r ON r.id = latest.price_record_id
            WHERE latest.store_id IN (:storeIds) AND latest.product_id IN (:productIds)
            ORDER BY latest.store_id, latest.product_id
            """, nativeQuery = true)
    List<PriceRecord> findLatestForStoresAndProducts(
            @Param("storeIds") Collection<UUID> storeIds,
            @Param("productIds") Collection<UUID> productIds);

    /**
     * {@link UsualPrice} of a product in a city, computed in the database: the average, rounded to cents, of the
     * city's lowest price of each day in {@code [since, until)}. Empty when fewer than {@code minimumDays} days had
     * a price.
     */
    @Query(value = """
            SELECT ROUND(AVG(day_lowest), 2)
            FROM (
                SELECT d.price_day, MIN(d.lowest_price) AS day_lowest
                FROM daily_lowest_prices d
                JOIN stores s ON s.id = d.store_id
                WHERE d.product_id = :productId AND s.city_id = :cityId
                  AND d.price_day >= :since AND d.price_day < :until
                GROUP BY d.price_day
            ) daily
            HAVING COUNT(*) >= :minimumDays
            """, nativeQuery = true)
    Optional<BigDecimal> findDailyLowestAverage(
            @Param("productId") UUID productId,
            @Param("cityId") UUID cityId,
            @Param("since") LocalDate since,
            @Param("until") LocalDate until,
            @Param("minimumDays") int minimumDays);

    @Query(value = """
            SELECT DISTINCT ON (product_id) *
            FROM price_records
            WHERE source_id = :sourceId
              AND store_id = :storeId
              AND product_id IN (:productIds)
            ORDER BY product_id, collected_at DESC, recorded_at DESC, id DESC
            """, nativeQuery = true)
    List<PriceRecord> findLatestForSourceStoreAndProducts(
            @Param("sourceId") UUID sourceId,
            @Param("storeId") UUID storeId,
            @Param("productIds") Collection<UUID> productIds);

    @Modifying
    @Query(value = """
            INSERT INTO price_records (
                id, product_id, store_id, source_id, source_reference,
                regular_price, promotional_price, currency, collected_at, recorded_at,
                valid_until, promotion_valid_until, promotion_condition,
                availability, origin_type, contribution_id, origin_url, source_product_reference, source_product_name
            ) VALUES (
                :#{#record.id}, :#{#record.productId}, :#{#record.storeId},
                :#{#record.sourceId}, :#{#record.sourceReference},
                :#{#record.regularPrice}, :#{#record.promotionalPrice}, :#{#record.currency},
                :#{#record.collectedAt}, :#{#record.recordedAt}, :#{#record.validUntil},
                :#{#record.promotionValidUntil}, :#{#record.promotionCondition},
                :#{#record.availability.name()},
                :#{#record.originType.name()}, :#{#record.contributionId}, :#{#record.originUrl}, :#{#record.sourceProductReference}, :#{#record.sourceProductName}
            ) ON CONFLICT (source_id, source_reference) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("record") PriceRecord record);

    @Modifying
    @Query(value = "UPDATE price_records SET origin_url = :originUrl WHERE id = :id AND origin_url IS NULL", nativeQuery = true)
    int fillMissingOrigin(@Param("id") UUID id, @Param("originUrl") String originUrl);

    @Modifying
    @Query(value = "UPDATE price_records SET source_product_name = :name WHERE id = :id AND source_product_name IS NULL", nativeQuery = true)
    int fillMissingSourceDescription(@Param("id") UUID id, @Param("name") String name);
}

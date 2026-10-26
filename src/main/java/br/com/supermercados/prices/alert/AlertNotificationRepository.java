package br.com.supermercados.prices.alert;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AlertNotificationRepository extends JpaRepository<AlertNotification, UUID> {

    String DETAILED = """
            select new br.com.supermercados.prices.alert.AlertNotificationResponse(
                notification.id, notification.alertId, notification.priceRecordId, notification.storeId,
                notification.unitPrice, notification.createdAt, notification.readAt, alert.productId, product.name,
                store.name, alert.kind, alert.targetPrice, alert.discountPercent)
            from AlertNotification notification
            join PriceAlert alert on alert.id = notification.alertId
            join Product product on product.id = alert.productId
            join Store store on store.id = notification.storeId
            """;

    @Query(value = DETAILED + " where notification.userId = :userId",
            countQuery = "select count(notification) from AlertNotification notification where notification.userId = :userId")
    Page<AlertNotificationResponse> findDetailedByUserId(@Param("userId") UUID userId, Pageable pageable);

    @Query(DETAILED + " where notification.id = :id and notification.userId = :userId")
    Optional<AlertNotificationResponse> findDetailedByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    Optional<AlertNotification> findByIdAndUserId(UUID id, UUID userId);

    long countByUserIdAndReadAtIsNull(UUID userId);

    @Modifying
    @Query(value = """
            INSERT INTO alert_notifications (
                id, user_id, alert_id, price_record_id, store_id, unit_price, created_at
            ) VALUES (
                :id, :userId, :alertId, :priceRecordId, :storeId, :unitPrice, :createdAt
            ) ON CONFLICT (alert_id, price_record_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("userId") UUID userId,
            @Param("alertId") UUID alertId,
            @Param("priceRecordId") UUID priceRecordId,
            @Param("storeId") UUID storeId,
            @Param("unitPrice") BigDecimal unitPrice,
            @Param("createdAt") Instant createdAt);
}

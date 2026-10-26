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

    Page<AlertNotification> findByUserId(UUID userId, Pageable pageable);

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

package br.com.supermercados.prices.alert;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PriceAlertRepository extends JpaRepository<PriceAlert, UUID> {

    Page<PriceAlert> findByUserId(UUID userId, Pageable pageable);

    Optional<PriceAlert> findByIdAndUserId(UUID id, UUID userId);

    long countByUserIdAndActiveTrue(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select alert from PriceAlert alert
            where alert.productId = :productId and alert.cityId = :cityId and alert.active = true
            order by alert.id
            """)
    List<PriceAlert> findActiveForEvaluation(
            @Param("productId") UUID productId, @Param("cityId") UUID cityId);
}

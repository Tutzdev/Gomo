package br.com.supermercados.prices.preference;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface FavoriteStoreRepository extends JpaRepository<FavoriteStore, UUID> {

    /** A retired market keeps its favourite rows, so they come back if it returns, but is not listed. */
    @Query("""
            select f.storeId from FavoriteStore f where f.userId = :userId
            and exists (select 1 from Store s where s.id = f.storeId and s.active)
            order by f.createdAt, f.id
            """)
    List<UUID> findActiveStoreIds(UUID userId);

    boolean existsByUserIdAndStoreId(UUID userId, UUID storeId);

    void deleteByUserIdAndStoreId(UUID userId, UUID storeId);
}

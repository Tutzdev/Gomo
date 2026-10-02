package br.com.supermercados.prices.store;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StoreRepository extends JpaRepository<Store, UUID> {

    Page<Store> findByActiveTrue(Pageable pageable);

    Page<Store> findByCityIdAndActiveTrue(UUID cityId, Pageable pageable);

    /** Active stores that have published at least one price; an empty store cannot be compared. */
    @Query("""
            select s from Store s where s.cityId = :cityId and s.active
            and exists (select 1 from PriceRecord r where r.storeId = s.id)
            """)
    Page<Store> findPricedActiveStores(UUID cityId, Pageable pageable);

    Optional<Store> findBySourceIdAndSourceReference(UUID sourceId, String sourceReference);
}

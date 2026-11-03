package br.com.supermercados.prices.suggestion;

import br.com.supermercados.prices.contribution.ContributionStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface ProductSuggestionRepository extends JpaRepository<ProductSuggestion, UUID> {

    Page<ProductSuggestion> findByContributorId(UUID contributorId, Pageable pageable);

    Page<ProductSuggestion> findByStatus(ContributionStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ProductSuggestion s where s.id = :id")
    Optional<ProductSuggestion> findByIdForUpdate(UUID id);
}

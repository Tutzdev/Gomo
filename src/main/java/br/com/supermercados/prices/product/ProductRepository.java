package br.com.supermercados.prices.product;

import java.util.Optional;
import java.util.List;
import java.util.Collection;
import java.math.BigDecimal;
import org.springframework.data.domain.Pageable;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

    Optional<Product> findByGtin(String gtin);

    List<Product> findByComparisonFamilyIn(Collection<String> families);

    @Query(value = """
            SELECT DISTINCT p.* FROM unnest(string_to_array(:families, '|')) family(name)
            JOIN products p ON p.comparison_family % family.name
            """, nativeQuery = true)
    List<Product> findSimilarComparisonFamilies(@Param("families") String families);

    List<Product> findByNormalizedBrandAndUnitAndQuantity(String brand, String unit,
            BigDecimal quantity, Pageable pageable);
}

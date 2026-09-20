package br.com.supermercados.prices.product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    @Id
    private UUID id;

    @Column(unique = true, length = 14)
    private String gtin;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 200)
    private String normalizedName;

    @Column(length = 120)
    private String brand;

    @Column(length = 120)
    private String normalizedBrand;

    @Column(length = 500)
    private String comparisonFamily;

    private int comparisonIdentityVersion;

    @Column(length = 2000)
    private String description;

    @Column(length = 30)
    private String unit;

    @Column(precision = 14, scale = 4)
    private BigDecimal quantity;

    @Column(length = 120)
    private String category;

    @Column(length = 120)
    private String packageDescription;

    @Column(length = 2048)
    private String imageUrl;

    @Column(length = 2048)
    private String originUrl;

    @Column(nullable = false)
    private UUID sourceId;

    @Column(nullable = false, length = 2048)
    private String sourceReference;

    @Column(nullable = false)
    private Instant collectedAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    Product(ProductObservation observation, String normalizedGtin, Instant now) {
        id = UUID.randomUUID();
        gtin = normalizedGtin;
        updateDetails(observation, now);
    }

    void assignGtin(String normalizedGtin) {
        if (gtin == null) {
            gtin = normalizedGtin;
        }
    }

    void updateDetails(ProductObservation observation, Instant now) {
        ProductNormalizer.NormalizedProduct normalized = ProductNormalizer.normalize(observation);

        name = observation.name().strip();
        normalizedName = normalized.name();
        brand = cleanOptional(observation.brand());
        normalizedBrand = normalized.brand();
        description = cleanOptional(observation.description());
        unit = normalized.unit();
        quantity = normalized.quantity();
        category = cleanOptional(observation.category());
        packageDescription = normalized.packageDescription();
        comparisonFamily = ProductComparisonIdentity.searchFamily(name);
        comparisonIdentityVersion = 4;
        if (observation.imageUrl() != null) imageUrl = publicUrl(observation.imageUrl());
        if (observation.originUrl() != null) originUrl = publicUrl(observation.originUrl());
        sourceId = observation.source().sourceId();
        sourceReference = observation.source().sourceReference();
        collectedAt = observation.source().collectedAt();
        updatedAt = now;
    }

    private String cleanOptional(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    void enrichMedia(ProductObservation observation) {
        if (imageUrl == null && observation.imageUrl() != null) {
            imageUrl = publicUrl(observation.imageUrl());
            originUrl = publicUrl(observation.originUrl());
        }
    }

    private String publicUrl(String value) {
        if (value == null || value.isBlank()) return null;
        java.net.URI uri = java.net.URI.create(value.replaceAll("[\\s\\p{Zs}]+", "%20"));
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("A origem do produto deve ser uma URL HTTPS pública");
        }
        return uri.toString();
    }
}

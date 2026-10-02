package br.com.supermercados.prices.catalog;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A real-world product sold by several stores. Written only by {@link CatalogBuilder}. */
@Entity
@Table(name = "catalog_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CatalogItem {

    @Id
    private UUID id;

    @Column(nullable = false, updatable = false)
    private String catalogKey;

    @Column(nullable = false)
    private String displayName;

    private String brand;

    @Column(nullable = false)
    private String sizeLabel;

    private String category;

    private String imageUrl;

    @Column(nullable = false)
    private UUID representativeProductId;

    @Column(nullable = false)
    private int storeCount;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private Instant updatedAt;
}

package br.com.supermercados.prices.store;

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
@Table(name = "stores")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Store {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID supermarketChainId;

    @Column(nullable = false)
    private UUID cityId;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 500)
    private String address;

    @Column(length = 1000)
    private String priceSourceNote;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(nullable = false)
    private boolean active;

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

    Store(StoreObservation observation, Instant now) {
        id = UUID.randomUUID();
        sourceId = observation.source().sourceId();
        sourceReference = observation.source().sourceReference();
        update(observation, now);
    }

    void update(StoreObservation observation, Instant now) {
        supermarketChainId = observation.supermarketChainId();
        cityId = observation.cityId();
        name = observation.name().strip();
        address = observation.address() == null || observation.address().isBlank()
                ? null : observation.address().strip();
        latitude = observation.latitude();
        longitude = observation.longitude();
        active = observation.active();
        collectedAt = observation.source().collectedAt();
        updatedAt = now;
    }
}

package br.com.supermercados.prices.preference;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "favorite_stores")
class FavoriteStore {

    @Id
    private UUID id;

    private UUID userId;

    private UUID storeId;

    private Instant createdAt;

    protected FavoriteStore() {}

    FavoriteStore(UUID userId, UUID storeId, Instant createdAt) {
        id = UUID.randomUUID();
        this.userId = userId;
        this.storeId = storeId;
        this.createdAt = createdAt;
    }

    UUID getStoreId() {
        return storeId;
    }
}

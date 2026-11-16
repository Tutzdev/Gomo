package br.com.supermercados.prices.preference;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_preferences")
class UserPreference {

    @Id
    private UUID userId;

    private UUID preferredCityId;

    private Instant updatedAt;

    protected UserPreference() {}

    UserPreference(UUID userId, UUID preferredCityId, Instant updatedAt) {
        this.userId = userId;
        this.preferredCityId = preferredCityId;
        this.updatedAt = updatedAt;
    }

    void changePreferredCity(UUID preferredCityId, Instant now) {
        this.preferredCityId = preferredCityId;
        updatedAt = now;
    }

    UUID getPreferredCityId() {
        return preferredCityId;
    }
}

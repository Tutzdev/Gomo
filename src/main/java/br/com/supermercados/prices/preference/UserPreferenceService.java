package br.com.supermercados.prices.preference;

import br.com.supermercados.prices.location.LocationService;
import br.com.supermercados.prices.store.StoreService;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserPreferenceService {

    private final UserPreferenceRepository preferences;
    private final FavoriteStoreRepository favoriteStores;
    private final LocationService locations;
    private final StoreService stores;
    private final Clock clock;

    public UserPreferenceService(UserPreferenceRepository preferences, FavoriteStoreRepository favoriteStores,
            LocationService locations, StoreService stores, Clock clock) {
        this.preferences = preferences;
        this.favoriteStores = favoriteStores;
        this.locations = locations;
        this.stores = stores;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public UserPreferenceResponse find(UUID userId) {
        UUID preferredCityId = preferences.findById(userId)
                .map(UserPreference::getPreferredCityId)
                .orElse(null);

        return response(userId, preferredCityId);
    }

    @Transactional
    public UserPreferenceResponse changePreferredCity(UUID userId, UUID cityId) {
        locations.requireCity(cityId);
        UserPreference preference = preferences.findById(userId)
                .orElseGet(() -> new UserPreference(userId, cityId, clock.instant()));
        preference.changePreferredCity(cityId, clock.instant());
        preferences.save(preference);

        return response(userId, cityId);
    }

    @Transactional
    public UserPreferenceResponse clearPreferredCity(UUID userId) {
        UserPreference preference = preferences.findById(userId)
                .orElseGet(() -> new UserPreference(userId, null, clock.instant()));
        preference.changePreferredCity(null, clock.instant());
        preferences.save(preference);

        return response(userId, null);
    }

    @Transactional
    public UserPreferenceResponse addFavoriteStore(UUID userId, UUID storeId) {
        stores.requireStore(storeId);
        if (!favoriteStores.existsByUserIdAndStoreId(userId, storeId)) {
            favoriteStores.save(new FavoriteStore(userId, storeId, clock.instant()));
        }
        UUID preferredCityId = preferences.findById(userId)
                .map(UserPreference::getPreferredCityId)
                .orElse(null);

        return response(userId, preferredCityId);
    }

    @Transactional
    public UserPreferenceResponse removeFavoriteStore(UUID userId, UUID storeId) {
        favoriteStores.deleteByUserIdAndStoreId(userId, storeId);
        UUID preferredCityId = preferences.findById(userId)
                .map(UserPreference::getPreferredCityId)
                .orElse(null);

        return response(userId, preferredCityId);
    }

    private UserPreferenceResponse response(UUID userId, UUID preferredCityId) {
        List<UUID> storeIds = favoriteStores.findByUserIdOrderByCreatedAtAscIdAsc(userId).stream()
                .map(FavoriteStore::getStoreId)
                .toList();
                
        return new UserPreferenceResponse(preferredCityId, storeIds);
    }
}

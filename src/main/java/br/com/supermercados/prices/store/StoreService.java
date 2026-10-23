package br.com.supermercados.prices.store;

import br.com.supermercados.prices.common.ApiException;
import br.com.supermercados.prices.location.LocationService;
import java.util.List;
import java.util.UUID;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;
import br.com.supermercados.prices.price.PricePolicy;
import br.com.supermercados.prices.price.PriceRecordRepository;
import br.com.supermercados.prices.price.StorePriceUpdate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreService {

    private final StoreRepository storeRepository;
    private final ChainRepository chainRepository;
    private final LocationService locationService;
    private final PriceRecordRepository prices;
    private final PricePolicy pricePolicy;
    private final Clock clock;

    public Page<ChainResponse> findChains(Pageable pageable) {
        return chainRepository.findAll(pageable).map(ChainResponse::from);
    }

    public Page<StoreResponse> findActiveStores(UUID cityId, Pageable pageable) {
        if (cityId == null) {
            return withPriceUpdates(storeRepository.findByActiveTrue(pageable));
        }
        locationService.requireCity(cityId);
        return withPriceUpdates(storeRepository.findPricedActiveStores(cityId, pageable));
    }

    /**
     * The markets shown to shoppers: every active one, each with its price status. A market whose collection
     * stopped stays listed as {@link StorePriceStatus#OUTDATED} (and out of searches and comparisons) until the
     * next successful collection, so the page does not empty out while the collectors are behind.
     */
    public Page<StoreResponse> findListedStores(UUID cityId, Pageable pageable) {
        if (cityId != null) locationService.requireCity(cityId);
        Page<Store> stores = cityId == null ? storeRepository.findByActiveTrue(pageable)
                : storeRepository.findByCityIdAndActiveTrue(cityId, pageable);
        if (stores.isEmpty()) return stores.map(StoreResponse::from);
        Map<UUID, Instant> updates = lastPriceUpdates(stores.stream().map(Store::getId).toList());
        return stores.map(store -> withStatus(store, updates.get(store.getId())));
    }

    public StoreResponse findStore(UUID storeId) {
        Store store = requireStore(storeId);
        return withStatus(store, lastPriceUpdates(List.of(storeId)).get(storeId));
    }

    private StoreResponse withStatus(Store store, Instant lastUpdate) {
        StorePriceStatus status = lastUpdate == null ? StorePriceStatus.NO_PRICES
                : lastUpdate.isAfter(clock.instant().minus(pricePolicy.maxAge())) ? StorePriceStatus.CURRENT
                : StorePriceStatus.OUTDATED;
        return StoreResponse.from(store, lastUpdate, status);
    }

    private Map<UUID, Instant> lastPriceUpdates(List<UUID> storeIds) {
        return prices.findStoreUpdates(storeIds).stream()
                .collect(Collectors.toMap(StorePriceUpdate::storeId, StorePriceUpdate::collectedAt));
    }

    private Page<StoreResponse> withPriceUpdates(Page<Store> stores) {
        if (stores.isEmpty()) {
            return stores.map(StoreResponse::from);
        }
        Map<UUID, Instant> updates = lastPriceUpdates(stores.stream().map(Store::getId).toList());
        return stores.map(store -> StoreResponse.from(store, updates.get(store.getId())));
    }

    public List<StoreResponse> findAllActiveStores(UUID cityId, int maximumStores) {
        locationService.requireCity(cityId);
        PageRequest request = PageRequest.of(0, maximumStores, Sort.by("name", "id"));
        Page<Store> page = storeRepository.findPricedActiveStores(cityId, request);
        if (page.getTotalElements() > maximumStores) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "A cidade possui mais lojas elegíveis que o limite seguro para uma recomendação completa.");
        }
        return page.getContent().stream().map(StoreResponse::from).toList();
    }

    /**
     * The selected markets that can still be compared. A retired or unknown ID (an old link, a market that left
     * the Gomo) is ignored; when none is left the selection counts as empty and every market is compared.
     */
    public List<StoreResponse> selectComparisonStores(UUID cityId, List<UUID> selectedIds, int maximumStores) {
        List<StoreResponse> available = findAllActiveStores(cityId, maximumStores);
        if (selectedIds == null || selectedIds.isEmpty()) return available;
        var selected = new java.util.HashSet<>(selectedIds);
        List<StoreResponse> filtered = available.stream().filter(store -> selected.contains(store.id())).toList();
        return filtered.isEmpty() ? available : filtered;
    }

    public Store requireStore(UUID storeId) {
        return storeRepository.findById(storeId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Loja não encontrada"));
    }

    /** A store that still takes new favourites and prices; a retired one only keeps its history. */
    public Store requireActiveStore(UUID storeId) {
        Store store = requireStore(storeId);
        if (!store.isActive()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "Este mercado não faz mais parte do Gomo.");
        }
        return store;
    }
}

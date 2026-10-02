package br.com.supermercados.prices.store;

import br.com.supermercados.prices.common.ApiException;
import br.com.supermercados.prices.location.LocationService;
import java.util.List;
import java.util.UUID;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;
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

    public StoreResponse findStore(UUID storeId) {
        Store store = requireStore(storeId);
        Instant lastUpdate = prices.findStoreUpdates(List.of(storeId)).stream()
                .map(StorePriceUpdate::collectedAt).findFirst().orElse(null);
        return StoreResponse.from(store, lastUpdate);
    }

    private Page<StoreResponse> withPriceUpdates(Page<Store> stores) {
        if (stores.isEmpty()) {
            return stores.map(StoreResponse::from);
        }
        List<UUID> storeIds = stores.stream().map(Store::getId).toList();
        Map<UUID, Instant> updates = prices.findStoreUpdates(storeIds).stream()
                .collect(Collectors.toMap(StorePriceUpdate::storeId, StorePriceUpdate::collectedAt));
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

    public List<StoreResponse> selectComparisonStores(UUID cityId, List<UUID> selectedIds, int maximumStores) {
        List<StoreResponse> available = findAllActiveStores(cityId, maximumStores);
        if (selectedIds == null || selectedIds.isEmpty()) return available;
        var selected = new java.util.HashSet<>(selectedIds);
        List<StoreResponse> filtered = available.stream().filter(store -> selected.contains(store.id())).toList();
        if (filtered.size() != selected.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Selecione apenas mercados ativos da cidade escolhida.");
        }
        return filtered;
    }

    public Store requireStore(UUID storeId) {
        return storeRepository.findById(storeId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Loja não encontrada"));
    }
}

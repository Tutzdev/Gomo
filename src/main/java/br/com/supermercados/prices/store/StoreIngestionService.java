package br.com.supermercados.prices.store;

import java.time.Clock;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.supermercados.prices.common.ApiException;
import br.com.supermercados.prices.datasource.DataSourceService;
import br.com.supermercados.prices.datasource.ObservationValidator;
import br.com.supermercados.prices.location.LocationService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StoreIngestionService {

    private final StoreRepository storeRepository;
    private final ChainRepository chainRepository;
    private final LocationService locationService;
    private final DataSourceService dataSourceService;
    private final ObservationValidator validator;
    private final Clock clock;

    @Transactional
    public ChainResponse ingestChain(ChainObservation observation) {
        validator.validate(observation);
        dataSourceService.verifyObservation(observation.source());

        var existing = chainRepository.findBySourceIdAndSourceReference(
            observation.source().sourceId(),
            observation.source().sourceReference());

        if (existing.isEmpty()) {
            return ChainResponse.from(chainRepository.save(new SupermarketChain(observation, clock.instant())));
        }

        SupermarketChain chain = existing.orElseThrow();

        if (observation.source().collectedAt().isAfter(chain.getCollectedAt())) {
            chain.update(observation, clock.instant());
        }

        return ChainResponse.from(chain);
    }

    @Transactional
    public StoreResponse ingestStore(StoreObservation observation) {
        validator.validate(observation);
        dataSourceService.verifyObservation(observation.source());
        validateLocation(observation);

        var existing = storeRepository.findBySourceIdAndSourceReference(
            observation.source().sourceId(),
            observation.source().sourceReference());

        if (existing.isEmpty()) {
            return StoreResponse.from(storeRepository.save(new Store(observation, clock.instant())));
        }

        Store store = existing.orElseThrow();

        if (observation.source().collectedAt().isAfter(store.getCollectedAt())) {
            store.update(observation, clock.instant());
        }

        return StoreResponse.from(store);
    }

    private void validateLocation(StoreObservation observation) {
        locationService.requireCity(observation.cityId());

        if (!chainRepository.existsById(observation.supermarketChainId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Rede de supermercados não encontrada");
        }

        if ((observation.latitude() == null) != (observation.longitude() == null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Latitude e longitude devem ser informadas juntas");
        }
    }
}

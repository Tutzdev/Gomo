package br.com.supermercados.prices.location;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.supermercados.prices.common.ApiException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LocationService {

    private final StateRepository stateRepository;
    private final CityRepository cityRepository;

    public Page<StateResponse> findStates(Pageable pageable) {
        return stateRepository.findAll(pageable).map(StateResponse::from);
    }

    public Page<CityResponse> findCities(UUID stateId, Pageable pageable) {
        if (stateId == null) {return cityRepository.findAll(pageable).map(CityResponse::from);}

        if (!stateRepository.existsById(stateId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Estado não encontrado");
        }

        return cityRepository.findByStateId(stateId, pageable).map(CityResponse::from);
    }

    public City requireCity(UUID cityId) {
        return cityRepository.findById(cityId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Cidade não encontrada"));
    }
}

package br.com.supermercados.prices.location;

import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.supermercados.prices.common.PageRequests;
import br.com.supermercados.prices.common.PageResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;

    @GetMapping("/states")
    public PageResponse<StateResponse> findStates(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {

        return PageResponse.from(locationService.findStates(
                PageRequests.create(page, size, Sort.by("name", "id"))));
    }

    @GetMapping("/cities")
    public PageResponse<CityResponse> findCities(
        @RequestParam(required = false) UUID stateId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {

        return PageResponse.from(locationService.findCities(
            stateId,
            PageRequests.create(page, size, Sort.by("name", "id"))));
    }
}

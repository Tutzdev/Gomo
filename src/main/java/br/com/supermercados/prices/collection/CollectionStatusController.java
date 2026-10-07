package br.com.supermercados.prices.collection;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public freshness check for an uptime monitor: 200 while every market with a price source has recent prices,
 * 503 as soon as one stops updating, so the alert arrives before its prices expire and it leaves the comparisons.
 * Kept out of {@code /actuator/health} on purpose: a broken market must not make the deploy roll back.
 */
@RestController
public class CollectionStatusController {

    private final CollectionHealthService health;

    public CollectionStatusController(CollectionHealthService health) {
        this.health = health;
    }

    @GetMapping("/api/v1/status/collections")
    public ResponseEntity<CollectionStatusResponse> status() {
        var report = health.report();
        var markets = report.markets().stream()
                .filter(market -> market.status() != CollectionHealthService.MarketStatus.NO_SOURCE)
                .map(market -> new MarketStatusResponse(market.storeName(), market.status(), market.lastPriceAt()))
                .toList();
        return ResponseEntity.status(report.healthy() ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(new CollectionStatusResponse(report.healthy(), report.checkedAt(), markets));
    }

    public record CollectionStatusResponse(boolean healthy, Instant checkedAt, List<MarketStatusResponse> markets) {
    }

    public record MarketStatusResponse(String name, CollectionHealthService.MarketStatus status, Instant lastPriceAt) {
    }
}

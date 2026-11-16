package br.com.supermercados.prices.collection;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

@Component
public class PriceObservationReference {

    public String create(
        String collectorCode,
        String storeReference,
        CollectedProduct product,
        Instant collectedAt) {

        return collectorCode + ":" + storeReference + ":" + product.sourceReference()
                + ":" + collectedAt.truncatedTo(ChronoUnit.MICROS);
    }
}

package br.com.supermercados.prices.collection.nagumo;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;

import br.com.supermercados.prices.collection.CollectedCatalog;
import br.com.supermercados.prices.collection.CatalogCheckpoint;
import br.com.supermercados.prices.collection.CollectedStore;
import br.com.supermercados.prices.collection.CollectedProduct;
import br.com.supermercados.prices.collection.CollectorMetadata;
import br.com.supermercados.prices.collection.SupermarketCollector;

@Component
@Order(10)
@ConditionalOnProperty(
        name = "app.collection.nagumo.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class NagumoCollector implements SupermarketCollector {

    private static final UUID VOLTA_REDONDA_ID =
            UUID.fromString("5c4cb935-52e1-4bf8-8d17-902dc0837c66");
    private static final Instant SOURCE_VERIFIED_AT = Instant.parse("2026-09-17T00:00:00Z");

    private final NagumoProperties properties;
    private final NagumoClient client;
    private final NagumoProductParser parser;
    private final Clock clock;

    NagumoCollector(
            NagumoProperties properties,
            NagumoClient client,
            NagumoProductParser parser,
            Clock clock) {
        this.properties = properties;
        this.client = client;
        this.parser = parser;
        this.clock = clock;
    }

    @Override
    public CollectorMetadata metadata() {
        return new CollectorMetadata(
                "nagumo_volta_redonda",
                "Nagumo",
                properties.getStoreName(),
                "nagumo_delivery",
                "E-commerce público Nagumo",
                properties.getBaseUrl().toString(),
                SOURCE_VERIFIED_AT,
                "Nagumo",
                "nagumo:chain",
                "nagumo:store:" + properties.getStoreId(),
                VOLTA_REDONDA_ID);
    }

    @Override
    public CollectedCatalog collect() {
        List<String> warnings = new ArrayList<>();
        CatalogCheckpoint checkpoint = new CatalogCheckpoint(metadata().code(), clock, new tools.jackson.databind.ObjectMapper());
        List<NagumoCatalogResponse> externalCatalogs = client.fetch(checkpoint, warnings);
        Instant collectedAt = checkpoint.startedAt();
        Instant validUntil = null;
        Map<String, CollectedProduct> products = new LinkedHashMap<>();
        int foundCount = 0;
        for (NagumoCatalogResponse externalCatalog : externalCatalogs) {
            NagumoProductParser.ParsedProducts parsed = parser.parse(externalCatalog, validUntil);
            foundCount += externalCatalog.foundCount();
            warnings.addAll(parsed.warnings());
            parsed.products().forEach(product -> products.putIfAbsent(product.sourceReference(), product));
        }
        if (foundCount > 0 && products.isEmpty()) {
            throw new IllegalStateException("Nenhum produto válido foi retornado pela Nagumo");
        }

        CollectedStore store = new CollectedStore(
                "Nagumo Ponte Alta (" + properties.getStoreName() + ")",
                properties.getAddress(),
                properties.getLatitude(),
                properties.getLongitude(),
                true);
        return new CollectedCatalog(store, List.copyOf(products.values()), foundCount,
                collectedAt, warnings);
    }
}

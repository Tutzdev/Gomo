package br.com.supermercados.prices.collection.royal;

import br.com.supermercados.prices.collection.vip.VipProductParser;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import br.com.supermercados.prices.collection.CollectedCatalog;
import br.com.supermercados.prices.collection.CollectedStore;
import br.com.supermercados.prices.collection.CollectorMetadata;
import br.com.supermercados.prices.collection.SupermarketCollector;
import lombok.RequiredArgsConstructor;

@Component
@Order(20)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.collection.royal.enabled", havingValue = "true", matchIfMissing = true)
public class RoyalCollector implements SupermarketCollector {

    private final RoyalProperties properties;
    private final RoyalClient client;
    private final VipProductParser parser;
    private final Clock clock;

    @Override
    public CollectorMetadata metadata() {
        return new CollectorMetadata("royal_retiro", "Royal Supermercados", "Royal Retiro",
                "royal_delivery", "E-commerce público Royal Supermercados", properties.getBaseUrl().toString(),
                Instant.parse("2026-09-17T00:00:00Z"), "Royal Supermercados", "royal:chain",
                "royal:store:255:2:1", UUID.fromString("5c4cb935-52e1-4bf8-8d17-902dc0837c66"));
    }

    @Override
    public CollectedCatalog collect() {
        RoyalClient.Catalog catalog = client.fetch(clock);
        VipProductParser.ParsedProducts parsed = parser.parse(catalog.products(), "royal", "Royal", properties.getBaseUrl());
        if (!catalog.products().isEmpty() && parsed.products().isEmpty()) {
            throw new IllegalStateException("Nenhum produto válido no catálogo Royal recebido");
        }
        CollectedStore store = new CollectedStore("Royal Retiro",
                "Avenida Antônio de Almeida, 1477 - Retiro - Volta Redonda/RJ - CEP 27277-330",
                new BigDecimal("-22.5004720"), new BigDecimal("-44.1262770"), true);
        var warnings = new java.util.ArrayList<>(catalog.warnings());
        warnings.addAll(parsed.warnings());
        return new CollectedCatalog(store, parsed.products(), catalog.products().size(), catalog.collectedAt(), List.copyOf(warnings));
    }
}

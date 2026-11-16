package br.com.supermercados.prices.collection.mercafacil;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import br.com.supermercados.prices.collection.CollectorMetadata;

public record MercafacilStoreDefinition(
        String code,
        String chain,
        String name,
        URI website,
        String slug,
        String storeId,
        String cnpj,
        String address) {

    public CollectorMetadata metadata() {
        return new CollectorMetadata(
            code,
            chain,
            name,
            code + "_catalog",
            "Catálogo público " + chain,
            website.resolve("/" + slug).toString(),
            Instant.parse("2026-09-18T00:00:00Z"),
            chain,
            "mercafacil:" + code + ":chain",
            "mercafacil:" + storeId,
            UUID.fromString("5c4cb935-52e1-4bf8-8d17-902dc0837c66"));
    }
}

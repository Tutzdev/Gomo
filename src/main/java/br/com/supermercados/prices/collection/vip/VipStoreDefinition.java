package br.com.supermercados.prices.collection.vip;

import java.net.URI;
import java.time.Instant;

import br.com.supermercados.prices.collection.CollectorMetadata;
import br.com.supermercados.prices.collection.ServedCity;

public record VipStoreDefinition(
        String code, String chain, String name, URI website, String configurationPath,
        int organizationId, int storefrontId, int branchId, int distributionId, String cnpj, ServedCity city) {

    public CollectorMetadata metadata() {
        return new CollectorMetadata(code, chain, name, "vip_" + organizationId,
                "Loja pública " + chain, website.toString(), Instant.parse("2026-09-18T00:00:00Z"),
                chain, "vip:" + organizationId + ":chain",
                "vip:" + organizationId + ":store:" + distributionId, city.id());
    }
}

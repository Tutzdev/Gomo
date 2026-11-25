package br.com.supermercados.prices.collection.ifood;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Instant;

import br.com.supermercados.prices.collection.CollectedStore;
import br.com.supermercados.prices.collection.CollectorMetadata;
import br.com.supermercados.prices.collection.ServedCity;

public record IfoodMerchant(
        String code,
        String chain,
        String name,
        String merchantId,
        String slug,
        String address,
        String cnpj,
        BigDecimal deliveryLatitude,
        BigDecimal deliveryLongitude,
        Instant verifiedAt,
        ServedCity city) {

    public static final String WEBSITE = "https://www.ifood.com.br";

    public CollectorMetadata metadata() {
        return new CollectorMetadata(
            code,
            chain,
            name,
            "ifood_delivery",
            "Cardápio público iFood",
            WEBSITE,
            verifiedAt,
            chain,
            "ifood:chain:" + cnpj.substring(0, 8),
            "ifood:merchant:" + merchantId,
            city.id());
    }

    public CollectedStore store() {
        return new CollectedStore(
            name,
            address,
            null,
            null,
            true);
    }

    public String storeUrl() {
        return WEBSITE + "/delivery/" + citySlug() + "/" + slug + "/" + merchantId;
    }

    private String citySlug() {
        String plain = Normalizer.normalize(city.name(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");

        return (plain + "-" + city.stateCode()).toLowerCase().replace(' ', '-');
    }
}

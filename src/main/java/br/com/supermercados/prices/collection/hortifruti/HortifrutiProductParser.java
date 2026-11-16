package br.com.supermercados.prices.collection.hortifruti;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.format.DateTimeParseException;

import org.jsoup.Jsoup;

import br.com.supermercados.prices.collection.CollectedProduct;
import br.com.supermercados.prices.common.ApiException;
import br.com.supermercados.prices.price.StockAvailability;
import br.com.supermercados.prices.product.Gtin;
import tools.jackson.databind.JsonNode;

final class HortifrutiProductParser {

    CollectedProduct parse(JsonNode product) {
        JsonNode offer = null;

        for (JsonNode candidate : product.path("offers").path("offers")) {
            if (HortifrutiCollector.SELLER.equals(candidate.path("seller").path("identifier").asString())) {
                offer = candidate;
                break;
            }
        }

        if (offer == null) {throw new IllegalArgumentException("Preço sem vendedor local confirmado");}

        BigDecimal multiplier = product.path("unitMultiplier").decimalValue();

        if (multiplier.compareTo(BigDecimal.ONE) != 0) {
            throw new IllegalArgumentException("Peso variável exige base de cálculo confirmada");
        }

        String id = required(product, "sku");
        BigDecimal price = offer.path("price").decimalValue().setScale(2, RoundingMode.UNNECESSARY);
        BigDecimal regular = offer.path("listPrice").decimalValue().setScale(2, RoundingMode.UNNECESSARY).max(price);

        if (price.signum() <= 0) {throw new IllegalArgumentException("Preço não positivo");}

        String gtin;

        try {
            gtin = Gtin.normalize(product.path("gtin").asString(null));
        } catch (ApiException exception) {
            gtin = null;
        }

        String description = Jsoup.parse(product.path("description").asString("")).text();

        if (description.length() > 2000) {description = description.substring(0, 2000);}

        Instant expiry = expiry(offer);

        StockAvailability availability = "https://schema.org/InStock".equals(offer.path("availability").asString())
                ? StockAvailability.AVAILABLE : StockAvailability.UNAVAILABLE;

        return new CollectedProduct(
            "hortifruti:product:" + id,
            required(product, "name"),
            gtin,
            product.path("brand").path("name").asString(null),
            description,
            null,
            regular,
            price.compareTo(regular) < 0 ? price : null,
            null,
            expiry,
            price.compareTo(regular) < 0 ? expiry : null,
            availability,
            product.path("image").path(0).path("url").asString(null),
            "https://www.hortifruti.com.br/" + required(product, "slug") + "/p");
    }

    private Instant expiry(JsonNode offer) {
        String date = offer.path("priceValidUntil").asString("");

        if (date.isBlank()) {return null;}

        try {
            return Instant.parse(date);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Validade de preço inválida", exception);
        }
    }

    private String required(JsonNode product, String field) {
        String value = product.path(field).asString("");

        if (value.isBlank()) {throw new IllegalArgumentException("Campo obrigatório ausente: " + field);}

        return value;
    }
}

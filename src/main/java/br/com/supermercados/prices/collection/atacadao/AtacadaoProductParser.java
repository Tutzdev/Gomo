package br.com.supermercados.prices.collection.atacadao;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

import br.com.supermercados.prices.collection.CollectedProduct;
import br.com.supermercados.prices.price.StockAvailability;
import tools.jackson.databind.JsonNode;

final class AtacadaoProductParser {

    /** Units sold as one closed package, so the price is per package. Weighed goods (KG) and kits stay out. */
    private static final Set<String> PACKAGE_UNITS = Set.of("UND", "UN", "PCT", "FRC", "GFA", "CX", "CXT", "LT", "PT",
            "PTE", "TB", "TBO", "BD", "BBA", "VDO");

    CollectedProduct parse(JsonNode product, String seller) {
        String sku = required(product, "sku");
        String name = required(product, "name");
        if (!product.path("unitMultiplier").isNumber()
                || product.path("unitMultiplier").decimalValue().compareTo(BigDecimal.ONE) != 0
                // The source sends the same unit as "UND", "un" or "Un".
                || !PACKAGE_UNITS.contains(product.path("measurementUnit").asString("").strip()
                        .toUpperCase(java.util.Locale.ROOT))) {
            throw new IllegalArgumentException("Unidade/peso exige confirmação da base de preço");
        }
        BigDecimal regular = null;
        BigDecimal promotion = null;
        String condition = null;
        for (JsonNode offer : product.path("offers").path("offers")) {
            if (!seller.equals(offer.path("seller").path("identifier").asString())) continue;
            BigDecimal price = price(offer.path("price"));
            int minimum = offer.path("minQuantity").asInt(-1);
            if (minimum == 1) {
                regular = regular == null ? price : regular.min(price);
            } else if (minimum > 1 && (promotion == null || price.compareTo(promotion) < 0)) {
                promotion = price;
                condition = "Preço por unidade ao comprar no mínimo " + minimum + " unidades. Não aplicado ao total.";
            }
        }
        if (regular == null) throw new IllegalArgumentException("Sem preço unitário do vendedor local confirmado");
        if (promotion != null && promotion.compareTo(regular) >= 0) {
            promotion = null;
            condition = null;
        }
        String category = product.path("breadcrumbList").path("itemListElement").path(0).path("name").asString(null);
        // The field called gtin contains local SKU/ERP identifiers in this API, not verified EANs.
        return new CollectedProduct("atacadao:online:product:" + sku, name, null,
                product.path("brand").path("name").asString(null), "Preço do canal online; retirada na unidade São Geraldo.",
                category, regular, promotion, condition, null, null, StockAvailability.UNKNOWN,
                product.path("image").path(0).path("url").asString(null),
                "https://www.atacadao.com.br/" + required(product, "slug") + "/p");
    }

    private BigDecimal price(JsonNode node) {
        if (!node.isNumber()) throw new IllegalArgumentException("Preço ausente");
        BigDecimal price = node.decimalValue().setScale(2, RoundingMode.UNNECESSARY);
        if (price.signum() <= 0) throw new IllegalArgumentException("Preço inválido");
        return price;
    }

    private String required(JsonNode node, String field) {
        String value = node.path(field).asString("");
        if (value.isBlank()) throw new IllegalArgumentException("Campo ausente: " + field);
        return value;
    }
}

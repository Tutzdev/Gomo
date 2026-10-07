package br.com.supermercados.prices.collection.mercafacil;

import java.math.BigDecimal;
import java.math.RoundingMode;

import br.com.supermercados.prices.collection.CollectedProduct;
import br.com.supermercados.prices.price.StockAvailability;
import br.com.supermercados.prices.product.ProductNormalizer;
import tools.jackson.databind.JsonNode;

final class MercafacilProductParser {

    CollectedProduct parse(JsonNode product, MercafacilPage page, MercafacilStoreDefinition store) {
        String id = required(product, "id");
        String name = required(product, "name").strip();
        String measurement = product.path("quantityDescription").asString("").strip();
        String unit = page.resolve(product.path("unitOfMeasurement")).path("abbreviation").asString("");
        if ("kg".equalsIgnoreCase(unit)) {
            name += " (preço de 1 kg)";
        } else if (!measurement.isBlank() && !ProductNormalizer.statesMeasurement(name)) {
            // The registered quantity is often the shipping weight ("PAO FORMA SEVEN BOYS 450G" registers
            // 500 g), so it only completes a description that states no size, such as one cut short.
            name += " " + measurement;
        }
        if (!product.path("variants").isEmpty()) {
            throw new IllegalArgumentException("Variações precisam de identificação individual");
        }
        BigDecimal price = new BigDecimal(required(product, "price")).setScale(2, RoundingMode.UNNECESSARY);
        if (price.signum() <= 0) throw new IllegalArgumentException("Preço não positivo");
        BigDecimal promotion = product.path("priceWithDiscount").isNumber()
                ? product.path("priceWithDiscount").decimalValue() : null;
        if (promotion != null && (promotion.signum() <= 0 || promotion.compareTo(price) >= 0)) promotion = null;
        StockAvailability availability = product.path("stock").isNumber()
                ? (product.path("stock").decimalValue().signum() > 0 ? StockAvailability.AVAILABLE : StockAvailability.UNAVAILABLE)
                : StockAvailability.UNKNOWN;
        String image = product.path("image").asString(null);
        if (image != null && (!image.startsWith("https://") || image.contains("sem-imagem"))) image = null;
        String origin = store.website().resolve("/" + store.slug() + "/produto/m/"
                + required(product, "slug") + "-" + required(product, "modelId")).toString();
        return new CollectedProduct("mercafacil:" + store.storeId() + ":product:" + id, name, null,
                product.path("brand").asString(null), null, product.path("department").asString(null),
                price, promotion, null, null, null, availability, image, origin);
    }

    private String required(JsonNode node, String field) {
        String value = node.path(field).asString("");
        if (value.isBlank()) throw new IllegalArgumentException("Campo obrigatório ausente: " + field);
        return value;
    }
}

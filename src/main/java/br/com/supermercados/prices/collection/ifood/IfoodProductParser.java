package br.com.supermercados.prices.collection.ifood;

import java.math.BigDecimal;
import java.math.RoundingMode;

import br.com.supermercados.prices.collection.CollectedProduct;
import br.com.supermercados.prices.common.ApiException;
import br.com.supermercados.prices.price.StockAvailability;
import br.com.supermercados.prices.product.Gtin;
import tools.jackson.databind.JsonNode;

final class IfoodProductParser {

    private static final String IMAGES = "https://static.ifood-static.com.br/image/upload/t_medium/pratos/";

    CollectedProduct parse(
        JsonNode item,
        IfoodMerchant merchant,
        String category) {

        if (!"DEFAULT".equals(item.path("type").asString(""))) {
            throw new IllegalArgumentException("Combo ou kit não entra na comparação");
        }

        if (item.path("needChoices").asBoolean(false)) {
            throw new IllegalArgumentException("Item com opções de escolha não tem preço único");
        }

        if (!soldByUnit(item)) {
            throw new IllegalArgumentException("Peso variável exige base de cálculo confirmada");
        }

        String id = required(item, "id");
        BigDecimal regular = price(item.path("unitPrice"));

        if (regular == null || regular.signum() <= 0) {throw new IllegalArgumentException("Preço não positivo");}

        BigDecimal promotional = price(item.path("promotionalPrice"));

        if (promotional != null && (promotional.signum() <= 0 || promotional.compareTo(regular) >= 0)) {
            promotional = null;
        }

        return new CollectedProduct(
            "ifood:" + merchant.merchantId() + ":product:" + id,
            required(item, "description"),
            gtin(item.path("ean").asString(null)),
            null,
            details(item),
            category,
            regular,
            promotional,
            null,
            null,
            null,
            availability(item),
            image(item),
            merchant.storeUrl());
    }

    private boolean soldByUnit(JsonNode item) {
        JsonNode units = item.path("sellingOption").path("availableUnits");

        if (!units.isArray() || units.isEmpty()) {return true;}

        for (JsonNode unit : units) {
            if (!"UNIT".equals(unit.asString(""))) {return false;}
        }

        return true;
    }

    private BigDecimal price(JsonNode value) {
        if (!value.isNumber()) {return null;}

        return value.decimalValue().setScale(2, RoundingMode.HALF_UP);
    }

    private String gtin(String ean) {
        try {
            return Gtin.normalize(ean);
        } catch (ApiException exception) {
            return null;
        }
    }

    private String details(JsonNode item) {
        String details = item.path("additionalInfo").asString("").strip();

        if (details.isBlank() || "-".equals(details)) {return null;}

        return details.length() > 2000 ? details.substring(0, 2000) : details;
    }

    private StockAvailability availability(JsonNode item) {
        boolean available = item.path("enabled").asBoolean(false)
                && "AVAILABLE".equals(item.path("availability").asString(""));

        return available ? StockAvailability.AVAILABLE : StockAvailability.UNAVAILABLE;
    }

    private String image(JsonNode item) {
        String logo = item.path("logoUrl").asString("");

        if (logo.isBlank()) {return null;}

        return IMAGES + logo;
    }

    private String required(JsonNode item, String field) {
        String value = item.path(field).asString("").strip();

        if (value.isBlank()) {throw new IllegalArgumentException("Campo obrigatório ausente: " + field);}

        return value;
    }
}

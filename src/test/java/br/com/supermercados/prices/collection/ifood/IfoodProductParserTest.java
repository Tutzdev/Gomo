package br.com.supermercados.prices.collection.ifood;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import br.com.supermercados.prices.price.StockAvailability;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class IfoodProductParserTest {

    private final IfoodProductParser parser = new IfoodProductParser();
    private final IfoodMerchant merchant = IfoodCollectorsConfiguration.SUPERMARKET_FLORESTA;

    @Test
    void readsPriceBarcodeAndLinkOfAUnitItem() throws Exception {
        var product = parser.parse(
            item(0),
            merchant,
            "Congelados");

        assertThat(product.sourceReference())
                .isEqualTo("ifood:964260c7-c1e2-4a1f-8f2b-5a053c9e09b1:product:f0cd0ead-c9c5-4c6e-9d51-a9ba88ffdcd5");

        assertThat(product.name()).isEqualTo("Pizza Frango com Requeijão e Mussarela Sadia 460g");
        assertThat(product.gtin()).isEqualTo("07893000672109");
        assertThat(product.regularPrice()).isEqualByComparingTo("26.08");
        assertThat(product.promotionalPrice()).isNull();
        assertThat(product.category()).isEqualTo("Congelados");
        assertThat(product.availability()).isEqualTo(StockAvailability.AVAILABLE);
        assertThat(product.imageUrl()).startsWith("https://static.ifood-static.com.br/image/upload/");

        assertThat(product.originUrl()).isEqualTo("https://www.ifood.com.br/delivery/volta-redonda-rj/"
                + "supermarket-floresta---volta-redonda-aterrado/964260c7-c1e2-4a1f-8f2b-5a053c9e09b1");
    }

    @Test
    void keepsThePromotionOnlyWhenItIsBelowTheRegularPrice() throws Exception {
        ObjectNode item = item(1);

        assertThat(parser.parse(item, merchant, "Doces").promotionalPrice()).isEqualByComparingTo("6.49");

        item.put("promotionalPrice", 13.03);

        assertThat(parser.parse(item, merchant, "Doces").promotionalPrice()).isNull();
    }

    @Test
    void rejectsWeighedItemsCombosAndItemsWithChoices() throws Exception {
        assertThatThrownBy(() -> parser.parse(item(2), merchant, "Carnes")).hasMessageContaining("Peso variável");
        assertThatThrownBy(() -> parser.parse(item(3), merchant, "Bebidas")).hasMessageContaining("Combo");

        ObjectNode withChoices = item(0);
        withChoices.put("needChoices", true);

        assertThatThrownBy(() -> parser.parse(withChoices, merchant, "Bebidas")).hasMessageContaining("opções");
    }

    @Test
    void dropsInternalCodesAndMarksDisabledItemsUnavailable() throws Exception {
        ObjectNode item = item(0);
        item.put("ean", "SM00201136134");
        item.put("enabled", false);

        var product = parser.parse(item, merchant, "Frios");

        assertThat(product.gtin()).isNull();
        assertThat(product.availability()).isEqualTo(StockAvailability.UNAVAILABLE);
    }

    @Test
    void rejectsMissingOrZeroPrice() throws Exception {
        ObjectNode item = item(0);
        item.put("unitPrice", 0);

        assertThatThrownBy(() -> parser.parse(item, merchant, "Frios")).hasMessageContaining("Preço");

        item.remove("unitPrice");

        assertThatThrownBy(() -> parser.parse(item, merchant, "Frios")).hasMessageContaining("Preço");
    }

    private ObjectNode item(int index) throws Exception {
        try (var input = getClass().getResourceAsStream("/collectors/regional/ifood-floresta-category.json")) {
            JsonNode root = JsonMapper.builder().build().readTree(input);

            return (ObjectNode) root.path("data").path("categoryMenu").path("itens").path(index);
        }
    }
}

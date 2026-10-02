package br.com.supermercados.prices.collection.atacadao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

class AtacadaoProductParserTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void selectsLocalUnitPriceAndKeepsWholesaleConditionAndInternalCodeSeparate() {
        var product = mapper.readTree("""
                {"sku":"123", "gtin":"11757979", "slug":"detergente-123", "name":"Detergente Ype 500ml",
                 "measurementUnit":"UND", "unitMultiplier":1,"brand":{"name":"Ype"},
                 "offers":{"offers":[
                   {"price":1.99,"minQuantity":1,"seller":{"identifier":"other-city"}},
                   {"price":2.55,"minQuantity":1,"seller":{"identifier":"atacadaobr815"}},
                   {"price":2.39,"minQuantity":3,"seller":{"identifier":"atacadaobr815"}}
                 ]}}
                """);
        var parsed = new AtacadaoProductParser().parse(product, "atacadaobr815");
        assertThat(parsed.regularPrice()).isEqualByComparingTo("2.55");
        assertThat(parsed.promotionalPrice()).isEqualByComparingTo("2.39");
        assertThat(parsed.promotionCondition()).contains("mínimo 3");
        assertThat(parsed.gtin()).isNull();
        assertThatThrownBy(() -> new AtacadaoProductParser().parse(product, "atacadaobr289"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("vendedor local");
    }

    @Test
    void acceptsLowercasePackageUnitsButNotWeighedGoods() {
        String template = """
                {"sku":"9", "slug":"acucar-9", "name":"Açúcar Refinado Bulnez 1kg",
                 "measurementUnit":"%s", "unitMultiplier":%s,
                 "offers":{"offers":[{"price":2.89,"minQuantity":1,"seller":{"identifier":"atacadaobr815"}}]}}
                """;
        var parser = new AtacadaoProductParser();
        assertThat(parser.parse(mapper.readTree(template.formatted("un", "1")), "atacadaobr815").regularPrice())
                .isEqualByComparingTo("2.89");
        assertThatThrownBy(() -> parser.parse(mapper.readTree(template.formatted("kg", "0.5")), "atacadaobr815"))
                .hasMessageContaining("base de preço");
    }
}

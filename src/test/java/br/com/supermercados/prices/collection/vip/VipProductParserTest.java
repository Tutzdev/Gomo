package br.com.supermercados.prices.collection.vip;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.supermercados.prices.collection.CollectedProduct;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class VipProductParserTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void packageSoldByWeightKeepsItsPackagePriceWhenTheNameAgrees() {
        var product = parse("""
                {"produto_id": 1, "descricao": "Queijo Provolone Regina 300g", "unidade_sigla": "KG",
                 "possui_unidade_diferente": true, "quantidade_unidade_diferente": 0.3,
                 "unidade_fracao": {"sigla": "KG", "quantidade": 0.3, "preco": 130}, "preco": "39.00",
                 "disponivel": true, "em_oferta": false}""");

        assertThat(product.name()).isEqualTo("Queijo Provolone Regina 300g");
        assertThat(product.regularPrice()).isEqualByComparingTo("39.00");
    }

    @Test
    void trayOfApproximateWeightIsPublishedPerKilogram() {
        var product = parse("""
                {"produto_id": 2, "descricao": "Banana Prata Bandeja 800g", "unidade_sigla": "KG",
                 "possui_unidade_diferente": true, "quantidade_unidade_diferente": 0.8,
                 "unidade_fracao": {"sigla": "KG", "quantidade": 0.8, "preco": 6.99}, "preco": "5.59",
                 "disponivel": true, "em_oferta": true,
                 "oferta": {"categoria": "G", "tipo_oferta_id": 1, "quantidade_minima": 1,
                            "preco_antigo": "5.59", "preco_oferta": "4.79"}}""");

        assertThat(product.name()).isEqualTo("Banana Prata (preço de 1 kg)");
        assertThat(product.regularPrice()).isEqualByComparingTo("6.99");
        assertThat(product.promotionalPrice()).isEqualByComparingTo("5.99");
    }

    @Test
    void weighedItemWithoutAKnownPriceBasisIsLeftForReview() {
        var parsed = new VipProductParser().parse(List.of(
                node("""
                        {"produto_id": 3, "descricao": "Queijo Brie Forma Vigor 200gr", "unidade_sigla": "KG",
                         "possui_unidade_diferente": false, "preco": "149.99", "disponivel": true}"""),
                node("""
                        {"produto_id": 4, "descricao": "Coxa E Sobrecoxa Bandeja 1,3kg", "unidade_sigla": "KG",
                         "possui_unidade_diferente": true, "quantidade_unidade_diferente": 1.3, "preco": "12.99"}"""),
                node("""
                        {"produto_id": 5, "descricao": "Batata Lavada Media 1kg", "unidade_sigla": "KG",
                         "possui_unidade_diferente": true, "quantidade_unidade_diferente": 1, "preco": "5000.00"}""")),
                "vip:test", "Teste");

        assertThat(parsed.products()).isEmpty();
        assertThat(parsed.warnings()).hasSize(3);
    }

    private CollectedProduct parse(String json) {
        var parsed = new VipProductParser().parse(List.of(node(json)), "vip:test", "Teste");
        assertThat(parsed.warnings()).isEmpty();
        return parsed.products().getFirst();
    }

    private JsonNode node(String json) {
        return mapper.readTree(json);
    }

    @ParameterizedTest
    @ValueSource(strings = {"bramil-24-coca", "perola-1-coca", "spani-6-coca"})
    void parsesObservedRegionalCatalogsWithoutReusingAnotherStoresIdentity(String fixture) throws Exception {
        try (var input = getClass().getResourceAsStream("/collectors/regional/" + fixture + ".json")) {
            JsonNode response = JsonMapper.builder().build().readTree(input);
            var entries = new ArrayList<JsonNode>();
            response.path("data").forEach(entries::add);
            var parsed = new VipProductParser().parse(entries, fixture, fixture);
            assertThat(parsed.warnings()).isEmpty();
            assertThat(parsed.products()).hasSize(3).allSatisfy(product -> {
                assertThat(product.sourceReference()).startsWith(fixture + ":product:");
                assertThat(product.regularPrice()).isPositive();
                assertThat(product.name()).isNotBlank();
                assertThat(product.imageUrl()).startsWith("https://produto-assets-vipcommerce-com-br.br-se1.magaluobjects.com/250x250/");
                assertThat(product.promotionValidUntil()).isNull();
            });
        }
    }
}

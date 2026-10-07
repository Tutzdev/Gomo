package br.com.supermercados.prices.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class CatalogDisplayNameTest {

    private final CatalogKey keys = CatalogKey.learn(List.of("Coca-Cola", "Monster", "Ninho", "Tio João", "Omo", "Nescau"), List.of());

    private String generic(String retailerName) {
        return CatalogBuilder.Group.genericName(retailerName, keys.identify(retailerName));
    }

    @Test
    void retailerNoiseIsRemovedAndTheStandardSizeAppended() {
        assertThat(generic("Refr. Coca-cola 2lt Pet")).isEqualTo("Coca-Cola 2 L");
        assertThat(generic("REFRIGERANTE COCA COLA ZERO 350ML C/6")).isEqualTo("Coca-Cola Zero 6 × 350 ml");
        assertThat(generic("Coca-Cola Sem Açúcar 1,5L")).isEqualTo("Coca-Cola Sem Açúcar 1,5 L");
        assertThat(generic("Bebida Energética Monster 473ml Mango Loco")).isEqualTo("Monster Mango Loco 473 ml");
        assertThat(generic("Leite em Pó Ninho Integral Lata 380g")).isEqualTo("Leite em Pó Ninho Integral 380 g");
        assertThat(generic("Leite Lv Ninho Integral 1l")).isEqualTo("Leite Ninho Integral 1 L");
        assertThat(generic("ARROZ TIO JOAO TP 1 5kg")).isEqualTo("Arroz Tio João Tipo 1 5 kg");
        assertThat(generic("Leite Po Ninho Integral 380g")).isEqualTo("Leite em Pó Ninho Integral 380 g");
        assertThat(generic("Lava Roupa Liq. Omo 900ml")).isEqualTo("Lava Roupa Líquido Omo 900 ml");
        assertThat(generic("Bebida Láctea Nescau 180ml")).isEqualTo("Bebida Láctea Nescau 180 ml");
    }

    @Test
    void genericItemsAreNamedFromTheirKeyWithTheAccentsSomeMarketWrites() {
        assertThat(CatalogBuilder.Group.accented("BANANA MACA",
                List.of("BANANA MACA KG (preço de 1 kg)", "Banana Maçã (preço de 1 kg)"))).isEqualTo("Banana Maçã");
        assertThat(CatalogBuilder.Group.accented("TOMATE", List.of("TOMATE DEBORA KG"))).isEqualTo("Tomate");
        assertThat(CatalogBuilder.Group.accented("Ovos Brancos Grandes", List.of("Ovos Branco Gde Iana 12un")))
                .isEqualTo("Ovos Brancos Grandes");
    }
}

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
        var spellings = CatalogBuilder.Group.spellings(List.of("BANANA MACA KG (preço de 1 kg)",
                "Banana Maçã (preço de 1 kg)", "Maçã Fuji (preço de 1 kg)", "Banana Maça (preço de 1 kg)",
                "Limão Siciliano (preço de 1 kg)", "LIMAO KG (preço de 1 kg)", "TOMATE DEBORA KG"));
        // The most common accented spelling, even from another item ("Limão Siciliano").
        assertThat(CatalogBuilder.Group.accented("BANANA MACA", spellings)).isEqualTo("Banana Maçã");
        assertThat(CatalogBuilder.Group.accented("LIMAO", spellings)).isEqualTo("Limão");
        assertThat(CatalogBuilder.Group.accented("TOMATE", spellings)).isEqualTo("Tomate");
        assertThat(CatalogBuilder.Group.accented("Ovos Brancos Grandes", spellings)).isEqualTo("Ovos Brancos Grandes");
    }

    @Test
    void aNumberCutFromTheSizeAndErpAbbreviationsAreCleanedUp() {
        CatalogKey learned = CatalogKey.learn(List.of("Queensberry", "Baby Soft", "Itambe", "Farao"), List.of());
        assertThat(CatalogBuilder.Group.genericName("Geleia Queensberry Classic 320",
                learned.identify("Geleia Queensberry Classica 320g Morango"))).isEqualTo("Geleia Queensberry Classic 320 g");
        assertThat(CatalogBuilder.Group.genericName("Leite Cond Itambe 39",
                learned.identify("Leite Condensado Itambe 395g"))).isEqualTo("Leite Cond Itambe 395 g");
        assertThat(CatalogBuilder.Group.genericName("Amac. Roupa Conc. Baby Soft 500ml",
                learned.identify("Amac. Roupa Conc. Baby Soft 500ml"))).isEqualTo("Amaciante Roupa Concentrado Baby Soft 500 ml");
        assertThat(CatalogBuilder.Group.genericName("CAFE FARAO VACUO 500 Grama(s)",
                learned.identify("CAFE FARAO VACUO 500 Grama(s)"))).isEqualTo("Cafe Farao Vacuo 500 g");
        // A number that is not the start of the size stays: "Tipo 1", "Nº 8".
        assertThat(generic("ARROZ TIO JOAO TP 1 5kg")).isEqualTo("Arroz Tio João Tipo 1 5 kg");
    }

    @Test
    void accentsDroppedByTheStoresAreRestored() {
        assertThat(CatalogSpelling.accented("Oleo Liza Soja")).isEqualTo("Óleo Liza Soja");
        assertThat(CatalogSpelling.accented("Cafe 3 Coracoes Extra Forte")).isEqualTo("Café 3 Corações Extra Forte");
        assertThat(CatalogSpelling.accented("PAO DE FORMA")).isEqualTo("PÃO DE FORMA");
        // Words with two readings stay as written.
        assertThat(CatalogSpelling.accented("Massa Para Lasanha Maca Verde")).isEqualTo("Massa Para Lasanha Maca Verde");
    }
}

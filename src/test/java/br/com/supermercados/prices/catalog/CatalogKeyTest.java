package br.com.supermercados.prices.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class CatalogKeyTest {

    private final CatalogKey keys = CatalogKey.learn(List.of("Coca-Cola", "Monster", "Sadia", "Ypê", "Serramar",
            "Qualy", "Tio João", "Aviação", "3 Corações", "Colman", "Minalba", "Pilão"), List.of());

    @Test
    void retailerSpellingsOfTheSameProductShareOneKey() {
        assertThat(List.of("Refrigerante Coca Cola 2l", "REF COCA COLA 2L", "Refr. Coca-cola 2lt Pet",
                "Refrigerante Original Coca Cola 2L", "COCA COLA 2L GELADO"))
                .extracting(name -> keys.identify(name).key()).containsOnly("COCA COLA|1x2000ML");
        assertThat(List.of("Bebida Energética Monster 473ml Mango Loco", "ENERG MONSTER MANGO LOCO 473ML",
                "Energético Monster Mango Loco Lata 473ml", "Repositor Monster 473ml Mango Loco"))
                .extracting(name -> keys.identify(name).key()).containsOnly("LOCO MANGO MONSTER|1x473ML");
    }

    @Test
    void variantSizeAndPackCountKeepProductsApart() {
        String regular = keys.identify("Refrigerante Coca Cola 2l").key();
        assertThat(keys.identify("Coca-Cola Sem Açúcar 2L").key()).isNotEqualTo(regular)
                .isEqualTo(keys.identify("Coca-cola Zero 2lts").key());
        assertThat(keys.identify("Refrigerante Coca Cola 1,5l").key()).isEqualTo("COCA COLA|1x1500ML");
        assertThat(keys.identify("REFRIGERANTE COCA COLA 350ML C/6").key()).isEqualTo("COCA COLA|6x350ML");
    }

    @Test
    void erpAbbreviationsMeanTheFullWord() {
        assertThat(keys.identify("IOG SERRAMAR BATIDO 900GR").key())
                .isEqualTo(keys.identify("Iogurte Batido Serramar 900G").key());
        assertThat(keys.identify("MARG QUALY LIGHT 250 250gr").key())
                .isEqualTo(keys.identify("Margarina Qualy 250g Light").key());
        assertThat(keys.identify("Cafe Po Pilao 500g").key())
                .isEqualTo(keys.identify("Café Torrado e Moído Pilão 500g").key());
    }

    @Test
    void defaultVariantsCanBeOmittedButOthersCannot() {
        String rice = keys.identify("Arroz Tio Joao Tipo 1 5kg").key();
        assertThat(List.of("Arroz Tio Joao 5kg Tp1", "ARROZ TIO JOAO TP 1 5kg", "Arroz Tio João 5kg"))
                .extracting(name -> keys.identify(name).key()).containsOnly(rice);
        assertThat(keys.identify("Arroz Tio Joao Tipo 2 5kg").key()).isNotEqualTo(rice);

        String stillWater = keys.identify("Água Mineral Minalba Sem Gás 510ml").key();
        assertThat(keys.identify("Agua Mineral Minalba 510ml").key()).isEqualTo(stillWater);
        assertThat(keys.identify("Água Mineral Minalba Com Gás 510ml").key()).isNotEqualTo(stillWater);
    }

    @Test
    void sizesRepeatedByTheStoreAreReadOnce() {
        assertThat(keys.identify("DOCE LEITE AVIACAO 6 600 Grama(s)").key())
                .isEqualTo(keys.identify("Doce Leite Aviacao 600g Vidro").key());
        assertThat(keys.identify("ANIL LIQ COLMAN 200M 0.2 lt").size()).isEqualTo("1x200ML");
    }

    @Test
    void brandsWithNumbersAreRecognised() {
        assertThat(keys.identify("CAFE 3 CORACOES 500G 500gr").brand()).isEqualTo("3 CORACOES");
    }

    @Test
    void bundlesAndUnbrandedGenericItemsAreNotGrouped() {
        assertThat(keys.identify("Kit Coca Cola 2l + Guaraná 2l")).isNull();
        assertThat(keys.identify("Leve 3 Pague 2 Sabão Ypê 200g")).isNull();
        assertThat(keys.identify("Arroz Branco 5kg")).isNull();
        assertThat(keys.identify("Banana Prata")).isNull();
    }

    @Test
    void searchTermsUnderstandSizesTypedByShoppers() {
        assertThat(CatalogKey.searchTerms("coca cola 1 litro")).isEqualTo(
                new CatalogKey.SearchTerms(List.of("COCA", "COLA"), "x1000ML"));
        assertThat(CatalogKey.searchTerms("Monster 473ml")).isEqualTo(
                new CatalogKey.SearchTerms(List.of("MONSTER"), "x473ML"));
    }

    @Test
    void sizeLabelsAreReadable() {
        assertThat(CatalogKey.sizeLabel("1x2000ML")).isEqualTo("2 L");
        assertThat(CatalogKey.sizeLabel("1x1500ML")).isEqualTo("1,5 L");
        assertThat(CatalogKey.sizeLabel("6x350ML")).isEqualTo("6 × 350 ml");
        assertThat(CatalogKey.sizeLabel("1x500G")).isEqualTo("500 g");
        assertThat(CatalogKey.sizeLabel("30xUN")).isEqualTo("30 un");
    }
}

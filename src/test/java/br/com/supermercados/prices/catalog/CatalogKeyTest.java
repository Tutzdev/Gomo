package br.com.supermercados.prices.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class CatalogKeyTest {

    private final CatalogKey keys = CatalogKey.learn(List.of("Coca-Cola", "Monster", "Sadia", "Ypê", "Serramar",
            "Qualy", "Tio João", "Aviação", "3 Corações", "Colman", "Minalba", "Pilão", "Royal", "Nivea", "Galiotto"), List.of());

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
    void villeAndPameAbbreviationsMeanTheFullWord() {
        assertThat(keys.identify("FILEZINHO FRG SADIA 1kg").key()).isEqualTo(keys.identify("Filezinho Frango Sadia 1kg").key());
        assertThat(keys.identify("ST NIVEA TOQUE BAUNILHA 85G").key())
                .isEqualTo(keys.identify("Sabonete Nivea Toque Baunilha 85g").key());
        assertThat(keys.identify("GELATINA EM PO SAB MORANGO ROYAL 25G").key())
                .isEqualTo(keys.identify("Gelatina em Pó Royal Morango 25g").key());
    }

    @Test
    void defaultVariantsCanBeOmittedButOthersCannot() {
        String salted = keys.identify("Manteiga Aviação 500g").key();
        assertThat(keys.identify("MANTEIGA AVIACAO C S 500gr").key()).isEqualTo(salted);
        assertThat(keys.identify("Manteiga Aviação Com Sal 500g").key()).isEqualTo(salted);
        assertThat(keys.identify("Manteiga Aviação Sem Sal 500g").key()).isNotEqualTo(salted);
        assertThat(keys.identify("Suco Galiotto Uva Tinto Integral 1l").key())
                .isEqualTo(keys.identify("SUCO DE UVA INTEGRAL GALIOTTO 1L").key());

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
        assertThat(keys.identify("Alimento Achocolatado Nescau Pacote Leve 730g Pague 680g")).isNull();
        assertThat(keys.identify("Biscoito Integral Leve Mais E Pague Menos Club Social 288G")).isNull();
    }

    @Test
    void brandsThatLookLikeBundleWordsAreStillProducts() {
        assertThat(keys.identify("CAPELETTI MASSA LEVE 400gr")).isNotNull();
        assertThat(keys.identify("CEREAL KIT KAT 210G")).isNotNull();
        assertThat(keys.identify("Kit Pano Multiuso Camesa com 3 peças")).isNull();
    }

    @Test
    void looseProducePricedPerKilogramIsIdentifiedByItsExactWords() {
        String onion = keys.identify("Cebola Nacional (preço de 1 kg)").key();
        assertThat(keys.identify("CEBOLA NACIONAL KG (preço de 1 kg)").key()).isEqualTo(onion);
        assertThat(keys.identify("Cebola Nacional Bandeja (preço de 1 kg)").key()).isEqualTo(onion);
        assertThat(keys.identify("Cebola Roxa (preço de 1 kg)").key()).isNotEqualTo(onion);
        assertThat(keys.identify("Alho (preço de 1 kg)").key()).isEqualTo(keys.identify("ALHO A GRANEL (preço de 1 kg)").key());
        assertThat(keys.identify("Pernil Suína Com Osso (preço de 1 kg)").key())
                .isEqualTo(keys.identify("PERNIL SUINO C OSSO (preço de 1 kg)").key());
        // Without the per-kg price a generic name still identifies nothing.
        assertThat(keys.identify("Cebola Nacional 1kg")).isNull();
    }

    @Test
    void looseProduceIgnoresTheGradeAndTheTradeNamesOfTheEverydayVariety() {
        // Real descriptions of Nagumo, Ville, Pame and the VIP trays (published per kg by the collector).
        String banana = keys.identify("Banana Prata KG (preço de 1 kg)").key();
        assertThat(List.of("BANANA PRATA (preço de 1 kg)", "BANANA PRATA MEDIA KG (preço de 1 kg)",
                "Banana Prata (preço de 1 kg)")).extracting(name -> keys.identify(name).key()).containsOnly(banana);
        assertThat(keys.identify("BANANA DAGUA KG (preço de 1 kg)").key())
                .isEqualTo(keys.identify("Banana Nanica KG (preço de 1 kg)").key()).isNotEqualTo(banana);

        String tomato = keys.identify("Tomate Debora (preço de 1 kg)").key();
        assertThat(List.of("Tomate Salada (preço de 1 kg)", "TOMATE KG (preço de 1 kg)",
                "Tomate Carmem Graudo (preço de 1 kg)", "Tomate Selecionado (preço de 1 kg)"))
                .extracting(name -> keys.identify(name).key()).containsOnly(tomato);
        assertThat(keys.identify("Tomate Debora (preço de 1 kg)").genericName()).isEqualTo("TOMATE");
        // Other varieties are priced apart and stay items of their own.
        assertThat(keys.identify("TOMATE ITALIANO KG (preço de 1 kg)").key()).isNotEqualTo(tomato);
        assertThat(keys.identify("Tomate Cereja (preço de 1 kg)").key()).isNotEqualTo(tomato);

        String potato = keys.identify("Batata lavada (preço de 1 kg)").key();
        assertThat(keys.identify("BATATA LAVADA KG (preço de 1 kg)").key()).isEqualTo(potato);
        assertThat(keys.identify("Batata Escovada (preço de 1 kg)").key()).isEqualTo(potato);
        assertThat(keys.identify("Batata Asterix (preço de 1 kg)").key()).isNotEqualTo(potato);
        assertThat(keys.identify("BATATA DOCE (preço de 1 kg)").key()).isNotEqualTo(potato);
        assertThat(keys.identify("Limao Tahiti (preço de 1 kg)").key()).isEqualTo(keys.identify("LIMAO TAITI KG (preço de 1 kg)").key());
    }

    @Test
    void cartonsOfEggsCompareByColourSizeAndCountWhateverTheFarm() {
        String white12 = keys.identify("Ovos Branco Gde Iana 12un").key();
        assertThat(List.of("Ovos Branco Grande Santa Monica C/12un", "Ovos Brancos Grandes Mantiqueira Dúzia",
                "OVOS BRANCOS GRANDES C/12")).extracting(name -> keys.identify(name).key()).containsOnly(white12);
        assertThat(keys.identify("Ovos Branco Gde Iana 12un").genericName()).isEqualTo("Ovos Brancos Grandes");
        assertThat(keys.identify("Ovos Branco Gde Iana Filme 30un").key()).isNotEqualTo(white12);
        assertThat(keys.identify("Ovos Branco Jumbo Iana 12un").key()).isNotEqualTo(white12);
        assertThat(keys.identify("Ovos Vermelhos Grandes 12 unidades").key()).isNotEqualTo(white12);
        assertThat(keys.identify("Ovos Brancos Embalados com 30 unidades").genericName()).isEqualTo("Ovos Brancos");

        String caipira = keys.identify("Ovos Vermelho Caipira com 10 Unidades").key();
        assertThat(keys.identify("Ovo Caipira Vermelho Natural da Terra 10 unidades").key()).isEqualTo(caipira);
        assertThat(keys.identify("Ovos Caipira com 10 Unidades").genericName()).isEqualTo("Ovos Caipira");
        assertThat(keys.identify("Ovos Codorna Pet C/30").genericName()).isEqualTo("Ovos de Codorna");

        // A count cut at the 30-character ERP limit is not the real count; egg products are not cartons.
        assertThat(keys.identify("Ovos Bco Mantiqueira Jumbo C/1")).isNull();
        assertThat(keys.identify("Ovos Bco Mantiqueira Duzia Hfg").genericName()).isEqualTo("Ovos Brancos");
        assertThat(keys.identify("Ovo Integral Pausterizado Resfriado Tp 1kg").genericName()).isNull();
        assertThat(keys.identify("Ovos de Páscoa Sortidos 6 unidades")).isNull();
    }

    @Test
    void withoutIsNeverReadAsWith() {
        assertThat(keys.identify("AZEITONA VERDE S CAROCO (preço de 1 kg)").key())
                .isEqualTo(keys.identify("Azeitona Verde Sem Caroço (preço de 1 kg)").key())
                .isNotEqualTo(keys.identify("AZEITONA VERDE C/ CAROCO (preço de 1 kg)").key());
    }

    @Test
    void approximateTrayCountsAreNotPackCounts() {
        assertThat(keys.identify("Abacate Bandeja (aproximadamente 2 Unids) (preço de 1 kg)").size()).isEqualTo("1x1000G");
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

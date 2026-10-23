package br.com.supermercados.prices.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class CatalogCategoryTest {

    @Test
    void theFirstWordOfTheNameDecidesTheAisle() {
        assertThat(CatalogCategory.of("Leite Condensado Moça Nestlé 395 g", List.of())).isEqualTo(CatalogCategory.GROCERY);
        assertThat(CatalogCategory.of("Leite Integral Itambé 1 L", List.of())).isEqualTo(CatalogCategory.DAIRY);
        assertThat(CatalogCategory.of("Água Sanitária Super Globo 1 L", List.of())).isEqualTo(CatalogCategory.CLEANING);
        assertThat(CatalogCategory.of("Água Mineral Minalba 500 ml", List.of())).isEqualTo(CatalogCategory.DRINKS);
        assertThat(CatalogCategory.of("Creme Dental Colgate 90 g", List.of())).isEqualTo(CatalogCategory.PERSONAL_CARE);
        assertThat(CatalogCategory.of("Vinagre de Vinho Tinto Peixe 750 ml", List.of())).isEqualTo(CatalogCategory.GROCERY);
        assertThat(CatalogCategory.of("Pão de Queijo Forno de Minas 400 g", List.of())).isEqualTo(CatalogCategory.FROZEN);
    }

    @Test
    void preservedFruitIsNotProduceAndBrandsCanDecide() {
        assertThat(CatalogCategory.of("Tomate 1 kg", List.of())).isEqualTo(CatalogCategory.PRODUCE);
        assertThat(CatalogCategory.of("Tomate Pelado Paganin 400 g", List.of())).isEqualTo(CatalogCategory.GROCERY);
        assertThat(CatalogCategory.of("Batata Pringles Original 105 g", List.of())).isEqualTo(CatalogCategory.SWEETS);
        assertThat(CatalogCategory.of("Biscoito Biscrok Pedigree 500 g", List.of())).isEqualTo(CatalogCategory.PET);
        assertThat(CatalogCategory.of("Cerveja Sem Álcool Heineken 350 ml", List.of())).isEqualTo(CatalogCategory.ALCOHOL);
    }

    @Test
    void theStoresCategoryHelpsWhenTheNameSaysNothing() {
        assertThat(CatalogCategory.of("Grapette Framboesa 290 ml", List.of())).isEqualTo(CatalogCategory.DRINKS);
        assertThat(CatalogCategory.of("Mista Beats 269 ml", List.of("BEBIDA ALCOOLICA"))).isEqualTo(CatalogCategory.ALCOHOL);
        assertThat(CatalogCategory.of("Mista Beats 269 ml", List.of("BEBIDA NAO ALCOOLICA"))).isEqualTo(CatalogCategory.DRINKS);
        assertThat(CatalogCategory.of("Sardinha Coqueiro 125 g", List.of("PEIXARIA"))).isEqualTo(CatalogCategory.GROCERY);
        assertThat(CatalogCategory.of("Xpto 100 g", List.of())).isNull();
    }
}

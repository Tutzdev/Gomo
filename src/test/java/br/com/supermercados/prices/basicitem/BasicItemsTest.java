package br.com.supermercados.prices.basicitem;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class BasicItemsTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', value = {
        "Pão de Hambúrguer Plusvita Com gergelim 420g | pao-de-hamburguer",
        "Pão para Hambúrguer Bauducco 200g | pao-de-hamburguer",
        "Pao De Hamburguer Panco Com 6 Unidades 300gr | pao-de-hamburguer",
        "Pão de Hambúrguer com 4 unidades | pao-de-hamburguer",
        "Plusvita Pao Hamburguer 420g C Gerg | pao-de-hamburguer",
        "Pao Hamburger Delamarie 200G | pao-de-hamburguer",
        "PAO DE HAMBURGUER C/15 UND | pao-de-hamburguer",
        "PAO HAMBUGUER PLUS VITA 420G | pao-de-hamburguer",
        "Pão P/Hambúrguer Wickbold Gergelim | pao-de-hamburguer",
        "Pao Hamburguer Plus Vita Artesano 420g | pao-de-hamburguer",
        "Pão De Forma Kim Nobre Original 500G | pao-de-forma",
        "Pao De Forma Wickbold Tradicional 450g | pao-de-forma",
        "Carne Bovina Acem Reserva Resfriada Venda 1kg | acem",
        "Carne Bov. Acem Reserva Kg Ban (preço de 1 kg) | acem",
        "Carne Bovina Acem Porc Minerva Atc Kg (preço de 1 kg) | acem",
        "Carne 2a  Acem S Osso Bandeja Kg (preço de 1 kg) | acem",
        "Carne Bovina Patinho Jbs Porcionado 1kg | patinho",
        "PATINHO (preço de 1 kg) | patinho",
        "Patinho Extra Limpo | patinho",
        "Bife Patinho Res. Kg Bdj (preço de 1 kg) | patinho",
        "Carne Patinho Moido (preço de 1 kg) | carne-moida",
        "Acém Moído Better Beef 500G | carne-moida",
        "Carne Moida Bovina Friboi 500g | carne-moida",
        "Paleta Moída (preço de 1 kg) | carne-moida",
        "Carne 1a  Coxao Mole Bandeja Kg (preço de 1 kg) | coxao-mole",
        "Músculo Dianteiro (preço de 1 kg) | musculo",
        "CORACAO ALCATRA MATURATTA JBS KG (preço de 1 kg) | alcatra",
        "Filé de Peito de Frango Bio Sadia 1kg | peito-de-frango",
        "Coxa Sobecoxa Frango Lar Bandeja 1kg | coxa-e-sobrecoxa",
        "Arroz Tipo 1 Prime Raroz 5 Kg | arroz-branco",
        "Arroz Branco Tipo 1 Prato Fino 2Kg | arroz-branco",
        "Feijao Carioca Da Casa Tipo 1 1Kg | feijao-carioca",
        "Feijao Preto Bomchef Tipo 1 1kg | feijao-preto",
        "Acucar Ref. Uniao 1kg | acucar-refinado",
        "Óleo De Soja Liza 900 Ml | oleo-de-soja",
        "Cafe Santa Monica Prata 500gr | cafe-em-po",
        "Café torrado e moido tradicional Caboclo vacuo 250g | cafe-em-po",
        "LEITE INTEGRAL SHEFA 1L | leite-integral",
        "Leite Longa Vida Integral Paulista 1L | leite-integral",
        "Farinha de Trigo Rosa Branca Tipo 1 1kg | farinha-de-trigo",
        "MACARRAO GALO ESPAGUETE FIDELINHO N10 500G | macarrao-espaguete",
        "SAL REFINADO CISNEI 1KG | sal-refinado",
        "Ovos Grande Brancos Granja Gemar com 12 Unidades | ovos-brancos",
        "Ovos Branco Gde Iana Filme 30un | ovos-brancos",
        "Manteiga Itambe 200g C/sal Tab | manteiga",
        "Margarina Qualy 250g C/sal | margarina"
    })
    void everydayNamesFromTheMarketsMatchTheirBasicItem(String name, String slug) {
        assertThat(BasicItems.match(name)).map(BasicItem::slug).contains(slug);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
        "Pao de Hamburguer Gluten Free Schar 130g",
        "Hambúrguer de Frango Seara 672g Congelado",
        "Carne Bovina Acem E Pescoco Friboi Reserva Congelada 1kg",
        "Limpador Desengordurante Cozinha Mr Musculo 500Ml",
        "Carne Suina Alcatra Sulita Sem Osso Temperado Congelado 1kg",
        "Peito de Frango Defumado Perdigão Ouro (preço de 1 kg)",
        "Nuggets Sadia Peito De Frango Legumes 275G",
        "Pimenta do Reino Preta Moída Kitano Pouch 50g",
        "Escondidinho De Carne Moída E Purê De Batata Sadia 600G",
        "Carne Moída Vegetal Superbom 390G",
        "ARROZ TIO JOAO PARBO 1kg",
        "Arroz Japonês Mirokumai 5kg",
        "Arroz Carreteiro Cozinha & Sabor Tio João 175G",
        "Feijao Carioca Camil Pronto 38",
        "Sardinha Coqueiro Em Óleo De Soja 125G",
        "Café Solúvel Tradição Nescafé Sache Leve 50G Pague 40G",
        "Leite Em Pó Integral Ninho 380G",
        "Leite UHT Zero Lactose Integral Italac 1L",
        "Farinha De Trigo Mirella Para Pastel 5Kg",
        "Massa Espaguete Renata Sem Glúten 500g",
        "Palmito Pupunha Spaghetti Golden Palm 255G",
        "Ovo Páscoa Lacta Hotwheels 166g",
        "Pipoca De Microondas Yoki Manteiga Suave Embalagem 100G",
        "Molho De Tomate Knorr Desidratado 38G",
        "Batata Pré-frita Carinhas Congelada Bem Brasil Pacote 1,05kg",
        "Pao De Forma 36% Integral Macio Visconti 400G"
    })
    void lookAlikesStayOutOfTheBasicItems(String name) {
        assertThat(BasicItems.match(name)).isEmpty();
    }

    @ParameterizedTest(name = "{1} {0} -> {2}")
    @CsvSource(delimiter = '|', value = {
        "Pão de Hambúrguer Plusvita Com gergelim 420g | KG | 0.42",
        "Pao De Hamburguer Panco Com 6 Unidades 300gr | KG | 0.3",
        "Pão Para Hambúrguer Kim 200G Com 4Un | KG | 0.2",
        "Pão de Hambúrguer (preço de 1 kg) | KG | 1",
        "Carne Em Embalagem A Vacuo Patinho Peca Kg ( Peso Aproximado 500G ) (preço de 1 kg) | KG | 1",
        "Batata Pré-frita Bem Brasil 1,05kg | KG | 1.05",
        "Óleo De Soja Liza 900 Ml | L | 0.9",
        "Óleo de Soja Vila Velha 18L | L | 18",
        "Ovos Branco Gde Iana Filme 30un | UN | 30",
        "OVOS BRANCOS EXTRA C/12 | UN | 12",
        "Ovo Branco Dúzia | UN | 12",
        "Leite Integral Caixa 12 x 1L | L | 12"
    })
    void packageSizesAreReadInTheUnitOfTheItem(String name, Measure measure, BigDecimal expected) {
        Optional<BigDecimal> size = PackageSize.of(measure, name, null, null, null);

        assertThat(size).hasValueSatisfying(amount -> assertThat(amount).isEqualByComparingTo(expected));
    }

    @Test
    void declaredQuantityIsUsedWhenTheNameHasNoSize() {
        Optional<BigDecimal> size = PackageSize.of(
            Measure.KG,
            "Pão P/Hambúrguer Wickbold Gergelim",
            null,
            new BigDecimal("320"),
            "G");

        assertThat(size).hasValueSatisfying(amount -> assertThat(amount).isEqualByComparingTo("0.32"));
    }

    @Test
    void unknownSizeStaysUnknown() {
        assertThat(PackageSize.of(Measure.KG, "Patinho Extra Limpo", null, null, null)).isEmpty();
    }

    @Test
    void typicalPackagesSkipBulkAndTinyOnes() {
        BasicItem bread = BasicItems.find("pao-de-hamburguer").orElseThrow();

        assertThat(bread.typicalPackage(new BigDecimal("0.42"))).isTrue();
        assertThat(bread.typicalPackage(new BigDecimal("0.2"))).isTrue();
        assertThat(bread.typicalPackage(new BigDecimal("0.6"))).isTrue();
        assertThat(bread.typicalPackage(BigDecimal.ONE)).isFalse();
        assertThat(bread.typicalPackage(new BigDecimal("0.13"))).isFalse();
        assertThat(bread.typicalPackage(new BigDecimal("5"))).isFalse();
        assertThat(bread.typicalPackage(null)).isFalse();
    }

    @Test
    void searchFindsItemsByEverydayWords() {
        assertThat(BasicItems.search("pão de hambúrguer")).extracting(BasicItem::slug).containsExactly("pao-de-hamburguer");
        assertThat(BasicItems.search("feijao")).extracting(BasicItem::slug)
                .containsExactly("feijao-carioca", "feijao-preto");
        assertThat(BasicItems.search("acem")).extracting(BasicItem::slug).containsExactly("acem");
        assertThat(BasicItems.search("de")).isEmpty();
    }
}

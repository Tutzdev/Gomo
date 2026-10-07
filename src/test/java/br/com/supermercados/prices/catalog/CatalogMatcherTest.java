package br.com.supermercados.prices.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class CatalogMatcherTest {

    private static final UUID STORE_A = UUID.randomUUID();
    private static final UUID STORE_B = UUID.randomUUID();
    private static final UUID STORE_C = UUID.randomUUID();

    private final CatalogKey keys = CatalogKey.learn(List.of("Sol", "União", "Ninho", "Ypê", "Pomarola", "Perdigão",
            "Piraquê", "Serramar", "Leão", "Renata"), List.of());
    private final List<CatalogMatcher.Listing> listings = new ArrayList<>();

    @Test
    void wordsAboutTheFormOfTheProductAreOptional() {
        UUID powder = sku(STORE_A, "Gelatina em Pó Sabor Abacaxi Sol 20g");
        UUID plain = sku(STORE_B, "Gelatina Sol 20g Abacaxi");

        assertThat(item(powder)).isEqualTo(item(plain));
    }

    @Test
    void aWordCutAtTheErpLimitMatchesTheCompleteWord() {
        UUID cut = sku(STORE_A, "Acucar Granulado Uniao 1kg Pre");
        UUID complete = sku(STORE_B, "Açúcar Granulado União Premium 1kg");

        assertThat(item(cut)).isEqualTo(item(complete));
    }

    @Test
    void oneSkuDescribedByAnotherStoreLinksThatDescription() {
        UUID shared = sku(STORE_A, "Leite Po Ninho 380g Integral I");
        listings.add(new CatalogMatcher.Listing(shared, STORE_B, "Leite Po Ninho 380g Integral I",
                "Leite em Pó Ninho Integral Instantâneo 380g"));
        UUID instant = sku(STORE_C, "Leite em Pó Integral Instantâneo Ninho 380g");
        UUID regular = sku(STORE_C, "Leite em Pó Ninho Integral 380g");

        assertThat(item(shared)).isEqualTo(item(instant)).isNotEqualTo(item(regular));
    }

    @Test
    void flavourAndPackagingKeepProductsApart() {
        UUID pineapple = sku(STORE_A, "Gelatina Sol 20g Abacaxi");
        UUID strawberry = sku(STORE_B, "Gelatina Sol 20g Morango");
        UUID box = sku(STORE_A, "Sabão em Pó Tixan Ypê Primavera 800g");
        UUID refill = sku(STORE_B, "Sabão em Pó Tixan Ypê Primavera Sachê 800g");

        assertThat(item(pineapple)).isNotEqualTo(item(strawberry));
        assertThat(item(box)).isNotEqualTo(item(refill));
    }

    @Test
    void aStoreSellingBothDescriptionsProvesTheyAreDifferentProducts() {
        UUID homemade = sku(STORE_A, "Molho Tomate Pomarola 300g Cas");
        listings.add(new CatalogMatcher.Listing(homemade, STORE_B, "Molho Tomate Pomarola 300g Cas",
                "Molho de Tomate Pomarola Azeitona 300g"));
        UUID storeBHomemade = sku(STORE_B, "Molho de Tomate Pomarola Caseiro 300g");

        assertThat(item(homemade)).isNotEqualTo(item(storeBHomemade));
    }

    @Test
    void erpAbbreviationsOfTheSameBrandAndSizeMatchTheFullDescription() {
        UUID abbreviated = sku(STORE_A, "LING PERDIGAO CALABR 400gr");
        UUID full = sku(STORE_B, "Linguiça Calabresa Perdigão 400g");
        UUID cookie = sku(STORE_A, "Bisc. Cookies Piraque 80g Choc");
        UUID packed = sku(STORE_B, "Biscoito Cookie Chocolate Piraquê Pacote 80G");

        assertThat(item(abbreviated)).isEqualTo(item(full));
        assertThat(item(cookie)).isEqualTo(item(packed));
    }

    @Test
    void anAbbreviationNeverAbsorbsAFlavourOrVariant() {
        UUID plain = sku(STORE_A, "Iogurte Serramar 170g");
        UUID strawberry = sku(STORE_B, "Iogurte Serramar Morango 170g");
        UUID regular = sku(STORE_A, "Chá Leão Lichia 1,5L");
        UUID zero = sku(STORE_B, "Chá Leão Lichia Zero 1,5L");

        assertThat(item(plain)).isNotEqualTo(item(strawberry));
        assertThat(item(regular)).isNotEqualTo(item(zero));
    }

    @Test
    void anAbbreviationTwoProductsOfOneStoreCouldStandForIsLeftAlone() {
        UUID ambiguous = sku(STORE_A, "Macarrão Renata Esp 500g");
        UUID spaghetti = sku(STORE_B, "Macarrão Renata Espaguete 500g");
        UUID spinach = sku(STORE_B, "Macarrão Renata Espinafre 500g");

        assertThat(item(ambiguous)).isNotEqualTo(item(spaghetti)).isNotEqualTo(item(spinach));
    }

    private UUID sku(UUID store, String name) {
        UUID product = UUID.randomUUID();
        listings.add(new CatalogMatcher.Listing(product, store, name, name));
        return product;
    }

    private String item(UUID product) {
        Map<UUID, CatalogKey.Identity> identities = new CatalogMatcher(keys).match(listings);
        return identities.get(product).key();
    }
}

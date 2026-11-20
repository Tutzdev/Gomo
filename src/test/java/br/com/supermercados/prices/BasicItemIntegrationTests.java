package br.com.supermercados.prices;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import br.com.supermercados.prices.basicitem.BasicItemComparisonResponse;
import br.com.supermercados.prices.basicitem.BasicItemLinker;
import br.com.supermercados.prices.basicitem.BasicItemService;
import br.com.supermercados.prices.comparison.ComparisonItemResponse;
import br.com.supermercados.prices.comparison.ShoppingComparisonService;
import br.com.supermercados.prices.comparison.ShoppingStoreComparison;
import br.com.supermercados.prices.shoppinglist.AddItemRequest;
import br.com.supermercados.prices.shoppinglist.ShoppingListRequest;
import br.com.supermercados.prices.shoppinglist.ShoppingListService;
import br.com.supermercados.prices.shoppinglist.ShoppingType;
import br.com.supermercados.prices.store.MissingPriceReason;
import br.com.supermercados.prices.subscription.ComparisonAccess;
import br.com.supermercados.prices.support.PostgresTestDatabase;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Sql("/fixtures/catalog.sql")
class BasicItemIntegrationTests {

    private static final UUID SOURCE_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID STORE_A = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID STORE_B = UUID.fromString("00000000-0000-0000-0000-000000000202");

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    BasicItemLinker linker;

    @Autowired
    BasicItemService basicItems;

    @Autowired
    ShoppingListService lists;

    @Autowired
    ShoppingComparisonService comparisons;

    UUID cityId;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry);
    }

    @BeforeEach
    void pricesInTwoStores() {
        cityId = jdbc.queryForObject("SELECT city_id FROM stores WHERE id = ?", UUID.class, STORE_A);

        price(product("Pão de Hambúrguer Wickbold 200g"), STORE_A, "8.79");
        price(product("Pao De Hamburguer Trigovita 400G"), STORE_A, "6.79");
        price(product("Pao Hamburguer Plus Vita 520g"), STORE_B, "15.99");
        price(product("Carne Bovina Acem Reserva Resfriada Venda 1kg"), STORE_A, "39.90");

        linker.relink();
    }

    @Test
    void differentBrandsOfTheSameEverydayItemAreComparedByPricePerKilogram() {
        BasicItemComparisonResponse bread = basicItems.compare(
            "pao-de-hamburguer",
            cityId,
            null,
            ComparisonAccess.premiumAccess());

        assertThat(bread.item().pricedStores()).isEqualTo(2);
        assertThat(bread.offers()).extracting(BasicItemComparisonResponse.StoreOffer::storeId)
                .containsExactly(STORE_A, STORE_B);
        assertThat(bread.offers().getFirst().pricePerMeasure()).isEqualByComparingTo("16.98");
        assertThat(bread.offers().getFirst().productName()).isEqualTo("Pao De Hamburguer Trigovita 400G");
        assertThat(bread.offers().get(1).pricePerMeasure()).isEqualByComparingTo("30.75");
        assertThat(bread.missingStores()).isEmpty();
    }

    @Test
    void aMarketWithoutTheItemIsNamedWithTheReason() {
        BasicItemComparisonResponse acem = basicItems.compare("acem", cityId, null, ComparisonAccess.premiumAccess());

        assertThat(acem.offers()).extracting(BasicItemComparisonResponse.StoreOffer::storeId).containsExactly(STORE_A);
        assertThat(acem.missingStores()).singleElement().satisfies(missing -> {
            assertThat(missing.storeId()).isEqualTo(STORE_B);
            assertThat(missing.reason()).isEqualTo(MissingPriceReason.NOT_SOLD_ONLINE);
        });
    }

    @Test
    void aMarketThatListsTheItemOutOfStockSaysSoInsteadOfNotSellingIt() {
        price(product("Carne Bovina Acem Resfriada 1kg"), STORE_B, "36.90", "UNAVAILABLE");
        linker.relink();

        BasicItemComparisonResponse acem = basicItems.compare("acem", cityId, null, ComparisonAccess.premiumAccess());

        assertThat(acem.missingStores()).singleElement().satisfies(missing -> {
            assertThat(missing.storeId()).isEqualTo(STORE_B);
            assertThat(missing.reason()).isEqualTo(MissingPriceReason.OUT_OF_STOCK);
        });
    }

    @Test
    void anExactListItemThatIsAnEverydayItemCanSwitchToAnyBrand() {
        UUID user = user();
        UUID bread = product("Pão de Hambúrguer Seven Boys 300g");
        price(bread, STORE_B, "7.50");
        linker.relink();

        var list = lists.create(user, true, new ShoppingListRequest("Churrasco", ShoppingType.CUSTOM));
        var exact = lists.addItem(user, true, list.id(), new AddItemRequest(bread, new BigDecimal("2")));

        assertThat(lists.getList(user, list.id()).items()).singleElement().satisfies(item -> {
            assertThat(item.anyBrandSuggestion()).isNotNull();
            assertThat(item.anyBrandSuggestion().basicItem()).isEqualTo("pao-de-hamburguer");
        });

        var switched = lists.switchToAnyBrand(user, list.id(), exact.id());

        assertThat(switched.basicItem()).isEqualTo("pao-de-hamburguer");
        assertThat(switched.productName()).isEqualTo("Pão de hambúrguer (qualquer marca)");
        assertThat(switched.quantity()).isEqualByComparingTo("2");
        assertThat(switched.anyBrandSuggestion()).isNull();
        assertThat(lists.getList(user, list.id()).items()).singleElement()
                .satisfies(item -> assertThat(item.basicItem()).isEqualTo("pao-de-hamburguer"));
    }

    @Test
    void anAnyBrandListItemIsPricedInEveryMarketThatSellsTheItem() {
        UUID user = user();
        var list = lists.create(user, true, new ShoppingListRequest("Compra da semana", ShoppingType.WEEKLY));

        var item = lists.addItem(user, true, list.id(), AddItemRequest.anyBrand("pao-de-hamburguer", BigDecimal.ONE));

        assertThat(item.productName()).isEqualTo("Pão de hambúrguer (qualquer marca)");
        assertThat(item.basicItem()).isEqualTo("pao-de-hamburguer");

        var compared = comparisons.compareShoppingList(user, list.id(), cityId, PageRequest.of(0, 20, Sort.by("name")));

        assertThat(compared.stores().content()).allSatisfy(store -> assertThat(store.completeShoppingList()).isTrue());
        assertThat(lineTotal(compared.stores().content(), STORE_A)).isEqualByComparingTo("6.79");
        assertThat(lineTotal(compared.stores().content(), STORE_B)).isEqualByComparingTo("15.99");
    }

    private static BigDecimal lineTotal(Iterable<ShoppingStoreComparison> stores, UUID storeId) {
        for (ShoppingStoreComparison store : stores) {
            if (store.storeId().equals(storeId)) {return store.items().getFirst().lineTotal();}
        }

        throw new AssertionError("Mercado ausente da comparação: " + storeId);
    }

    private UUID product(String name) {
        UUID id = UUID.randomUUID();

        jdbc.update("""
                INSERT INTO products (id, name, source_id, source_reference, collected_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, id, name, SOURCE_ID, "synthetic-basic-" + id, recently(), recently());

        return id;
    }

    private void price(UUID product, UUID store, String regularPrice) {
        price(product, store, regularPrice, "AVAILABLE");
    }

    private void price(UUID product, UUID store, String regularPrice, String availability) {
        jdbc.update("""
                INSERT INTO price_records (id, product_id, store_id, source_id, source_reference, regular_price,
                    collected_at, recorded_at, availability)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), product, store, SOURCE_ID, "synthetic-basic-price-" + UUID.randomUUID(),
                new BigDecimal(regularPrice), recently(), recently(), availability);
    }

    private UUID user() {
        UUID user = UUID.randomUUID();

        jdbc.update("""
                INSERT INTO app_users (id, name, email, password_hash, created_at, updated_at)
                VALUES (?, 'Pessoa fictícia', ?, 'hash', ?, ?)
                """, user, "fictitious-" + user + "@example.test", recently(), recently());

        return user;
    }

    private static Timestamp recently() {
        return Timestamp.from(Instant.now().minusSeconds(5));
    }
}

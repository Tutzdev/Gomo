package br.com.supermercados.prices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.supermercados.prices.common.ApiException;
import br.com.supermercados.prices.comparison.ShoppingComparisonService;
import br.com.supermercados.prices.price.PriceObservation;
import br.com.supermercados.prices.price.PriceService;
import br.com.supermercados.prices.price.PriceStatus;
import br.com.supermercados.prices.price.StockAvailability;
import br.com.supermercados.prices.product.ProductService;
import br.com.supermercados.prices.shoppinglist.AddItemRequest;
import br.com.supermercados.prices.shoppinglist.ShoppingListRequest;
import br.com.supermercados.prices.shoppinglist.ShoppingListService;
import br.com.supermercados.prices.shoppinglist.ShoppingType;
import br.com.supermercados.prices.support.PostgresTestDatabase;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
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

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Sql("/fixtures/catalog.sql")
class PricePersistenceTests {

    private static final UUID SOURCE = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID STORE_A = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID STORE_B = UUID.fromString("00000000-0000-0000-0000-000000000202");
    private static final UUID PRODUCT_A = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final UUID PRODUCT_B = UUID.fromString("00000000-0000-0000-0000-000000000302");

    @Autowired PriceService prices;
    @Autowired ShoppingComparisonService comparisons;
    @Autowired ShoppingListService lists;
    @Autowired ProductService products;
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @Autowired br.com.supermercados.prices.store.StoreCatalogService storeCatalog;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry);
    }

    @Test
    void storeCatalogPaginatesUniqueProductsAndUsesOnlyTheLatestObservation() {
        Instant now = Instant.now().minusSeconds(5);
        prices.appendObservation(observation(STORE_A, PRODUCT_A, "catalog-old", "2.00", now.minusSeconds(60)));
        prices.appendObservation(observation(STORE_A, PRODUCT_A, "catalog-new", "3.00", now));
        prices.appendObservation(observation(STORE_A, PRODUCT_B, "catalog-other", "4.00", now));
        var search = new br.com.supermercados.prices.product.ProductSearch(null, null, null, null);

        var first = storeCatalog.findProducts(STORE_A, search, PageRequest.of(0, 1, Sort.by("name", "id")));
        var second = storeCatalog.findProducts(STORE_A, search, PageRequest.of(1, 1, Sort.by("name", "id")));

        assertThat(first.getTotalElements()).isEqualTo(2);
        assertThat(first.getContent()).singleElement().satisfies(item -> {
            assertThat(item.product().id()).isEqualTo(PRODUCT_A);
            assertThat(item.price().unitPrice()).isEqualByComparingTo("3.00");
        });
        assertThat(second.getContent()).singleElement().satisfies(item ->
                assertThat(item.product().id()).isEqualTo(PRODUCT_B));
        assertThat(storeCatalog.findProducts(STORE_B, search, storePage())).isEmpty();
        var filtered = storeCatalog.findProducts(STORE_A,
                new br.com.supermercados.prices.product.ProductSearch("beta", null, null, null), storePage());
        assertThat(filtered.getTotalElements()).isEqualTo(1);
    }

    @Test
    void historyIsAppendOnlyAndLatestObservationWinsRegardlessOfArrivalOrder() {
        Instant now = Instant.now().minusSeconds(5);
        var latest = prices.appendObservation(observation(STORE_A, PRODUCT_A, "latest", "4.35", now));
        prices.appendObservation(observation(STORE_A, PRODUCT_A, "older", "2.20", now.minusSeconds(60)));
        var history = prices.findHistory(PRODUCT_A, STORE_A,
                PageRequest.of(0, 20, Sort.by("collectedAt").descending()));
        assertThat(history.totalElements()).isEqualTo(2);
        assertThat(history.content().getFirst().id()).isEqualTo(latest.id());
        var result = comparisons.compareProduct(PRODUCT_A, cityId(), storePage());
        assertThat(result.stores().content()).singleElement()
                .satisfies(store -> assertThat(store.price().unitPrice()).isEqualByComparingTo("4.35"));
    }

    @Test
    void repeatedObservationIsIdempotentAtPostgresTimestampPrecision() {
        var observation = observation(STORE_A, PRODUCT_A, "same-event", "3.10", Instant.now().minusSeconds(5));
        assertThat(prices.appendObservation(observation).id()).isEqualTo(prices.appendObservation(observation).id());
        assertThat(jdbc.queryForObject("select count(*) from price_records", Integer.class)).isEqualTo(1);
    }

    @Test
    void changedContentCannotReuseAnObservationReference() {
        Instant collectedAt = Instant.now().minusSeconds(5);
        prices.appendObservation(observation(STORE_A, PRODUCT_A, "same-event", "3.10", collectedAt));
        assertThatThrownBy(() -> prices.appendObservation(
                observation(STORE_A, PRODUCT_A, "same-event", "3.20", collectedAt)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void persistsPublicOriginAndEnrichesAnIdenticalLegacyObservationWithoutChangingItsTimestamp() {
        Instant collected = Instant.now().minusSeconds(5).truncatedTo(ChronoUnit.MICROS);
        var previous = prices.appendObservation(observation(STORE_A, PRODUCT_A, "legacy-origin", "3.10", collected));
        String origin = "https://example.com/public-product";
        for (String reference : new String[] {"legacy-origin", "new-origin"}) {
            prices.appendObservation(new PriceObservation(PRODUCT_A, STORE_A, SOURCE, reference,
                    new BigDecimal("3.10"), null, "BRL", collected, null, null, null,
                    StockAvailability.UNKNOWN, origin));
        }
        entityManager.flush();
        entityManager.clear();
        var history = prices.findHistory(PRODUCT_A, STORE_A, PageRequest.of(0, 20));
        assertThat(history.totalElements()).isEqualTo(2);
        assertThat(history.content()).allSatisfy(record -> {
            assertThat(record.originUrl()).isEqualTo(origin);
            assertThat(record.collectedAt()).isEqualTo(collected);
        });
        assertThat(history.content()).anyMatch(record -> record.id().equals(previous.id()));
    }

    @Test
    void expiredLatestPriceDoesNotFallBackToAnOlderUnexpiredPrice() {
        Instant now = Instant.now().minusSeconds(5);
        prices.appendObservation(observation(STORE_A, PRODUCT_A, "older-valid", "3.10", now.minusSeconds(120)));
        prices.appendObservation(new PriceObservation(PRODUCT_A, STORE_A, SOURCE, "new-expired",
                new BigDecimal("4.10"), null, "BRL", now.minusSeconds(60), now, null, StockAvailability.UNKNOWN));
        var quote = comparisons.compareProduct(PRODUCT_A, cityId(), storePage()).stores().content().getFirst().price();
        assertThat(quote.status()).isEqualTo(PriceStatus.EXPIRED);
        assertThat(quote.unitPrice()).isNull();
    }

    @Test
    void quantitiesPromotionsAndMissingPricesProduceHonestTotals() {
        Instant collected = Instant.now().minusSeconds(5);
        prices.appendObservation(new PriceObservation(PRODUCT_A, STORE_A, SOURCE, "promo-a",
                new BigDecimal("3.00"), new BigDecimal("2.35"), "BRL", collected,
                null, collected.plusSeconds(3600), StockAvailability.UNKNOWN));
        prices.appendObservation(observation(STORE_A, PRODUCT_B, "regular-b", "0.10", collected));
        prices.appendObservation(observation(STORE_B, PRODUCT_A, "partial-a", "1.00", collected));
        UUID userId = createTestUser();
        var list = lists.create(userId, true, new ShoppingListRequest("Lista fictícia", ShoppingType.WEEKLY));
        lists.addItem(userId, true, list.id(), new AddItemRequest(PRODUCT_A, new BigDecimal("2.5")));
        lists.addItem(userId, true, list.id(), new AddItemRequest(PRODUCT_B, new BigDecimal("3")));
        var result = comparisons.compareShoppingList(userId, list.id(), cityId(), storePage());
        var complete = result.stores().content().getFirst();
        assertThat(complete.subtotalKnown()).isEqualByComparingTo("6.18");
        assertThat(complete.completeShoppingList()).isTrue();
        assertThat(complete.items().getFirst().price().promotionApplied()).isTrue();
        var partial = result.stores().content().get(1);
        assertThat(partial.subtotalKnown()).isEqualByComparingTo("2.50");
        assertThat(partial.completeShoppingList()).isFalse();
        assertThat(partial.missingItems()).isEqualTo(1);
        assertThat(partial.items().get(1).lineTotal()).isNull();
    }

    @Test
    void emptyListAndStoresWithoutPricesNeverHaveAFreeTotal() {
        UUID userId = createTestUser();
        var list = lists.create(userId, true, new ShoppingListRequest("Lista fictícia vazia", ShoppingType.CUSTOM));
        var result = comparisons.compareShoppingList(userId, list.id(), cityId(), storePage());
        assertThat(result.stores().content()).allSatisfy(store -> {
            assertThat(store.subtotalKnown()).isNull();
            assertThat(store.completeShoppingList()).isFalse();
        });
        lists.addItem(userId, true, list.id(), new AddItemRequest(PRODUCT_A, BigDecimal.ONE));
        assertThat(comparisons.compareShoppingList(userId, list.id(), cityId(), storePage()).stores().content())
                .allSatisfy(store -> {
                    assertThat(store.subtotalKnown()).isNull();
                    assertThat(store.missingItems()).isEqualTo(1);
                });
    }

    @Test
    void deletingAListPreservesAllPriceHistory() {
        prices.appendObservation(observation(STORE_A, PRODUCT_A, "historical", "0.10", Instant.now().minusSeconds(5)));
        UUID userId = createTestUser();
        var list = lists.create(userId, true, new ShoppingListRequest("Lista fictícia", ShoppingType.DAILY));
        lists.addItem(userId, true, list.id(), new AddItemRequest(PRODUCT_A, BigDecimal.ONE));
        lists.delete(userId, list.id());
        assertThat(jdbc.queryForObject("select count(*) from price_records", Integer.class)).isEqualTo(1);
    }

    @Test
    void comparisonPaginationCountsStoresAndExcludesInactiveStores() {
        Instant now = Instant.now().minusSeconds(5);
        prices.appendObservation(observation(STORE_A, PRODUCT_A, "pagination-a", "3.00", now));
        prices.appendObservation(observation(STORE_B, PRODUCT_B, "pagination-b", "4.00", now));
        assertThat(comparisons.compareProduct(PRODUCT_A, cityId(), PageRequest.of(0, 1, Sort.by("name")))
                .stores().totalElements()).isEqualTo(2);
        jdbc.update("update stores set active = false where id = ?", STORE_B);
        var result = comparisons.compareProduct(PRODUCT_A, cityId(), storePage());
        assertThat(result.stores().content()).hasSize(1);
        assertThat(result.stores().totalElements()).isEqualTo(1);
    }

    private PriceObservation observation(UUID store, UUID product, String reference, String amount, Instant collected) {
        return new PriceObservation(product, store, SOURCE, reference, new BigDecimal(amount), null,
                "BRL", collected, null, null, StockAvailability.UNKNOWN);
    }

    private UUID cityId() {
        return jdbc.queryForObject("select id from cities where name = 'Rio de Janeiro'", UUID.class);
    }

    private PageRequest storePage() {
        return PageRequest.of(0, 20, Sort.by("name").and(Sort.by("id")));
    }

    private UUID createTestUser() {
        UUID userId = UUID.randomUUID();
        jdbc.update("""
                insert into app_users(id, name, email, password_hash, created_at, updated_at, version)
                values (?, 'Pessoa fictícia', ?, 'not-a-login-hash-test-only', ?, ?, 0)
                """, userId, "fictional-" + userId + "@example.test",
                java.sql.Timestamp.from(Instant.now().truncatedTo(ChronoUnit.MICROS)),
                java.sql.Timestamp.from(Instant.now().truncatedTo(ChronoUnit.MICROS)));
        return userId;
    }
}

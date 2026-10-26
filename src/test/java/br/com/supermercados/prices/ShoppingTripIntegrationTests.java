package br.com.supermercados.prices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.supermercados.prices.price.PriceObservation;
import br.com.supermercados.prices.price.PriceService;
import br.com.supermercados.prices.price.StockAvailability;
import br.com.supermercados.prices.support.PostgresTestDatabase;
import br.com.supermercados.prices.support.SignedInAccounts;
import br.com.supermercados.prices.support.SignedInAccounts.Account;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** The "No mercado" mode and the purchases behind the savings report, through the API, with fictional data. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Sql("/fixtures/catalog.sql")
class ShoppingTripIntegrationTests {

    private static final UUID SOURCE = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID STORE_A = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID STORE_B = UUID.fromString("00000000-0000-0000-0000-000000000202");
    private static final UUID PRODUCT_A = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final UUID PRODUCT_B = UUID.fromString("00000000-0000-0000-0000-000000000302");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private PriceService prices;

    private SignedInAccounts accounts;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry);
    }

    @BeforeEach
    void setUp() {
        accounts = new SignedInAccounts(mvc, json, jdbc, entityManager);
        // Product A costs 5.00 at A and 6.00 at B; product B is only priced at B.
        price(PRODUCT_A, STORE_A, "trip-a-a", "5.00");
        price(PRODUCT_A, STORE_B, "trip-a-b", "6.00");
        price(PRODUCT_B, STORE_B, "trip-b-b", "4.00");
    }

    @Test
    void checkingAnItemIsSharedByTheListAndRetryingKeepsTheFirstTime() throws Exception {
        Account account = accounts.free();
        String list = list(account);
        String rice = item(account, list, PRODUCT_A, "2");

        JsonNode first = accounts.read(accounts.perform(put(items(list) + "/" + rice + "/check"), account)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkedBy").value(account.id().toString())));
        accounts.perform(put(items(list) + "/" + rice + "/check"), account)
                .andExpect(jsonPath("$.checkedAt").value(first.path("checkedAt").asString()));
        accounts.perform(get("/api/v1/shopping-lists/" + list), account)
                .andExpect(jsonPath("$.items[0].checkedAt").value(first.path("checkedAt").asString()));

        accounts.perform(delete(items(list) + "/" + rice + "/check"), account)
                .andExpect(jsonPath("$.checkedAt").isEmpty());
        accounts.perform(put(items(list) + "/" + rice + "/check"), account).andExpect(status().isOk());
        accounts.perform(delete("/api/v1/shopping-lists/" + list + "/checks"), account)
                .andExpect(status().isNoContent());
        accounts.perform(get("/api/v1/shopping-lists/" + list), account)
                .andExpect(jsonPath("$.items[0].checkedAt").isEmpty());
    }

    @Test
    void tripShowsWhatEachItemCostsAtTheChosenMarketForAnyPlan() throws Exception {
        Account account = accounts.free();
        String list = list(account);
        item(account, list, PRODUCT_A, "2");
        item(account, list, PRODUCT_B, "1");

        accounts.perform(get("/api/v1/shopping-lists/" + list + "/trip").param("storeId", STORE_A.toString()), account)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storeName").value("Loja fictícia A"))
                .andExpect(jsonPath("$.pricedItems").value(1))
                .andExpect(jsonPath("$.expectedTotal").value(10.00))
                .andExpect(jsonPath("$.items[0].unitPrice").value(5.00))
                .andExpect(jsonPath("$.items[0].storeProductId").value(PRODUCT_A.toString()))
                .andExpect(jsonPath("$.items[1].unitPrice").isEmpty());
    }

    @Test
    void purchaseIsComparedWithTheCityAverageRecordedOnceAndRestartsTheList() throws Exception {
        Account account = accounts.premium();
        String list = list(account);
        String rice = item(account, list, PRODUCT_A, "2");
        String beans = item(account, list, PRODUCT_B, "1");
        accounts.perform(put(items(list) + "/" + rice + "/check"), account).andExpect(status().isOk());
        accounts.perform(put(items(list) + "/" + beans + "/check"), account).andExpect(status().isOk());
        String purchase = accounts.write(Map.of("id", UUID.randomUUID(), "storeId", STORE_A, "items", List.of(
                Map.of("itemId", rice), Map.of("itemId", beans, "shelfPrice", new BigDecimal("3.00")))));

        accounts.perform(post("/api/v1/shopping-lists/" + list + "/purchases").content(purchase), account)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listRestarted").value(true))
                .andExpect(jsonPath("$.purchase.storeName").value("Loja fictícia A"))
                // 2 × 5.00 collected + 1 × 3.00 typed from the shelf.
                .andExpect(jsonPath("$.purchase.totalPaid").value(13.00))
                // Only product A has a price in two stores: average 5.50, so 2 × 0.50 saved.
                .andExpect(jsonPath("$.purchase.comparedItems").value(1))
                .andExpect(jsonPath("$.purchase.savings").value(1.00))
                .andExpect(jsonPath("$.purchase.itemsWithoutPrice").value(0));
        accounts.perform(post("/api/v1/shopping-lists/" + list + "/purchases").content(purchase), account)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purchase.savings").value(1.00));
        assertThat(jdbc.queryForObject("select count(*) from purchases where user_id = ?", Integer.class,
                account.id())).isOne();
        accounts.perform(get("/api/v1/shopping-lists/" + list), account)
                .andExpect(jsonPath("$.items[0].checkedAt").isEmpty());

        JsonNode report = accounts.read(accounts.perform(get("/api/v1/savings"), account)
                .andExpect(jsonPath("$.purchases").value(1))
                .andExpect(jsonPath("$.savings").value(1.00))
                .andExpect(jsonPath("$.locked").value(false))
                .andExpect(jsonPath("$.details[0].items.length()").value(2)));
        accounts.perform(delete("/api/v1/purchases/" + report.path("details").get(0).path("id").asString()), account)
                .andExpect(status().isNoContent());
        accounts.perform(get("/api/v1/savings"), account).andExpect(jsonPath("$.purchases").value(0));
    }

    @Test
    void freePlanSeesTheMonthTotalButNotEachPurchase() throws Exception {
        Account account = accounts.free();
        String list = list(account);
        String rice = item(account, list, PRODUCT_A, "1");
        accounts.perform(post("/api/v1/shopping-lists/" + list + "/purchases").content(accounts.write(Map.of(
                "id", UUID.randomUUID(), "storeId", STORE_A, "items", List.of(Map.of("itemId", rice))))), account)
                .andExpect(jsonPath("$.listRestarted").value(false));

        accounts.perform(get("/api/v1/savings"), account)
                .andExpect(jsonPath("$.purchases").value(1))
                .andExpect(jsonPath("$.savings").value(0.50))
                .andExpect(jsonPath("$.locked").value(true))
                .andExpect(jsonPath("$.details.length()").value(0));
    }

    @Test
    void anotherPersonCannotReuseAPurchaseIdOrBuyFromSomeoneElsesList() throws Exception {
        Account owner = accounts.free();
        String list = list(owner);
        String rice = item(owner, list, PRODUCT_A, "1");
        UUID purchaseId = UUID.randomUUID();
        accounts.perform(post("/api/v1/shopping-lists/" + list + "/purchases").content(accounts.write(Map.of(
                "id", purchaseId, "storeId", STORE_A, "items", List.of(Map.of("itemId", rice))))), owner)
                .andExpect(status().isOk());

        Account other = accounts.free();
        String otherList = list(other);
        String otherItem = item(other, otherList, PRODUCT_A, "1");
        accounts.perform(post("/api/v1/shopping-lists/" + otherList + "/purchases").content(accounts.write(Map.of(
                "id", purchaseId, "storeId", STORE_A, "items", List.of(Map.of("itemId", otherItem))))), other)
                .andExpect(status().isConflict());
        accounts.perform(post("/api/v1/shopping-lists/" + list + "/purchases").content(accounts.write(Map.of(
                "id", UUID.randomUUID(), "storeId", STORE_A, "items", List.of(Map.of("itemId", rice))))), other)
                .andExpect(status().isNotFound());
        accounts.perform(post("/api/v1/shopping-lists/" + otherList + "/purchases").content(accounts.write(Map.of(
                "id", UUID.randomUUID(), "storeId", STORE_A, "items", List.of(Map.of("itemId", rice))))), other)
                .andExpect(status().isUnprocessableContent());
    }

    private String list(Account account) throws Exception {
        return accounts.read(accounts.perform(post("/api/v1/shopping-lists")
                .content("{\"name\":\"Compra da semana\",\"shoppingType\":\"WEEKLY\"}"), account)
                .andExpect(status().isCreated())).path("id").asString();
    }

    private String item(Account account, String list, UUID product, String quantity) throws Exception {
        return accounts.read(accounts.perform(post(items(list)).content(accounts.write(Map.of(
                "productId", product, "quantity", new BigDecimal(quantity)))), account)
                .andExpect(status().isCreated())).path("id").asString();
    }

    private static String items(String list) {
        return "/api/v1/shopping-lists/" + list + "/items";
    }

    private void price(UUID product, UUID store, String reference, String amount) {
        prices.appendObservation(new PriceObservation(product, store, SOURCE, reference, new BigDecimal(amount),
                null, "BRL", Instant.now().minusSeconds(60), null, null, StockAvailability.AVAILABLE));
    }
}

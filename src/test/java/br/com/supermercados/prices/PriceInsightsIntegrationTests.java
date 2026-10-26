package br.com.supermercados.prices;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.supermercados.prices.price.PriceObservation;
import br.com.supermercados.prices.price.PriceService;
import br.com.supermercados.prices.price.StockAvailability;
import br.com.supermercados.prices.price.UsualPrice;
import br.com.supermercados.prices.support.PostgresTestDatabase;
import br.com.supermercados.prices.support.SignedInAccounts;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** What Gomo reads from the price history (verdict, usual price), through the API, with fictional data. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Sql("/fixtures/catalog.sql")
class PriceInsightsIntegrationTests {

    private static final UUID SOURCE = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID STORE_A = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID STORE_B = UUID.fromString("00000000-0000-0000-0000-000000000202");
    private static final UUID PRODUCT_A = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final UUID PRODUCT_B = UUID.fromString("00000000-0000-0000-0000-000000000302");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PriceService prices;
    @Autowired private EntityManager entityManager;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry);
    }

    @Test
    void verdictReadsTheCheapestCurrentOfferAgainstTheUsualPriceOfTheCity() throws Exception {
        UUID item = catalogItem(PRODUCT_A, "Arroz Fictício 5 kg", "Mercearia");
        LocalDate today = LocalDate.now(UsualPrice.ZONE);
        for (int day = 1; day <= 8; day++) {
            price(STORE_A, "history-" + day, "10.00", noonOf(today.minusDays(day)));
        }
        price(STORE_A, "today-a", "9.40", Instant.now().minusSeconds(60));
        price(STORE_B, "today-b", "8.00", Instant.now().minusSeconds(30));
        String token = premiumAccount();

        perform(get("/api/v1/catalog/items/" + item + "/history").param("cityId", rioCityId()), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict.kind").value("LOWEST_IN_PERIOD"))
                .andExpect(jsonPath("$.verdict.currentPrice").value(8.00))
                .andExpect(jsonPath("$.verdict.averagePrice").value(10.00))
                .andExpect(jsonPath("$.verdict.differencePercent").value(-20))
                .andExpect(jsonPath("$.verdict.days").value(8))
                .andExpect(jsonPath("$.verdict.periodDays").value(8));
    }

    @Test
    void publicDealsListWhatIsAtLeastTenPercentUnderTheUsualPriceAtEachStore() throws Exception {
        UUID item = catalogItem(PRODUCT_A, "Café Fictício 500 g", "Café da Manhã");
        LocalDate today = LocalDate.now(UsualPrice.ZONE);
        for (int day = 1; day <= 8; day++) {
            price(STORE_A, "deal-history-" + day, "10.00", noonOf(today.minusDays(day)));
        }
        price(STORE_A, "deal-a", "8.50", Instant.now().minusSeconds(60));
        price(STORE_B, "deal-b", "9.50", Instant.now().minusSeconds(60));

        mvc.perform(get("/api/v1/deals").param("cityId", rioCityId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minimumDiscountPercent").value(10))
                .andExpect(jsonPath("$.deals.content.length()").value(1))
                .andExpect(jsonPath("$.deals.content[0].itemId").value(item.toString()))
                .andExpect(jsonPath("$.deals.content[0].storeName").value("Loja fictícia A"))
                .andExpect(jsonPath("$.deals.content[0].price").value(8.50))
                .andExpect(jsonPath("$.deals.content[0].usualPrice").value(10.00))
                .andExpect(jsonPath("$.deals.content[0].discountPercent").value(15))
                .andExpect(jsonPath("$.deals.content[0].lowestIn90Days").value(true))
                .andExpect(jsonPath("$.stores[0].deals").value(1))
                .andExpect(jsonPath("$.categories[0].category").value("Café da Manhã"));
        // 5% under the usual price is the usual price with some noise, not a deal.
        mvc.perform(get("/api/v1/deals").param("cityId", rioCityId()).param("storeId", STORE_B.toString()))
                .andExpect(jsonPath("$.deals.content.length()").value(0));
    }

    @Test
    void onlyMyListsIsPremiumAndKeepsTheProductsOfThePersonsLists() throws Exception {
        UUID item = catalogItem(PRODUCT_A, "Óleo Fictício 900 ml", "Mercearia");
        LocalDate today = LocalDate.now(UsualPrice.ZONE);
        for (int day = 1; day <= 8; day++) {
            price(STORE_A, "mine-history-" + day, "10.00", noonOf(today.minusDays(day)));
        }
        price(STORE_A, "mine-today", "7.00", Instant.now().minusSeconds(60));

        mvc.perform(get("/api/v1/deals").param("cityId", rioCityId()).param("onlyMyLists", "true"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.limit").value("DEALS_FOR_MY_LISTS"));

        String token = premiumAccount();
        perform(get("/api/v1/deals").param("cityId", rioCityId()).param("onlyMyLists", "true"), token)
                .andExpect(jsonPath("$.deals.content.length()").value(0));
        String list = json.readTree(perform(post("/api/v1/shopping-lists").content(
                "{\"name\":\"Mês\",\"shoppingType\":\"MONTHLY\"}"), token).andReturn().getResponse()
                .getContentAsByteArray()).path("id").asString();
        perform(post("/api/v1/shopping-lists/" + list + "/items").content(
                "{\"catalogItemId\":\"" + item + "\",\"quantity\":1}"), token).andExpect(status().isCreated());

        perform(get("/api/v1/deals").param("cityId", rioCityId()).param("onlyMyLists", "true"), token)
                .andExpect(jsonPath("$.onlyMyLists").value(true))
                .andExpect(jsonPath("$.deals.content.length()").value(1))
                .andExpect(jsonPath("$.deals.content[0].discountPercent").value(30));
    }

    @Test
    void stockUpIsThePantryItemsAtTheLowestPriceOfTheHistoryAndTheRestIsBelowAverage() throws Exception {
        UUID rice = catalogItem(PRODUCT_A, "Arroz Fictício 5 kg", "Mercearia");
        UUID tomato = catalogItem(PRODUCT_B, "Tomate Fictício kg", "Hortifrúti");
        LocalDate today = LocalDate.now(UsualPrice.ZONE);
        for (int day = 1; day <= 8; day++) {
            price(PRODUCT_A, STORE_A, "stock-a-" + day, "10.00", noonOf(today.minusDays(day)));
            price(PRODUCT_B, STORE_A, "stock-b-" + day, "10.00", noonOf(today.minusDays(day)));
        }
        price(PRODUCT_A, STORE_B, "stock-a-today", "8.00", Instant.now().minusSeconds(60));
        price(PRODUCT_B, STORE_B, "stock-b-today", "8.00", Instant.now().minusSeconds(60));
        var accounts = new SignedInAccounts(mvc, json, jdbc, entityManager);
        var premium = accounts.premium();
        String list = listWith(accounts, premium, rice, tomato);

        accounts.perform(get("/api/v1/comparisons/shopping-lists/" + list + "/good-prices").param("cityId", rioCityId()),
                        premium)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locked").value(false))
                .andExpect(jsonPath("$.stockUpItems").value(1))
                .andExpect(jsonPath("$.belowAverageItems").value(1))
                .andExpect(jsonPath("$.items[0].catalogItemId").value(rice.toString()))
                .andExpect(jsonPath("$.items[0].stockUp").value(true))
                .andExpect(jsonPath("$.items[0].kind").value("LOWEST_IN_PERIOD"))
                .andExpect(jsonPath("$.items[0].storeName").value("Loja fictícia B"))
                .andExpect(jsonPath("$.items[0].differencePercent").value(-20))
                // A tomato at its lowest is a good buy today, not one to keep for weeks.
                .andExpect(jsonPath("$.items[1].catalogItemId").value(tomato.toString()))
                .andExpect(jsonPath("$.items[1].stockUp").value(false));

        var free = accounts.free();
        String freeList = listWith(accounts, free, rice);
        accounts.perform(get("/api/v1/comparisons/shopping-lists/" + freeList + "/good-prices")
                        .param("cityId", rioCityId()), free)
                .andExpect(jsonPath("$.locked").value(true))
                .andExpect(jsonPath("$.stockUpItems").value(1))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    private String listWith(SignedInAccounts accounts, SignedInAccounts.Account account, UUID... catalogItems)
            throws Exception {
        String list = accounts.read(accounts.perform(post("/api/v1/shopping-lists")
                .content("{\"name\":\"Despensa\",\"shoppingType\":\"MONTHLY\"}"), account)).path("id").asString();
        for (UUID item : catalogItems) {
            accounts.perform(post("/api/v1/shopping-lists/" + list + "/items").content(
                    "{\"catalogItemId\":\"" + item + "\",\"quantity\":1}"), account).andExpect(status().isCreated());
        }
        return list;
    }

    UUID catalogItem(UUID productId, String name, String category) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO catalog_items (id, catalog_key, display_name, size_label, category,
                    representative_product_id, search_text, store_count, active, updated_at)
                VALUES (?, ?, ?, '1 un', ?, ?, ?, 2, TRUE, ?)
                """, id, "SYNTHETIC|" + id, name, category, productId, name.toUpperCase(),
                Timestamp.from(Instant.now()));
        jdbc.update("INSERT INTO catalog_item_products (product_id, catalog_item_id) VALUES (?, ?)", productId, id);
        return id;
    }

    void price(UUID store, String reference, String amount, Instant collectedAt) {
        price(PRODUCT_A, store, reference, amount, collectedAt);
    }

    void price(UUID product, UUID store, String reference, String amount, Instant collectedAt) {
        prices.appendObservation(new PriceObservation(product, store, SOURCE, reference, new BigDecimal(amount),
                null, "BRL", collectedAt, null, null, StockAvailability.AVAILABLE));
    }

    /** Noon in São Paulo, safely inside the shoppers' calendar day whatever time the test runs. */
    static Instant noonOf(LocalDate day) {
        return day.atTime(12, 0).atZone(UsualPrice.ZONE).toInstant();
    }

    String premiumAccount() throws Exception {
        return new SignedInAccounts(mvc, json, jdbc, entityManager).premium().token();
    }

    ResultActions perform(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON));
    }

    String rioCityId() {
        return jdbc.queryForObject("select id from cities where name = 'Rio de Janeiro'", UUID.class).toString();
    }
}

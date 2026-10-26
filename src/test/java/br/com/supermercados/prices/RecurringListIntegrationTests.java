package br.com.supermercados.prices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.supermercados.prices.price.PriceObservation;
import br.com.supermercados.prices.price.PriceService;
import br.com.supermercados.prices.price.StockAvailability;
import br.com.supermercados.prices.price.UsualPrice;
import br.com.supermercados.prices.recurring.RecurringListService;
import br.com.supermercados.prices.support.PostgresTestDatabase;
import br.com.supermercados.prices.support.SignedInAccounts;
import br.com.supermercados.prices.support.SignedInAccounts.Account;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
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
import tools.jackson.databind.ObjectMapper;

/** Weekly and monthly lists recalculated and compared with about a week ago, with fictional data. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Sql("/fixtures/catalog.sql")
class RecurringListIntegrationTests {

    private static final UUID SOURCE = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID STORE_A = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID STORE_B = UUID.fromString("00000000-0000-0000-0000-000000000202");
    private static final UUID PRODUCT_A = UUID.fromString("00000000-0000-0000-0000-000000000301");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private PriceService prices;
    @Autowired private RecurringListService recurringLists;

    private SignedInAccounts accounts;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry);
    }

    @BeforeEach
    void setUp() {
        accounts = new SignedInAccounts(mvc, json, jdbc, entityManager);
        price(STORE_A, "recurring-a", "5.00");
        price(STORE_B, "recurring-b", "6.00");
    }

    @Test
    void weeklyListTodayIsComparedWithTheSameListAboutAWeekAgo() throws Exception {
        Account account = accounts.premium();
        String weekly = list(account, "Semana", "WEEKLY");
        String item = item(account, weekly, "2");
        list(account, "Churrasco", "CUSTOM");

        accounts.perform(get("/api/v1/recurring-lists").param("cityId", rioCityId()), account)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Semana"))
                .andExpect(jsonPath("$[0].storeName").value("Loja fictícia A"))
                .andExpect(jsonPath("$[0].total").value(10.00))
                .andExpect(jsonPath("$[0].complete").value(true))
                .andExpect(jsonPath("$[0].previousTotal").isEmpty())
                .andExpect(jsonPath("$[0].difference").isEmpty());

        // Today's row becomes last week's, two reais more expensive.
        jdbc.update("""
                UPDATE recurring_list_snapshots SET snapshot_day = ?, total = 12.00, store_name = 'Loja fictícia B'
                WHERE shopping_list_id = ?
                """, LocalDate.now(UsualPrice.ZONE).minusDays(7), UUID.fromString(weekly));
        accounts.perform(get("/api/v1/recurring-lists").param("cityId", rioCityId()), account)
                .andExpect(jsonPath("$[0].previousTotal").value(12.00))
                .andExpect(jsonPath("$[0].previousStoreName").value("Loja fictícia B"))
                .andExpect(jsonPath("$[0].difference").value(-2.00))
                .andExpect(jsonPath("$[0].listChanged").value(false));

        accounts.perform(put("/api/v1/shopping-lists/" + weekly + "/items/" + item).content("{\"quantity\":3}"), account)
                .andExpect(status().isOk());
        accounts.perform(get("/api/v1/recurring-lists").param("cityId", rioCityId()), account)
                .andExpect(jsonPath("$[0].total").value(15.00))
                .andExpect(jsonPath("$[0].listChanged").value(true))
                .andExpect(jsonPath("$[0].difference").isEmpty());
    }

    @Test
    void dailyJobRecalculatesTheListsOfPeopleWithAPreferredCity() throws Exception {
        Account account = accounts.free();
        String monthly = list(account, "Mês", "MONTHLY");
        item(account, monthly, "1");
        entityManager.flush();
        jdbc.update("INSERT INTO user_preferences (user_id, preferred_city_id, updated_at) VALUES (?, ?, ?)",
                account.id(), UUID.fromString(rioCityId()), Timestamp.from(Instant.now()));

        assertThat(recurringLists.snapshotAll()).isPositive();

        assertThat(jdbc.queryForObject("""
                SELECT total FROM recurring_list_snapshots WHERE shopping_list_id = ? AND snapshot_day = ?
                """, BigDecimal.class, UUID.fromString(monthly), LocalDate.now(UsualPrice.ZONE)))
                .isEqualByComparingTo("5.00");
    }

    private String list(Account account, String name, String type) throws Exception {
        return accounts.read(accounts.perform(post("/api/v1/shopping-lists")
                .content(accounts.write(Map.of("name", name, "shoppingType", type))), account)
                .andExpect(status().isCreated())).path("id").asString();
    }

    private String item(Account account, String list, String quantity) throws Exception {
        return accounts.read(accounts.perform(post("/api/v1/shopping-lists/" + list + "/items").content(
                accounts.write(Map.of("productId", PRODUCT_A, "quantity", new BigDecimal(quantity)))), account)
                .andExpect(status().isCreated())).path("id").asString();
    }

    private void price(UUID store, String reference, String amount) {
        prices.appendObservation(new PriceObservation(PRODUCT_A, store, SOURCE, reference, new BigDecimal(amount),
                null, "BRL", Instant.now().minusSeconds(60), null, null, StockAvailability.AVAILABLE));
    }

    private String rioCityId() {
        return jdbc.queryForObject("select id from cities where name = 'Rio de Janeiro'", UUID.class).toString();
    }
}

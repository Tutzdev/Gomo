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
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
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

    private static final String PASSWORD = "synthetic-test-password";
    private static final UUID SOURCE = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID STORE_A = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID STORE_B = UUID.fromString("00000000-0000-0000-0000-000000000202");
    private static final UUID PRODUCT_A = UUID.fromString("00000000-0000-0000-0000-000000000301");

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
        prices.appendObservation(new PriceObservation(PRODUCT_A, store, SOURCE, reference, new BigDecimal(amount),
                null, "BRL", collectedAt, null, null, StockAvailability.AVAILABLE));
    }

    /** Noon in São Paulo, safely inside the shoppers' calendar day whatever time the test runs. */
    static Instant noonOf(LocalDate day) {
        return day.atTime(12, 0).atZone(UsualPrice.ZONE).toInstant();
    }

    String premiumAccount() throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Pessoa sintética", "email", email,
                                "password", PASSWORD))))
                .andExpect(status().isAccepted());
        String token = json.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray())
                .path("accessToken").asString();
        // The account and its session are still in this transaction's persistence context: write them out,
        // change the account and forget both, so the next request reads them as a new request would.
        entityManager.flush();
        jdbc.update("update app_users set email_verified_at = current_timestamp, subscriber = true where email = ?",
                email);
        entityManager.clear();
        return token;
    }

    ResultActions perform(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON));
    }

    String rioCityId() {
        return jdbc.queryForObject("select id from cities where name = 'Rio de Janeiro'", UUID.class).toString();
    }
}

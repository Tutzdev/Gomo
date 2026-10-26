package br.com.supermercados.prices;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.supermercados.prices.price.PriceObservation;
import br.com.supermercados.prices.price.PriceService;
import br.com.supermercados.prices.price.StockAvailability;
import br.com.supermercados.prices.support.PostgresTestDatabase;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Price alerts and their notifications through the API, with fictional stores and products. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Sql("/fixtures/catalog.sql")
class AlertIntegrationTests {

    private static final String PASSWORD = "synthetic-test-password";
    private static final UUID SOURCE = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID STORE_A = UUID.fromString("00000000-0000-0000-0000-000000000201");
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
    void unreadCountFollowsTheNotificationsAPriceCreates() throws Exception {
        String token = registerAndLogin();
        perform(post("/api/v1/alerts").content(json.writeValueAsString(Map.of(
                "productId", PRODUCT_A, "cityId", rioCityId(), "targetPrice", new BigDecimal("10.00")))), token)
                .andExpect(status().isCreated());
        perform(get("/api/v1/notifications/unread-count"), token).andExpect(jsonPath("$.unread").value(0));

        price("unread-count", "9.00");

        JsonNode notifications = read(perform(get("/api/v1/notifications"), token)
                .andExpect(jsonPath("$.content.length()").value(1)));
        perform(get("/api/v1/notifications/unread-count"), token).andExpect(jsonPath("$.unread").value(1));
        perform(patch("/api/v1/notifications/" + notifications.path("content").get(0).path("id").asString()
                + "/read"), token).andExpect(status().isOk());
        perform(get("/api/v1/notifications/unread-count"), token).andExpect(jsonPath("$.unread").value(0));
    }

    @Test
    void unreadCountNeedsASignedInAccount() throws Exception {
        mvc.perform(get("/api/v1/notifications/unread-count")).andExpect(status().isUnauthorized());
    }

    private void price(String reference, String amount) {
        prices.appendObservation(new PriceObservation(PRODUCT_A, STORE_A, SOURCE, reference, new BigDecimal(amount),
                null, "BRL", Instant.now().minusSeconds(5), null, null, StockAvailability.AVAILABLE));
    }

    /** A free account with a confirmed e-mail, which alerts require. */
    private String registerAndLogin() throws Exception {
        return registerAndLogin(false);
    }

    private String registerAndLogin(boolean premium) throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Pessoa sintética", "email", email,
                                "password", PASSWORD))))
                .andExpect(status().isAccepted());
        String token = read(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())).path("accessToken").asString();
        // The account and its new session are still in this transaction's persistence context: write them out,
        // change the account and forget both, so the next request reads them as a new request would.
        entityManager.flush();
        jdbc.update("update app_users set email_verified_at = current_timestamp, subscriber = ? where email = ?",
                premium, email);
        entityManager.clear();
        return token;
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON));
    }

    private JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsByteArray());
    }

    private String rioCityId() {
        return jdbc.queryForObject("select id from cities where name = 'Rio de Janeiro'", UUID.class).toString();
    }
}

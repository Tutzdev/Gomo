package br.com.supermercados.prices.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.sun.net.httpserver.HttpServer;

import br.com.supermercados.prices.price.PriceObservation;
import br.com.supermercados.prices.price.PriceService;
import br.com.supermercados.prices.price.StockAvailability;
import br.com.supermercados.prices.support.PostgresTestDatabase;
import br.com.supermercados.prices.support.SignedInAccounts;
import br.com.supermercados.prices.support.SignedInAccounts.Account;
import jakarta.persistence.EntityManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
    "app.whatsapp.enabled=true",
    "app.whatsapp.phone-number-id=1234567890",
    "app.whatsapp.access-token=token-ficticio"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Sql("/fixtures/catalog.sql")
class WhatsAppAlertIntegrationTests {

    private static final UUID SOURCE = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID STORE_A = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID PRODUCT_A = UUID.fromString("00000000-0000-0000-0000-000000000301");

    private static final List<Sent> SENT = new CopyOnWriteArrayList<>();
    private static final HttpServer META = startMeta();
    private static volatile int metaStatus = 200;

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PriceService prices;
    @Autowired private EntityManager entityManager;
    @Autowired private WhatsAppAlertDispatcher dispatcher;

    private SignedInAccounts accounts;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry);
        registry.add("app.whatsapp.base-url", () -> "http://127.0.0.1:" + META.getAddress().getPort() + "/v21.0");
    }

    @BeforeEach
    void reset() {
        accounts = new SignedInAccounts(mvc, json, jdbc, entityManager);
        SENT.clear();
        metaStatus = 200;
    }

    @Test
    void aPriceAlertGoesToTheAuthorizedNumberAsTheApprovedTemplate() throws Exception {
        Account account = accounts.free();
        authorize(account, "(24) 99999-8888")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.phoneNumber").value("+5524999998888"));
        alertBelow(account, "10.00");

        price("whatsapp-sent", "9.00");
        dispatch();

        assertThat(SENT).singleElement().satisfies(sent -> {
            assertThat(sent.path()).isEqualTo("/v21.0/1234567890/messages");
            assertThat(sent.authorization()).isEqualTo("Bearer token-ficticio");
            assertThat(sent.body().path("to").asString()).isEqualTo("5524999998888");
            assertThat(sent.body().path("template").path("name").asString()).isEqualTo("alerta_de_preco");
            assertThat(sent.body().path("template").path("language").path("code").asString()).isEqualTo("pt_BR");
            assertThat(parameters(sent)).containsExactly("Produto FICTÍCIO Alpha", "R$ 9,00", "Loja fictícia A");
        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM whatsapp_deliveries WHERE sent_at IS NOT NULL", Long.class))
                .isEqualTo(1);

        dispatch();

        assertThat(SENT).hasSize(1);
    }

    @Test
    void aRefusedMessageWaitsForTheNextAttemptInsteadOfRepeatingAtOnce() throws Exception {
        Account account = accounts.free();
        authorize(account, "24999998888").andExpect(status().isOk());
        alertBelow(account, "10.00");
        metaStatus = 400;

        price("whatsapp-refused", "9.00");
        dispatch();
        dispatch();

        assertThat(SENT).hasSize(1);
        Map<String, Object> delivery = jdbc.queryForMap("SELECT * FROM whatsapp_deliveries");
        assertThat(delivery.get("sent_at")).isNull();
        assertThat(delivery.get("attempts")).isEqualTo(1);
        assertThat((String) delivery.get("last_error")).contains("HTTP 400").contains("Template inexistente");
    }

    @Test
    void alertsCreatedBeforeTheNumberWasAuthorizedAreNotSent() throws Exception {
        Account account = accounts.free();
        alertBelow(account, "10.00");
        price("whatsapp-before-consent", "9.00");

        authorize(account, "24999998888").andExpect(status().isOk());
        dispatch();

        assertThat(SENT).isEmpty();
    }

    @Test
    void theNumberNeedsConsentAndAMobileWithDdd() throws Exception {
        Account account = accounts.free();

        accounts.perform(put("/api/v1/users/me/whatsapp").content(accounts.write(Map.of(
                "phoneNumber", "24999998888", "consent", false))), account)
                .andExpect(status().isBadRequest());
        authorize(account, "(24) 3333-4444").andExpect(status().isBadRequest());
    }

    @Test
    void removingTheNumberStopsTheMessagesAndTheExportShowsIt() throws Exception {
        Account account = accounts.free();
        authorize(account, "24999998888").andExpect(status().isOk());
        entityManager.flush();

        accounts.perform(get("/api/v1/users/me/export"), account)
                .andExpect(jsonPath("$.whatsapp.celular").value("+5524999998888"));
        accounts.perform(delete("/api/v1/users/me/whatsapp"), account)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phoneNumber").isEmpty());
        alertBelow(account, "10.00");
        price("whatsapp-removed", "9.00");
        dispatch();

        assertThat(SENT).isEmpty();
    }

    private ResultActions authorize(Account account, String number)
            throws Exception {

        return accounts.perform(put("/api/v1/users/me/whatsapp").content(accounts.write(Map.of(
                "phoneNumber", number, "consent", true))), account);
    }

    private void alertBelow(Account account, String target) throws Exception {
        accounts.perform(post("/api/v1/alerts").content(accounts.write(Map.of(
                "productId", PRODUCT_A, "cityId", rioCityId(), "targetPrice", new BigDecimal(target)))), account)
                .andExpect(status().isCreated());
    }

    private void price(String reference, String amount) {
        prices.appendObservation(new PriceObservation(PRODUCT_A, STORE_A, SOURCE, reference, new BigDecimal(amount),
                null, "BRL", Instant.now().minusSeconds(5), null, null, StockAvailability.AVAILABLE));
    }

    private void dispatch() {
        entityManager.flush();
        dispatcher.dispatch();
    }

    private UUID rioCityId() {
        return jdbc.queryForObject("SELECT id FROM cities WHERE name = 'Rio de Janeiro'", UUID.class);
    }

    private static List<String> parameters(Sent sent) {
        JsonNode parameters = sent.body().path("template").path("components").get(0).path("parameters");

        return parameters.valueStream().map(parameter -> parameter.path("text").asString()).toList();
    }

    private static HttpServer startMeta() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            ObjectMapper reader = new ObjectMapper();

            server.createContext("/v21.0/", exchange -> {
                SENT.add(new Sent(
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    reader.readTree(exchange.getRequestBody().readAllBytes())));

                String answer = metaStatus == 200
                        ? "{\"messages\":[{\"id\":\"wamid.ficticio\"}]}"
                        : "{\"error\":{\"message\":\"Template inexistente\",\"code\":132001}}";

                byte[] body = answer.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(metaStatus, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });

            server.start();

            return server;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private record Sent(
            String path,
            String authorization,
            JsonNode body) {}
}

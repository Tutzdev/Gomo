package br.com.supermercados.prices.subscription.abacatepay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.sun.net.httpserver.HttpServer;

import br.com.supermercados.prices.common.ApiException;
import br.com.supermercados.prices.subscription.BillingCycle;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** A local HTTP server stands in for AbacatePay and records what the gateway sends. */
class AbacatePayGatewayTest {

    private final JsonMapper json = JsonMapper.builder().build();
    private final List<Recorded> requests = new ArrayList<>();
    private HttpServer server;
    private int status;
    private String response;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v2/", exchange -> {
            requests.add(new Recorded(exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    json.readTree(exchange.getRequestBody().readAllBytes())));
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void opensTheCheckoutOfTheChosenPlanAndSendsThePersonBackToThePlansPage() {
        respond(200, """
                {"data":{"id":"bill_abc123","url":"https://app.abacatepay.com/pay/bill_abc123","status":"PENDING"},
                 "success":true,"error":null}
                """);

        var checkout = gateway().openSubscriptionCheckout(BillingCycle.ANNUAL);

        assertThat(checkout.id()).isEqualTo("bill_abc123");
        assertThat(checkout.url()).isEqualTo(URI.create("https://app.abacatepay.com/pay/bill_abc123"));
        Recorded request = requests.getFirst();
        assertThat(request.path()).isEqualTo("/v2/subscriptions/create");
        assertThat(request.authorization()).isEqualTo("Bearer abc_dev_key");
        assertThat(request.body().path("items").get(0).path("id").asString()).isEqualTo("prod_annual");
        assertThat(request.body().path("items").get(0).path("quantity").asInt()).isEqualTo(1);
        assertThat(request.body().path("returnUrl").asString()).isEqualTo("https://gomo.example/assinar");
        assertThat(request.body().path("completionUrl").asString())
                .isEqualTo("https://gomo.example/assinar?pagamento=concluido");
        assertThat(request.body().path("methods").get(0).asString()).isEqualTo("CARD");
    }

    @Test
    void providerErrorsBecomeAGatewayErrorWithoutLeakingTheProviderMessage() {
        respond(403, """
                {"data":null,"success":false,"error":"Insufficient permissions"}
                """);

        assertThatThrownBy(() -> gateway().openSubscriptionCheckout(BillingCycle.MONTHLY))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(exception.getMessage()).doesNotContain("permissions");
                });
    }

    @Test
    void aSuccessfulResponseWithoutCheckoutUrlIsRejected() {
        respond(200, """
                {"data":{"id":"bill_abc123"},"success":true,"error":null}
                """);

        assertThatThrownBy(() -> gateway().openSubscriptionCheckout(BillingCycle.MONTHLY))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void cancellationMustBeConfirmedByTheProvider() {
        respond(200, """
                {"data":{"id":"subs_1","status":"CANCELLED"},"success":true,"error":null}
                """);
        gateway().cancelSubscription("subs_1");
        assertThat(requests.getFirst().path()).isEqualTo("/v2/subscriptions/cancel");
        assertThat(requests.getFirst().body().path("id").asString()).isEqualTo("subs_1");

        respond(200, """
                {"data":{"id":"subs_1","status":"ACTIVE"},"success":true,"error":null}
                """);
        assertThatThrownBy(() -> gateway().cancelSubscription("subs_1")).isInstanceOf(ApiException.class);
    }

    @Test
    void anUnconfiguredGatewayIsUnavailableAndAHalfConfiguredOneRefusesToStart() {
        var disabled = new AbacatePayGateway(new AbacatePayProperties(), json);
        assertThat(disabled.available()).isFalse();
        assertThatThrownBy(() -> disabled.openSubscriptionCheckout(BillingCycle.MONTHLY))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));

        var halfConfigured = new AbacatePayProperties();
        halfConfigured.setEnabled(true);
        halfConfigured.setApiKey("abc_dev_key");
        assertThatThrownBy(() -> new AbacatePayGateway(halfConfigured, json))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ABACATEPAY_WEBHOOK_SECRET");
        assertThat(requests).isEmpty();
    }

    private AbacatePayGateway gateway() {
        var properties = new AbacatePayProperties();
        properties.setEnabled(true);
        properties.setBaseUrl(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v2"));
        properties.setApiKey("abc_dev_key");
        properties.setWebhookSecret("segredo-de-teste");
        properties.setMonthlyProductId("prod_monthly");
        properties.setAnnualProductId("prod_annual");
        properties.setSiteUrl(URI.create("https://gomo.example"));
        return new AbacatePayGateway(properties, json);
    }

    private void respond(int status, String body) {
        this.status = status;
        this.response = body;
        requests.clear();
    }

    private record Recorded(String path, String authorization, JsonNode body) {
    }
}

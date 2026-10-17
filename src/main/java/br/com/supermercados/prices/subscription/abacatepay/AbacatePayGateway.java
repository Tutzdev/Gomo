package br.com.supermercados.prices.subscription.abacatepay;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import br.com.supermercados.prices.common.ApiException;
import br.com.supermercados.prices.subscription.BillingCycle;
import br.com.supermercados.prices.subscription.PaymentGateway;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** AbacatePay API v2: hosted subscription checkout and cancellation. Amounts and cycles live in the products. */
@Component
public class AbacatePayGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(AbacatePayGateway.class);
    /** A failed renewal is retried on the next three days before AbacatePay cancels the subscription. */
    private static final Map<String, Integer> RETRY_POLICY = Map.of("maxRetry", 3, "retryEvery", 1);
    private static final String UNAVAILABLE = "O pagamento online está indisponível agora. Tente de novo em instantes.";

    private final AbacatePayProperties properties;
    private final ObjectMapper json;
    private final HttpClient client;

    public AbacatePayGateway(AbacatePayProperties properties, ObjectMapper json) {
        properties.validate();
        this.properties = properties;
        this.json = json;
        this.client = HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout()).build();
    }

    @Override
    public boolean available() {
        return properties.isEnabled();
    }

    @Override
    public Checkout openSubscriptionCheckout(BillingCycle cycle) {
        requireAvailable();
        URI plans = UriComponentsBuilder.fromUri(properties.getSiteUrl()).path("/assinar").build().toUri();
        URI paid = UriComponentsBuilder.fromUri(plans).queryParam("pagamento", "concluido").build().toUri();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", List.of(Map.of("id", properties.productId(cycle), "quantity", 1)));
        body.put("methods", properties.getMethods());
        body.put("returnUrl", plans.toString());
        body.put("completionUrl", paid.toString());
        body.put("retryPolicy", RETRY_POLICY);

        JsonNode data = post("/subscriptions/create", body);
        String id = data.path("id").asString("");
        String url = data.path("url").asString("");
        if (!id.startsWith("bill_") || !url.startsWith("https://")) {
            log.error("AbacatePay devolveu um checkout sem id ou URL válidos");
            throw new ApiException(HttpStatus.BAD_GATEWAY, UNAVAILABLE);
        }
        return new Checkout(id, URI.create(url));
    }

    @Override
    public void cancelSubscription(String subscriptionId) {
        requireAvailable();
        JsonNode data = post("/subscriptions/cancel", Map.of("id", subscriptionId));
        String status = data.path("status").asString("");
        if (!"CANCELLED".equals(status)) {
            log.error("AbacatePay não confirmou o cancelamento da assinatura {}: status {}", subscriptionId, status);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Não foi possível cancelar a assinatura agora. Tente de novo.");
        }
    }

    private void requireAvailable() {
        if (!available()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "O pagamento online ainda não está disponível.");
        }
    }

    private JsonNode post(String path, Object body) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getBaseUrl() + path))
                .timeout(properties.getRequestTimeout())
                .header("Authorization", "Bearer " + properties.getApiKey())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build();
        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException exception) {
            // A timeout may hide a request AbacatePay did process; the webhook still settles the outcome.
            log.error("AbacatePay {} sem resposta: {}", path, exception.getClass().getSimpleName());
            throw new ApiException(HttpStatus.BAD_GATEWAY, UNAVAILABLE);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE);
        }

        JsonNode envelope;
        try {
            envelope = json.readTree(response.body());
        } catch (JacksonException exception) {
            envelope = null;
        }
        boolean success = envelope != null && envelope.path("success").asBoolean(false);
        if (response.statusCode() / 100 != 2 || !success) {
            // The error text is the provider's own message; the key and the request body are never logged.
            String error = envelope == null ? "resposta não JSON" : envelope.path("error").asString("sem detalhe");
            log.error("AbacatePay {} respondeu {}: {}", path, response.statusCode(), error);
            throw new ApiException(HttpStatus.BAD_GATEWAY, UNAVAILABLE);
        }
        return envelope.path("data");
    }
}

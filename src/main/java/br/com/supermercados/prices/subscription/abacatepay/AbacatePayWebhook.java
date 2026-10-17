package br.com.supermercados.prices.subscription.abacatepay;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import br.com.supermercados.prices.common.ApiException;
import br.com.supermercados.prices.subscription.BillingCycle;
import br.com.supermercados.prices.subscription.PaymentEvent;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Authenticates and reads AbacatePay webhooks. The secret in the URL proves the sender; the HMAC proves the body
 * was not changed. Only the fields Gomo uses are read, so new fields from the provider never break delivery.
 */
@Component
public class AbacatePayWebhook {

    private final AbacatePayProperties properties;
    private final ObjectMapper json;

    public AbacatePayWebhook(AbacatePayProperties properties, ObjectMapper json) {
        this.properties = properties;
        this.json = json;
    }

    /**
     * @return the event, or empty when it belongs to the other environment (a Dev mode event in production)
     * @throws ApiException 401 for a wrong secret or signature, 400 for a body that is not an event
     */
    public Optional<PaymentEvent> read(String secret, String signature, byte[] body) {
        if (!properties.isEnabled()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Pagamentos não configurados.");
        }
        if (!sameText(secret, properties.getWebhookSecret()) || !validSignature(signature, body)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Webhook não autenticado.");
        }

        JsonNode event;
        try {
            event = json.readTree(body);
        } catch (JacksonException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Evento inválido.");
        }
        String id = event.path("id").asString("");
        String name = event.path("event").asString("");
        if (id.isBlank() || name.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Evento inválido.");
        }
        if (event.path("devMode").asBoolean(false) != properties.isDevMode()) {
            return Optional.empty();
        }

        JsonNode data = event.path("data");
        JsonNode subscription = data.path("subscription");
        return Optional.of(new PaymentEvent(id, type(name), text(subscription, "id"), text(data.path("checkout"), "id"),
                cycle(text(subscription, "frequency")), instant(text(data.path("payment"), "updatedAt")),
                "max_payment_retries_exceeded".equals(text(subscription, "cancelledDueTo"))));
    }

    private boolean validSignature(String signature, byte[] body) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getSignatureKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = Base64.getEncoder().encodeToString(mac.doFinal(body));
            return sameText(signature.strip(), expected);
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("HMAC-SHA256 indisponível", exception);
        }
    }

    /** Constant-time comparison, so response timing does not reveal how much of a guess was right. */
    private static boolean sameText(String received, String expected) {
        return received != null && !expected.isEmpty() && MessageDigest.isEqual(
                received.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }

    private static PaymentEvent.Type type(String name) {
        return switch (name) {
            case "subscription.completed" -> PaymentEvent.Type.SUBSCRIPTION_STARTED;
            case "subscription.renewed" -> PaymentEvent.Type.SUBSCRIPTION_RENEWED;
            case "subscription.cancelled" -> PaymentEvent.Type.SUBSCRIPTION_CANCELLED;
            case "subscription.payment_failed" -> PaymentEvent.Type.PAYMENT_FAILED;
            default -> PaymentEvent.Type.OTHER;
        };
    }

    private static BillingCycle cycle(String frequency) {
        return switch (frequency == null ? "" : frequency) {
            case "MONTHLY" -> BillingCycle.MONTHLY;
            case "ANNUALLY" -> BillingCycle.ANNUAL;
            default -> null;
        };
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asString("");
        return value.isBlank() ? null : value;
    }

    private static Instant instant(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }
}

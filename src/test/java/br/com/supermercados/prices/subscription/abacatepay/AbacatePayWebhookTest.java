package br.com.supermercados.prices.subscription.abacatepay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import br.com.supermercados.prices.common.ApiException;
import br.com.supermercados.prices.subscription.BillingCycle;
import br.com.supermercados.prices.subscription.PaymentEvent;
import tools.jackson.databind.json.JsonMapper;

class AbacatePayWebhookTest {

    private static final String SECRET = "segredo-de-teste";
    /** Shape of the documented subscription.completed payload, trimmed to what matters plus unknown fields. */
    private static final String COMPLETED = """
            {"id":"log_1","event":"subscription.completed","apiVersion":2,"devMode":true,"novoCampo":{"x":1},
             "data":{"subscription":{"id":"subs_1","amount":1490,"frequency":"MONTHLY","status":"ACTIVE",
                                     "cancelledDueTo":null},
                     "payment":{"id":"char_1","status":"PAID","updatedAt":"2026-10-16T20:00:05.000Z"},
                     "checkout":{"id":"bill_1","frequency":"SUBSCRIPTION"}}}
            """;

    private final AbacatePayWebhook webhook = new AbacatePayWebhook(properties(true), JsonMapper.builder().build());

    @Test
    void readsAnAuthenticatedEventIgnoringFieldsItDoesNotUse() {
        PaymentEvent event = webhook.read(SECRET, AbacatePaySignatures.sign(COMPLETED), bytes(COMPLETED)).orElseThrow();

        assertThat(event.id()).isEqualTo("log_1");
        assertThat(event.type()).isEqualTo(PaymentEvent.Type.SUBSCRIPTION_STARTED);
        assertThat(event.subscriptionId()).isEqualTo("subs_1");
        assertThat(event.checkoutId()).isEqualTo("bill_1");
        assertThat(event.cycle()).isEqualTo(BillingCycle.MONTHLY);
        assertThat(event.paidAt()).isEqualTo(Instant.parse("2026-10-16T20:00:05Z"));
        assertThat(event.paymentsExhausted()).isFalse();
    }

    @Test
    void aWrongSecretOrSignatureIsRejected() {
        assertUnauthorized(() -> webhook.read("outro", AbacatePaySignatures.sign(COMPLETED), bytes(COMPLETED)));
        assertUnauthorized(() -> webhook.read(null, AbacatePaySignatures.sign(COMPLETED), bytes(COMPLETED)));
        assertUnauthorized(() -> webhook.read(SECRET, null, bytes(COMPLETED)));
        // The body was changed after it was signed.
        String tampered = COMPLETED.replace("MONTHLY", "ANNUALLY");
        assertUnauthorized(() -> webhook.read(SECRET, AbacatePaySignatures.sign(COMPLETED), bytes(tampered)));
    }

    @Test
    void anEventFromTheOtherEnvironmentIsIgnored() {
        var production = new AbacatePayWebhook(properties(false), JsonMapper.builder().build());

        assertThat(production.read(SECRET, AbacatePaySignatures.sign(COMPLETED), bytes(COMPLETED))).isEmpty();
    }

    @Test
    void cancellationAfterFailedRetriesIsRecognised() {
        String cancelled = """
                {"id":"log_2","event":"subscription.cancelled","devMode":true,
                 "data":{"subscription":{"id":"subs_1","frequency":"ANNUALLY","status":"CANCELLED",
                                         "cancelledDueTo":"max_payment_retries_exceeded"}}}
                """;

        PaymentEvent event = webhook.read(SECRET, AbacatePaySignatures.sign(cancelled), bytes(cancelled)).orElseThrow();

        assertThat(event.type()).isEqualTo(PaymentEvent.Type.SUBSCRIPTION_CANCELLED);
        assertThat(event.cycle()).isEqualTo(BillingCycle.ANNUAL);
        assertThat(event.paymentsExhausted()).isTrue();
        assertThat(event.paidAt()).isNull();
    }

    @Test
    void aSignedBodyThatIsNotAnEventIsABadRequest() {
        String body = "{\"data\":{}}";

        assertThatThrownBy(() -> webhook.read(SECRET, AbacatePaySignatures.sign(body), bytes(body)))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private static void assertUnauthorized(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    private static byte[] bytes(String body) {
        return body.getBytes(StandardCharsets.UTF_8);
    }

    private static AbacatePayProperties properties(boolean devMode) {
        var properties = new AbacatePayProperties();
        properties.setEnabled(true);
        properties.setWebhookSecret(SECRET);
        properties.setDevMode(devMode);
        return properties;
    }
}

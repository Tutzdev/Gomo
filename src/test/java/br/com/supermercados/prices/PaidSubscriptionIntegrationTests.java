package br.com.supermercados.prices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import br.com.supermercados.prices.subscription.BillingCycle;
import br.com.supermercados.prices.subscription.PaymentGateway;
import br.com.supermercados.prices.subscription.abacatepay.AbacatePaySignatures;
import br.com.supermercados.prices.support.PostgresTestDatabase;
import tools.jackson.databind.ObjectMapper;

/** Paid Premium end to end: the provider's API is a mock, its webhooks are signed like the real ones. */
@SpringBootTest(properties = {
        "app.billing.abacatepay.enabled=true",
        "app.billing.abacatepay.api-key=abc_dev_not_used",
        "app.billing.abacatepay.webhook-secret=segredo-de-teste",
        "app.billing.abacatepay.monthly-product-id=prod_monthly",
        "app.billing.abacatepay.annual-product-id=prod_annual",
        "app.billing.abacatepay.site-url=https://gomo.example",
        "app.billing.abacatepay.dev-mode=true"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaidSubscriptionIntegrationTests {

    private static final String PASSWORD = "synthetic-test-password";
    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private PaymentGateway gateway;

    private String email;
    private String token;
    private UUID userId;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry);
    }

    @BeforeEach
    void account() throws Exception {
        when(gateway.available()).thenReturn(true);
        email = UUID.randomUUID() + "@example.com";
        token = registerAndLogin(email);
        userId = jdbc.queryForObject("select id from app_users where email = ?", UUID.class, email);
        jdbc.update("update app_users set email_verified_at = now() where id = ?", userId);
    }

    @Test
    void premiumStartsOnlyWhenTheProviderConfirmsThePayment() throws Exception {
        String checkoutId = openCheckout(BillingCycle.MONTHLY);
        currentUser().andExpect(jsonPath("$.premium").value(false));

        Instant paidAt = Instant.parse("2026-10-16T20:00:05Z");
        webhook(completed("log_" + UUID.randomUUID(), "subs_" + checkoutId, checkoutId, "MONTHLY", paidAt))
                .andExpect(status().isOk());

        currentUser().andExpect(jsonPath("$.premium").value(true))
                .andExpect(jsonPath("$.premiumSource").value("SUBSCRIPTION"))
                .andExpect(jsonPath("$.trialAvailable").value(false));
        assertThat(paidUntil()).isEqualTo(paidAt.atZone(SAO_PAULO).plusMonths(1).toInstant().plus(Duration.ofDays(3)));
        subscription().andExpect(jsonPath("$.renews").value(true))
                .andExpect(jsonPath("$.checkoutAvailable").value(true));
    }

    @Test
    void aRetriedDeliveryIsAppliedOnceAndARenewalExtendsTheAnnualPlan() throws Exception {
        String checkoutId = openCheckout(BillingCycle.ANNUAL);
        String subscriptionId = "subs_" + checkoutId;
        // Paid almost a year ago, so the renewal below is due now.
        String first = completed("log_" + UUID.randomUUID(), subscriptionId, checkoutId, "ANNUALLY",
                Instant.now().minus(Duration.ofDays(360)));
        webhook(first).andExpect(status().isOk());
        Instant afterFirst = paidUntil();
        webhook(first).andExpect(status().isOk());
        assertThat(paidUntil()).isEqualTo(afterFirst);

        Instant renewedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS).minusSeconds(60);
        webhook(event("log_" + UUID.randomUUID(), "subscription.renewed", subscriptionId, null, "ANNUALLY",
                renewedAt, null)).andExpect(status().isOk());

        assertThat(paidUntil()).isEqualTo(renewedAt.atZone(SAO_PAULO).plusYears(1).toInstant()
                .plus(Duration.ofDays(3)));
        assertThat(jdbc.queryForObject("select count(*) from billing_webhook_events where id like 'log_%'",
                Long.class)).isPositive();
    }

    @Test
    void cancellingKeepsThePaidPeriodAndFailedRetriesEndIt() throws Exception {
        String checkoutId = openCheckout(BillingCycle.MONTHLY);
        String subscriptionId = "subs_" + checkoutId;
        webhook(completed("log_" + UUID.randomUUID(), subscriptionId, checkoutId, "MONTHLY", Instant.now()))
                .andExpect(status().isOk());

        mvc.perform(delete("/api/v1/subscription").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.premium").value(true))
                .andExpect(jsonPath("$.renews").value(false));
        verify(gateway).cancelSubscription(subscriptionId);

        webhook(event("log_" + UUID.randomUUID(), "subscription.cancelled", subscriptionId, null, "MONTHLY", null,
                "max_payment_retries_exceeded")).andExpect(status().isOk());
        currentUser().andExpect(jsonPath("$.premium").value(false));
    }

    @Test
    void forgedOrForeignWebhooksGrantNothing() throws Exception {
        String checkoutId = openCheckout(BillingCycle.MONTHLY);
        String body = completed("log_" + UUID.randomUUID(), "subs_" + checkoutId, checkoutId, "MONTHLY", Instant.now());

        mvc.perform(post("/api/v1/billing/abacatepay/webhook").param("webhookSecret", "chute")
                        .header("X-Webhook-Signature", AbacatePaySignatures.sign(body))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        // A checkout Gomo never opened (another app on the same provider account).
        webhook(completed("log_" + UUID.randomUUID(), "subs_x", "bill_" + UUID.randomUUID(), "MONTHLY", Instant.now()))
                .andExpect(status().isOk());
        // A production event reaching the Dev mode deployment.
        webhook(body.replace("\"devMode\":true", "\"devMode\":false")).andExpect(status().isOk());

        currentUser().andExpect(jsonPath("$.premium").value(false));
    }

    @Test
    void unverifiedEmailAndSecondSubscriptionAreRefused() throws Exception {
        jdbc.update("update app_users set email_verified_at = null where id = ?", userId);
        checkout(BillingCycle.MONTHLY).andExpect(status().isForbidden());
        jdbc.update("update app_users set email_verified_at = now() where id = ?", userId);

        String checkoutId = openCheckout(BillingCycle.MONTHLY);
        webhook(completed("log_" + UUID.randomUUID(), "subs_" + checkoutId, checkoutId, "MONTHLY", Instant.now()))
                .andExpect(status().isOk());

        checkout(BillingCycle.ANNUAL).andExpect(status().isConflict());
    }

    @Test
    void deletingTheAccountCancelsTheSubscriptionFirst() throws Exception {
        String checkoutId = openCheckout(BillingCycle.MONTHLY);
        String subscriptionId = "subs_" + checkoutId;
        webhook(completed("log_" + UUID.randomUUID(), subscriptionId, checkoutId, "MONTHLY", Instant.now()))
                .andExpect(status().isOk());

        mvc.perform(delete("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("password", PASSWORD))))
                .andExpect(status().isNoContent());

        verify(gateway).cancelSubscription(subscriptionId);
        assertThat(jdbc.queryForObject("select count(*) from billing_subscriptions where user_id = ?", Long.class,
                userId)).isZero();
    }

    private String openCheckout(BillingCycle cycle) throws Exception {
        String checkoutId = "bill_" + UUID.randomUUID().toString().replace("-", "");
        when(gateway.openSubscriptionCheckout(cycle)).thenReturn(
                new PaymentGateway.Checkout(checkoutId, URI.create("https://app.abacatepay.com/pay/" + checkoutId)));
        checkout(cycle).andExpect(status().isOk())
                .andExpect(jsonPath("$.checkoutUrl").value("https://app.abacatepay.com/pay/" + checkoutId));
        return checkoutId;
    }

    private ResultActions checkout(BillingCycle cycle) throws Exception {
        return mvc.perform(post("/api/v1/subscription/checkout").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("billingCycle", cycle.name()))));
    }

    private ResultActions webhook(String body) throws Exception {
        return mvc.perform(post("/api/v1/billing/abacatepay/webhook").param("webhookSecret", "segredo-de-teste")
                .header("X-Webhook-Signature", AbacatePaySignatures.sign(body))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String completed(String id, String subscriptionId, String checkoutId, String frequency,
            Instant paidAt) {
        return event(id, "subscription.completed", subscriptionId, checkoutId, frequency, paidAt, null);
    }

    private static String event(String id, String type, String subscriptionId, String checkoutId, String frequency,
            Instant paidAt, String cancelledDueTo) {
        String checkout = checkoutId == null ? "null" : "{\"id\":\"" + checkoutId + "\"}";
        String payment = paidAt == null ? "null" : "{\"status\":\"PAID\",\"updatedAt\":\"" + paidAt + "\"}";
        String dueTo = cancelledDueTo == null ? "null" : "\"" + cancelledDueTo + "\"";
        return """
                {"id":"%s","event":"%s","apiVersion":2,"devMode":true,
                 "data":{"subscription":{"id":"%s","frequency":"%s","cancelledDueTo":%s},
                         "payment":%s,"checkout":%s}}
                """.formatted(id, type, subscriptionId, frequency, dueTo, payment, checkout);
    }

    private Instant paidUntil() {
        return jdbc.queryForObject("select premium_paid_until from app_users where id = ?", java.sql.Timestamp.class,
                userId).toInstant();
    }

    private ResultActions currentUser() throws Exception {
        return mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    private ResultActions subscription() throws Exception {
        return mvc.perform(get("/api/v1/subscription").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    private String registerAndLogin(String email) throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Pessoa sintética", "email", email,
                                "password", PASSWORD))))
                .andExpect(status().isCreated());
        var response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        return json.readTree(response).path("accessToken").asString();
    }
}

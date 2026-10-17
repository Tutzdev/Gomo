package br.com.supermercados.prices.subscription.abacatepay;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import br.com.supermercados.prices.subscription.BillingCycle;

/**
 * Paid Premium through AbacatePay (API v2). Off by default: without the keys the plans page keeps only the free
 * trial. The API key decides the environment (Dev mode or production); {@code devMode} must match it so a test
 * event can never unlock Premium in production.
 */
@ConfigurationProperties("app.billing.abacatepay")
public class AbacatePayProperties {

    /** Public key AbacatePay documents for the HMAC of every webhook body. */
    static final String DOCUMENTED_SIGNATURE_KEY = "t9dXRhHHo3yDEj5pVDYz0frf7q6bMKyMRmxxCPIPp3RCplBfXRxqlC6Zpi"
            + "WmOqj4L63qEaeUOtrCI8P0VMUgo6iIga2ri9ogaHFs0WIIywSMg0q7RmBfybe1E5XJcfC4IW3alNqym0tXoAKkzvfEjZxV6bE0oG2zJr"
            + "NNYmUCKZyV0KZ3JS8Votf9EAWWYdiDkMkpbMdPggfh1EqHlVkMiTady6jOR3hyzGEHrIz2Ret0xHKMbiqkr9HS1JhNHDX9";

    private boolean enabled;
    private URI baseUrl = URI.create("https://api.abacatepay.com/v2");
    private String apiKey = "";
    private String webhookSecret = "";
    private String signatureKey = DOCUMENTED_SIGNATURE_KEY;
    private String monthlyProductId = "";
    private String annualProductId = "";
    /** Public address of the site; the checkout sends the person back to the plans page there. */
    private URI siteUrl;
    private boolean devMode;
    private List<String> methods = new ArrayList<>(List.of("CARD"));
    private Duration connectTimeout = Duration.ofSeconds(10);
    private Duration requestTimeout = Duration.ofSeconds(20);

    /** Refuses to start half configured instead of failing on the first customer who tries to pay. */
    void validate() {
        if (!enabled) {
            return;
        }
        List<String> missing = new ArrayList<>();
        if (apiKey.isBlank()) missing.add("ABACATEPAY_API_KEY");
        if (webhookSecret.isBlank()) missing.add("ABACATEPAY_WEBHOOK_SECRET");
        if (monthlyProductId.isBlank()) missing.add("ABACATEPAY_MONTHLY_PRODUCT_ID");
        if (annualProductId.isBlank()) missing.add("ABACATEPAY_ANNUAL_PRODUCT_ID");
        if (siteUrl == null) missing.add("ABACATEPAY_SITE_URL");
        if (!missing.isEmpty()) {
            throw new IllegalStateException("AbacatePay habilitada sem " + String.join(", ", missing));
        }
        if (!"https".equals(baseUrl.getScheme()) && !"localhost".equals(baseUrl.getHost())
                && !"127.0.0.1".equals(baseUrl.getHost())) {
            throw new IllegalStateException("A API da AbacatePay precisa de HTTPS");
        }
    }

    String productId(BillingCycle cycle) {
        return cycle == BillingCycle.ANNUAL ? annualProductId : monthlyProductId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public URI getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(URI baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey.strip();
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret == null ? "" : webhookSecret.strip();
    }

    public String getSignatureKey() {
        return signatureKey;
    }

    public void setSignatureKey(String signatureKey) {
        this.signatureKey = signatureKey == null || signatureKey.isBlank() ? DOCUMENTED_SIGNATURE_KEY : signatureKey.strip();
    }

    public String getMonthlyProductId() {
        return monthlyProductId;
    }

    public void setMonthlyProductId(String monthlyProductId) {
        this.monthlyProductId = monthlyProductId == null ? "" : monthlyProductId.strip();
    }

    public String getAnnualProductId() {
        return annualProductId;
    }

    public void setAnnualProductId(String annualProductId) {
        this.annualProductId = annualProductId == null ? "" : annualProductId.strip();
    }

    public URI getSiteUrl() {
        return siteUrl;
    }

    public void setSiteUrl(URI siteUrl) {
        this.siteUrl = siteUrl;
    }

    public boolean isDevMode() {
        return devMode;
    }

    public void setDevMode(boolean devMode) {
        this.devMode = devMode;
    }

    public List<String> getMethods() {
        return methods;
    }

    public void setMethods(List<String> methods) {
        this.methods = methods;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }
}

package br.com.supermercados.prices.whatsapp;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.whatsapp")
public record WhatsAppProperties(
        boolean enabled,
        @DefaultValue("https://graph.facebook.com/v21.0") URI baseUrl,
        @DefaultValue("") String phoneNumberId,
        @DefaultValue("") String accessToken,
        @DefaultValue("alerta_de_preco") String templateName,
        @DefaultValue("pt_BR") String templateLanguage,
        @DefaultValue("3") int maxAttempts,
        @DefaultValue("PT10M") Duration retryDelay,
        @DefaultValue("PT6H") Duration maxDelay,
        @DefaultValue("50") int batchSize,
        @DefaultValue("PT10S") Duration connectTimeout,
        @DefaultValue("PT20S") Duration requestTimeout) {

    public WhatsAppProperties {
        phoneNumberId = phoneNumberId.strip();
        accessToken = accessToken.strip();

        if (enabled) {requireComplete(phoneNumberId, accessToken, baseUrl);}
    }

    private static void requireComplete(String phoneNumberId, String accessToken, URI baseUrl) {
        List<String> missing = new ArrayList<>();

        if (phoneNumberId.isBlank()) {missing.add("WHATSAPP_PHONE_NUMBER_ID");}

        if (accessToken.isBlank()) {missing.add("WHATSAPP_ACCESS_TOKEN");}

        if (!missing.isEmpty()) {
            throw new IllegalStateException("WhatsApp habilitado sem " + String.join(", ", missing));
        }

        if (!"https".equals(baseUrl.getScheme()) && !isLocal(baseUrl)) {
            throw new IllegalStateException("A API do WhatsApp precisa de HTTPS");
        }
    }

    private static boolean isLocal(URI uri) {
        return "localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost());
    }
}

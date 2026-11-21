package br.com.supermercados.prices.whatsapp;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
class WhatsAppCloudClient {

    private final WhatsAppProperties properties;
    private final ObjectMapper json;
    private final HttpClient client;

    WhatsAppCloudClient(WhatsAppProperties properties, ObjectMapper json) {
        this.properties = properties;
        this.json = json;
        this.client = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
    }

    void sendPriceAlert(PendingAlert alert) {
        HttpRequest request = HttpRequest.newBuilder(messagesUri())
                .timeout(properties.requestTimeout())
                .header("Authorization", "Bearer " + properties.accessToken())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(templateMessage(alert))))
                .build();

        HttpResponse<String> response = send(request);

        if (response.statusCode() / 100 != 2) {
            throw new WhatsAppDeliveryException("HTTP " + response.statusCode() + ": " + errorMessage(response.body()));
        }
    }

    private URI messagesUri() {
        return URI.create(properties.baseUrl() + "/" + properties.phoneNumberId() + "/messages");
    }

    private Map<String, Object> templateMessage(PendingAlert alert) {
        List<Map<String, String>> parameters = List.of(
            text(alert.productName()),
            text(alert.priceLabel()),
            text(alert.storeName()));

        return Map.of(
            "messaging_product", "whatsapp",
            "to", alert.number().withoutPlus(),
            "type", "template",
            "template", Map.of(
                "name", properties.templateName(),
                "language", Map.of("code", properties.templateLanguage()),
                "components", List.of(Map.of("type", "body", "parameters", parameters))));
    }

    private static Map<String, String> text(String value) {
        return Map.of("type", "text", "text", value);
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException exception) {
            throw new WhatsAppDeliveryException("sem resposta: " + exception.getClass().getSimpleName());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new WhatsAppDeliveryException("envio interrompido");
        }
    }

    private String errorMessage(String body) {
        try {
            JsonNode error = json.readTree(body).path("error");

            return error.path("message").asString("erro sem descrição");
        } catch (JacksonException exception) {
            return "resposta inválida";
        }
    }
}

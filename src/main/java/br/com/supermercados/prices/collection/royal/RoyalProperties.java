package br.com.supermercados.prices.collection.royal;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties("app.collection.royal")
public class RoyalProperties {

    private URI baseUrl = URI.create("https://www.royalsupermercados.com.br");
    private URI apiUrl = URI.create("https://services.vipcommerce.com.br");
    private String publicConfigurationPath = "/chunk-TLHOSSIT.js";

    private List<String> searchTerms = List.of(
        "coca",
        "italac",
        "bauducco",
        "yoki",
        "coracoes");

    private boolean fullCatalog;
    private Duration connectTimeout = Duration.ofSeconds(10);
    private Duration requestTimeout = Duration.ofSeconds(20);
    private Duration requestDelay = Duration.ofSeconds(1);
    private int maxAttempts = 2;
    private int maxResponseBytes = 5_000_000;
}

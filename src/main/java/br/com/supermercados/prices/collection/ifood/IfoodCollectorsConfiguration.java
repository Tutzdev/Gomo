package br.com.supermercados.prices.collection.ifood;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import br.com.supermercados.prices.collection.ServedCity;
import br.com.supermercados.prices.collection.SupermarketCollector;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.collection.ifood.enabled", havingValue = "true")
public class IfoodCollectorsConfiguration {

    static final IfoodMerchant SUPERMARKET_FLORESTA = new IfoodMerchant(
        "supermarket_floresta_aterrado",
        "Supermarket",
        "Supermarket Floresta Aterrado",
        "964260c7-c1e2-4a1f-8f2b-5a053c9e09b1",
        "supermarket-floresta---volta-redonda-aterrado",
        "Avenida Paulo de Frontin, 1096 - Aterrado - Volta Redonda/RJ - CEP 27213-150",
        "32500977000598",
        new BigDecimal("-22.512595"),
        new BigDecimal("-44.088170"),
        Instant.parse("2026-11-25T00:00:00Z"),
        ServedCity.VOLTA_REDONDA);

    @Bean
    SupermarketCollector supermarketFlorestaAterrado(
        ObjectMapper mapper,
        Clock clock,
        @Value("${app.collection.ifood.request-delay:PT2S}") Duration delay) {

        return new IfoodMerchantCollector(
            SUPERMARKET_FLORESTA,
            URI.create(IfoodMerchant.WEBSITE),
            mapper,
            clock,
            delay);
    }
}

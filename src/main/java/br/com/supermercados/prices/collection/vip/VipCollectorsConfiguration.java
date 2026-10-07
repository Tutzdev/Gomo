package br.com.supermercados.prices.collection.vip;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import br.com.supermercados.prices.collection.SupermarketCollector;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.collection.regional.enabled", havingValue = "true", matchIfMissing = true)
public class VipCollectorsConfiguration {

    @Bean
    SupermarketCollector bramilVoltaRedonda(ObjectMapper mapper, Clock clock, VipProductParser parser) {
        return collector(new VipStoreDefinition("bramil_santo_agostinho", "Bramil", "Bramil Santo Agostinho",
                URI.create("https://www.bramilemcasa.com.br"), "/chunk-CA3SU4MX.js",
                53, 24, 1, 24, "32296378004753"), mapper, clock, parser);
    }

    @Bean
    SupermarketCollector perolaVoltaRedonda(ObjectMapper mapper, Clock clock, VipProductParser parser) {
        return collector(new VipStoreDefinition("perola_agua_limpa", "Pérola", "Pérola Água Limpa",
                URI.create("https://www.perolasupermercados.com.br"), "/chunk-TLHOSSIT.js",
                329, 375, 1, 1, "07954309000240"), mapper, clock, parser);
    }

    @Bean
    SupermarketCollector spaniVoltaRedonda(ObjectMapper mapper, Clock clock, VipProductParser parser) {
        return collector(new VipStoreDefinition("spani_volta_redonda", "Spani", "Spani Volta Redonda",
                URI.create("https://www.spanionline.com.br"), "/chunk-CA3SU4MX.js",
                67, 108, 1, 6, "05868574001171"), mapper, clock, parser);
    }

    private SupermarketCollector collector(VipStoreDefinition definition, ObjectMapper mapper,
            Clock clock, VipProductParser parser) {
        return new VipStoreCollector(definition, URI.create("https://services.vipcommerce.com.br"),
                mapper, clock, Duration.ofSeconds(1), parser);
    }
}

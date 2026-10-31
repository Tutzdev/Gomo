package br.com.supermercados.prices.collection.vip;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import br.com.supermercados.prices.collection.ServedCity;
import br.com.supermercados.prices.collection.SupermarketCollector;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.collection.regional.enabled", havingValue = "true", matchIfMissing = true)
public class VipCollectorsConfiguration {

    @Bean
    SupermarketCollector bramilVoltaRedonda(ObjectMapper mapper, Clock clock, VipProductParser parser) {
        return collector(new VipStoreDefinition("bramil_santo_agostinho", "Bramil", "Bramil Santo Agostinho",
                URI.create("https://www.bramilemcasa.com.br"), "/chunk-CA3SU4MX.js",
                53, 24, 1, 24, "32296378004753", ServedCity.VOLTA_REDONDA), mapper, clock, parser);
    }

    @Bean
    SupermarketCollector perolaVoltaRedonda(ObjectMapper mapper, Clock clock, VipProductParser parser) {
        return collector(new VipStoreDefinition("perola_agua_limpa", "Pérola", "Pérola Água Limpa",
                URI.create("https://www.perolasupermercados.com.br"), "/chunk-IBICK36J.js",
                329, 375, 1, 1, "07954309000240", ServedCity.VOLTA_REDONDA), mapper, clock, parser);
    }

    @Bean
    SupermarketCollector spaniVoltaRedonda(ObjectMapper mapper, Clock clock, VipProductParser parser) {
        return collector(new VipStoreDefinition("spani_volta_redonda", "Spani", "Spani Volta Redonda",
                URI.create("https://www.spanionline.com.br"), "/chunk-CA3SU4MX.js",
                67, 108, 1, 6, "05868574001171", ServedCity.VOLTA_REDONDA), mapper, clock, parser);
    }

    @Bean
    SupermarketCollector bramilBarraMansa(ObjectMapper mapper, Clock clock, VipProductParser parser) {
        return collector(new VipStoreDefinition("bramil_barra_mansa_centro", "Bramil", "Bramil Centro",
                URI.create("https://www.bramilemcasa.com.br"), "/chunk-CA3SU4MX.js",
                53, 24, 1, 23, "32296378002033", ServedCity.BARRA_MANSA), mapper, clock, parser);
    }

    @Bean
    SupermarketCollector perolaBarraMansa(ObjectMapper mapper, Clock clock, VipProductParser parser) {
        return collector(new VipStoreDefinition("perola_ano_bom", "Pérola", "Pérola Ano Bom",
                URI.create("https://www.perolasupermercados.com.br"), "/chunk-IBICK36J.js",
                329, 375, 1, 2, "07954309000321", ServedCity.BARRA_MANSA), mapper, clock, parser);
    }

    private SupermarketCollector collector(VipStoreDefinition definition, ObjectMapper mapper,
            Clock clock, VipProductParser parser) {
        return new VipStoreCollector(definition, URI.create("https://services.vipcommerce.com.br"),
                mapper, clock, Duration.ofSeconds(1), parser);
    }
}

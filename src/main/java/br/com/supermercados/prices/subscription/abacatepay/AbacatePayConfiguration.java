package br.com.supermercados.prices.subscription.abacatepay;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AbacatePayProperties.class)
class AbacatePayConfiguration {
}

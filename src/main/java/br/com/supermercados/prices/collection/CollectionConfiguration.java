package br.com.supermercados.prices.collection;

import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import br.com.supermercados.prices.collection.nagumo.NagumoProperties;
import br.com.supermercados.prices.collection.royal.RoyalProperties;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties({NagumoProperties.class, RoyalProperties.class})
public class CollectionConfiguration {}

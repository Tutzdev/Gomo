package br.com.supermercados.prices.collection.flyer;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import br.com.supermercados.prices.collection.CollectionCatalogService;

/**
 * Registers the reviewed Atacadão units at startup, whether or not a reviewed flyer is still valid.
 * The online Atacadão collector attaches its prices to these units, so without this it fails on a fresh
 * database ("Diretório verificado da unidade ausente") until someone reviews new flyers.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "app.collection.enabled", havingValue = "true", matchIfMissing = true)
class ReviewedStoreDirectory implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReviewedStoreDirectory.class);

    private final ObjectProvider<ReviewedAtacadaoFlyerCollector> collectors;
    private final CollectionCatalogService catalogs;
    private final Clock clock;

    ReviewedStoreDirectory(ObjectProvider<ReviewedAtacadaoFlyerCollector> collectors,
            CollectionCatalogService catalogs, Clock clock) {
        this.collectors = collectors;
        this.catalogs = catalogs;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        collectors.orderedStream().forEach(collector -> {
            try {
                catalogs.ensureCatalog(collector.metadata(), collector.store(), clock.instant());
            } catch (RuntimeException exception) {
                LOGGER.warn("Unidade revisada {} não registrada: {}", collector.metadata().code(), exception.getMessage());
            }
        });
    }
}

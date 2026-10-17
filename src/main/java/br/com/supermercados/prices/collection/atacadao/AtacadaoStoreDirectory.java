package br.com.supermercados.prices.collection.atacadao;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import br.com.supermercados.prices.collection.CollectedStore;
import br.com.supermercados.prices.collection.CollectionCatalogService;
import br.com.supermercados.prices.collection.CollectorMetadata;

/**
 * Registers Atacadão São Geraldo at startup. {@link AtacadaoCollector} attaches its prices to this unit, so
 * without it the online collection fails on a fresh database ("Diretório verificado da unidade ausente").
 * The source code and reference came from the retired flyer importer and stay as they are: production
 * already holds the unit, with its history, under them, and a new identity would create a second unit.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "app.collection.enabled", havingValue = "true", matchIfMissing = true)
class AtacadaoStoreDirectory implements ApplicationRunner {

    static final String SOURCE_CODE = "atacadao_reviewed_flyers";
    static final String STORE_REFERENCE = "atacadao:store:815";
    private static final Logger LOGGER = LoggerFactory.getLogger(AtacadaoStoreDirectory.class);
    private static final CollectorMetadata METADATA = new CollectorMetadata("atacadao_store_directory", "Atacadão",
            "Atacadão São Geraldo", SOURCE_CODE, "Diretório oficial de lojas Atacadão",
            "https://www.atacadao.com.br", Instant.parse("2026-09-19T00:20:00Z"), "Atacadão", "atacadao:chain",
            STORE_REFERENCE, UUID.fromString("5c4cb935-52e1-4bf8-8d17-902dc0837c66"));
    private static final CollectedStore STORE = new CollectedStore("Atacadão São Geraldo",
            "Rodovia dos Metalúrgicos, 1085 - São Geraldo - Volta Redonda/RJ",
            new BigDecimal("-22.521388"), new BigDecimal("-44.080095"), true);

    private final CollectionCatalogService catalogs;
    private final Clock clock;

    AtacadaoStoreDirectory(CollectionCatalogService catalogs, Clock clock) {
        this.catalogs = catalogs;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        try {
            catalogs.ensureCatalog(METADATA, STORE, clock.instant());
        } catch (RuntimeException exception) {
            LOGGER.warn("Unidade Atacadão São Geraldo não registrada: {}", exception.getMessage());
        }
    }
}

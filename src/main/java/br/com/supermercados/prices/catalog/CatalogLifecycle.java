package br.com.supermercados.prices.catalog;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import br.com.supermercados.prices.collection.CollectionCompletedListener;
import br.com.supermercados.prices.collection.CollectionCoordinator;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CatalogLifecycle implements CollectionCompletedListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogLifecycle.class);
    private static final Duration FULL_IMPORT_INTERVAL = Duration.ofDays(7);

    private final CatalogBuilder builder;
    private final ObjectProvider<CollectionCoordinator> coordinator;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final boolean rebuildOnStart;
    private final boolean collectionEnabled;
    private final Duration refreshWhenOlderThan;
    private final boolean seedWhenEmpty;

    public CatalogLifecycle(
        CatalogBuilder builder,
        ObjectProvider<CollectionCoordinator> coordinator,
        JdbcTemplate jdbc,
        Clock clock,
        @Value("${app.catalog.rebuild-on-start:true}") boolean rebuildOnStart,
        @Value("${app.collection.enabled:true}") boolean collectionEnabled,
        @Value("${app.collection.refresh-when-older-than:PT8H}") Duration refreshWhenOlderThan,
        @Value("${app.collection.seed-when-empty:true}") boolean seedWhenEmpty) {

        this.builder = builder;
        this.coordinator = coordinator;
        this.jdbc = jdbc;
        this.clock = clock;
        this.rebuildOnStart = rebuildOnStart;
        this.collectionEnabled = collectionEnabled;
        this.refreshWhenOlderThan = refreshWhenOlderThan;
        this.seedWhenEmpty = seedWhenEmpty;
    }

    @Override
    public void collectionCompleted() {
        builder.rebuild();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStart() {
        if (!rebuildOnStart && !collectionEnabled) {return;}

        Thread.ofVirtual().name("catalog-startup").start(this::prepareInBackground);
    }

    private void prepareInBackground() {
        try {
            failInterruptedRuns();

            boolean seeded = collectionEnabled && seedWhenEmpty && withoutPrices() && !seed().isEmpty();

            if (rebuildOnStart && !seeded) {builder.rebuild();}

            if (collectionEnabled && !coordinator.getObject().isRunning()) {refreshOutdatedMarkets(seeded);}
        } catch (RuntimeException exception) {
            LOGGER.error("Atualização inicial do catálogo falhou: {}", exception.getMessage(), exception);
        }
    }

    private void failInterruptedRuns() {
        int interrupted = jdbc.update("""
                UPDATE collection_runs SET status = 'FAILED', finished_at = now(), error_count = error_count + 1,
                    error_message = 'Interrompida: a aplicação foi encerrada durante a coleta.'
                WHERE status = 'RUNNING'
                """);

        if (interrupted > 0) {LOGGER.info("{} coleta(s) interrompida(s) marcadas como falha.", interrupted);}
    }

    private void refreshOutdatedMarkets(boolean seeded) {
        Set<String> upToDate = seeded ? Set.of() : upToDateCollectors();
        Set<String> imported = importedCollectors();

        LOGGER.info("Atualizando em segundo plano os mercados sem coleta nas últimas {} (em dia: {}) "
                + "e importando por completo os sem importação completa em {} (importados: {})",
                refreshWhenOlderThan, upToDate, FULL_IMPORT_INTERVAL, imported);

        coordinator.getObject().refreshAtStartup(upToDate, imported);
    }

    private boolean withoutPrices() {
        return Boolean.FALSE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM price_records)", Boolean.class));
    }

    private List<?> seed() {
        LOGGER.info("Banco sem preços: carregando as coletas incluídas na aplicação.");
        var results = coordinator.getObject().seedFromBundledSnapshots();
        LOGGER.info("Carga inicial concluída: {} mercado(s).", results.size());

        return results;
    }

    private Set<String> importedCollectors() {
        return new HashSet<>(jdbc.queryForList("""
                SELECT collector_code FROM collection_runs
                WHERE full_import AND status IN ('SUCCESS', 'PARTIAL') AND finished_at > ?
                """, String.class, Timestamp.from(clock.instant().minus(FULL_IMPORT_INTERVAL))));
    }

    private Set<String> upToDateCollectors() {
        return new HashSet<>(jdbc.queryForList("""
                SELECT collector_code FROM collection_runs
                WHERE status IN ('SUCCESS', 'PARTIAL') AND finished_at > ?
                """, String.class, Timestamp.from(clock.instant().minus(refreshWhenOlderThan))));
    }
}

package br.com.supermercados.prices.catalog;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import br.com.supermercados.prices.collection.CollectionCompletedListener;
import br.com.supermercados.prices.collection.CollectionCoordinator;

/**
 * Keeps the generic catalog in step with collected prices: rebuilt after every collection and at startup,
 * and when the newest price is older than {@code app.collection.refresh-when-older-than} a refresh starts
 * in the background, so a machine that was off does not serve expired prices all day.
 */
@Component
public class CatalogLifecycle implements CollectionCompletedListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogLifecycle.class);

    private final CatalogBuilder builder;
    // Lazy: the coordinator itself notifies this listener.
    private final ObjectProvider<CollectionCoordinator> coordinator;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final boolean rebuildOnStart;
    private final boolean collectionEnabled;
    private final Duration refreshWhenOlderThan;

    public CatalogLifecycle(CatalogBuilder builder, ObjectProvider<CollectionCoordinator> coordinator, JdbcTemplate jdbc, Clock clock,
            @Value("${app.catalog.rebuild-on-start:true}") boolean rebuildOnStart,
            @Value("${app.collection.enabled:true}") boolean collectionEnabled,
            @Value("${app.collection.refresh-when-older-than:PT8H}") Duration refreshWhenOlderThan) {
        this.builder = builder;
        this.coordinator = coordinator;
        this.jdbc = jdbc;
        this.clock = clock;
        this.rebuildOnStart = rebuildOnStart;
        this.collectionEnabled = collectionEnabled;
        this.refreshWhenOlderThan = refreshWhenOlderThan;
    }

    @Override
    public void collectionCompleted() {
        builder.rebuild();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStart() {
        if (!rebuildOnStart && !collectionEnabled) return;
        Thread.ofVirtual().name("catalog-startup").start(() -> {
            try {
                // A run still marked RUNNING at startup belonged to a process that no longer exists.
                int interrupted = jdbc.update("""
                        UPDATE collection_runs SET status = 'FAILED', finished_at = now(), error_count = error_count + 1,
                            error_message = 'Interrompida: a aplicação foi encerrada durante a coleta.'
                        WHERE status = 'RUNNING'
                        """);
                if (interrupted > 0) LOGGER.info("{} coleta(s) interrompida(s) marcadas como falha.", interrupted);
                if (rebuildOnStart) builder.rebuild();
                if (collectionEnabled && !coordinator.getObject().isRunning()) {
                    var upToDate = upToDateCollectors();
                    LOGGER.info("Atualizando em segundo plano os mercados sem coleta nas últimas {}; em dia: {}",
                            refreshWhenOlderThan, upToDate);
                    coordinator.getObject().refreshExistingExcept(upToDate);
                }
            } catch (RuntimeException exception) {
                LOGGER.error("Atualização inicial do catálogo falhou: {}", exception.getMessage(), exception);
            }
        });
    }

    /** Collectors that completed within the threshold; all others are refreshed at startup. */
    private java.util.Set<String> upToDateCollectors() {
        return new java.util.HashSet<>(jdbc.queryForList("""
                SELECT collector_code FROM collection_runs
                WHERE status IN ('SUCCESS', 'PARTIAL') AND finished_at > ?
                """, String.class, Timestamp.from(clock.instant().minus(refreshWhenOlderThan))));
    }
}

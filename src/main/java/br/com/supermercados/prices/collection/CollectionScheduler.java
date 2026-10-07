package br.com.supermercados.prices.collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CollectionScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(CollectionScheduler.class);

    private final CollectionCoordinator coordinator;
    private final boolean enabled;
    private final boolean existingOnly;

    public CollectionScheduler(
            CollectionCoordinator coordinator,
            @Value("${app.collection.enabled:true}") boolean enabled,
            @Value("${app.collection.existing-only:true}") boolean existingOnly) {
        this.coordinator = coordinator;
        this.enabled = enabled;
        this.existingOnly = existingOnly;
    }

    @Scheduled(
            cron = "${app.collection.cron:0 0 6,11,16,21 * * *}",
            zone = "${app.collection.zone:America/Sao_Paulo}")
    public void collectDaily() {
        if (!enabled || coordinator.isRunning()) {
            return;
        }
        try {
            if (existingOnly) {
                coordinator.refreshExisting(null);
            } else {
                coordinator.collectAll();
            }
        } catch (RuntimeException exception) {
            LOGGER.error("Não foi possível iniciar a coleta agendada: {}", exception.getMessage(), exception);
        }
    }

    /**
     * Once a week every offer is imported, not only the products already known, so the catalog follows what
     * the markets launch and the SKUs they renumber. The other collections of the day only refresh prices.
     */
    @Scheduled(
            cron = "${app.collection.full-cron:0 0 3 * * SUN}",
            zone = "${app.collection.zone:America/Sao_Paulo}")
    public void importNewProducts() {
        if (!enabled || coordinator.isRunning()) {
            return;
        }
        try {
            coordinator.collectAll();
        } catch (RuntimeException exception) {
            LOGGER.error("Não foi possível iniciar a importação semanal: {}", exception.getMessage(), exception);
        }
    }
}

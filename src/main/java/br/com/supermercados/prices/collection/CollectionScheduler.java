package br.com.supermercados.prices.collection;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
    private final Set<String> failedInLastRound = ConcurrentHashMap.newKeySet();

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
        if (!enabled || coordinator.isRunning()) {return;}

        try {
            List<CollectionRunResponse> runs = existingOnly ? coordinator.refreshExisting(null) : coordinator.collectAll();
            failedInLastRound.clear();

            if (runs != null) {
                runs.stream().filter(run -> run.status() == CollectionStatus.FAILED)
                        .map(CollectionRunResponse::collectorCode).forEach(failedInLastRound::add);
            }
        } catch (RuntimeException exception) {
            LOGGER.error("Não foi possível iniciar a coleta agendada: {}", exception.getMessage(), exception);
        }
    }

    @Scheduled(
            cron = "${app.collection.retry-cron:0 45 6,11,16,21 * * *}",
            zone = "${app.collection.zone:America/Sao_Paulo}")
    public void retryFailed() {
        if (!enabled || coordinator.isRunning() || failedInLastRound.isEmpty()) {return;}

        List<String> due = List.copyOf(failedInLastRound);
        failedInLastRound.clear();

        for (String code : due) {
            try {
                LOGGER.info("Nova tentativa de coleta para {}", code);

                if (existingOnly) {
                    coordinator.refreshExisting(code);
                } else {
                    coordinator.collectSelected(code);
                }
            } catch (RuntimeException exception) {
                LOGGER.error(
                    "Nova tentativa de coleta para {} não iniciou: {}",
                    code,
                    exception.getMessage(),
                    exception);
            }
        }
    }

    @Scheduled(
            cron = "${app.collection.full-cron:0 0 3 * * SUN}",
            zone = "${app.collection.zone:America/Sao_Paulo}")
    public void importNewProducts() {
        if (!enabled || coordinator.isRunning()) {return;}

        try {
            coordinator.collectAll();
        } catch (RuntimeException exception) {
            LOGGER.error("Não foi possível iniciar a importação semanal: {}", exception.getMessage(), exception);
        }
    }
}

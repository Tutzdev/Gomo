package br.com.supermercados.prices.collection;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Optional operator command. Normal restarts never trigger a catalog download. */
@Component
@ConditionalOnProperty(name = "app.collection.run-on-start")
public class CollectionStartupRunner implements ApplicationRunner {

    private final CollectionCoordinator coordinator;
    private final String collectorCodes;

    public CollectionStartupRunner(CollectionCoordinator coordinator,
            @Value("${app.collection.run-on-start}") String collectorCodes) {
        this.coordinator = coordinator;
        this.collectorCodes = collectorCodes;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        for (String code : collectorCodes.split(",")) {
            if (code.strip().startsWith("replay-existing:")) {
                coordinator.replayExisting(code.strip().substring("replay-existing:".length()));
            } else if (code.strip().startsWith("refresh:")) {
                coordinator.refreshExisting(code.strip().substring("refresh:".length()));
            } else if (code.strip().startsWith("replay:")) {
                coordinator.replayArchived(code.strip().substring("replay:".length()));
            } else if (!code.isBlank()) {
                coordinator.collectSelected(code.strip());
            }
        }
    }
}

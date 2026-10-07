package br.com.supermercados.prices.collection;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import br.com.supermercados.prices.common.ApiException;

@Service
public class CollectionCoordinator {

    private static final Logger LOGGER = LoggerFactory.getLogger(CollectionCoordinator.class);

    private final List<SupermarketCollector> collectors;
    private final CollectedCatalogIngestionService ingestion;
    private final CollectionRunService runs;
    private final CollectedCatalogArchive archive;
    private final Duration collectorInterval;
    private final int parallelism;
    private final AtomicBoolean running = new AtomicBoolean();
    /** Downloads run in parallel (each collector talks to its own site); database writes run one at a time. */
    private final Object ingestionLock = new Object();
    private List<CollectionCompletedListener> listeners = List.of();

    public CollectionCoordinator(
            List<SupermarketCollector> collectors,
            CollectedCatalogIngestionService ingestion,
            CollectionRunService runs,
            CollectedCatalogArchive archive,
            Duration collectorInterval) {
        this(collectors, ingestion, runs, archive, collectorInterval, 1);
    }

    @Autowired
    public CollectionCoordinator(
            List<SupermarketCollector> collectors,
            CollectedCatalogIngestionService ingestion,
            CollectionRunService runs,
            CollectedCatalogArchive archive,
            @Value("${app.collection.collector-interval:PT2S}") Duration collectorInterval,
            @Value("${app.collection.parallelism:3}") int parallelism) {
        if (parallelism < 1) {
            throw new IllegalArgumentException("app.collection.parallelism deve ser positivo");
        }
        this.parallelism = parallelism;
        if (collectorInterval.isNegative()) {
            throw new IllegalArgumentException("Intervalo entre coletores não pode ser negativo");
        }
        this.collectors = List.copyOf(collectors);
        this.ingestion = ingestion;
        this.runs = runs;
        this.archive = archive;
        this.collectorInterval = collectorInterval;
    }

    @Autowired(required = false)
    void setListeners(List<CollectionCompletedListener> listeners) {
        this.listeners = List.copyOf(listeners);
    }

    /** Every registered collector, including those skipped by the schedule because their source has nothing current. */
    public List<CollectorMetadata> collectors() {
        return collectors.stream().map(SupermarketCollector::metadata).toList();
    }

    /** Whether the scheduled collections run this collector now (see {@link SupermarketCollector#hasCurrentSource()}). */
    public boolean isScheduled(String collectorCode) {
        return collectors.stream().anyMatch(collector -> collector.metadata().code().equals(collectorCode)
                && collector.hasCurrentSource());
    }

    public boolean isRunning() {
        return running.get();
    }

    public List<CollectionRunResponse> collectAll() {
        return collectSelected(null);
    }

    public List<CollectionRunResponse> collectSelected(String collectorCode) {
        return collectSelected(collectorCode, CatalogOrigin.LIVE, false);
    }

    public List<CollectionRunResponse> refreshExisting(String collectorCode) {
        return collectSelected(collectorCode, CatalogOrigin.LIVE, true);
    }

    /** Refreshes only the collectors whose code is not in {@code upToDate}, e.g. after the machine was off. */
    public List<CollectionRunResponse> refreshExistingExcept(java.util.Set<String> upToDate) {
        List<SupermarketCollector> stale = collectors.stream().filter(SupermarketCollector::hasCurrentSource)
                .filter(collector -> !upToDate.contains(collector.metadata().code())).toList();
        return stale.isEmpty() ? List.of() : run(stale, CatalogOrigin.LIVE, true);
    }

    public List<CollectionRunResponse> replayArchived(String collectorCode) {
        return collectSelected(collectorCode, CatalogOrigin.ARCHIVE, false);
    }

    public List<CollectionRunResponse> replayExisting(String collectorCode) {
        return collectSelected(collectorCode, CatalogOrigin.ARCHIVE, true);
    }

    /**
     * Fills an empty database with the collections bundled in the application, one market at a time
     * (reading every snapshot at once would need several times the memory). Live collections then keep
     * the prices current.
     */
    public List<CollectionRunResponse> seedFromBundledSnapshots() {
        List<SupermarketCollector> seeded = collectors.stream()
                .filter(collector -> archive.hasSeed(collector.metadata())).toList();
        if (seeded.isEmpty()) return List.of();
        if (!running.compareAndSet(false, true)) {
            throw new ApiException(HttpStatus.CONFLICT, "Já existe uma coleta em andamento");
        }
        try {
            List<CollectionRunResponse> results = new ArrayList<>();
            for (SupermarketCollector collector : seeded) {
                results.add(collect(collector, CatalogOrigin.SEED, false));
            }
            notifyListeners();
            return List.copyOf(results);
        } finally {
            running.set(false);
        }
    }

    private List<CollectionRunResponse> collectSelected(String collectorCode, CatalogOrigin origin, boolean existingOnly) {
        List<SupermarketCollector> selected = collectorCode == null
                ? collectors.stream().filter(SupermarketCollector::hasCurrentSource).toList() : collectors.stream()
                .filter(collector -> collector.metadata().code().equals(collectorCode)).toList();
        if (selected.isEmpty() && collectorCode != null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Coletor não encontrado");
        }
        return run(selected, origin, existingOnly);
    }

    private List<CollectionRunResponse> run(List<SupermarketCollector> selected, CatalogOrigin origin, boolean existingOnly) {
        if (!running.compareAndSet(false, true)) {
            throw new ApiException(HttpStatus.CONFLICT, "Já existe uma coleta em andamento");
        }

        try {
            List<CollectionRunResponse> results = parallelism > 1 && selected.size() > 1
                    ? collectInParallel(selected, origin, existingOnly)
                    : collectInSequence(selected, origin, existingOnly);
            notifyListeners();
            return List.copyOf(results);
        } finally {
            running.set(false);
        }
    }

    private List<CollectionRunResponse> collectInSequence(
            List<SupermarketCollector> selected, CatalogOrigin origin, boolean existingOnly) {
        List<CollectionRunResponse> results = new ArrayList<>();
        for (int index = 0; index < selected.size(); index++) {
            if (index > 0 && !waitBeforeNextCollector()) {
                break;
            }
            results.add(collect(selected.get(index), origin, existingOnly));
        }
        return results;
    }

    /** Results keep the collectors' order; one collector failing never stops the others. */
    private List<CollectionRunResponse> collectInParallel(
            List<SupermarketCollector> selected, CatalogOrigin origin, boolean existingOnly) {
        var pool = java.util.concurrent.Executors.newFixedThreadPool(Math.min(parallelism, selected.size()),
                Thread.ofPlatform().name("collector-", 1).daemon().factory());
        try {
            List<java.util.concurrent.Future<CollectionRunResponse>> futures = selected.stream()
                    .map(collector -> pool.submit(() -> collect(collector, origin, existingOnly))).toList();
            List<CollectionRunResponse> results = new ArrayList<>();
            for (var future : futures) {
                try {
                    results.add(future.get());
                } catch (java.util.concurrent.ExecutionException exception) {
                    LOGGER.error("Coletor encerrado inesperadamente: {}", exception.getCause().getMessage(),
                            exception.getCause());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    LOGGER.warn("Coleta paralela interrompida");
                    break;
                }
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private CollectionRunResponse collect(SupermarketCollector collector, CatalogOrigin origin, boolean existingOnly) {
        CollectorMetadata metadata = collector.metadata();
        CollectionRunResponse run = runs.start(metadata);
        LOGGER.info("Coleta {} iniciada para {}", run.id(), metadata.code());

        try {
            CollectedCatalog catalog = switch (origin) {
                case LIVE -> collector.collect();
                case ARCHIVE -> archive.read(metadata);
                case SEED -> archive.readSeed(metadata);
            };
            CollectionRunResponse completed;
            synchronized (ingestionLock) {
                if (origin == CatalogOrigin.LIVE) {
                    try {
                        archive.save(metadata, catalog);
                    } catch (RuntimeException exception) {
                        LOGGER.warn("Arquivo opcional da coleta {} indisponível: {}", metadata.code(), exception.getMessage());
                    }
                }
                completed = runs.finish(run.id(), existingOnly
                        ? ingestion.refreshExisting(metadata, catalog) : ingestion.ingest(metadata, catalog));
            }
            LOGGER.info("Coleta {} finalizada para {}: status={}, encontrados={}, criados={}, "
                            + "atualizados={}, ignorados={}, erros={}",
                    completed.id(), metadata.code(), completed.status(), completed.foundCount(),
                    completed.createdCount(), completed.updatedCount(), completed.skippedCount(),
                    completed.errorCount());
            return completed;
        } catch (RuntimeException exception) {
            String message = exception.getMessage() == null
                    ? exception.getClass().getSimpleName() : exception.getMessage();
            CollectionRunResponse failed = runs.fail(run.id(), message);
            LOGGER.error("Coleta {} falhou para {}: {}", failed.id(), metadata.code(), message, exception);
            return failed;
        } catch (OutOfMemoryError error) {
            // The collector's data is unreachable once the stack unwinds; record the failure instead of a run left RUNNING.
            CollectionRunResponse failed = runs.fail(run.id(), "Memória insuficiente durante a coleta");
            LOGGER.error("Coleta {} sem memória para {}", failed.id(), metadata.code(), error);
            return failed;
        }
    }

    private void notifyListeners() {
        for (CollectionCompletedListener listener : listeners) {
            try {
                listener.collectionCompleted();
            } catch (RuntimeException exception) {
                LOGGER.error("Pós-processamento da coleta falhou: {}", exception.getMessage(), exception);
            }
        }
    }

    private boolean waitBeforeNextCollector() {
        try {
            Thread.sleep(collectorInterval.toMillis());
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Execução de coletores interrompida antes da próxima integração");
            return false;
        }
    }

    /** Where a collection's catalog comes from: the market's site, the local archive or the snapshots bundled in the app. */
    private enum CatalogOrigin {
        LIVE, ARCHIVE, SEED
    }
}

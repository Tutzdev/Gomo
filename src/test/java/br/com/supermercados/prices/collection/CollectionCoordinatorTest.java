package br.com.supermercados.prices.collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class CollectionCoordinatorTest {

    private final CollectedCatalogIngestionService ingestion = mock(CollectedCatalogIngestionService.class);
    private final CollectionRunService runs = mock(CollectionRunService.class);

    @Test
    void retriesOnlyTheRequestedCollectorAndRejectsUnknownCodes() {
        TestCollector selected = new TestCollector("selected", false);
        TestCollector untouched = new TestCollector("untouched", false);
        when(runs.start(selected.metadata())).thenReturn(response(selected.metadata(), CollectionStatus.RUNNING));
        when(ingestion.ingest(any(), any())).thenReturn(new CollectionResult(
                UUID.randomUUID(), UUID.randomUUID(), 0, 0, 0, 0, 0, null));
        when(runs.finish(any(), any())).thenReturn(response(selected.metadata(), CollectionStatus.SUCCESS));
        var coordinator = new CollectionCoordinator(List.of(selected, untouched), ingestion, runs, mock(CollectedCatalogArchive.class), Duration.ZERO);

        assertThat(coordinator.collectSelected("selected")).hasSize(1);
        assertThat(selected.collected).isTrue();
        assertThat(untouched.collected).isFalse();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> coordinator.collectSelected("missing"))
                .isInstanceOf(br.com.supermercados.prices.common.ApiException.class);
    }

    @Test
    void failureInOneCollectorDoesNotPreventTheNextCollector() {
        TestCollector failing = new TestCollector("failing", true);
        TestCollector successful = new TestCollector("successful", false);
        CollectionRunResponse startedFailing = response(failing.metadata(), CollectionStatus.RUNNING);
        CollectionRunResponse startedSuccessful = response(successful.metadata(), CollectionStatus.RUNNING);
        CollectionRunResponse failed = response(failing.metadata(), CollectionStatus.FAILED);
        CollectionRunResponse completed = response(successful.metadata(), CollectionStatus.SUCCESS);

        when(runs.start(failing.metadata())).thenReturn(startedFailing);
        when(runs.start(successful.metadata())).thenReturn(startedSuccessful);
        when(runs.fail(startedFailing.id(), "falha controlada")).thenReturn(failed);
        when(ingestion.ingest(any(), any())).thenReturn(new CollectionResult(
                UUID.randomUUID(), UUID.randomUUID(), 0, 0, 0, 0, 0, null));
        when(runs.finish(any(), any())).thenReturn(completed);

        List<CollectionRunResponse> results = new CollectionCoordinator(
                List.of(failing, successful), ingestion, runs, mock(CollectedCatalogArchive.class), Duration.ZERO).collectAll();

        assertThat(results).extracting(CollectionRunResponse::status)
                .containsExactly(CollectionStatus.FAILED, CollectionStatus.SUCCESS);
        assertThat(successful.collected).isTrue();
        verify(ingestion).ingest(successful.metadata(), successful.catalog());
    }

    @Test
    void parallelCollectionKeepsCollectorOrderAndIsolatesFailures() {
        TestCollector failing = new TestCollector("failing", true);
        TestCollector first = new TestCollector("first", false);
        TestCollector second = new TestCollector("second", false);
        CollectionRunResponse startedFailing = response(failing.metadata(), CollectionStatus.RUNNING);
        when(runs.start(failing.metadata())).thenReturn(startedFailing);
        when(runs.start(first.metadata())).thenReturn(response(first.metadata(), CollectionStatus.RUNNING));
        when(runs.start(second.metadata())).thenReturn(response(second.metadata(), CollectionStatus.RUNNING));
        when(runs.fail(startedFailing.id(), "falha controlada"))
                .thenReturn(response(failing.metadata(), CollectionStatus.FAILED));
        when(ingestion.ingest(any(), any())).thenReturn(new CollectionResult(
                UUID.randomUUID(), UUID.randomUUID(), 0, 0, 0, 0, 0, null));
        when(runs.finish(any(), any())).thenReturn(response(first.metadata(), CollectionStatus.SUCCESS));

        List<CollectionRunResponse> results = new CollectionCoordinator(List.of(failing, first, second), ingestion,
                runs, mock(CollectedCatalogArchive.class), Duration.ZERO, 3).collectAll();

        assertThat(results).extracting(CollectionRunResponse::status)
                .containsExactly(CollectionStatus.FAILED, CollectionStatus.SUCCESS, CollectionStatus.SUCCESS);
        assertThat(first.collected).isTrue();
        assertThat(second.collected).isTrue();
    }

    @Test
    void seedLoadsOnlyMarketsWithABundledSnapshotWithoutDownloadingOrArchiving() {
        TestCollector seeded = new TestCollector("seeded", false);
        TestCollector withoutSeed = new TestCollector("without_seed", false);
        CollectedCatalogArchive archive = mock(CollectedCatalogArchive.class);
        when(archive.hasSeed(seeded.metadata())).thenReturn(true);
        when(archive.readSeed(seeded.metadata())).thenReturn(seeded.catalog());
        when(runs.start(seeded.metadata())).thenReturn(response(seeded.metadata(), CollectionStatus.RUNNING));
        when(ingestion.ingest(any(), any())).thenReturn(new CollectionResult(
                UUID.randomUUID(), UUID.randomUUID(), 0, 0, 0, 0, 0, null));
        when(runs.finish(any(), any())).thenReturn(response(seeded.metadata(), CollectionStatus.SUCCESS));
        CollectionCompletedListener listener = mock(CollectionCompletedListener.class);
        var coordinator = new CollectionCoordinator(List.of(seeded, withoutSeed), ingestion, runs, archive, Duration.ZERO, 3);
        coordinator.setListeners(List.of(listener));

        assertThat(coordinator.seedFromBundledSnapshots()).extracting(CollectionRunResponse::status)
                .containsExactly(CollectionStatus.SUCCESS);
        assertThat(seeded.collected).isFalse();
        assertThat(withoutSeed.collected).isFalse();
        verify(ingestion).ingest(seeded.metadata(), seeded.catalog());
        verify(archive, never()).save(any(), any());
        verify(listener).collectionCompleted();
    }

    private CollectionRunResponse response(CollectorMetadata metadata, CollectionStatus status) {
        Instant now = Instant.parse("2026-09-17T12:00:00Z");
        return new CollectionRunResponse(UUID.randomUUID(), metadata.code(), metadata.supermarketName(),
                metadata.storeName(), null, null, now,
                status == CollectionStatus.RUNNING ? null : now, status,
                0, 0, 0, 0, status == CollectionStatus.FAILED ? 1 : 0,
                status == CollectionStatus.FAILED ? "falha controlada" : null);
    }

    private static final class TestCollector implements SupermarketCollector {

        private final CollectorMetadata metadata;
        private final boolean fail;
        private final CollectedCatalog catalog;
        private volatile boolean collected;

        private TestCollector(String code, boolean fail) {
            this.fail = fail;
            metadata = new CollectorMetadata(code, code, code, code, code,
                    "https://example.test", Instant.EPOCH, code, code + ":chain",
                    code + ":store", UUID.randomUUID());
            catalog = new CollectedCatalog(new CollectedStore(code, null,
                    BigDecimal.ZERO, BigDecimal.ZERO, true), List.of(), 0, Instant.EPOCH, List.of());
        }

        @Override
        public CollectorMetadata metadata() {
            return metadata;
        }

        @Override
        public CollectedCatalog collect() {
            collected = true;
            if (fail) {
                throw new IllegalStateException("falha controlada");
            }
            return catalog;
        }

        private CollectedCatalog catalog() {
            return catalog;
        }
    }
}

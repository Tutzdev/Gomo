package br.com.supermercados.prices.collection;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

class CollectionSchedulerTest {

    private final CollectionCoordinator coordinator = mock(CollectionCoordinator.class);

    @Test
    void scheduledRefreshPreservesTheExistingCatalog() {
        new CollectionScheduler(coordinator, true, true).collectDaily();

        verify(coordinator).refreshExisting(null);
        verify(coordinator, never()).collectAll();
    }

    @Test
    void disabledScheduleDoesNotContactSources() {
        new CollectionScheduler(coordinator, false, true).collectDaily();

        verifyNoInteractions(coordinator);
    }

    @Test
    void fullImportRequiresAnExplicitConfigurationChange() {
        new CollectionScheduler(coordinator, true, false).collectDaily();

        verify(coordinator).collectAll();
        verify(coordinator, never()).refreshExisting(null);
    }

    @Test
    void weeklyImportBringsInProductsTheMarketsLaunched() {
        new CollectionScheduler(coordinator, true, true).importNewProducts();

        verify(coordinator).collectAll();
        verify(coordinator, never()).refreshExisting(null);
    }

    @Test
    void aMarketThatFailedGetsOneMoreTryAfterTheRound() {
        when(coordinator.refreshExisting(null)).thenReturn(List.of(run("pame_santo_agostinho", CollectionStatus.FAILED),
                run("nagumo_ponte_alta", CollectionStatus.SUCCESS)));
        CollectionScheduler scheduler = new CollectionScheduler(coordinator, true, true);

        scheduler.collectDaily();
        scheduler.retryFailed();
        scheduler.retryFailed();

        verify(coordinator, times(1)).refreshExisting("pame_santo_agostinho");
        verify(coordinator, never()).refreshExisting("nagumo_ponte_alta");
    }

    @Test
    void noRetryWhenEveryMarketWasCollected() {
        when(coordinator.refreshExisting(null)).thenReturn(List.of(run("nagumo_ponte_alta", CollectionStatus.SUCCESS)));
        CollectionScheduler scheduler = new CollectionScheduler(coordinator, true, true);

        scheduler.collectDaily();
        scheduler.retryFailed();

        verify(coordinator, times(1)).refreshExisting(null);
        verify(coordinator, never()).refreshExisting("nagumo_ponte_alta");
    }

    @Test
    void weeklyImportRespectsTheDisabledSchedule() {
        new CollectionScheduler(coordinator, false, true).importNewProducts();

        verifyNoInteractions(coordinator);
    }

    private static CollectionRunResponse run(String code, CollectionStatus status) {
        return new CollectionRunResponse(null, code, null, null, null, null, Instant.EPOCH, Instant.EPOCH, status,
                0, 0, 0, 0, 0, null, 0, 0, 0, 0, null);
    }
}

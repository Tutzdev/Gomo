package br.com.supermercados.prices.collection;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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
}

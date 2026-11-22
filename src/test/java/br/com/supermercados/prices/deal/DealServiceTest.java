package br.com.supermercados.prices.deal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.supermercados.prices.catalog.CatalogItemRepository;
import br.com.supermercados.prices.location.LocationService;
import br.com.supermercados.prices.shoppinglist.ShoppingListService;
import br.com.supermercados.prices.store.StoreResponse;
import br.com.supermercados.prices.store.StoreService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class DealServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");

    @Mock private DealFinder finder;
    @Mock private StoreService stores;
    @Mock private LocationService locations;
    @Mock private CatalogItemRepository items;
    @Mock private ShoppingListService lists;

    private final UUID cityId = UUID.randomUUID();
    private final MovingClock clock = new MovingClock(NOW);
    /** Background refreshes, run by the test when it chooses. */
    private final List<Runnable> background = new ArrayList<>();
    private DealService service;

    @BeforeEach
    void setUp() {
        service = new DealService(finder, stores, locations, items, lists, clock, Duration.ofMinutes(30), true,
                background::add);
        UUID storeId = UUID.randomUUID();
        when(stores.selectComparisonStores(cityId, null, 500)).thenReturn(List.of(new StoreResponse(storeId, null,
                cityId, "Synthetic store", null, null, null, true, UUID.randomUUID(), "synthetic", NOW, NOW, null)));
        when(finder.currentPrices(any(), any())).thenReturn(List.of());
        when(finder.history(any(), any())).thenReturn(Map.of());
    }

    @Test
    void cityDealsAreComputedOncePerHalfHourWhateverTheFilters() {
        service.find(null, cityId, null, null, null, false, PageRequest.of(0, 24));
        clock.advance(Duration.ofMinutes(29));
        service.find(null, cityId, UUID.randomUUID(), "Mercearia", null, false, PageRequest.of(1, 24));

        verify(finder, times(1)).currentPrices(any(), any());
        assertThat(background).isEmpty();
    }

    @Test
    void anExpiredPageIsServedAtOnceWhileOneRefreshRunsInTheBackground() {
        service.find(null, cityId, null, null, null, false, PageRequest.of(0, 24));
        clock.advance(Duration.ofMinutes(31));

        var stale = service.find(null, cityId, null, null, null, false, PageRequest.of(0, 24));
        service.find(null, cityId, null, "Mercearia", null, false, PageRequest.of(0, 24));

        assertThat(stale.computedAt()).isEqualTo(NOW);
        assertThat(background).hasSize(1);
        verify(finder, times(1)).currentPrices(any(), any());

        background.removeFirst().run();
        var fresh = service.find(null, cityId, null, null, null, false, PageRequest.of(0, 24));

        assertThat(fresh.computedAt()).isEqualTo(NOW.plus(Duration.ofMinutes(31)));
        verify(finder, times(2)).currentPrices(any(), any());
    }

    @Test
    void aPageOfAnyAgeIsServedAtOnceSoNoVisitorWaitsForTheCalculation() {
        service.find(null, cityId, null, null, null, false, PageRequest.of(0, 24));
        clock.advance(Duration.ofHours(12));

        var response = service.find(null, cityId, null, null, null, false, PageRequest.of(0, 24));

        assertThat(response.computedAt()).isEqualTo(NOW);
        assertThat(background).hasSize(1);
        verify(finder, times(1)).currentPrices(any(), any());
    }

    @Test
    void thePriceHistoryIsReadOncePerDayWhileCurrentPricesFollowEveryRefresh() {
        service.find(null, cityId, null, null, null, false, PageRequest.of(0, 24));
        clock.advance(Duration.ofHours(1));
        service.find(null, cityId, null, null, null, false, PageRequest.of(0, 24));
        background.removeFirst().run();

        verify(finder, times(2)).currentPrices(any(), any());
        verify(finder, times(1)).history(any(), any());

        clock.advance(Duration.ofDays(1));
        service.find(null, cityId, null, null, null, false, PageRequest.of(0, 24));
        background.removeFirst().run();

        verify(finder, times(2)).history(any(), any());
    }

    @Test
    void startupAndEveryCollectionComputeTheCitiesWithStoresAhead() {
        when(finder.citiesWithStores()).thenReturn(List.of(cityId));

        service.onStart();
        background.removeFirst().run();
        service.find(null, cityId, null, null, null, false, PageRequest.of(0, 24));
        verify(finder, times(1)).currentPrices(any(), any());

        clock.advance(Duration.ofMinutes(10));
        service.collectionCompleted();
        var response = service.find(null, cityId, null, null, null, false, PageRequest.of(0, 24));

        assertThat(response.computedAt()).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
        verify(finder, times(2)).currentPrices(any(), any());
    }

    /** A clock the test moves forward. */
    private static final class MovingClock extends Clock {

        private Instant now;

        private MovingClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}

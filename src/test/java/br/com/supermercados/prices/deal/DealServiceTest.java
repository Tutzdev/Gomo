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
import java.util.List;
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
    private DealService service;

    @BeforeEach
    void setUp() {
        service = new DealService(finder, stores, locations, items, lists, clock, Duration.ofMinutes(30));
        UUID storeId = UUID.randomUUID();
        when(stores.selectComparisonStores(cityId, null, 500)).thenReturn(List.of(new StoreResponse(storeId, null,
                cityId, "Synthetic store", null, null, null, true, UUID.randomUUID(), "synthetic", NOW, NOW, null)));
        when(finder.find(any(), any())).thenReturn(List.of());
    }

    @Test
    void cityDealsAreComputedOncePerHalfHourWhateverTheFilters() {
        service.find(null, cityId, null, null, false, PageRequest.of(0, 24));
        clock.advance(Duration.ofMinutes(29));
        service.find(null, cityId, UUID.randomUUID(), "Mercearia", false, PageRequest.of(1, 24));
        verify(finder, times(1)).find(any(), any());

        clock.advance(Duration.ofMinutes(2));
        var response = service.find(null, cityId, null, null, false, PageRequest.of(0, 24));

        verify(finder, times(2)).find(any(), any());
        assertThat(response.computedAt()).isEqualTo(NOW.plus(Duration.ofMinutes(31)));
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

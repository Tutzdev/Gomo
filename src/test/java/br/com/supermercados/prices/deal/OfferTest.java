package br.com.supermercados.prices.deal;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.supermercados.prices.deal.DealFinder.CurrentPrice;
import br.com.supermercados.prices.deal.DealFinder.ItemHistory;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class OfferTest {

    private static final Instant NOW = Instant.parse("2026-11-22T12:00:00Z");

    private final UUID store = UUID.randomUUID();

    @Test
    void onlyPricesAtLeastTenPercentUnderTheUsualPriceAreOffers() {
        UUID deal = UUID.randomUUID();
        UUID noise = UUID.randomUUID();
        UUID withoutHistory = UUID.randomUUID();

        var offers = Offer.match(
            List.of(price(deal, "9.00"), price(noise, "9.50"), price(withoutHistory, "1.00")),
            Map.of(deal, history("10.00", "9.00"), noise, history("10.00", "9.00")));

        assertThat(offers).singleElement().satisfies(offer -> {
            assertThat(offer.itemId()).isEqualTo(deal);
            assertThat(offer.discountPercent()).isEqualTo(10);
            assertThat(offer.lowestIn90Days()).isTrue();
        });
    }

    @Test
    void theBiggestDiscountComesFirst() {
        UUID small = UUID.randomUUID();
        UUID big = UUID.randomUUID();

        var offers = Offer.match(
            List.of(price(small, "8.00"), price(big, "5.00")),
            Map.of(small, history("10.00", "7.00"), big, history("10.00", "7.00")));

        assertThat(offers).extracting(Offer::itemId).containsExactly(big, small);
        assertThat(offers.getLast().lowestIn90Days()).isFalse();
    }

    @Test
    void theListStopsAtTheMaximumNumberOfOffers() {
        List<UUID> items = IntStream.range(0, Offer.MAXIMUM_OFFERS + 5).mapToObj(index -> UUID.randomUUID()).toList();

        var offers = Offer.match(
            items.stream().map(item -> price(item, "5.00")).toList(),
            items.stream().collect(Collectors.toMap(item -> item, item -> history("10.00", "5.00"))));

        assertThat(offers).hasSize(Offer.MAXIMUM_OFFERS);
    }

    private CurrentPrice price(UUID item, String amount) {
        return new CurrentPrice(item, store, new BigDecimal(amount), false, NOW);
    }

    private static ItemHistory history(String usual, String lowest) {
        return new ItemHistory(new BigDecimal(usual), new BigDecimal(lowest));
    }
}

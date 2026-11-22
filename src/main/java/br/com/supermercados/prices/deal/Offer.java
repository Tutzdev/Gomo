package br.com.supermercados.prices.deal;

import br.com.supermercados.prices.deal.DealFinder.CurrentPrice;
import br.com.supermercados.prices.deal.DealFinder.ItemHistory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

record Offer(
        UUID itemId,
        UUID storeId,
        BigDecimal price,
        boolean promotion,
        Instant collectedAt,
        BigDecimal usualPrice,
        BigDecimal lowestBefore) {

    static final int MINIMUM_DISCOUNT_PERCENT = 10;
    static final int MAXIMUM_OFFERS = 600;

    private static final BigDecimal MAXIMUM_SHARE = BigDecimal.valueOf(100 - MINIMUM_DISCOUNT_PERCENT).movePointLeft(2);
    private static final int SHARE_SCALE = 6;

    private static final Comparator<Offer> BEST_FIRST = Comparator.comparing(Offer::share)
            .thenComparing(Offer::itemId)
            .thenComparing(Offer::storeId);

    static List<Offer> match(List<CurrentPrice> prices, Map<UUID, ItemHistory> history) {
        return prices.stream()
                .filter(price -> history.containsKey(price.itemId()))
                .map(price -> of(price, history.get(price.itemId())))
                .filter(Offer::isUnderUsualPrice)
                .sorted(BEST_FIRST)
                .limit(MAXIMUM_OFFERS)
                .toList();
    }

    int discountPercent() {
        return usualPrice.subtract(price)
                .movePointRight(2)
                .divide(usualPrice, 0, RoundingMode.HALF_UP)
                .intValue();
    }

    boolean lowestIn90Days() {
        return price.compareTo(lowestBefore) <= 0;
    }

    private static Offer of(CurrentPrice price, ItemHistory history) {
        return new Offer(
            price.itemId(),
            price.storeId(),
            price.price(),
            price.promotion(),
            price.collectedAt(),
            history.usualPrice(),
            history.lowestBefore());
    }

    private boolean isUnderUsualPrice() {
        return price.compareTo(usualPrice.multiply(MAXIMUM_SHARE)) <= 0;
    }

    private BigDecimal share() {
        return price.divide(usualPrice, SHARE_SCALE, RoundingMode.HALF_UP);
    }
}

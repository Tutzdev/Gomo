package br.com.supermercados.prices.catalog;

import br.com.supermercados.prices.price.UsualPrice;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Whether today's price is a good one. "Today's price" is the cheapest current offer, the same one the page lists
 * first; it is read against the {@link UsualPrice} (the same reference as the alerts below the average) and against
 * the lowest price of the previous days of the history. A chart answers this too, but only for people who like
 * charts; the verdict says it in one line.
 *
 * @param averagePrice the usual price
 * @param differencePercent how far the current price is from the usual price, rounded; negative is cheaper
 * @param days days with a price behind the usual price
 * @param periodDays calendar days from the first price of the history to today, for "the lowest in N days"
 */
public record PriceVerdict(
        Kind kind,
        BigDecimal currentPrice,
        BigDecimal averagePrice,
        BigDecimal lowestPrice,
        LocalDate lowestDay,
        Integer differencePercent,
        int days,
        int periodDays) {

    public enum Kind {
        /**
         * Cheaper than every previous day of the history, or tied with the cheapest one while still under the
         * usual price: a price that never moves is the usual price, not the lowest.
         */
        LOWEST_IN_PERIOD,
        /** At least 5% under the usual price. */
        BELOW_AVERAGE,
        /** Within 5% of the usual price. */
        USUAL,
        /** At least 5% over the usual price: worth waiting. */
        ABOVE_AVERAGE,
        /** No current price, or too few days for a usual price: nothing honest to say. */
        NOT_ENOUGH_DATA
    }

    private static final BigDecimal THRESHOLD = new BigDecimal("0.05");

    /**
     * @param stores the history of each store; points of {@code today} itself are not part of the reference
     * @param currentPrice the cheapest current offer, or {@code null} when no store has one
     */
    public static PriceVerdict of(List<CatalogItemHistoryResponse.StoreSeries> stores, BigDecimal currentPrice,
            LocalDate today) {
        NavigableMap<LocalDate, BigDecimal> previous = new TreeMap<>();
        for (var store : stores) {
            for (var point : store.points()) {
                if (point.day().isBefore(today)) {
                    previous.merge(point.day(), point.price(), BigDecimal::min);
                }
            }
        }
        var usual = UsualPrice.of(previous, today);
        if (currentPrice == null || usual.isEmpty()) {
            return new PriceVerdict(Kind.NOT_ENOUGH_DATA, currentPrice, null, null, null, null,
                    usual.map(UsualPrice::days).orElse(0), 0);
        }

        Map.Entry<LocalDate, BigDecimal> lowest = previous.entrySet().stream()
                // The most recent day wins a tie: "it was this price on <day>" should point to the latest time.
                .min(Comparator.comparing(Map.Entry<LocalDate, BigDecimal>::getValue)
                        .thenComparing(Map.Entry::getKey, Comparator.reverseOrder()))
                .orElseThrow();
        BigDecimal difference = usual.get().differenceOf(currentPrice);
        int differencePercent = difference.movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValue();

        boolean belowAverage = difference.compareTo(THRESHOLD.negate()) <= 0;
        int againstLowest = currentPrice.compareTo(lowest.getValue());
        Kind kind = againstLowest < 0 || againstLowest == 0 && belowAverage ? Kind.LOWEST_IN_PERIOD
                : belowAverage ? Kind.BELOW_AVERAGE
                : difference.compareTo(THRESHOLD) >= 0 ? Kind.ABOVE_AVERAGE
                : Kind.USUAL;
        int periodDays = (int) ChronoUnit.DAYS.between(previous.firstKey(), today);
        return new PriceVerdict(kind, currentPrice, usual.get().average(), lowest.getValue(), lowest.getKey(),
                differencePercent, usual.get().days(), periodDays);
    }
}

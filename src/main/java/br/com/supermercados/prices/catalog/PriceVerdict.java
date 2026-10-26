package br.com.supermercados.prices.catalog;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Whether today's price is a good one, read from the price history: the city's lowest price on the latest day,
 * against the average and the lowest of the previous days. A chart answers this too, but only for people who
 * like charts; the verdict says it in one line.
 *
 * @param differencePercent how far the current price is from the average, rounded; negative is cheaper
 * @param days days with a price before the latest one, the base of the average
 */
public record PriceVerdict(
        Kind kind,
        BigDecimal currentPrice,
        BigDecimal averagePrice,
        BigDecimal lowestPrice,
        LocalDate lowestDay,
        Integer differencePercent,
        int days) {

    public enum Kind {
        /** As cheap as or cheaper than every previous day of the window. */
        LOWEST_IN_PERIOD,
        /** At least 5% under the average. */
        BELOW_AVERAGE,
        /** Within 5% of the average. */
        USUAL,
        /** At least 5% over the average: worth waiting. */
        ABOVE_AVERAGE,
        /** Too few days, or no price in the last days, to say anything honest. */
        NOT_ENOUGH_DATA
    }

    /** Fewer previous days than this and an "average" says nothing about the usual price. */
    static final int MINIMUM_DAYS = 7;
    /** A latest price older than this is not "today's price" anymore. */
    static final int MAXIMUM_AGE_DAYS = 2;
    private static final BigDecimal THRESHOLD = new BigDecimal("0.05");

    static PriceVerdict of(List<CatalogItemHistoryResponse.StoreSeries> stores, LocalDate today) {
        TreeMap<LocalDate, BigDecimal> lowestByDay = new TreeMap<>();
        for (var store : stores) {
            for (var point : store.points()) {
                lowestByDay.merge(point.day(), point.price(), BigDecimal::min);
            }
        }
        if (lowestByDay.isEmpty() || lowestByDay.lastKey().isBefore(today.minusDays(MAXIMUM_AGE_DAYS))) {
            return notEnoughData(null, lowestByDay.size());
        }

        BigDecimal current = lowestByDay.lastEntry().getValue();
        var previous = lowestByDay.headMap(lowestByDay.lastKey(), false);
        if (previous.size() < MINIMUM_DAYS) {
            return notEnoughData(current, previous.size());
        }
        BigDecimal average = previous.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(previous.size()), 2, RoundingMode.HALF_UP);
        Map.Entry<LocalDate, BigDecimal> lowest = previous.entrySet().stream()
                // The most recent day wins a tie: "it was this price on <day>" should point to the latest time.
                .min(Comparator.comparing(Map.Entry<LocalDate, BigDecimal>::getValue)
                        .thenComparing(Map.Entry::getKey, Comparator.reverseOrder()))
                .orElseThrow();
        BigDecimal difference = current.subtract(average).divide(average, 4, RoundingMode.HALF_UP);
        int differencePercent = difference.movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValue();

        Kind kind = current.compareTo(lowest.getValue()) <= 0 ? Kind.LOWEST_IN_PERIOD
                : difference.compareTo(THRESHOLD.negate()) <= 0 ? Kind.BELOW_AVERAGE
                : difference.compareTo(THRESHOLD) >= 0 ? Kind.ABOVE_AVERAGE
                : Kind.USUAL;
        return new PriceVerdict(kind, current, average, lowest.getValue(), lowest.getKey(), differencePercent,
                previous.size());
    }

    private static PriceVerdict notEnoughData(BigDecimal current, int days) {
        return new PriceVerdict(Kind.NOT_ENOUGH_DATA, current, null, null, null, null, days);
    }
}

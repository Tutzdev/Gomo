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
        LOWEST_IN_PERIOD,
        BELOW_AVERAGE,
        USUAL,
        ABOVE_AVERAGE,
        NOT_ENOUGH_DATA
    }

    private static final BigDecimal THRESHOLD = new BigDecimal("0.05");

    public static PriceVerdict of(
        List<CatalogItemHistoryResponse.StoreSeries> stores,
        BigDecimal currentPrice,
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
            return new PriceVerdict(
                Kind.NOT_ENOUGH_DATA,
                currentPrice,
                null,
                null,
                null,
                null,
                usual.map(UsualPrice::days).orElse(0),
                0);
        }

        Map.Entry<LocalDate, BigDecimal> lowest = previous.entrySet().stream()
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

        return new PriceVerdict(
            kind,
            currentPrice,
            usual.get().average(),
            lowest.getValue(),
            lowest.getKey(),
            differencePercent,
            usual.get().days(),
            periodDays);
    }
}

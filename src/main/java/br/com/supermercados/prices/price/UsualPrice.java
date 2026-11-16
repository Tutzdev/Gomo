package br.com.supermercados.prices.price;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.NavigableMap;
import java.util.Optional;

public record UsualPrice(BigDecimal average, int days) {

    public static final int WINDOW_DAYS = 30;
    public static final int MINIMUM_DAYS = 7;
    public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    public static LocalDate today(Clock clock) {
        return LocalDate.ofInstant(clock.instant(), ZONE);
    }

    public static LocalDate windowStart(LocalDate today) {
        return today.minusDays(WINDOW_DAYS);
    }

    public static Optional<UsualPrice> of(NavigableMap<LocalDate, BigDecimal> lowestByDay, LocalDate today) {
        var window = lowestByDay.subMap(windowStart(today), true, today, false);

        if (window.size() < MINIMUM_DAYS) {return Optional.empty();}

        BigDecimal average = window.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(window.size()), 2, RoundingMode.HALF_UP);

        return Optional.of(new UsualPrice(average, window.size()));
    }

    public BigDecimal differenceOf(BigDecimal price) {
        return price.subtract(average).divide(average, 4, RoundingMode.HALF_UP);
    }
}

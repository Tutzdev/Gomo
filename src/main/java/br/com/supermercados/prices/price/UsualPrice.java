package br.com.supermercados.prices.price;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.NavigableMap;
import java.util.Optional;

/**
 * The one definition of "the usual price" in Gomo: the average of the lowest price of each day over the
 * {@value #WINDOW_DAYS} days before today, among the stores being looked at (the whole city by default). Today is
 * left out, so a promotion that just started does not lower its own reference, and fewer than
 * {@value #MINIMUM_DAYS} days with a price give no average at all: two days say nothing about the usual price.
 *
 * <p>The alerts below the average, the price verdict, the deals page and the stock-up hints all read it, so the
 * screen never says "12% below the average" while a 10% alert stays quiet.
 *
 * @param average rounded to cents
 * @param days days with a price in the window, the base of the average
 */
public record UsualPrice(BigDecimal average, int days) {

    public static final int WINDOW_DAYS = 30;
    public static final int MINIMUM_DAYS = 7;
    /** The shoppers' calendar: the day a price belongs to, the same one {@code daily_lowest_prices} uses. */
    public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    public static LocalDate today(Clock clock) {
        return LocalDate.ofInstant(clock.instant(), ZONE);
    }

    /** First day of the window that ends (exclusive) at {@code today}. */
    public static LocalDate windowStart(LocalDate today) {
        return today.minusDays(WINDOW_DAYS);
    }

    /** The usual price from the lowest price of each day; empty while there are too few days to say. */
    public static Optional<UsualPrice> of(NavigableMap<LocalDate, BigDecimal> lowestByDay, LocalDate today) {
        var window = lowestByDay.subMap(windowStart(today), true, today, false);
        if (window.size() < MINIMUM_DAYS) {
            return Optional.empty();
        }
        BigDecimal average = window.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(window.size()), 2, RoundingMode.HALF_UP);
        return Optional.of(new UsualPrice(average, window.size()));
    }

    /** How far {@code price} is from the usual price, as a fraction rounded to 4 places; negative is cheaper. */
    public BigDecimal differenceOf(BigDecimal price) {
        return price.subtract(average).divide(average, 4, RoundingMode.HALF_UP);
    }
}

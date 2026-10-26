package br.com.supermercados.prices.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.supermercados.prices.catalog.CatalogItemHistoryResponse.PricePoint;
import br.com.supermercados.prices.catalog.CatalogItemHistoryResponse.StoreSeries;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PriceVerdictTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-10-20");

    @Test
    void cheapestPriceOfTheWindowIsTheLowestInThePeriodEvenWhenOtherStoresChargeMore() {
        var steady = series("10.00", "10.00", "9.80", "10.00", "10.00", "10.00", "10.00", "10.00");
        var cheaperToday = new StoreSeries(UUID.randomUUID(), "Cheap", List.of(new PricePoint(TODAY, new BigDecimal("9.50"))));

        PriceVerdict verdict = PriceVerdict.of(List.of(steady, cheaperToday), TODAY);

        assertThat(verdict.kind()).isEqualTo(PriceVerdict.Kind.LOWEST_IN_PERIOD);
        assertThat(verdict.currentPrice()).isEqualByComparingTo("9.50");
        assertThat(verdict.averagePrice()).isEqualByComparingTo("9.97");
        assertThat(verdict.lowestPrice()).isEqualByComparingTo("9.80");
        assertThat(verdict.lowestDay()).isEqualTo(TODAY.minusDays(5));
        assertThat(verdict.days()).isEqualTo(7);
    }

    @Test
    void priceFivePercentUnderTheAverageIsBelowAverageButNotTheLowest() {
        PriceVerdict verdict = PriceVerdict.of(List.of(
                series("8.00", "10.00", "10.00", "10.00", "10.00", "10.00", "10.00", "9.00")), TODAY);

        assertThat(verdict.kind()).isEqualTo(PriceVerdict.Kind.BELOW_AVERAGE);
        assertThat(verdict.differencePercent()).isEqualTo(-7);
        assertThat(verdict.lowestDay()).isEqualTo(TODAY.minusDays(7));
    }

    @Test
    void priceNearTheAverageIsUsualAndAboveItIsWorthWaiting() {
        var days = List.of("10.00", "10.00", "10.00", "10.00", "10.00", "10.00", "10.00");

        PriceVerdict usual = PriceVerdict.of(List.of(series(with(days, "10.20"))), TODAY);
        PriceVerdict above = PriceVerdict.of(List.of(series(with(days, "11.00"))), TODAY);

        assertThat(usual.kind()).isEqualTo(PriceVerdict.Kind.USUAL);
        assertThat(usual.differencePercent()).isEqualTo(2);
        assertThat(above.kind()).isEqualTo(PriceVerdict.Kind.ABOVE_AVERAGE);
        assertThat(above.differencePercent()).isEqualTo(10);
    }

    @Test
    void priceThatNeverMovesIsTheUsualPriceNotTheLowest() {
        PriceVerdict verdict = PriceVerdict.of(List.of(
                series("10.00", "10.00", "10.00", "10.00", "10.00", "10.00", "10.00", "10.00")), TODAY);

        assertThat(verdict.kind()).isEqualTo(PriceVerdict.Kind.USUAL);
        assertThat(verdict.differencePercent()).isZero();
    }

    @Test
    void promotionThatKeepsGoingStaysTheLowestWhileItIsUnderTheAverage() {
        PriceVerdict verdict = PriceVerdict.of(List.of(
                series("10.00", "10.00", "10.00", "10.00", "10.00", "8.00", "8.00", "8.00")), TODAY);

        assertThat(verdict.kind()).isEqualTo(PriceVerdict.Kind.LOWEST_IN_PERIOD);
        assertThat(verdict.lowestDay()).isEqualTo(TODAY.minusDays(1));
    }

    @Test
    void tooFewDaysOrAnOldLatestPriceGiveNoVerdict() {
        PriceVerdict fewDays = PriceVerdict.of(List.of(series("10.00", "9.00", "8.00")), TODAY);
        PriceVerdict stale = PriceVerdict.of(List.of(
                series("10.00", "10.00", "10.00", "10.00", "10.00", "10.00", "10.00", "5.00")), TODAY.plusDays(3));

        assertThat(fewDays.kind()).isEqualTo(PriceVerdict.Kind.NOT_ENOUGH_DATA);
        assertThat(fewDays.currentPrice()).isEqualByComparingTo("8.00");
        assertThat(fewDays.averagePrice()).isNull();
        assertThat(stale.kind()).isEqualTo(PriceVerdict.Kind.NOT_ENOUGH_DATA);
        assertThat(PriceVerdict.of(List.of(), TODAY).kind()).isEqualTo(PriceVerdict.Kind.NOT_ENOUGH_DATA);
    }

    /** One store with a price per day, the last one being today. */
    private static StoreSeries series(String... prices) {
        List<PricePoint> points = new ArrayList<>();
        for (int index = 0; index < prices.length; index++) {
            points.add(new PricePoint(TODAY.minusDays(prices.length - 1 - index), new BigDecimal(prices[index])));
        }
        return new StoreSeries(UUID.randomUUID(), "Store", points);
    }

    private static String[] with(List<String> days, String today) {
        List<String> all = new ArrayList<>(days);
        all.add(today);
        return all.toArray(String[]::new);
    }
}

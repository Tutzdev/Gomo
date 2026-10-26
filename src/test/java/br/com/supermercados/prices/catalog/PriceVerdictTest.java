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
    void cheapestCurrentOfferBelowEveryPreviousDayIsTheLowestInThePeriod() {
        var steady = history("10.00", "10.00", "9.80", "10.00", "10.00", "10.00", "10.00");
        var otherStore = new StoreSeries(UUID.randomUUID(), "Other", List.of(
                new PricePoint(TODAY.minusDays(1), new BigDecimal("10.50"))));

        PriceVerdict verdict = PriceVerdict.of(List.of(steady, otherStore), price("9.50"), TODAY);

        assertThat(verdict.kind()).isEqualTo(PriceVerdict.Kind.LOWEST_IN_PERIOD);
        assertThat(verdict.currentPrice()).isEqualByComparingTo("9.50");
        assertThat(verdict.averagePrice()).isEqualByComparingTo("9.97");
        assertThat(verdict.lowestPrice()).isEqualByComparingTo("9.80");
        assertThat(verdict.lowestDay()).isEqualTo(TODAY.minusDays(5));
        assertThat(verdict.days()).isEqualTo(7);
        assertThat(verdict.periodDays()).isEqualTo(7);
    }

    @Test
    void priceFivePercentUnderTheUsualPriceIsBelowAverageButNotTheLowest() {
        PriceVerdict verdict = PriceVerdict.of(List.of(
                history("8.00", "10.00", "10.00", "10.00", "10.00", "10.00", "10.00")), price("9.00"), TODAY);

        assertThat(verdict.kind()).isEqualTo(PriceVerdict.Kind.BELOW_AVERAGE);
        assertThat(verdict.differencePercent()).isEqualTo(-7);
        assertThat(verdict.lowestDay()).isEqualTo(TODAY.minusDays(7));
    }

    @Test
    void priceNearTheUsualPriceIsUsualAndAboveItIsWorthWaiting() {
        var days = history("10.00", "10.00", "10.00", "10.00", "10.00", "10.00", "10.00");

        PriceVerdict usual = PriceVerdict.of(List.of(days), price("10.20"), TODAY);
        PriceVerdict above = PriceVerdict.of(List.of(days), price("11.00"), TODAY);

        assertThat(usual.kind()).isEqualTo(PriceVerdict.Kind.USUAL);
        assertThat(usual.differencePercent()).isEqualTo(2);
        assertThat(above.kind()).isEqualTo(PriceVerdict.Kind.ABOVE_AVERAGE);
        assertThat(above.differencePercent()).isEqualTo(10);
    }

    @Test
    void priceThatNeverMovesIsTheUsualPriceNotTheLowest() {
        PriceVerdict verdict = PriceVerdict.of(List.of(
                history("10.00", "10.00", "10.00", "10.00", "10.00", "10.00", "10.00")), price("10.00"), TODAY);

        assertThat(verdict.kind()).isEqualTo(PriceVerdict.Kind.USUAL);
        assertThat(verdict.differencePercent()).isZero();
    }

    @Test
    void promotionThatKeepsGoingStaysTheLowestWhileItIsUnderTheUsualPrice() {
        PriceVerdict verdict = PriceVerdict.of(List.of(
                history("10.00", "10.00", "10.00", "10.00", "10.00", "8.00", "8.00")), price("8.00"), TODAY);

        assertThat(verdict.kind()).isEqualTo(PriceVerdict.Kind.LOWEST_IN_PERIOD);
        assertThat(verdict.lowestDay()).isEqualTo(TODAY.minusDays(1));
    }

    @Test
    void todaysPointsAndTheMonthBeforeTheWindowAreNotPartOfTheUsualPrice() {
        List<PricePoint> points = new ArrayList<>();
        points.add(new PricePoint(TODAY.minusDays(60), new BigDecimal("2.00")));
        for (int day = 1; day <= 7; day++) {
            points.add(new PricePoint(TODAY.minusDays(day), new BigDecimal("10.00")));
        }
        points.add(new PricePoint(TODAY, new BigDecimal("1.00")));

        PriceVerdict verdict = PriceVerdict.of(List.of(new StoreSeries(UUID.randomUUID(), "Store", points)),
                price("9.00"), TODAY);

        assertThat(verdict.averagePrice()).isEqualByComparingTo("10.00");
        assertThat(verdict.days()).isEqualTo(7);
        // The 90-day history still knows the old R$ 2,00.
        assertThat(verdict.lowestPrice()).isEqualByComparingTo("2.00");
        assertThat(verdict.periodDays()).isEqualTo(60);
        assertThat(verdict.kind()).isEqualTo(PriceVerdict.Kind.BELOW_AVERAGE);
    }

    @Test
    void noCurrentOfferOrTooFewDaysGiveNoVerdict() {
        PriceVerdict fewDays = PriceVerdict.of(List.of(history("10.00", "9.00", "8.00")), price("8.00"), TODAY);
        PriceVerdict noOffer = PriceVerdict.of(List.of(
                history("10.00", "10.00", "10.00", "10.00", "10.00", "10.00", "10.00")), null, TODAY);

        assertThat(fewDays.kind()).isEqualTo(PriceVerdict.Kind.NOT_ENOUGH_DATA);
        assertThat(fewDays.currentPrice()).isEqualByComparingTo("8.00");
        assertThat(fewDays.averagePrice()).isNull();
        assertThat(noOffer.kind()).isEqualTo(PriceVerdict.Kind.NOT_ENOUGH_DATA);
        assertThat(noOffer.days()).isEqualTo(7);
        assertThat(PriceVerdict.of(List.of(), null, TODAY).kind()).isEqualTo(PriceVerdict.Kind.NOT_ENOUGH_DATA);
    }

    /** One store with a price on each of the days before today, the last one yesterday. */
    private static StoreSeries history(String... prices) {
        List<PricePoint> points = new ArrayList<>();
        for (int index = 0; index < prices.length; index++) {
            points.add(new PricePoint(TODAY.minusDays(prices.length - index), new BigDecimal(prices[index])));
        }
        return new StoreSeries(UUID.randomUUID(), "Store", points);
    }

    private static BigDecimal price(String value) {
        return new BigDecimal(value);
    }
}

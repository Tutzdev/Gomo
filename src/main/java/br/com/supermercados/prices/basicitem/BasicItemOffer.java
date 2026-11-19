package br.com.supermercados.prices.basicitem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.UUID;

import br.com.supermercados.prices.price.PriceQuote;
import br.com.supermercados.prices.price.PriceRecord;

public record BasicItemOffer(
        UUID storeId,
        PriceRecord record,
        PriceQuote price,
        BigDecimal packageAmount,
        BigDecimal pricePerMeasure,
        boolean typicalPackage) {

    private static final Comparator<BasicItemOffer> CHEAPEST_FIRST = Comparator
            .comparing((BasicItemOffer offer) -> !offer.typicalPackage())
            .thenComparing(BasicItemOffer::pricePerMeasure, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(offer -> offer.price().unitPrice())
            .thenComparing(offer -> offer.record().getProductId());

    public static BasicItemOffer of(BasicItem item, PriceRecord record, PriceQuote price, BigDecimal packageAmount) {
        BigDecimal perMeasure = packageAmount == null ? null
                : price.unitPrice().divide(packageAmount, 2, RoundingMode.HALF_UP);

        return new BasicItemOffer(
            record.getStoreId(),
            record,
            price,
            packageAmount,
            perMeasure,
            item.typicalPackage(packageAmount));
    }

    public static Comparator<BasicItemOffer> cheapestFirst() {
        return CHEAPEST_FIRST;
    }

    public static BasicItemOffer cheaper(BasicItemOffer current, BasicItemOffer next) {
        return CHEAPEST_FIRST.compare(next, current) < 0 ? next : current;
    }

    public UUID productId() {
        return record.getProductId();
    }
}

package br.com.supermercados.prices.price;

import java.math.BigDecimal;
import java.time.Instant;

public record PriceQuote(
        PriceStatus status,
        StockAvailability availability,
        BigDecimal unitPrice,
        boolean promotionApplied,
        Instant expiresAt,
        PriceRecordResponse observation) {

    public static PriceQuote missing() {
        return new PriceQuote(
            PriceStatus.NO_OBSERVATION,
            StockAvailability.UNKNOWN,
            null,
            false,
            null,
            null);
    }
}

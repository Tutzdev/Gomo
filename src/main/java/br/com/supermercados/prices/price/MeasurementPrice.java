package br.com.supermercados.prices.price;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record MeasurementPrice(BigDecimal amount, String unit) {

    public static MeasurementPrice calculate(BigDecimal price, BigDecimal quantity, String unit) {
        if (price == null || quantity == null || quantity.signum() <= 0 || unit == null) {return null;}

        BigDecimal multiplier = switch (unit) {
            case "G", "ML" -> BigDecimal.valueOf(1000);
            case "KG", "L", "UN", "M" -> BigDecimal.ONE;
            default -> null;
        };

        if (multiplier == null) {return null;}

        String basis = switch (unit) {
            case "G" -> "KG";
            case "ML" -> "L";
            default -> unit;
        };

        return new MeasurementPrice(price.multiply(multiplier).divide(quantity, 4, RoundingMode.HALF_UP), basis);
    }
}

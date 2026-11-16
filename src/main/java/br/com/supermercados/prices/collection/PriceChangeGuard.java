package br.com.supermercados.prices.collection;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import br.com.supermercados.prices.price.PriceRecord;

@Component
public class PriceChangeGuard {

    private final BigDecimal minimumRatio;
    private final BigDecimal maximumRatio;

    public PriceChangeGuard(
        @Value("${app.collection.price.minimum-ratio:0.20}") BigDecimal minimumRatio,
        @Value("${app.collection.price.maximum-ratio:5.00}") BigDecimal maximumRatio) {

        if (minimumRatio.signum() <= 0 || maximumRatio.compareTo(BigDecimal.ONE) < 0
                || minimumRatio.compareTo(maximumRatio) >= 0) {
            throw new IllegalArgumentException("Limites de variação de preço inválidos");
        }

        this.minimumRatio = minimumRatio;
        this.maximumRatio = maximumRatio;
    }

    public boolean isSuspicious(BigDecimal currentPrice, PriceRecord previous) {
        if (previous == null) {return false;}

        BigDecimal previousPrice = previous.getRegularPrice();
        BigDecimal minimumAccepted = previousPrice.multiply(minimumRatio);
        BigDecimal maximumAccepted = previousPrice.multiply(maximumRatio);

        return currentPrice.compareTo(minimumAccepted) < 0 || currentPrice.compareTo(maximumAccepted) > 0;
    }
}

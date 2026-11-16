package br.com.supermercados.prices.price;

import br.com.supermercados.prices.common.ApiException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class PriceObservationRules {

    public void validate(
        BigDecimal regularPrice,
        BigDecimal promotionalPrice,
        Instant observedAt,
        Instant validUntil,
        Instant promotionValidUntil,
        Instant now) {

        validate(
            regularPrice,
            promotionalPrice,
            observedAt,
            validUntil,
            promotionValidUntil,
            null,
            now);
    }

    public void validate(
        BigDecimal regularPrice,
        BigDecimal promotionalPrice,
        Instant observedAt,
        Instant validUntil,
        Instant promotionValidUntil,
        String promotionCondition,
        Instant now) {

        if (observedAt.isAfter(now)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A coleta não pode ocorrer no futuro.");
        }

        if (promotionalPrice != null && promotionalPrice.compareTo(regularPrice) >= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "O preço promocional deve ser menor que o preço regular.");
        }

        if (promotionalPrice == null && promotionValidUntil != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A validade promocional exige um preço promocional.");
        }

        if (promotionalPrice == null && promotionCondition != null && !promotionCondition.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A condição promocional exige um preço promocional.");
        }

        requireValidPeriod(observedAt, validUntil);
        requireValidPeriod(observedAt, promotionValidUntil);
    }

    private void requireValidPeriod(Instant observedAt, Instant validUntil) {
        if (validUntil != null && !validUntil.truncatedTo(ChronoUnit.MICROS)
                .isAfter(observedAt.truncatedTo(ChronoUnit.MICROS))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A validade deve ser posterior à data de coleta.");
        }
    }
}

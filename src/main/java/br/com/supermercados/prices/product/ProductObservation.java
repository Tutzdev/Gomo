package br.com.supermercados.prices.product;

import java.math.BigDecimal;

import br.com.supermercados.prices.datasource.SourceObservation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ProductObservation(
        @Size(max = 14) String gtin,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 120) String brand,
        @Size(max = 2000) String description,
        @Size(max = 30) String unit,
        @Positive @Digits(integer = 10, fraction = 4) BigDecimal quantity,
        @Size(max = 120) String category,
        @NotNull @Valid SourceObservation source,
        @Size(max = 2048) String imageUrl,
        @Size(max = 2048) String originUrl) {

    public ProductObservation(
        String gtin,
        String name,
        String brand,
        String description,
        String unit,
        BigDecimal quantity,
        String category,
        SourceObservation source) {

        this(
            gtin,
            name,
            brand,
            description,
            unit,
            quantity,
            category,
            source,
            null,
            null);
    }
}

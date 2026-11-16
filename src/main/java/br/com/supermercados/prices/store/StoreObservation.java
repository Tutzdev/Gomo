package br.com.supermercados.prices.store;

import java.math.BigDecimal;
import java.util.UUID;

import br.com.supermercados.prices.datasource.SourceObservation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StoreObservation(
        @NotNull UUID supermarketChainId,
        @NotNull UUID cityId,
        @NotBlank @Size(max = 160) String name,
        @Size(max = 500) String address,
        @DecimalMin("-90") @DecimalMax("90") @Digits(integer = 3, fraction = 7) BigDecimal latitude,
        @DecimalMin("-180") @DecimalMax("180") @Digits(integer = 3, fraction = 7) BigDecimal longitude,
        boolean active,
        @NotNull @Valid SourceObservation source) {}

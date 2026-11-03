package br.com.supermercados.prices.suggestion;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/** Store and price are optional: a shopper may only know the product is missing. */
public record SubmitProductSuggestionRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 120) String brand,
        @Size(max = 80) String packageSize,
        @Pattern(regexp = "^$|^[0-9]{8,14}$", message = "O código de barras tem de 8 a 14 dígitos.") String barcode,
        UUID storeId,
        @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal price,
        @Size(max = 500) String note) {
}

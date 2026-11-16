package br.com.supermercados.prices.shoppinglist;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record UpdateItemRequest(
        @NotNull @Positive @DecimalMax("999999") @Digits(integer = 6, fraction = 3) BigDecimal quantity) {}

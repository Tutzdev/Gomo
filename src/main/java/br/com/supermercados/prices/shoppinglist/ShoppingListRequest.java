package br.com.supermercados.prices.shoppinglist;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ShoppingListRequest(@NotBlank @Size(max = 120) String name, @NotNull ShoppingType shoppingType) {}

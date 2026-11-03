package br.com.supermercados.prices.suggestion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectProductSuggestionRequest(@NotBlank @Size(max = 500) String reason) {
}

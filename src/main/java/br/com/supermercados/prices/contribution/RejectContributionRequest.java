package br.com.supermercados.prices.contribution;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectContributionRequest(@NotBlank @Size(max = 500) String reason) {}

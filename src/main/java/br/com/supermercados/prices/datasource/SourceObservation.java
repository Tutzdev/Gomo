package br.com.supermercados.prices.datasource;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SourceObservation(
        @NotNull UUID sourceId,
        @NotBlank @Size(max = 2048) String sourceReference,
        @NotNull Instant collectedAt) {}

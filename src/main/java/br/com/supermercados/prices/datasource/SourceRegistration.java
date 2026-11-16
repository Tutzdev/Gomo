package br.com.supermercados.prices.datasource;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SourceRegistration(
        @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9_-]{0,79}") String code,
        @NotBlank @Size(max = 160) String name,
        @NotBlank @Size(max = 2048) String baseUrl,
        @NotNull Instant verifiedAt) {}

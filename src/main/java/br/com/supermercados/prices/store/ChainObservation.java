package br.com.supermercados.prices.store;

import br.com.supermercados.prices.datasource.SourceObservation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChainObservation(@NotBlank @Size(max = 160) String name, @NotNull @Valid SourceObservation source) {}

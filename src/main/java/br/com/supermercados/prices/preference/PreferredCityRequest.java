package br.com.supermercados.prices.preference;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record PreferredCityRequest(@NotNull UUID cityId) {}

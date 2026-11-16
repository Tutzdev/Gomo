package br.com.supermercados.prices.product;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ProductSearchFacets(
        List<String> brands,
        List<String> categories,
        List<Measurement> measurements,
        List<Market> markets) {

    public record Measurement(String unit, BigDecimal quantity) {}

    public record Market(UUID id, String name) {}
}

package br.com.supermercados.prices.collection;

import java.math.BigDecimal;

public record CollectedStore(
        String name,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        boolean active) {}

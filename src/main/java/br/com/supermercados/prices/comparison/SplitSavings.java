package br.com.supermercados.prices.comparison;

import java.math.BigDecimal;
import java.util.UUID;

public record SplitSavings(
        UUID storeId,
        String storeName,
        int comparedItems,
        BigDecimal savings) {}

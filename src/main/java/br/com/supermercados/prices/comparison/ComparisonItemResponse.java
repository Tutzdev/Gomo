package br.com.supermercados.prices.comparison;

import java.math.BigDecimal;
import java.util.UUID;

import br.com.supermercados.prices.price.PriceQuote;
import br.com.supermercados.prices.product.ProductResponse;

public record ComparisonItemResponse(
        UUID productId,
        String productName,
        BigDecimal quantity,
        PriceQuote price,
        BigDecimal lineTotal,
        ProductResponse matchedProduct,
        boolean hasPossibleMatches) {

    public ComparisonItemResponse(UUID productId, String productName, BigDecimal quantity,
            PriceQuote price, BigDecimal lineTotal) {
        this(productId, productName, quantity, price, lineTotal, null, false);
    }
}

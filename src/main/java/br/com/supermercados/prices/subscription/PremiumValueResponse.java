package br.com.supermercados.prices.subscription;

import java.math.BigDecimal;

public record PremiumValueResponse(String listName, BigDecimal savings, int storeCount) {

    public static PremiumValueResponse none() {
        return new PremiumValueResponse(null, null, 0);
    }
}

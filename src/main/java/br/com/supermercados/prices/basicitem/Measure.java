package br.com.supermercados.prices.basicitem;

import java.math.BigDecimal;

public enum Measure {

    KG("kg"),
    L("L"),
    UN("un");

    private final String label;

    Measure(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public String amountLabel(BigDecimal amount) {
        BigDecimal thousand = BigDecimal.valueOf(1000);

        if (this == KG && amount.compareTo(BigDecimal.ONE) < 0) {
            return amount.multiply(thousand).stripTrailingZeros().toPlainString() + " g";
        }

        if (this == L && amount.compareTo(BigDecimal.ONE) < 0) {
            return amount.multiply(thousand).stripTrailingZeros().toPlainString() + " ml";
        }

        return amount.stripTrailingZeros().toPlainString().replace('.', ',') + " " + label;
    }
}

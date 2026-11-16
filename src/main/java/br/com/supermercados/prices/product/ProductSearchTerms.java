package br.com.supermercados.prices.product;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Pattern;

record ProductSearchTerms(String text, BigDecimal quantity, String unit) {

    private static final Pattern MEASUREMENT = Pattern.compile(
            "(?i)(\\d+(?:[.,]\\d+)?)\\s*(quilogramas?|quilos?|kg|gramas?|g|mililitros?|ml|litros?|l)\\b");

    static ProductSearchTerms parse(String query) {
        if (query == null) {return new ProductSearchTerms("", null, null);}

        var matcher = MEASUREMENT.matcher(query);

        if (!matcher.find()) {return new ProductSearchTerms(query, null, null);}

        BigDecimal amount = new BigDecimal(matcher.group(1).replace(',', '.'));
        String measurement = matcher.group(2).toLowerCase(Locale.ROOT);
        String baseUnit = measurement.startsWith("l") || measurement.startsWith("m") ? "ML" : "G";
        boolean thousands = measurement.startsWith("l") || measurement.startsWith("k") || measurement.startsWith("q");

        if (thousands) {
            amount = amount.multiply(BigDecimal.valueOf(1000));
        }

        String remaining = matcher.replaceFirst(" ");

        if (MEASUREMENT.matcher(remaining).find()) {return new ProductSearchTerms(query, null, null);}

        return new ProductSearchTerms(remaining, amount, baseUnit);
    }
}

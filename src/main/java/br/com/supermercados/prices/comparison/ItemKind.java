package br.com.supermercados.prices.comparison;

import br.com.supermercados.prices.catalog.CatalogKey;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

record ItemKind(Set<String> words, BigDecimal amount, String unit) {

    static Optional<ItemKind> of(String catalogKey, String brand) {
        int separator = catalogKey == null ? -1 : catalogKey.indexOf('|');

        if (separator < 0 || brand == null || brand.isBlank()) {return Optional.empty();}

        Set<String> words = new HashSet<>(Arrays.asList(catalogKey.substring(0, separator).split(" ")));
        words.removeAll(Arrays.asList(CatalogKey.words(brand).split(" ")));
        words.remove("");

        return words.isEmpty() ? Optional.empty() : size(catalogKey.substring(separator + 1))
                .map(size -> new ItemKind(Set.copyOf(words), size.amount(), size.unit()));
    }

    private static Optional<ItemKind> size(String size) {
        try {
            int separator = size.indexOf('x');
            BigDecimal count = new BigDecimal(size.substring(0, separator));
            String measure = size.substring(separator + 1);

            if (measure.equals("UN")) {return Optional.of(new ItemKind(Set.of(), count, "UN"));}

            String unit = measure.endsWith("ML") ? "ML" : measure.endsWith("G") ? "G" : null;

            if (unit == null) {return Optional.empty();}

            BigDecimal amount = new BigDecimal(measure.substring(0, measure.length() - unit.length())).multiply(count);
            return amount.signum() > 0 ? Optional.of(new ItemKind(Set.of(), amount, unit)) : Optional.empty();
        } catch (RuntimeException unreadable) {
            return Optional.empty();
        }
    }

    boolean sameKindAs(ItemKind other) {
        return words.equals(other.words) && unit.equals(other.unit);
    }

    BigDecimal sizeAgainst(ItemKind other) {
        return amount.divide(other.amount, 4, RoundingMode.HALF_UP);
    }

    BigDecimal pricePerMeasure(BigDecimal price) {
        BigDecimal base = unit.equals("UN") ? amount : amount.movePointLeft(3);

        return price.divide(base, 4, RoundingMode.HALF_UP);
    }

    String measureUnit() {
        return switch (unit) {
            case "ML" -> "L";
            case "G" -> "KG";
            default -> "UN";
        };
    }
}

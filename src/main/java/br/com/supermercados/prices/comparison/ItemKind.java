package br.com.supermercados.prices.comparison;

import br.com.supermercados.prices.catalog.CatalogKey;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * What a generic item is without its brand, read from its catalog key ("ARROZ BRANCO TIO JOAO TIPO 1|1x5000G"
 * with brand "Tio João" is "ARROZ BRANCO TIPO 1", 5000 g). Two items of the same kind and aisle are substitutes:
 * the same rice from another brand, or another size of it.
 *
 * @param words the key words that are not the brand
 * @param amount the whole package in {@code unit}: millilitres, grams or units
 * @param unit {@code ML}, {@code G} or {@code UN}
 */
record ItemKind(Set<String> words, BigDecimal amount, String unit) {

    static Optional<ItemKind> of(String catalogKey, String brand) {
        int separator = catalogKey == null ? -1 : catalogKey.indexOf('|');
        if (separator < 0 || brand == null || brand.isBlank()) {
            return Optional.empty();
        }
        Set<String> words = new HashSet<>(Arrays.asList(catalogKey.substring(0, separator).split(" ")));
        words.removeAll(Arrays.asList(CatalogKey.words(brand).split(" ")));
        words.remove("");
        return words.isEmpty() ? Optional.empty() : size(catalogKey.substring(separator + 1))
                .map(size -> new ItemKind(Set.copyOf(words), size.amount(), size.unit()));
    }

    /** "1x2000ML" is 2000 ml; "6x350ML" 2100 ml; "30xUN" 30 units. */
    private static Optional<ItemKind> size(String size) {
        try {
            int separator = size.indexOf('x');
            BigDecimal count = new BigDecimal(size.substring(0, separator));
            String measure = size.substring(separator + 1);
            if (measure.equals("UN")) {
                return Optional.of(new ItemKind(Set.of(), count, "UN"));
            }
            String unit = measure.endsWith("ML") ? "ML" : measure.endsWith("G") ? "G" : null;
            if (unit == null) {
                return Optional.empty();
            }
            BigDecimal amount = new BigDecimal(measure.substring(0, measure.length() - unit.length())).multiply(count);
            return amount.signum() > 0 ? Optional.of(new ItemKind(Set.of(), amount, unit)) : Optional.empty();
        } catch (RuntimeException unreadable) {
            return Optional.empty();
        }
    }

    /** Same words, same kind of measure. */
    boolean sameKindAs(ItemKind other) {
        return words.equals(other.words) && unit.equals(other.unit);
    }

    /** How many of {@code other} the package holds: 2 for a 2 kg pack against a 1 kg one. */
    BigDecimal sizeAgainst(ItemKind other) {
        return amount.divide(other.amount, 4, RoundingMode.HALF_UP);
    }

    /** The price per kilogram, litre or unit, as shoppers compare them. */
    BigDecimal pricePerMeasure(BigDecimal price) {
        BigDecimal base = unit.equals("UN") ? amount : amount.movePointLeft(3);
        return price.divide(base, 4, RoundingMode.HALF_UP);
    }

    /** "KG", "L" or "UN", the unit of {@link #pricePerMeasure}. */
    String measureUnit() {
        return switch (unit) {
            case "ML" -> "L";
            case "G" -> "KG";
            default -> "UN";
        };
    }
}

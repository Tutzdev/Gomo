package br.com.supermercados.prices.basicitem;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PackageSize {

    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);
    private static final BigDecimal LARGEST_AMOUNT = BigDecimal.valueOf(100);
    private static final BigDecimal LARGEST_COUNT = BigDecimal.valueOf(360);

    private static final String PRICED_PER_KILOGRAM = "PRECO DE 1 KG";

    private static final Pattern WEIGHT = Pattern.compile(
            "\\b(?:(\\d+) X )?(\\d+(?:\\.\\d+)?) (KG|KGS|KILOS?|QUILOS?|G|GR|GRS|GRAMAS?)\\b");

    private static final Pattern VOLUME = Pattern.compile(
            "\\b(?:(\\d+) X )?(\\d+(?:\\.\\d+)?) (ML|L|LT|LTS|LITROS?)\\b");

    private static final Pattern COUNT = Pattern.compile(
            "\\b(\\d+) (?:UN|UND|UNID|UNIDS|UNIDADES?|OVOS?)\\b|\\bC (\\d+)\\b");

    private PackageSize() {}

    public static Optional<BigDecimal> of(
        Measure measure,
        String name,
        String packageDescription,
        BigDecimal quantity,
        String unit) {

        Optional<BigDecimal> fromName = read(measure, name, true);

        if (fromName.isPresent()) {return fromName;}

        Optional<BigDecimal> fromPackage = read(measure, packageDescription, false);

        if (fromPackage.isPresent()) {return fromPackage;}

        return declared(measure, quantity, unit);
    }

    private static Optional<BigDecimal> read(Measure measure, String description, boolean multiplyPacks) {
        if (description == null || description.isBlank()) {return Optional.empty();}

        String text = normalized(description);

        if (measure == Measure.KG && text.contains(PRICED_PER_KILOGRAM)) {return Optional.of(BigDecimal.ONE);}

        return switch (measure) {
            case KG -> amount(WEIGHT.matcher(text), multiplyPacks).filter(PackageSize::plausibleAmount);
            case L -> amount(VOLUME.matcher(text), multiplyPacks).filter(PackageSize::plausibleAmount);
            case UN -> count(text).filter(PackageSize::plausibleCount);
        };
    }

    private static Optional<BigDecimal> amount(Matcher matcher, boolean multiplyPacks) {
        if (!matcher.find()) {return Optional.empty();}

        BigDecimal amount = new BigDecimal(matcher.group(2));
        String unit = matcher.group(3);

        if (unit.startsWith("G") || unit.equals("ML")) {amount = amount.divide(THOUSAND);}

        if (multiplyPacks && matcher.group(1) != null) {amount = amount.multiply(new BigDecimal(matcher.group(1)));}

        return Optional.of(amount.stripTrailingZeros());
    }

    private static Optional<BigDecimal> count(String text) {
        if (text.contains("MEIA DUZIA")) {return Optional.of(BigDecimal.valueOf(6));}

        if (text.matches(".*\\b(?:DUZIA|DZ)\\b.*")) {return Optional.of(BigDecimal.valueOf(12));}

        Matcher matcher = COUNT.matcher(text);

        if (!matcher.find()) {return Optional.empty();}

        String found = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);

        return Optional.of(new BigDecimal(found));
    }

    private static Optional<BigDecimal> declared(Measure measure, BigDecimal quantity, String unit) {
        if (quantity == null || unit == null || quantity.signum() <= 0) {return Optional.empty();}

        String declaredUnit = unit.strip().toUpperCase(Locale.ROOT);

        Optional<BigDecimal> amount = switch (measure) {
            case KG -> switch (declaredUnit) {
                case "KG" -> Optional.of(quantity);
                case "G" -> Optional.of(quantity.divide(THOUSAND));
                default -> Optional.empty();
            };
            case L -> switch (declaredUnit) {
                case "L" -> Optional.of(quantity);
                case "ML" -> Optional.of(quantity.divide(THOUSAND));
                default -> Optional.empty();
            };
            case UN -> declaredUnit.equals("UN") ? Optional.of(quantity) : Optional.empty();
        };

        return amount.map(BigDecimal::stripTrailingZeros)
                .filter(measure == Measure.UN ? PackageSize::plausibleCount : PackageSize::plausibleAmount);
    }

    private static boolean plausibleAmount(BigDecimal amount) {
        return amount.signum() > 0 && amount.compareTo(LARGEST_AMOUNT) <= 0;
    }

    private static boolean plausibleCount(BigDecimal count) {
        return count.signum() > 0 && count.compareTo(LARGEST_COUNT) <= 0;
    }

    private static String normalized(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("(\\d),(\\d)", "$1.$2")
                .replaceAll("[^A-Z0-9.]+", " ")
                .replaceAll("(?<!\\d)\\.|\\.(?!\\d)", " ")
                .replaceAll("(\\d)([A-Z])", "$1 $2")
                .replaceAll("([A-Z])(\\d)", "$1 $2")
                .replaceAll("\\s+", " ")
                .strip();
    }
}

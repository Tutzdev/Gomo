package br.com.supermercados.prices.product;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ProductNormalizer {

    private static final Pattern PROMOTIONAL_UNIT_COUNT = Pattern.compile(
            "(?i)\\bLEVE\\s*(\\d+)\\s*PAGUE\\s*\\d+\\b");
    private static final Pattern NUMBERED_VARIANT = Pattern.compile(
            "(?iu)\\b(?:N[.º°]?|N[ÚU]MERO)\\s*\\d+\\b");
    private static final Pattern EXPLICIT_UNIT_COUNT = Pattern.compile(
            "(?i)(\\d+(?:[.,]\\d+)?)\\s*(?:UNIDADES?|UN\\.?)\\b");
    private static final Pattern PACKAGE_COUNT = Pattern.compile(
            "(?i)(?:C/|COM|PACK|KIT|PACOTE)\\s*(\\d+)\\b");
    private static final Pattern MULTIPACK_COUNT = Pattern.compile(
            "(?i)\\b(\\d+)\\s*[X×]\\s*\\d+(?:[.,]\\d+)?\\s*(?:KG|G|ML|L)\\b");
    private static final Pattern MEASUREMENT = Pattern.compile(
            "(?i)(\\d+(?:[.,]\\d+)?)\\s*"
                    + "(QUILOGRAMAS?|QUILOS?|KG|GRAMAS?|G|MILILITROS?|ML|LITROS?|L|METROS?|M)\\b");
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^A-Z0-9]+");

    private ProductNormalizer() {
    }

    public static NormalizedProduct normalize(ProductObservation observation) {
        String normalizedName = normalizeText(observation.name());
        String normalizedBrand = normalizeOptionalText(observation.brand());
        PackageDetails packageDetails = parsePackage(observation.name());

        BigDecimal quantity = observation.quantity() == null
                ? packageDetails.quantity()
                : observation.quantity();
        String unit = observation.unit() == null || observation.unit().isBlank()
                ? packageDetails.unit()
                : observation.unit().strip().toUpperCase(Locale.ROOT);
        if (quantity != null && "KG".equals(unit)) {
            quantity = quantity.multiply(BigDecimal.valueOf(1000));
            unit = "G";
        } else if (quantity != null && "L".equals(unit)) {
            quantity = quantity.multiply(BigDecimal.valueOf(1000));
            unit = "ML";
        }

        return new NormalizedProduct(normalizedName, normalizedBrand,
                quantity, unit, packageDetails.description());
    }

    static String normalizeText(String value) {
        String decomposed = Normalizer.normalize(value.strip(), Normalizer.Form.NFD);
        String withoutDiacritics = DIACRITICS.matcher(decomposed).replaceAll("");
        return NON_ALPHANUMERIC.matcher(withoutDiacritics.toUpperCase(Locale.ROOT))
                .replaceAll(" ").strip();
    }

    /** Whether the description states a weight or volume of its own ("PAO FORMA SEVEN BOYS 450G"). */
    public static boolean statesMeasurement(String name) {
        return lastMeasurement(name) != null;
    }

    public static boolean describesSamePackage(String name, String measurement) {
        PackageDetails existing = parsePackage(name);
        PackageDetails supplied = parsePackage(measurement);
        return existing.quantity() != null && supplied.quantity() != null
                && existing.quantity().compareTo(supplied.quantity()) == 0
                && Objects.equals(existing.unit(), supplied.unit());
    }

    private static String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : normalizeText(value);
    }

    private static PackageDetails parsePackage(String name) {
        Measurement measurement = lastMeasurement(name);
        BigDecimal unitCount = firstValue(PROMOTIONAL_UNIT_COUNT.matcher(name));
        if (unitCount == null) {
            unitCount = firstValue(EXPLICIT_UNIT_COUNT.matcher(NUMBERED_VARIANT.matcher(name).replaceAll(" ")));
        }
        if (unitCount == null) {
            unitCount = firstValue(MULTIPACK_COUNT.matcher(name));
        }
        if (unitCount == null) {
            unitCount = firstValue(PACKAGE_COUNT.matcher(name));
        }

        if (unitCount != null) {
            String description = measurement == null
                    ? format(unitCount) + " UN"
                    : format(unitCount) + " X " + measurement.description();
            return new PackageDetails(unitCount, "UN", description);
        }
        if (measurement == null) {
            return new PackageDetails(null, null, null);
        }
        return new PackageDetails(measurement.quantity(), measurement.unit(), measurement.description());
    }

    private static Measurement lastMeasurement(String name) {
        Matcher matcher = MEASUREMENT.matcher(name);
        Measurement measurement = null;
        while (matcher.find()) {
            BigDecimal value = decimal(matcher.group(1));
            String unit = canonicalUnit(matcher.group(2));
            String description = format(value) + " " + unit;
            if ("KG".equals(unit)) {
                measurement = new Measurement(value.multiply(BigDecimal.valueOf(1000)), "G", description);
            } else if ("L".equals(unit)) {
                measurement = new Measurement(value.multiply(BigDecimal.valueOf(1000)), "ML", description);
            } else {
                measurement = new Measurement(value, unit, description);
            }
        }
        return measurement;
    }

    private static String canonicalUnit(String value) {
        String unit = value.toUpperCase(Locale.ROOT);
        if (unit.startsWith("QUILO") || "KG".equals(unit)) {
            return "KG";
        }
        if (unit.startsWith("GRAMA") || "G".equals(unit)) {
            return "G";
        }
        if (unit.startsWith("MILILITRO") || "ML".equals(unit)) {
            return "ML";
        }
        if (unit.startsWith("LITRO") || "L".equals(unit)) {
            return "L";
        }
        return "M";
    }

    private static BigDecimal firstValue(Matcher matcher) {
        return matcher.find() ? decimal(matcher.group(1)) : null;
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value.replace(',', '.'));
    }

    private static String format(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    public record NormalizedProduct(
            String name,
            String brand,
            BigDecimal quantity,
            String unit,
            String packageDescription) {
    }

    private record PackageDetails(BigDecimal quantity, String unit, String description) {
    }

    private record Measurement(BigDecimal quantity, String unit, String description) {
    }
}

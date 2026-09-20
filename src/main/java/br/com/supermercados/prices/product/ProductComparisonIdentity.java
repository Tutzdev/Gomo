package br.com.supermercados.prices.product;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.List;
import java.util.Objects;
import java.text.Normalizer;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Describes comparable contents without replacing a retailer's product or source reference. */
public record ProductComparisonIdentity(
        String family, String contents, String variant, String presentation, boolean confirmed) {

    private static final Pattern MEASURE = Pattern.compile(
            "(\\d+(?:[.,]\\d+)?)\\s*(MILILITROS?|ML|LITROS?|LTS?|L|QUILOGRAMAS?|QUILOS?|KG|GRAMAS?|GRS?|G)\\b");
    private static final Pattern COUNT = Pattern.compile(
            "\\b(\\d+)\\s*(?:UNIDADES?|UN|UND)\\b|\\b(\\d+)\\s*X(?=\\s*\\d)|\\b(?:PACK|C/|COM)\\s*(\\d+)\\b");
    private static final Pattern BUNDLE = Pattern.compile("\\b(KIT|COMBO|LEVE|PAGUE|BONUS|GRATIS)\\b|\\+");
    private static final Set<String> NOISE = Set.of(
            "DE", "DA", "DO", "DAS", "DOS", "EM", "REFRIGERANTE", "REF", "REFR", "REFRI",
            "GARRAFA", "GFA", "GF", "PET", "LATA", "LT", "VIDRO", "RETORNAVEL", "DESCARTAVEL",
            "PACOTE", "PCT", "PACK", "CAIXA", "CX", "COM", "UN", "UND", "UNIDADE", "UNIDADES");
    private static final Set<String> VARIANTS = Set.of("ORIGINAL", "ZERO");

    public static ProductComparisonIdentity from(Product product) {
        return from(product.getName(), product.getBrand(), product.getQuantity(), product.getUnit());
    }

    public static ProductComparisonIdentity from(Product product, Collection<String> declaredBrands) {
        Set<String> nameWords = new HashSet<>(Arrays.asList(ProductNormalizer.normalizeText(product.getName()).split(" +")));
        List<String> namedBrands = declaredBrands.stream().filter(brand -> brand != null && !brand.isBlank())
                .map(brand -> family("", brand)).filter(brand -> !brand.isBlank())
                .filter(brand -> nameWords.containsAll(Arrays.asList(brand.split(" +"))))
                .sorted(java.util.Comparator.comparingInt(String::length).reversed()).toList();
        String brand = product.getBrand();
        boolean declaredBrandInName = brand != null && nameWords.containsAll(Arrays.asList(family("", brand).split(" +")));
        // Some sources put their distributor or store in brand. Prefer a declared brand actually named on the item.
        if (!declaredBrandInName && !namedBrands.isEmpty()) brand = namedBrands.getFirst();
        return from(product.getName(), brand, product.getQuantity(), product.getUnit(), !namedBrands.isEmpty());
    }

    public static String searchFamily(String name) {
        return family(normalizedDescription(name), null);
    }

    public static ProductComparisonIdentity from(String name, String brand, BigDecimal quantity, String unit) {
        return from(name, brand, quantity, unit, false);
    }

    private static ProductComparisonIdentity from(String name, String brand, BigDecimal quantity, String unit,
            boolean brandInName) {
        String text = normalizedDescription(name);
        String words = normalizedDescription(text + " " + (brand == null ? "" : brand));
        String variant = variant(words);
        String family = family(text, brand);
        PackageContents contents = contents(text, quantity, unit);
        boolean brandKnown = brand != null && !brand.isBlank() || brandInName;
        boolean confirmed = brandKnown && contents.confirmed() && !family.isBlank()
                && !BUNDLE.matcher(text).find()
                && !(words.matches(".*\\bZERO\\b.*") && words.matches(".*\\bORIGINAL\\b.*"));
        return new ProductComparisonIdentity(family, contents.description(), variant, presentation(words), confirmed);
    }

    private static String normalizedDescription(String name) {
        // Preserve decimal punctuation until measurements have been read.
        String normalized = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .toUpperCase(Locale.ROOT).replaceAll("\\bCOCA[ -]*COLA\\b", "COCA COLA")
                .replaceAll("\\bORIG\\b", "ORIGINAL")
                .replaceAll("\\b(?:DET|DETERG)\\b", "DETERGENTE")
                .replaceAll("\\bLIQ\\b", "LIQUIDO")
                .replaceAll("\\bREFIN\\b", "REFINADO")
                .replaceAll("\\bTRAD\\b", "TRADICIONAL")
                .replaceAll("\\bNEUT\\b", "NEUTRO")
                .replaceAll("\\bACUC\\b", "ACUCAR")
                .replaceAll("\\b(?:SEM|S[./]?)\\s+ACUCAR\\b", "ZERO")
                .replaceAll("\\bZERO\\s+ACUCAR\\b", "ZERO");
        return normalized.contains("ACUCAR") ? normalized.replaceAll("\\bREF\\b", "REFINADO") : normalized;
    }

    private static String variant(String words) {
        boolean zero = words.matches(".*\\bZERO\\b.*");
        boolean original = words.matches(".*\\bORIGINAL\\b.*");
        if (zero && original) return null;
        return zero ? "ZERO" : original ? "ORIGINAL" : null;
    }

    private static PackageContents contents(String text, BigDecimal quantity, String unit) {
        String contents = null;
        var measures = MEASURE.matcher(text);
        Set<String> measurements = new HashSet<>();
        while (measures.find()) {
            BigDecimal amount = new BigDecimal(measures.group(1).replace(',', '.'));
            String symbol = measures.group(2);
            boolean litre = symbol.startsWith("L");
            boolean kilogram = symbol.startsWith("K") || symbol.startsWith("QUILO");
            if (litre || kilogram) amount = amount.multiply(BigDecimal.valueOf(1000));
            String baseUnit = litre || symbol.startsWith("M") ? "ML" : "G";
            contents = amount.stripTrailingZeros().toPlainString() + baseUnit;
            measurements.add(contents);
        }
        if (contents == null && quantity != null && unit != null) {
            BigDecimal amount = quantity;
            String baseUnit = unit;
            if ("L".equals(unit) || "KG".equals(unit)) {
                amount = amount.multiply(BigDecimal.valueOf(1000));
                baseUnit = "L".equals(unit) ? "ML" : "G";
            }
            contents = amount.stripTrailingZeros().toPlainString() + baseUnit;
        }
        BigDecimal count = BigDecimal.ONE;
        var counts = COUNT.matcher(text);
        int countMatches = 0;
        while (counts.find()) {
            countMatches++;
            for (int group = 1; group <= 3; group++) {
                if (counts.group(group) != null) count = new BigDecimal(counts.group(group));
            }
        }
        if (measurements.isEmpty() && (countMatches > 0 || "UN".equals(unit))) {
            contents = "1UN";
        }
        if (countMatches == 0 && "UN".equals(unit) && quantity != null) {
            count = quantity;
        }
        if (contents != null) contents = count.stripTrailingZeros().toPlainString() + "X" + contents;
        boolean confirmed = contents != null && measurements.size() <= 1 && countMatches <= 1
                && count.signum() > 0
                && !(count.compareTo(BigDecimal.ONE) == 0 && text.matches(".*\\b(?:PACK|CAIXA|CX)\\b.*"));
        return new PackageContents(contents, confirmed);
    }

    private static String presentation(String words) {
        String material = words.matches(".*\\bVIDRO\\b.*") ? "VIDRO"
                : words.matches(".*\\bLATA\\b.*") ? "LATA"
                : words.matches(".*\\bPET\\b.*") ? "PET" : "";
        String returnability = words.contains("RETORNAVEL") ? "RETORNAVEL"
                : words.contains("DESCARTAVEL") ? "DESCARTAVEL" : "";
        if (material.isEmpty() && returnability.isEmpty()) return null;
        return material + ":" + returnability;
    }

    private static String family(String text, String brand) {
        String withoutCounts = MEASURE.matcher(COUNT.matcher(text).replaceAll(" ")).replaceAll(" ");
        String description = normalizedDescription(withoutCounts + " " + (brand == null ? "" : brand));
        TreeSet<String> tokens = new TreeSet<>(Arrays.asList(ProductNormalizer.normalizeText(description).split(" +")));
        tokens.removeAll(NOISE);
        tokens.removeAll(VARIANTS);
        if (text.matches(".*\\d\\s*(?:ML|MILILITROS?|L|LITROS?)\\b.*")) tokens.remove("LIQUIDO");
        tokens.remove("");
        return String.join(" ", tokens);
    }

    private record PackageContents(String description, boolean confirmed) {
    }

    public boolean sameContents(ProductComparisonIdentity other) {
        return confirmed && other.confirmed && family.equals(other.family)
                && contents.equals(other.contents) && Objects.equals(variant, other.variant);
    }

    public boolean possiblyMatches(ProductComparisonIdentity other) {
        return family.equals(other.family)
                && (contents == null || other.contents == null || contents.equals(other.contents))
                && (variant == null || other.variant == null || variant.equals(other.variant));
    }

    public boolean similarCandidate(ProductComparisonIdentity other) {
        if (contents != null && other.contents != null && !contents.equals(other.contents)) return false;
        if (variant != null && other.variant != null && !variant.equals(other.variant)) return false;
        if (presentation != null && other.presentation != null && !presentation.equals(other.presentation)) return false;
        Set<String> words = new HashSet<>(Arrays.asList(family.split(" ")));
        Set<String> otherWords = new HashSet<>(Arrays.asList(other.family.split(" ")));
        Set<String> shared = new HashSet<>(words);
        shared.retainAll(otherWords);
        return shared.size() >= 2 && shared.size() * 2 >= Math.max(words.size(), otherWords.size());
    }

    public static boolean describesBundle(String name) {
        return BUNDLE.matcher(normalizedDescription(name)).find();
    }
}

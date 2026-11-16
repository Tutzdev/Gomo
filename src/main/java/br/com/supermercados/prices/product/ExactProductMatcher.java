package br.com.supermercados.prices.product;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ExactProductMatcher {

    private static final Pattern MEASUREMENT = Pattern.compile(
            "(?i)\\b\\d+(?:[.,]\\d+)?\\s*(?:QUILOGRAMAS?|QUILOS?|KG|GRAMAS?|G|MILILITROS?|ML|LITROS?|L)\\b");

    private static final Set<String> GENERIC_WORDS = Set.of("DE", "DA", "DO", "REFRIGERANTE");
    private final ProductRepository products;

    Optional<Product> find(ProductObservation observation, String gtin) {
        var normalized = ProductNormalizer.normalize(observation);

        if (normalized.brand() == null || normalized.quantity() == null || normalized.unit() == null) {
            return Optional.empty();
        }

        if (!MEASUREMENT.matcher(observation.name()).find()) {return Optional.empty();}

        var candidates = products.findByNormalizedBrandAndUnitAndQuantity(
            normalized.brand(),
            normalized.unit(),
            normalized.quantity(),
            PageRequest.of(0, 101));

        if (candidates.size() > 100) {return Optional.empty();}

        String identity = nameIdentity(observation.name(), normalized.brand());

        var matches = candidates.stream()
                .filter(candidate -> gtin == null || candidate.getGtin() == null || gtin.equals(candidate.getGtin()))
                .filter(candidate -> Objects.equals(identity, nameIdentity(candidate.getName(), normalized.brand())))
                .toList();

        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }

    static String nameIdentity(String name, String brand) {
        String withoutMeasurement = MEASUREMENT.matcher(name).replaceAll(" ");
        var words = new TreeSet<>(Arrays.asList(ProductNormalizer.normalizeText(withoutMeasurement + " " + brand).split(" ")));
        words.removeAll(GENERIC_WORDS);
        words.remove("");

        return String.join(" ", words);
    }
}

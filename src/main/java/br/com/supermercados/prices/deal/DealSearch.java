package br.com.supermercados.prices.deal;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class DealSearch {

    private static final Pattern ACCENTS = Pattern.compile("\\p{M}");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final Pattern SPACES = Pattern.compile(" ");

    private DealSearch() {}

    static Predicate<DealResponse> matching(String query) {
        List<String> terms = terms(query);

        if (terms.isEmpty()) {return deal -> true;}

        return deal -> {
            String text = searchableText(deal);

            return terms.stream().allMatch(text::contains);
        };
    }

    private static String searchableText(DealResponse deal) {
        return Stream.of(deal.name(), deal.brand(), deal.sizeLabel(), deal.storeName())
                .filter(Objects::nonNull)
                .map(DealSearch::normalize)
                .collect(Collectors.joining(" "));
    }

    private static List<String> terms(String query) {
        if (query == null || query.isBlank()) {return List.of();}

        return Arrays.stream(SPACES.split(normalize(query)))
                .filter(term -> !term.isBlank())
                .toList();
    }

    private static String normalize(String text) {
        String withoutAccents = ACCENTS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("");
        String lowerCase = withoutAccents.toLowerCase(Locale.ROOT);

        return NON_ALPHANUMERIC.matcher(lowerCase).replaceAll(" ").trim();
    }
}

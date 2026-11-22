package br.com.supermercados.prices.deal;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class DealSearch {

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
        return Stream.of(deal.name(), deal.brand(), deal.sizeLabel(), deal.category(), deal.storeName())
                .filter(Objects::nonNull)
                .map(DealSearch::normalize)
                .collect(Collectors.joining(" "));
    }

    private static List<String> terms(String query) {
        if (query == null || query.isBlank()) {return List.of();}

        return Arrays.stream(normalize(query).split(" "))
                .filter(term -> !term.isBlank())
                .toList();
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }
}

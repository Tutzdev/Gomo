package br.com.supermercados.prices.product;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.supermercados.prices.catalog.CatalogItemRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductEquivalenceService {

    private final ProductRepository products;
    private final ProductComparisonEvidence evidence;
    private CatalogItemRepository catalog;

    @Autowired(required = false)
    void setCatalog(CatalogItemRepository catalog) {
        this.catalog = catalog;
    }

    public Map<UUID, Matches> find(Collection<UUID> requestedIds) {
        return find(requestedIds, true);
    }

    public Map<UUID, Matches> findConfirmed(Collection<UUID> requestedIds) {
        return find(requestedIds, false);
    }

    private Map<UUID, Matches> find(Collection<UUID> requestedIds, boolean includeSimilar) {
        if (requestedIds.isEmpty()) return Map.of();
        List<Product> requested = products.findAllById(requestedIds);
        List<String> families = requested.stream().map(this::familyKey)
                .filter(family -> !family.isBlank()).distinct().toList();
        Map<String, List<Product>> candidates = families.isEmpty() ? Map.of()
                : products.findByComparisonFamilyIn(families).stream()
                        .collect(Collectors.groupingBy(Product::getComparisonFamily));
        Map<UUID, Matches> matches = new LinkedHashMap<>();
        List<Product> similar = includeSimilar && !families.isEmpty()
                ? products.findSimilarComparisonFamilies(String.join("|", families)) : List.of();
        Map<String, Map<UUID, ProductComparisonIdentity>> identitiesByFamily = new LinkedHashMap<>();
        candidates.forEach((key, family) -> {
            List<String> brands = family.stream().map(Product::getBrand).filter(Objects::nonNull).distinct().toList();
            Map<UUID, ProductComparisonIdentity> identities = family.stream().collect(Collectors.toMap(Product::getId,
                    candidate -> evidence.complete(candidate, ProductComparisonIdentity.from(candidate, brands))));
            boolean requiresVariant = identities.values().stream().anyMatch(identity -> identity.variant() != null);
            if (requiresVariant) identities.replaceAll((id, identity) -> identity.variant() == null
                    ? new ProductComparisonIdentity(identity.family(), identity.contents(), null, identity.presentation(), false)
                    : identity);
            identitiesByFamily.put(key, identities);
        });
        for (Product selected : requested) {
            String familyKey = familyKey(selected);
            List<Product> family = candidates.getOrDefault(familyKey, List.of());
            var identities = identitiesByFamily.getOrDefault(familyKey, Map.of());
            var identity = identities.get(selected.getId());
            if (identity == null) identity = ProductComparisonIdentity.from(selected);
            var selectedIdentity = identity;
            var presentations = identities.values().stream().filter(selectedIdentity::sameContents)
                    .map(ProductComparisonIdentity::presentation).filter(Objects::nonNull).distinct().toList();
            List<Product> confirmed = new ArrayList<>();
            List<Product> possible = new ArrayList<>();
            confirmed.add(selected);
            for (Product candidate : family) {
                if (candidate.getId().equals(selected.getId())) continue;
                var other = identities.get(candidate.getId());
                boolean samePresentation = Objects.equals(identity.presentation(), other.presentation());
                boolean unspecifiedPresentation = identity.presentation() == null || other.presentation() == null;
                // Missing packaging must not bridge known glass/PET/can presentations.
                boolean presentationConfirmed = samePresentation && !unspecifiedPresentation
                        || presentations.size() <= 1 && (samePresentation || unspecifiedPresentation);
                if (identity.sameContents(other) && presentationConfirmed) {
                    confirmed.add(candidate);
                } else if (identity.possiblyMatches(other) && (unspecifiedPresentation || samePresentation)) {
                    possible.add(candidate);
                }
            }
            for (Product candidate : similar) {
                if (familyKey.equals(candidate.getComparisonFamily())) continue;
                if (ProductComparisonIdentity.describesBundle(candidate.getName())) continue;
                var other = ProductComparisonIdentity.from(candidate);
                if (identity.similarCandidate(other)) possible.add(candidate);
            }
            matches.put(selected.getId(), new Matches(List.copyOf(confirmed), List.copyOf(possible), identity.confirmed()));
        }
        return withCatalogLinks(matches);
    }

    /** SKUs grouped under the same generic catalog item are confirmed equivalents of each other. */
    private Map<UUID, Matches> withCatalogLinks(Map<UUID, Matches> matches) {
        if (catalog == null || matches.isEmpty()) return matches;
        Map<UUID, Set<UUID>> linked = new HashMap<>();
        for (Object[] row : catalog.findEquivalentProductIds(matches.keySet())) {
            linked.computeIfAbsent(UUID.fromString((String) row[0]), ignored -> new LinkedHashSet<>())
                    .add(UUID.fromString((String) row[1]));
        }
        if (linked.isEmpty()) return matches;
        Map<UUID, Product> members = products.findAllById(linked.values().stream().flatMap(Set::stream).distinct().toList())
                .stream().collect(Collectors.toMap(Product::getId, product -> product));
        Map<UUID, Matches> merged = new LinkedHashMap<>(matches);
        linked.forEach((requestedId, ids) -> {
            Matches current = matches.get(requestedId);
            Map<UUID, Product> confirmed = new LinkedHashMap<>();
            current.confirmed().forEach(product -> confirmed.put(product.getId(), product));
            ids.stream().map(members::get).filter(Objects::nonNull).forEach(product -> confirmed.putIfAbsent(product.getId(), product));
            List<Product> possible = current.possible().stream().filter(product -> !confirmed.containsKey(product.getId())).toList();
            merged.put(requestedId, new Matches(List.copyOf(confirmed.values()), possible, true));
        });
        return merged;
    }

    private String familyKey(Product product) {
        return product.getComparisonFamily() == null
                ? ProductComparisonIdentity.searchFamily(product.getName()) : product.getComparisonFamily();
    }

    public record Matches(List<Product> confirmed, List<Product> possible, boolean identityConfirmed) {
        public Matches(List<Product> confirmed, List<Product> possible) {
            this(confirmed, possible, true);
        }
    }
}

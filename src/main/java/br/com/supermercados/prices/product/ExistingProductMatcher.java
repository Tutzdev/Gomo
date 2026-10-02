package br.com.supermercados.prices.product;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/** Associates a new retailer reference without expanding or merging the product catalog. */
@Component
@RequiredArgsConstructor
class ExistingProductMatcher {

    private final ProductRepository products;
    private final ProductComparisonEvidence evidence;

    Optional<Product> find(ProductObservation observation, String gtin) {
        Product incoming = new Product(observation, gtin, observation.source().collectedAt());
        var identity = evidence.complete(incoming, ProductComparisonIdentity.from(incoming));
        if (gtin != null) {
            var identified = products.findByGtin(gtin);
            if (identified.isPresent()) {
                var candidate = evidence.complete(identified.orElseThrow(), ProductComparisonIdentity.from(identified.orElseThrow()));
                return compatibleAttributes(identity, candidate) ? identified : Optional.empty();
            }
        }

        List<Product> family = products.findByComparisonFamilyIn(List.of(incoming.getComparisonFamily()));
        List<String> brands = family.stream().map(Product::getBrand).filter(Objects::nonNull).distinct().toList();
        var incomingIdentity = evidence.complete(incoming, ProductComparisonIdentity.from(incoming, brands));
        if (!incomingIdentity.confirmed()) return Optional.empty();
        // Missing variants or packaging cannot bridge known incompatible presentations.
        var identities = family.stream().map(product -> evidence.complete(product,
                ProductComparisonIdentity.from(product, brands))).toList();
        if (incomingIdentity.variant() == null && identities.stream().anyMatch(candidate -> candidate.variant() != null)) {
            return Optional.empty();
        }
        return family.stream()
                .filter(product -> gtin == null || product.getGtin() == null || gtin.equals(product.getGtin()))
                .filter(product -> {
                    var candidate = evidence.complete(product, ProductComparisonIdentity.from(product, brands));
                    return incomingIdentity.sameContents(candidate)
                            && Objects.equals(incomingIdentity.presentation(), candidate.presentation());
                })
                .min(Comparator.comparing(product -> product.getId().toString()));
    }

    private boolean compatibleAttributes(ProductComparisonIdentity incoming, ProductComparisonIdentity candidate) {
        return (incoming.contents() == null || candidate.contents() == null || incoming.contents().equals(candidate.contents()))
                && (incoming.variant() == null || candidate.variant() == null || incoming.variant().equals(candidate.variant()))
                && (incoming.presentation() == null || candidate.presentation() == null
                    || incoming.presentation().equals(candidate.presentation()));
    }
}

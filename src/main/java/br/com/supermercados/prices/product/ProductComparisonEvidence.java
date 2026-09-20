package br.com.supermercados.prices.product;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

/** Reviewed attributes fill omissions only; prices never come from this registry. */
@Component
public class ProductComparisonEvidence {

    private final List<Evidence> entries;

    public ProductComparisonEvidence(ObjectMapper mapper) throws IOException {
        try (var input = new ClassPathResource("product-mappings/comparison-attributes.json").getInputStream()) {
            entries = List.of(mapper.readValue(input, Evidence[].class));
        }
    }

    public ProductComparisonIdentity complete(Product product, ProductComparisonIdentity identity) {
        if (product.getGtin() == null) return identity;
        var evidence = entries.stream().filter(entry -> Gtin.normalize(entry.gtin()).equals(product.getGtin())).findFirst();
        if (evidence.isEmpty()) return identity;
        var entry = evidence.orElseThrow();
        var verified = ProductComparisonIdentity.from(entry.name(), entry.brand(), null, null);
        boolean reviewedName = entry.reviewedNames() != null && entry.reviewedNames().stream()
                .anyMatch(name -> ProductNormalizer.normalizeText(name).equals(product.getNormalizedName()));
        if (!identity.family().equals(verified.family()) && !reviewedName || !Objects.equals(identity.contents(), verified.contents())
                || identity.variant() != null && !identity.variant().equals(verified.variant())
                || identity.presentation() != null && !identity.presentation().equals(verified.presentation())
                || ProductComparisonIdentity.describesBundle(product.getName())) return identity;
        return verified;
    }

    public String displayName(Product product) {
        var identity = ProductComparisonIdentity.from(product);
        var completed = complete(product, identity);
        return entries.stream().filter(entry -> product.getGtin() != null
                        && Gtin.normalize(entry.gtin()).equals(product.getGtin())
                        && ProductComparisonIdentity.from(entry.name(), entry.brand(), null, null).equals(completed))
                .map(Evidence::name).findFirst().orElse(product.getName());
    }

    record Evidence(String gtin, String name, String brand, String verifiedAt, List<String> sources, List<String> reviewedNames) {
    }
}

package br.com.supermercados.prices.product;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import br.com.supermercados.prices.common.ApiException;
import br.com.supermercados.prices.datasource.DataSourceService;
import tools.jackson.databind.ObjectMapper;

@Component
public class VerifiedProductMappings {

    private final List<Mapping> mappings;
    private final DataSourceService sources;
    private final ProductSourceReferenceRepository references;
    private final ProductRepository products;

    public VerifiedProductMappings(
        ObjectMapper mapper,
        DataSourceService sources,
        ProductSourceReferenceRepository references,
        ProductRepository products) throws IOException {

        this.sources = sources;
        this.references = references;
        this.products = products;

        try (var input = new ClassPathResource("product-mappings/nagumo-royal.json").getInputStream()) {
            mappings = List.of(mapper.readValue(input, Mapping[].class));
        }
    }

    Optional<Product> findVerifiedProduct(ProductObservation observation) {
        String reference = observation.source().sourceReference();

        Optional<Mapping> match = mappings.stream().filter(mapping ->
                mapping.nagumoReference().equals(reference) || mapping.royalReference().equals(reference)).findFirst();

        if (match.isEmpty()) {return Optional.empty();}

        Mapping mapping = match.orElseThrow();
        boolean nagumo = mapping.nagumoReference().equals(reference);
        String currentCode = nagumo ? "nagumo_delivery" : "royal_delivery";

        if (!sources.requireSource(observation.source().sourceId()).getCode().equals(currentCode)) {
            return Optional.empty();
        }

        String expectedName = nagumo ? mapping.nagumoName() : mapping.royalName();

        if (!expectedName.equals(observation.name())
                || (!nagumo && !Gtin.normalize(mapping.royalGtin()).equals(Gtin.normalize(observation.gtin())))) {
            throw changedIdentity();
        }

        String otherCode = nagumo ? "royal_delivery" : "nagumo_delivery";
        String otherReference = nagumo ? mapping.royalReference() : mapping.nagumoReference();

        return sources.findByCode(otherCode)
                .flatMap(source -> references.findBySourceIdAndSourceReference(source.getId(), otherReference))
                .flatMap(referenceEntry -> products.findById(referenceEntry.getProductId()))
                .map(product -> {
                    String canonicalSource = sources.requireSource(product.getSourceId()).getCode();

                    String canonicalName = canonicalSource.equals("nagumo_delivery")
                            ? mapping.nagumoName() : mapping.royalName();

                    if (!canonicalName.equals(product.getName())) {
                        throw changedIdentity();
                    }

                    return product;
                });
    }

    private ApiException changedIdentity() {
        return new ApiException(
            HttpStatus.CONFLICT,
            "Identidade de produto mudou; o mapeamento verificado exige nova revisão");
    }

    record Mapping(
            String nagumoReference,
            String nagumoName,
            String royalReference,
            String royalName,
            String royalGtin,
            String verifiedAt,
            String nagumoUrl,
            String royalUrl,
            List<String> imageUrls) {}
}

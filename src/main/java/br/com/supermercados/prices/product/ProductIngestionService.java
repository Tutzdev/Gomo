package br.com.supermercados.prices.product;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.supermercados.prices.common.ApiException;
import br.com.supermercados.prices.datasource.DataSourceService;
import br.com.supermercados.prices.datasource.ObservationValidator;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductIngestionService {

    private final ProductRepository productRepository;
    private final ProductSourceReferenceRepository referenceRepository;
    private final DataSourceService dataSourceService;
    private final ObservationValidator validator;
    private final Clock clock;
    private final VerifiedProductMappings verifiedMappings;
    private final ExactProductMatcher exactMatcher;
    private final ExistingProductMatcher existingMatcher;

    @Transactional
    public Optional<ProductIngestionResult> linkExisting(ProductObservation observation) {
        validator.validate(observation);
        dataSourceService.verifyObservation(observation.source());
        String gtin = Gtin.normalize(observation.gtin());
        var reference = referenceRepository.findBySourceIdAndSourceReference(
                observation.source().sourceId(), observation.source().sourceReference());
        if (reference.isPresent()) {
            return Optional.of(updateReferencedProduct(reference.orElseThrow(), observation, gtin));
        }
        var matched = existingMatcher.find(observation, gtin);
        if (matched.isEmpty()) return Optional.empty();
        Product product = matched.orElseThrow();
        validateIdentity(product, gtin);
        product.assignGtin(gtin);
        product.enrichMedia(observation);
        referenceRepository.save(new ProductSourceReference(product.getId(), observation.source()));
        return Optional.of(new ProductIngestionResult(ProductResponse.from(product), ProductIngestionOutcome.LINKED));
    }

    @Transactional
    public ProductResponse ingest(ProductObservation observation) {
        return ingestWithOutcome(observation).product();
    }

    @Transactional
    public ProductIngestionResult ingestWithOutcome(ProductObservation observation) {
        validator.validate(observation);
        dataSourceService.verifyObservation(observation.source());

        String gtin = Gtin.normalize(observation.gtin());
        Optional<Product> verifiedProduct = verifiedMappings.findVerifiedProduct(observation);
        Optional<ProductSourceReference> existingReference = referenceRepository.findBySourceIdAndSourceReference(
                observation.source().sourceId(),
                observation.source().sourceReference());

        if (existingReference.isPresent()) {
            if (verifiedProduct.isPresent() && !verifiedProduct.orElseThrow().getId()
                    .equals(existingReference.orElseThrow().getProductId())) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "Produtos já cadastrados separadamente exigem revisão antes de vincular históricos");
            }
            return updateReferencedProduct(existingReference.orElseThrow(), observation, gtin);
        }

        Optional<Product> identifiedProduct = verifiedProduct.or(() -> findByGtin(gtin))
                .or(() -> exactMatcher.find(observation, gtin));
        identifiedProduct.ifPresent(product -> validateIdentity(product, gtin));
        Product product = identifiedProduct.orElseGet(
                () -> productRepository.save(new Product(observation, gtin, clock.instant())));
        product.assignGtin(gtin);
        product.enrichMedia(observation);
        referenceRepository.save(new ProductSourceReference(product.getId(), observation.source()));

        ProductIngestionOutcome outcome = identifiedProduct.isPresent()
                ? ProductIngestionOutcome.LINKED : ProductIngestionOutcome.CREATED;
        return new ProductIngestionResult(ProductResponse.from(product), outcome);
    }

    private ProductIngestionResult updateReferencedProduct(
            ProductSourceReference reference, ProductObservation observation, String gtin) {
        Product product = productRepository.findById(reference.getProductId())
                .orElseThrow(() -> new IllegalStateException("Product source reference has no product"));
        validateIdentity(product, gtin);

        if (!observation.source().collectedAt().isAfter(reference.getCollectedAt())) {
            return new ProductIngestionResult(ProductResponse.from(product), ProductIngestionOutcome.UNCHANGED);
        }

        product.assignGtin(gtin);

        product.enrichMedia(observation);
        if (product.getSourceId().equals(observation.source().sourceId())
                && product.getSourceReference().equals(observation.source().sourceReference())) {
            product.updateDetails(observation, clock.instant());
        }
        reference.recordCollection(observation.source().collectedAt());

        return new ProductIngestionResult(ProductResponse.from(product), ProductIngestionOutcome.UPDATED);
    }

    private void validateIdentity(Product product, String gtin) {
        if (gtin == null) {
            return;
        }

        boolean changedGtin = product.getGtin() != null && !Objects.equals(product.getGtin(), gtin);
        boolean anotherProductOwnsGtin = findByGtin(gtin)
                .filter(existing -> !existing.getId().equals(product.getId()))
                .isPresent();

        if (changedGtin || anotherProductOwnsGtin) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Identidade de produto conflitante; associação exige revisão da fonte");
        }
    }

    private Optional<Product> findByGtin(String gtin) {
        return gtin == null ? Optional.empty() : productRepository.findByGtin(gtin);
    }
}

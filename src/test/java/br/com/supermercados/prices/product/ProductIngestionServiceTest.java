package br.com.supermercados.prices.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.supermercados.prices.common.ApiException;
import br.com.supermercados.prices.datasource.DataSourceService;
import br.com.supermercados.prices.datasource.ObservationValidator;
import br.com.supermercados.prices.datasource.SourceObservation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

class ProductIngestionServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-10T12:00:00Z");
    private static final ValidatorFactory VALIDATOR_FACTORY = Validation.buildDefaultValidatorFactory();

    private final ProductRepository products = mock(ProductRepository.class);
    private final ProductSourceReferenceRepository references = mock(ProductSourceReferenceRepository.class);
    private final DataSourceService sources = mock(DataSourceService.class);
    private final UUID sourceId = UUID.randomUUID();
    private ProductIngestionService service;

    @BeforeEach
    void setUp() {
        service = new ProductIngestionService(products, references, sources,
                new ObservationValidator(VALIDATOR_FACTORY.getValidator()), Clock.fixed(NOW, ZoneOffset.UTC),
                mock(VerifiedProductMappings.class), mock(ExactProductMatcher.class), mock(ExistingProductMatcher.class));
    }

    @AfterAll
    static void closeValidationFactory() {
        VALIDATOR_FACTORY.close();
    }

    @Test
    void createsProductWithoutGtinWithoutMatchingAnotherProductByName() {
        when(products.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponse result = service.ingest(observation(null, sourceId, "synthetic-item-a", NOW));

        assertThat(result.id()).isNotNull();
        assertThat(result.gtin()).isNull();
        assertThat(result.sourceReference()).isEqualTo("synthetic-item-a");
        verify(products, never()).findByGtin(any());
        verify(sources).verifyObservation(any());
        verify(references).save(any(ProductSourceReference.class));
    }

    @Test
    void usesNormalizedGtinToLinkDifferentSourceNames() {
        Product existing = new Product(observation("0000000000017", sourceId, "synthetic-first", NOW),
                "00000000000017", NOW);
        when(products.findByGtin("00000000000017")).thenReturn(Optional.of(existing));

        ProductResponse result = service.ingest(
                observation("00000000000017", UUID.randomUUID(), "synthetic-second", NOW));

        assertThat(result.id()).isEqualTo(existing.getId());
        assertThat(result.sourceReference()).isEqualTo("synthetic-first");
        verify(products, never()).save(any());
        verify(references).save(any(ProductSourceReference.class));
    }

    @Test
    void rejectsChangingTheGtinOfAnExistingSourceReference() {
        Product existing = registerExisting("0000000000017", NOW.minusSeconds(60));

        assertThatThrownBy(() -> service.ingest(observation("0000000000024", sourceId, "synthetic-item", NOW)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Identidade");
        assertThat(existing.getGtin()).isEqualTo("00000000000017");
    }

    @Test
    void rejectsMergingAnUnidentifiedProductIntoAnotherExistingGtin() {
        Product unidentified = registerExisting(null, NOW.minusSeconds(60));
        Product alreadyIdentified = new Product(
                observation("0000000000017", UUID.randomUUID(), "synthetic-other", NOW),
                "00000000000017", NOW);
        when(products.findByGtin("00000000000017")).thenReturn(Optional.of(alreadyIdentified));

        assertThatThrownBy(() -> service.ingest(observation("0000000000017", sourceId, "synthetic-item", NOW)))
                .isInstanceOf(ApiException.class);
        assertThat(unidentified.getGtin()).isNull();
    }

    @Test
    void ignoresAnOlderObservationWithoutLosingNewerMetadata() {
        Product existing = registerExisting(null, NOW);

        ProductResponse result = service.ingest(observation(null, sourceId, "synthetic-item", NOW.minusSeconds(60)));

        assertThat(result.collectedAt()).isEqualTo(NOW);
        assertThat(existing.getCollectedAt()).isEqualTo(NOW);
    }

    @Test
    void rejectsNonpositivePackageQuantityBeforePersistence() {
        ProductObservation observation = new ProductObservation(null, "Synthetic test product", null,
                null, "g", BigDecimal.ZERO, null, new SourceObservation(sourceId, "synthetic-item", NOW));

        assertThatThrownBy(() -> service.ingest(observation)).isInstanceOf(ConstraintViolationException.class);
        verify(products, never()).save(any());
    }

    private Product registerExisting(String gtin, Instant collectedAt) {
        ProductObservation observation = observation(gtin, sourceId, "synthetic-item", collectedAt);
        Product existing = new Product(observation, Gtin.normalize(gtin), collectedAt);
        ProductSourceReference reference = new ProductSourceReference(existing.getId(), observation.source());
        when(references.findBySourceIdAndSourceReference(sourceId, "synthetic-item"))
                .thenReturn(Optional.of(reference));
        when(products.findById(existing.getId())).thenReturn(Optional.of(existing));
        return existing;
    }

    private ProductObservation observation(String gtin, UUID source, String reference, Instant collectedAt) {
        return new ProductObservation(gtin, "Synthetic test product", "Synthetic brand", null, "g",
                new BigDecimal("100.0000"), null, new SourceObservation(source, reference, collectedAt));
    }
}

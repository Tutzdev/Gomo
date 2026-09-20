package br.com.supermercados.prices.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import br.com.supermercados.prices.datasource.SourceObservation;

class ProductComparisonIdentityTest {

    @ParameterizedTest
    @ValueSource(strings = {"Refrigerante Coca Cola Original PET 2L", "Coca-Cola Original 2 litros",
            "COCA COLA ORIGINAL 2000 ML", "Ref. Coca-Cola Orig. 2lts"})
    void recognizesEquivalentDescriptionsAndUnits(String description) {
        assertThat(identity("Coca-Cola Original 2L").sameContents(identity(description))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Coca-Cola Zero 2L", "Coca-Cola Original 1,5L", "Coca-Cola Original 6x2L",
            "Pack Coca-Cola Original 2 unidades 2L", "Kit Coca-Cola Original 2L + Fanta 2L",
            "Coca-Cola Original 2L + 250ml grátis", "Coca-Cola Menos Açúcar 2L", "Coca-Cola 2L"})
    void doesNotTreatIncompatibleOrIncompleteDescriptionsAsIdentical(String description) {
        assertThat(identity("Coca-Cola Original 2L").sameContents(identity(description))).isFalse();
    }

    @Test
    void normalizesSugarFreeAndMassWithoutDiscardingBrandsOrFlavors() {
        assertThat(identity("Coca-Cola Zero 2L").sameContents(identity("Coca Cola Sem Açúcar 2000ml"))).isTrue();
        assertThat(identity("Arroz Integral Camil 1 kg").sameContents(identity("Arroz Camil Integral 1000 gr"))).isTrue();
        assertThat(identity("Arroz Integral Camil 1kg").sameContents(identity("Arroz Branco Camil 1kg"))).isFalse();
        assertThat(identity("Arroz Integral Camil 1kg").sameContents(identity("Arroz Integral Tio João 1kg"))).isFalse();
        assertThat(identity("Coca-Cola Zero Baunilha 2L").sameContents(identity("Coca-Cola Zero 2L"))).isFalse();
    }

    @Test
    void recognizesEquivalentMultipacksWithoutConfusingThemWithUnits() {
        var sixBottles = identity("Coca-Cola Original 6x2L");
        assertThat(sixBottles.sameContents(identity("Coca-Cola Original 6 unidades 2000ml"))).isTrue();
        assertThat(sixBottles.sameContents(identity("Coca-Cola Original 2L"))).isFalse();
    }

    @Test
    void unspecifiedPresentationCannotBridgeDistinctKnownPackages() {
        Product pet = product("Coca-Cola Original PET 2L");
        Product glass = product("Coca-Cola Original Vidro 2L");
        Product unspecified = product("Coca-Cola Original 2L");
        var repository = mock(ProductRepository.class);
        when(repository.findAllById(any())).thenReturn(List.of(pet, unspecified));
        when(repository.findByComparisonFamilyIn(any())).thenReturn(List.of(pet, glass, unspecified));
        var matches = new ProductEquivalenceService(repository, evidence()).find(List.of(pet.getId(), unspecified.getId()));

        assertThat(matches.get(pet.getId()).confirmed()).containsExactly(pet);
        assertThat(matches.get(pet.getId()).possible()).containsExactly(unspecified);
        assertThat(matches.get(unspecified.getId()).confirmed()).containsExactly(unspecified);
        assertThat(matches.get(unspecified.getId()).possible()).containsExactly(pet, glass);
    }

    @Test
    void confirmedNameEquivalenceDoesNotDependOnStoreCodesOrDifferentGtins() {
        Product selected = product("Coca-Cola Original PET 2L");
        Product equivalent = product("Refrigerante Coca Cola Original 2000ml");
        selected.assignGtin("07894900027012");
        equivalent.assignGtin("07894900027029");
        var repository = mock(ProductRepository.class);
        when(repository.findAllById(any())).thenReturn(List.of(selected));
        when(repository.findByComparisonFamilyIn(any())).thenReturn(List.of(selected, equivalent));

        var match = new ProductEquivalenceService(repository, evidence()).find(List.of(selected.getId())).get(selected.getId());

        assertThat(selected.getSourceReference()).isNotEqualTo(equivalent.getSourceReference());
        assertThat(match.confirmed()).containsExactly(selected, equivalent);
    }

    private ProductComparisonIdentity identity(String name) {
        String brand = name.contains("Camil") ? "Camil" : name.contains("Tio João") ? "Tio João"
                : name.toUpperCase().contains("COCA") ? "Coca-Cola" : null;
        return ProductComparisonIdentity.from(name, brand, null, null);
    }

    @Test
    void genericUnbrandedNamesDoNotConfirmAnIdentity() {
        var first = identity("Arroz Branco 1kg");
        var second = identity("Arroz Branco 1000g");
        assertThat(first.sameContents(second)).isFalse();
        assertThat(first.possiblyMatches(second)).isTrue();
    }

    @Test
    void declaredBrandCanConfirmANameWithoutBorrowingItsVariant() {
        var source = new SourceObservation(UUID.randomUUID(), "synthetic-brand", Instant.EPOCH);
        var declared = new Product(new ProductObservation(null, "Arroz Branco Camil 1kg", "Camil", null,
                null, null, null, source), null, Instant.EPOCH);
        var inferred = new Product(new ProductObservation(null, "Camil Arroz Branco 1000gr", null, null,
                null, null, null, source), null, Instant.EPOCH);
        var repository = mock(ProductRepository.class);
        when(repository.findAllById(any())).thenReturn(List.of(inferred));
        when(repository.findByComparisonFamilyIn(any())).thenReturn(List.of(inferred, declared));
        assertThat(new ProductEquivalenceService(repository, evidence()).find(List.of(inferred.getId()))
                .get(inferred.getId()).confirmed()).containsExactly(inferred, declared);

        var unknownCoca = product("Coca-Cola 2L");
        assertThat(ProductComparisonIdentity.from(unknownCoca, List.of("Coca-Cola Original")).variant()).isNull();
    }

    @Test
    void normalizedUnitCountsDoNotBecomeCountsSquared() {
        var first = ProductComparisonIdentity.from("Absorvente Modess 32 unidades", "Modess", null, null);
        var second = ProductComparisonIdentity.from("Absorvente Modess", "Modess", new java.math.BigDecimal("32"), "UN");
        assertThat(first.contents()).isEqualTo("32X1UN");
        assertThat(first.sameContents(second)).isTrue();
    }

    private ProductComparisonEvidence evidence() {
        var evidence = mock(ProductComparisonEvidence.class);
        when(evidence.complete(any(), any())).thenAnswer(call -> call.getArgument(1));
        return evidence;
    }

    private Product product(String name) {
        return new Product(new ProductObservation(null, name, "Coca-Cola", null, null, null, null,
                new SourceObservation(UUID.randomUUID(), "synthetic-" + UUID.randomUUID(), Instant.EPOCH)),
                null, Instant.EPOCH);
    }

    @Test
    void distributorInBrandDoesNotSplitTheBrandExplicitlyNamedOnTheProduct() {
        var source = new SourceObservation(UUID.randomUUID(), "synthetic-distributor", Instant.EPOCH);
        var item = new Product(new ProductObservation(null, "Detergente Ypê Neutro 500ml", "Distribuidor Teste",
                null, null, null, null, source), null, Instant.EPOCH);
        var identity = ProductComparisonIdentity.from(item, List.of("Ypê", "Distribuidor Teste"));
        assertThat(identity.sameContents(ProductComparisonIdentity.from("DET Ypê NEUT 0,5L", "Ypê", null, null))).isTrue();
        assertThat(identity.sameContents(ProductComparisonIdentity.from("DET Limpol NEUT 500ml", "Limpol", null, null))).isFalse();
    }
}

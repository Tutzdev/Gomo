package br.com.supermercados.prices.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.supermercados.prices.datasource.SourceObservation;
import tools.jackson.databind.ObjectMapper;

class ExistingProductMatcherTest {

    private final ProductRepository products = mock(ProductRepository.class);
    private final ExistingProductMatcher matcher;

    ExistingProductMatcherTest() throws Exception {
        matcher = new ExistingProductMatcher(products, new ProductComparisonEvidence(new ObjectMapper()));
    }

    @Test
    void linksDifferentLocalCodesWithEquivalentUnitsButNeverCreatesAProduct() {
        Product stored = product("Refrigerante Coca Cola Original PET 2L", "Coca Cola");
        when(products.findByComparisonFamilyIn(any())).thenReturn(List.of(stored));
        assertThat(matcher.find(observation("COCA COLA ORIGINAL PET 2000 ML", "Coca Cola"), null)).contains(stored);
        assertThat(matcher.find(observation("Coca Cola Zero PET 2L", "Coca Cola"), null)).isEmpty();
        assertThat(matcher.find(observation("Coca Cola Original PET 1,5L", "Coca Cola"), null)).isEmpty();
        assertThat(matcher.find(observation("Coca Cola Original PET 6 X 2L", "Coca Cola"), null)).isEmpty();
        assertThat(matcher.find(observation("Coca Cola Original 2L", "Coca Cola"), null)).isEmpty();
    }

    @Test
    void doesNotTrustAValidBarcodeWhenTheVolumeContradictsItsOwner() {
        Product stored = product("Coca Cola Original 2L", "Coca Cola");
        when(products.findByGtin("07894900027013")).thenReturn(Optional.of(stored));
        assertThat(matcher.find(observation("Coca Cola Original 1L", "Coca Cola"), "07894900027013")).isEmpty();
    }

    @Test
    void uncertainOrGenericDescriptionsStayUnmatched() {
        when(products.findByComparisonFamilyIn(any())).thenReturn(List.of(product("Coca Cola Zero 2L", "Coca Cola")));
        assertThat(matcher.find(observation("Coca Cola 2L", "Coca Cola"), null)).isEmpty();
        assertThat(matcher.find(observation("Arroz 1kg", null), null)).isEmpty();
    }

    private Product product(String name, String brand) {
        return new Product(observation(name, brand), null, Instant.now());
    }

    private ProductObservation observation(String name, String brand) {
        return new ProductObservation(null, name, brand, null, null, null, null,
                new SourceObservation(UUID.randomUUID(), UUID.randomUUID().toString(), Instant.now()));
    }
}

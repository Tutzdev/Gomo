package br.com.supermercados.prices.collection;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import br.com.supermercados.prices.price.StockAvailability;

class CollectedOfferPolicyTest {

    private static final Instant COLLECTED = Instant.parse("2026-10-07T09:00:00Z");
    private final CollectedOfferPolicy policy = new CollectedOfferPolicy(Duration.ofHours(12));

    @Test
    void undatedPromotionCountsAsCurrentForTheWindowAfterTheCollection() {
        var offer = offer("Cerveja Lokal Bier Lata 473ml", "3.79", "3.19", null, null, null);

        assertThat(policy.promotionValidUntil(offer, COLLECTED)).isEqualTo(COLLECTED.plus(Duration.ofHours(12)));
    }

    @Test
    void undatedPromotionNeverOutlivesTheOfferItself() {
        Instant offerEnds = COLLECTED.plus(Duration.ofHours(3));
        var offer = offer("Cerveja Lokal Bier Lata 473ml", "3.79", "3.19", null, offerEnds, null);

        assertThat(policy.promotionValidUntil(offer, COLLECTED)).isEqualTo(offerEnds);
    }

    @Test
    void explicitValidityAndConditionalPromotionsAreKeptAsPublished() {
        Instant published = COLLECTED.plus(Duration.ofDays(3));

        assertThat(policy.promotionValidUntil(offer("Arroz 5kg", "30.00", "25.00", null, null, published), COLLECTED))
                .isEqualTo(published);
        assertThat(policy.promotionValidUntil(offer("Arroz 5kg", "30.00", "25.00", "Clube Meu Nagumo", null, null), COLLECTED))
                .isNull();
        assertThat(policy.promotionValidUntil(offer("Arroz 5kg", "30.00", null, null, null, null), COLLECTED)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"99999.00", "9999.00", "9999.99", "5000.00", "1000.00"})
    void placeholderPricesNeverBecomePrices(String price) {
        assertThat(policy.rejection(offer("Batata Lavada Media 1kg", price, null, null, null, null))).isPresent();
    }

    @Test
    void roundHundredsPerKilogramArePlaceholders() {
        assertThat(policy.rejection(offer("Cebola Media (preço de 1 kg)", "500.00", null, null, null, null))).isPresent();
        assertThat(policy.rejection(offer("Filé Mignon (preço de 1 kg)", "109.90", null, null, null, null))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"12.99", "999.00", "999.99", "1299.90", "1984.69", "4999.00"})
    void realPricesPass(String price) {
        assertThat(policy.rejection(offer("Whisky Johnnie Walker Blue Label 750ml", price, null, null, null, null))).isEmpty();
    }

    private static CollectedProduct offer(String name, String regular, String promotion, String condition,
            Instant validUntil, Instant promotionValidUntil) {
        return new CollectedProduct("test:" + name, name, null, null, null, null, new BigDecimal(regular),
                promotion == null ? null : new BigDecimal(promotion), condition, validUntil, promotionValidUntil,
                StockAvailability.AVAILABLE);
    }
}

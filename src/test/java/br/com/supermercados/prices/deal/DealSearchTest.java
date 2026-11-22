package br.com.supermercados.prices.deal;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DealSearchTest {

    private final DealResponse coffee = deal("Café Pilão Tradicional 500g", "Pilão", "Mercearia", "Spani Volta Redonda");

    @Test
    void everyWordMustAppearIgnoringAccentsAndCase() {
        assertThat(DealSearch.matching("cafe pilao").test(coffee)).isTrue();
        assertThat(DealSearch.matching("CAFÉ").test(coffee)).isTrue();
        assertThat(DealSearch.matching("cafe soluvel").test(coffee)).isFalse();
    }

    @Test
    void theAisleAndTheMarketAreSearchableToo() {
        assertThat(DealSearch.matching("mercearia").test(coffee)).isTrue();
        assertThat(DealSearch.matching("spani").test(coffee)).isTrue();
    }

    @Test
    void anEmptySearchKeepsEveryDeal() {
        assertThat(DealSearch.matching(null).test(coffee)).isTrue();
        assertThat(DealSearch.matching("   ").test(coffee)).isTrue();
    }

    private static DealResponse deal(String name, String brand, String category, String store) {
        return new DealResponse(
            UUID.randomUUID(),
            name,
            brand,
            "500 g",
            category,
            null,
            UUID.randomUUID(),
            store,
            new BigDecimal("15.90"),
            new BigDecimal("19.90"),
            20,
            true,
            false,
            Instant.parse("2026-11-22T12:00:00Z"));
    }
}

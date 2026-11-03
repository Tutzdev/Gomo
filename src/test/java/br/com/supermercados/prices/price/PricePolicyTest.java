package br.com.supermercados.prices.price;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PricePolicyTest {

    private final Instant now = Instant.parse("2026-09-10T12:00:00Z");
    private final UUID productId = UUID.randomUUID();
    private final UUID storeId = UUID.randomUUID();
    private final PricePolicy policy = new PricePolicy(Duration.ofDays(2));

    @Test
    void missingObservationDoesNotAssertUnavailableStock() {
        PriceQuote quote = policy.quote(null, now);

        assertThat(quote.status()).isEqualTo(PriceStatus.NO_OBSERVATION);
        assertThat(quote.availability()).isEqualTo(StockAvailability.UNKNOWN);
        assertThat(quote.unitPrice()).isNull();
        assertThat(quote.observation()).isNull();
    }

    @Test
    void usesPromotionOnlyUntilItsExplicitValidity() {
        PriceRecord record = PriceFixtures.observation(productId, storeId, "10.00", "8.99",
                now.minusSeconds(60), null, now.plusSeconds(300), StockAvailability.AVAILABLE);

        PriceQuote current = policy.quote(record, now);
        PriceQuote atExpiration = policy.quote(record, now.plusSeconds(300));

        assertThat(current.unitPrice()).isEqualByComparingTo("8.99");
        assertThat(current.promotionApplied()).isTrue();
        assertThat(current.expiresAt()).isEqualTo(now.plusSeconds(300));
        assertThat(atExpiration.unitPrice()).isEqualByComparingTo("10.00");
        assertThat(atExpiration.promotionApplied()).isFalse();
    }

    @Test
    void promotionWithoutValidityRemainsHistoricalAndDoesNotSetCurrentPrice() {
        PriceRecord record = PriceFixtures.observation(productId, storeId, "10.00", "8.99",
                now.minusSeconds(60), null, null, StockAvailability.UNKNOWN);

        PriceQuote quote = policy.quote(record, now);

        assertThat(quote.unitPrice()).isEqualByComparingTo("10.00");
        assertThat(quote.promotionApplied()).isFalse();
        assertThat(quote.availability()).isEqualTo(StockAvailability.UNKNOWN);
        assertThat(quote.observation().promotionalPrice()).isEqualByComparingTo("8.99");
    }

    @Test
    void conditionalPromotionRemainsVisibleButDoesNotBecomeTheCommonPrice() {
        PriceRecord record = PriceFixtures.observation(productId, storeId, "10.00", "7.99",
                now.minusSeconds(60), null, now.plusSeconds(300),
                "Exclusivo para clientes do clube", StockAvailability.AVAILABLE);

        PriceQuote quote = policy.quote(record, now);

        assertThat(quote.unitPrice()).isEqualByComparingTo("10.00");
        assertThat(quote.promotionApplied()).isFalse();
        assertThat(quote.observation().promotionalPrice()).isEqualByComparingTo("7.99");
        assertThat(quote.observation().promotionCondition())
                .isEqualTo("Exclusivo para clientes do clube");
    }

    @Test
    void latestExpiredObservationCannotContributePriceOrAssertCurrentStock() {
        PriceRecord record = PriceFixtures.observation(productId, storeId, "10.00", null,
                now.minus(Duration.ofDays(2)), null, null, StockAvailability.AVAILABLE);

        PriceQuote quote = policy.quote(record, now);

        assertThat(quote.status()).isEqualTo(PriceStatus.EXPIRED);
        assertThat(quote.unitPrice()).isNull();
        assertThat(quote.availability()).isEqualTo(StockAvailability.UNKNOWN);
        assertThat(quote.observation().availability()).isEqualTo(StockAvailability.AVAILABLE);
    }

    @Test
    void explicitObservationExpiryWinsOverFreshnessWindow() {
        PriceRecord record = PriceFixtures.observation(productId, storeId, "10.00", null,
                now.minusSeconds(60), now, null, StockAvailability.UNKNOWN);

        assertThat(policy.quote(record, now).status()).isEqualTo(PriceStatus.EXPIRED);
    }

    @Test
    void explicitOutOfStockPreventsPricing() {
        PriceRecord record = PriceFixtures.observation(productId, storeId, "10.00", null,
                now.minusSeconds(60), null, null, StockAvailability.UNAVAILABLE);

        PriceQuote quote = policy.quote(record, now);

        assertThat(quote.status()).isEqualTo(PriceStatus.OUT_OF_STOCK);
        assertThat(quote.availability()).isEqualTo(StockAvailability.UNAVAILABLE);
        assertThat(quote.unitPrice()).isNull();
    }

    @Test
    void rejectsInvalidFreshnessConfiguration() {
        assertThatThrownBy(() -> new PricePolicy(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PricePolicy(Duration.ofDays(2), Duration.ofDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shelfPriceStaysCurrentForTheInStoreWindowWhileAScrapedOneExpires() {
        PriceObservation observation = new PriceObservation(productId, storeId, UUID.randomUUID(), "shelf",
                new java.math.BigDecimal("6.50"), null, "BRL", now.minus(Duration.ofDays(4)), null, null,
                StockAvailability.AVAILABLE);
        PriceRecord shelf = PriceRecord.fromStoreVisit(observation, now);
        PriceRecord scraped = PriceRecord.from(observation, now);

        PriceQuote shelfQuote = policy.quote(shelf, now);

        assertThat(shelfQuote.status()).isEqualTo(PriceStatus.KNOWN);
        assertThat(shelfQuote.expiresAt()).isEqualTo(now.plus(Duration.ofDays(3)));
        assertThat(shelfQuote.observation().salesChannel()).isEqualTo(PriceSalesChannel.IN_STORE);
        assertThat(policy.quote(scraped, now).status()).isEqualTo(PriceStatus.EXPIRED);
    }
}

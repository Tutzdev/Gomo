package br.com.supermercados.prices.collection.flyer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import br.com.supermercados.prices.price.StockAvailability;
import tools.jackson.databind.json.JsonMapper;

class ReviewedAtacadaoFlyerCollectorTest {

    @Test
    void importsOnlyReviewedOffersForBothExplicitlyCoveredStoresWithoutRefreshingTheReviewDate() {
        var configuration = new ReviewedFlyerConfiguration();
        var mapper = JsonMapper.builder().findAndAddModules().build();
        var clock = Clock.fixed(Instant.parse("2026-09-20T23:30:00Z"), ZoneOffset.UTC);

        var first = configuration.atacadaoSaoGeraldo(mapper, clock).collect();
        var second = configuration.atacadaoVilaRica(mapper, clock).collect();

        assertThat(first.products()).hasSize(96).containsExactlyElementsOf(second.products());
        assertThat(first.store().address()).contains("1085");
        assertThat(second.store().address()).contains("Avenida Dois, 10");
        assertThat(first.collectedAt()).isEqualTo(Instant.parse("2026-09-20T23:00:50.829142Z"));
        assertThat(first.products()).allSatisfy(product -> {
            assertThat(product.availability()).isEqualTo(StockAvailability.UNKNOWN);
            assertThat(product.gtin()).isNull();
            assertThat(product.originUrl()).startsWith("https://apigw.cloud.carrefour.com.br/");
            assertThat(product.name()).doesNotContain("?");
            if (product.promotionalPrice() != null) {
                assertThat(product.promotionCondition()).contains("aplicativo");
                assertThat(product.promotionalPrice()).isLessThan(product.regularPrice());
            }
        });
        assertThat(first.products()).noneMatch(product -> product.name().contains("Spaten"));
    }

    @Test
    void rejectsExpiredFlyersInsteadOfPresentingOldPricesAsFresh() {
        var clock = Clock.fixed(Instant.parse("2026-09-23T12:00:00Z"), ZoneOffset.UTC);
        var collector = new ReviewedFlyerConfiguration().atacadaoSaoGeraldo(
                JsonMapper.builder().findAndAddModules().build(), clock);

        assertThatThrownBy(collector::collect).isInstanceOf(IllegalStateException.class).hasMessageContaining("vencidos");
    }
}

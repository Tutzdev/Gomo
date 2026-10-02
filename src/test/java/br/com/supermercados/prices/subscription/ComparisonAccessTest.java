package br.com.supermercados.prices.subscription;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ComparisonAccessTest {

    private static final List<String> STORES_CHEAPEST_FIRST = List.of("A", "B", "C", "D", "E");

    @Test
    void freePlanSeesTheThreeCheapestStores() {
        assertThat(ComparisonAccess.freeAccess(1).visibleOf(STORES_CHEAPEST_FIRST)).containsExactly("A", "B", "C");
    }

    @Test
    void afterTheDailyLimitOnlyTheCheapestStoreRemains() {
        assertThat(ComparisonAccess.dailyLimitReachedAccess(5).visibleOf(STORES_CHEAPEST_FIRST)).containsExactly("A");
    }

    @Test
    void premiumSeesEveryStore() {
        assertThat(ComparisonAccess.premiumAccess().visibleOf(STORES_CHEAPEST_FIRST)).isEqualTo(STORES_CHEAPEST_FIRST);
    }
}

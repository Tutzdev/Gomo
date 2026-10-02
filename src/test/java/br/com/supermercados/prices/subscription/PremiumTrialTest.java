package br.com.supermercados.prices.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import br.com.supermercados.prices.alert.PriceAlertService;
import br.com.supermercados.prices.common.ApiException;
import br.com.supermercados.prices.comparison.ShoppingComparisonService;
import br.com.supermercados.prices.shoppinglist.ShoppingListService;
import br.com.supermercados.prices.user.User;
import br.com.supermercados.prices.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class PremiumTrialTest {

    private static final Instant NOW = Instant.parse("2026-10-02T15:00:00Z");

    @Mock private UserRepository users;
    @Mock private ShoppingListService shoppingLists;
    @Mock private PriceAlertService alerts;
    @Mock private ComparisonUsageService comparisonUsage;
    @Mock private ShoppingComparisonService comparisons;

    private User user;
    private SubscriptionService subscriptions;

    @BeforeEach
    void setUp() {
        user = new User("Conta grátis", "gratis@example.test", "not-used-by-test", NOW.minusSeconds(60));
        subscriptions = new SubscriptionService(users, shoppingLists, alerts, comparisonUsage, comparisons,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void trialGivesSevenDaysOfPremiumAndKeepsTheChosenCycle() {
        when(users.findById(user.getId())).thenReturn(Optional.of(user));

        var status = subscriptions.startTrial(user.getId(), new StartTrialRequest(BillingCycle.ANNUAL));

        assertThat(status.premium()).isTrue();
        assertThat(status.premiumSource()).isEqualTo(PremiumSource.TRIAL);
        assertThat(status.trialEndsAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(status.preferredBillingCycle()).isEqualTo(BillingCycle.ANNUAL);
        assertThat(user.hasPremiumAccess(NOW.plus(Duration.ofDays(7)))).isFalse();
    }

    @Test
    void trialCanBeUsedOnlyOnce() {
        user.startPremiumTrial(FreePlan.TRIAL_LENGTH, BillingCycle.MONTHLY, NOW.minus(Duration.ofDays(30)));
        when(users.findById(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> subscriptions.startTrial(user.getId(), new StartTrialRequest(BillingCycle.MONTHLY)))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void cancellingTheTrialReturnsToTheFreePlanImmediately() {
        user.startPremiumTrial(FreePlan.TRIAL_LENGTH, BillingCycle.MONTHLY, NOW.minus(Duration.ofDays(2)));
        when(users.findById(user.getId())).thenReturn(Optional.of(user));

        var status = subscriptions.cancelTrial(user.getId());

        assertThat(status.premium()).isFalse();
        assertThat(status.trialAvailable()).isFalse();
    }

    @Test
    void subscribersDoNotGetATrial() {
        user.activateSubscription(NOW.minusSeconds(30));

        assertThat(user.isTrialAvailable(NOW)).isFalse();
        assertThat(user.premiumSource(NOW)).isEqualTo(PremiumSource.SUBSCRIPTION);
    }
}

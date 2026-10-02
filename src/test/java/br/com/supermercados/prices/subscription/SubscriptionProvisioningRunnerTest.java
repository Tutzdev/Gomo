package br.com.supermercados.prices.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.supermercados.prices.user.User;
import br.com.supermercados.prices.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

@ExtendWith(MockitoExtension.class)
class SubscriptionProvisioningRunnerTest {

    private static final Instant NOW = Instant.parse("2026-09-17T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private UserRepository users;

    @Test
    void configuredAccountReceivesSubscriberAccess() throws Exception {
        User user = new User("Synthetic Subscriber", "subscriber@example.test", "not-used-by-test", NOW.minusSeconds(60));
        when(users.findByEmail("subscriber@example.test")).thenReturn(Optional.of(user));
        var runner = new SubscriptionProvisioningRunner(users, CLOCK, " SUBSCRIBER@EXAMPLE.TEST ");

        runner.run(new DefaultApplicationArguments());

        assertThat(user.isSubscriber()).isTrue();
        assertThat(user.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void blankConfigurationDoesNotQueryAccounts() throws Exception {
        var runner = new SubscriptionProvisioningRunner(users, CLOCK, " ");

        runner.run(new DefaultApplicationArguments());

        verifyNoInteractions(users);
    }

    @Test
    void missingConfiguredAccountPreventsSilentProvisioningFailure() {
        when(users.findByEmail("subscriber@example.test")).thenReturn(Optional.empty());
        var runner = new SubscriptionProvisioningRunner(users, CLOCK, "subscriber@example.test");

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("The configured subscriber must be an existing account");
    }
}

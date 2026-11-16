package br.com.supermercados.prices.subscription;

import br.com.supermercados.prices.user.User;
import br.com.supermercados.prices.user.UserRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SubscriptionProvisioningRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionProvisioningRunner.class);

    private final UserRepository users;
    private final Clock clock;
    private final String bootstrapEmail;

    public SubscriptionProvisioningRunner(
        UserRepository users,
        Clock clock,
        @Value("${app.subscription.bootstrap-email:}") String bootstrapEmail) {

        this.users = users;
        this.clock = clock;
        this.bootstrapEmail = bootstrapEmail;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (bootstrapEmail.isBlank()) {return;}

        User user = users.findByEmail(User.normalizeEmail(bootstrapEmail))
                .orElseThrow(() -> new IllegalStateException(
                        "The configured subscriber must be an existing account"));

        user.activateSubscription(clock.instant());
        log.info("Subscription activated for user {}", user.getId());
    }
}

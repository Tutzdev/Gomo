package br.com.supermercados.prices.admin;

import br.com.supermercados.prices.user.User;
import br.com.supermercados.prices.user.UserRepository;
import br.com.supermercados.prices.user.UserRole;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminProvisioningRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminProvisioningRunner.class);

    private final UserRepository users;
    private final Clock clock;
    private final String bootstrapEmail;

    public AdminProvisioningRunner(UserRepository users, Clock clock,
            @Value("${app.admin.bootstrap-email:}") String bootstrapEmail) {
        this.users = users;
        this.clock = clock;
        this.bootstrapEmail = bootstrapEmail;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (bootstrapEmail.isBlank() || users.existsByRole(UserRole.ADMIN)) {

            return;
        }

        User user = users.findByEmail(User.normalizeEmail(bootstrapEmail))
                .orElseThrow(() -> new IllegalStateException("The configured bootstrap administrator must be an existing account"));

        if (!user.isEmailVerified()) {
            throw new IllegalStateException("The configured bootstrap administrator must have a verified email");
        }

        user.promoteToAdmin(clock.instant());
        
        log.info("First administrator provisioned for user {}", user.getId());
    }
}

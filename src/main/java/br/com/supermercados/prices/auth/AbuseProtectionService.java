package br.com.supermercados.prices.auth;

import br.com.supermercados.prices.user.User;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AbuseProtectionService {

    private final AttemptRateLimiter limiter;
    private final int registrationLimit;
    private final int loginLimit;
    private final int accountMessageLimit;

    public AbuseProtectionService(
        AttemptRateLimiter limiter,
        @Value("${app.rate-limit.registration:5}") int registrationLimit,
        @Value("${app.rate-limit.login:10}") int loginLimit,
        @Value("${app.rate-limit.account-messages:5}") int accountMessageLimit) {

        this.limiter = limiter;
        this.registrationLimit = requirePositive(registrationLimit, "app.rate-limit.registration");
        this.loginLimit = requirePositive(loginLimit, "app.rate-limit.login");
        this.accountMessageLimit = requirePositive(accountMessageLimit, "app.rate-limit.account-messages");
    }

    public void checkRegistration(String remoteAddress, String email) {
        limiter.check("registration-ip", safe(remoteAddress), registrationLimit, Duration.ofHours(1));
        limiter.check("registration-account", emailKey(email), accountMessageLimit, Duration.ofHours(1));
    }

    public void checkLogin(String remoteAddress, String email) {
        limiter.check("login-ip", safe(remoteAddress), loginLimit, Duration.ofMinutes(1));
        limiter.check("login-account", emailKey(email), loginLimit, Duration.ofMinutes(1));
    }

    public void checkAccountMessage(String remoteAddress, String email) {
        limiter.check("account-message-ip", safe(remoteAddress), accountMessageLimit, Duration.ofHours(1));
        limiter.check("account-message-account", emailKey(email), accountMessageLimit, Duration.ofHours(1));
    }

    public void checkAccountConfirmation(String remoteAddress) {
        limiter.check("account-confirmation-ip", safe(remoteAddress), loginLimit, Duration.ofMinutes(1));
    }

    public void checkPasswordChange(UUID userId) {
        limiter.check("password-change-user", userId.toString(), loginLimit, Duration.ofMinutes(15));
    }

    private String emailKey(String email) {
        return SecureTokenValues.hash(User.normalizeEmail(email));
    }

    private String safe(String remoteAddress) {
        return remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress;
    }

    private static int requirePositive(int value, String property) {
        if (value < 1) {
            throw new IllegalArgumentException(property + " must be positive");
        }

        return value;
    }
}

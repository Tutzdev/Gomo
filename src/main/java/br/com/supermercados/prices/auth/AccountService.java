package br.com.supermercados.prices.auth;

import br.com.supermercados.prices.common.ApiException;
import br.com.supermercados.prices.user.User;
import br.com.supermercados.prices.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final UserRepository users;
    private final EmailVerificationTokenRepository verificationTokens;
    private final PasswordResetTokenRepository resetTokens;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final TokenService accessTokens;
    private final AccountEmailSender emailSender;
    private final Clock clock;
    private final Duration verificationTtl;
    private final Duration resetTtl;

    public AccountService(UserRepository users, EmailVerificationTokenRepository verificationTokens,
            PasswordResetTokenRepository resetTokens, PasswordEncoder passwordEncoder,
            PasswordPolicy passwordPolicy, TokenService accessTokens, AccountEmailSender emailSender,
            Clock clock,
            @Value("${app.auth.email-verification-ttl:P1D}") Duration verificationTtl,
            @Value("${app.auth.password-reset-ttl:PT1H}") Duration resetTtl) {
        requirePositive(verificationTtl, "app.auth.email-verification-ttl");
        requirePositive(resetTtl, "app.auth.password-reset-ttl");
        this.users = users;
        this.verificationTokens = verificationTokens;
        this.resetTokens = resetTokens;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.accessTokens = accessTokens;
        this.emailSender = emailSender;
        this.clock = clock;
        this.verificationTtl = verificationTtl;
        this.resetTtl = resetTtl;
    }

    @Transactional
    public void issueEmailVerification(User user) {
        if (user.isEmailVerified()) {
            return;
        }
        Instant now = clock.instant();
        verificationTokens.invalidateUnused(user.getId(), now);
        String rawToken = SecureTokenValues.random();
        verificationTokens.save(new EmailVerificationToken(
                user.getId(), SecureTokenValues.hash(rawToken), now, now.plus(verificationTtl)));
        deliverAfterCommit(() -> emailSender.sendEmailVerification(user.getEmail(), rawToken));
    }

    /** Tells the owner that someone tried to sign up with their e-mail; the sign-up itself answers as usual. */
    @Transactional
    public void notifyExistingAccount(User user) {
        deliverAfterCommit(() -> emailSender.sendExistingAccountNotice(user.getEmail()));
    }

    @Transactional
    public void requestEmailVerification(UUID userId) {
        User user = users.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Usuário não encontrado."));
        issueEmailVerification(user);
    }

    @Transactional
    public void confirmEmail(String rawToken) {
        Instant now = clock.instant();
        EmailVerificationToken token = verificationTokens
                .findByTokenHashForUpdate(SecureTokenValues.hash(rawToken))
                .orElseThrow(this::invalidToken);
        if (token.getUsedAt() != null || !token.getExpiresAt().isAfter(now)) {
            throw invalidToken();
        }
        User user = users.findById(token.getUserId())
                .orElseThrow(this::invalidToken);
        token.use(now);
        user.verifyEmail(now);
    }

    @Transactional
    public void requestPasswordReset(String requestedEmail) {
        users.findByEmail(User.normalizeEmail(requestedEmail)).ifPresent(this::issuePasswordReset);
    }

    @Transactional
    public void resetPassword(String rawToken, String password) {
        passwordPolicy.validate(password);
        Instant now = clock.instant();
        PasswordResetToken token = resetTokens
                .findByTokenHashForUpdate(SecureTokenValues.hash(rawToken))
                .orElseThrow(this::invalidToken);
        if (token.getUsedAt() != null || !token.getExpiresAt().isAfter(now)) {
            throw invalidToken();
        }
        User user = users.findById(token.getUserId())
                .orElseThrow(this::invalidToken);

        resetTokens.invalidateUnused(user.getId(), now);
        token.use(now);
        user.changePassword(passwordEncoder.encode(password), now);
        accessTokens.revokeAll(user.getId());
    }

    /**
     * Changes the password of a signed-in user who proves the current one. Every other session ends (someone who
     * knew the old password is signed out) and a pending reset link stops working; this session stays.
     */
    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword, String currentSession) {
        User user = users.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Usuário não encontrado."));
        if (currentPassword.getBytes(StandardCharsets.UTF_8).length > PasswordPolicy.BCRYPT_MAX_BYTES
                || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Senha atual incorreta.");
        }
        passwordPolicy.validate(newPassword);
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A nova senha precisa ser diferente da atual.");
        }
        Instant now = clock.instant();
        resetTokens.invalidateUnused(userId, now);
        user.changePassword(passwordEncoder.encode(newPassword), now);
        accessTokens.revokeOthers(userId, currentSession);
    }

    private void issuePasswordReset(User user) {
        Instant now = clock.instant();
        resetTokens.invalidateUnused(user.getId(), now);
        String rawToken = SecureTokenValues.random();
        resetTokens.save(new PasswordResetToken(
                user.getId(), SecureTokenValues.hash(rawToken), now, now.plus(resetTtl)));
        deliverAfterCommit(() -> emailSender.sendPasswordReset(user.getEmail(), rawToken));
    }

    private void deliverAfterCommit(Runnable delivery) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            deliver(delivery);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deliver(delivery);
            }
        });
    }

    private void deliver(Runnable delivery) {
        try {
            delivery.run();
        } catch (RuntimeException exception) {
            log.error("Account e-mail delivery failed: {}", exception.getClass().getSimpleName());
        }
    }

    private ApiException invalidToken() {
        return new ApiException(HttpStatus.BAD_REQUEST, "Token inválido, expirado ou já utilizado.");
    }

    private static void requirePositive(Duration value, String property) {
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(property + " must be positive");
        }
    }
}

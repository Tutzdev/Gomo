package br.com.supermercados.prices.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TokenService {

    private static final int TOKEN_LENGTH = 43;

    private final AuthTokenRepository tokens;
    private final Clock clock;
    private final Duration tokenTtl;

    public TokenService(AuthTokenRepository tokens, Clock clock,
            @Value("${app.auth.token-ttl:PT12H}") Duration tokenTtl) {
        if (tokenTtl.isZero() || tokenTtl.isNegative()) {
            throw new IllegalArgumentException("app.auth.token-ttl must be positive");
        }
        this.tokens = tokens;
        this.clock = clock;
        this.tokenTtl = tokenTtl;
    }

    @Transactional
    public IssuedToken issue(UUID userId) {
        String rawToken = SecureTokenValues.random();
        Instant now = clock.instant();
        Instant expiresAt = now.plus(tokenTtl);
        tokens.deleteExpiredForUser(userId, now);
        tokens.save(new AuthToken(userId, SecureTokenValues.hash(rawToken), now, expiresAt));
        return new IssuedToken(rawToken, expiresAt);
    }

    @Transactional(readOnly = true)
    public Optional<AuthenticatedUser> authenticate(String rawToken) {
        if (rawToken == null || rawToken.length() != TOKEN_LENGTH
                || !rawToken.matches("[A-Za-z0-9_-]{43}")) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        return tokens.findByTokenHashAndExpiresAtAfter(SecureTokenValues.hash(rawToken), now)
                .map(token -> token.getUser() == null
                        ? new AuthenticatedUser(token.getUserId())
                        : new AuthenticatedUser(token.getUserId(), token.getUser().getRole(),
                                token.getUser().isEmailVerified(), token.getUser().hasPremiumAccess(now)));
    }

    @Transactional
    public void revoke(String rawToken, UUID userId) {
        tokens.revoke(SecureTokenValues.hash(rawToken), userId);
    }

    @Transactional
    public void revokeAll(UUID userId) {
        tokens.revokeAll(userId);
    }

    public record IssuedToken(String value, Instant expiresAt) {

        @Override
        public String toString() {
            return "IssuedToken[redacted]";
        }
    }
}

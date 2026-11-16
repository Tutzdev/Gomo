package br.com.supermercados.prices.auth;

import br.com.supermercados.prices.subscription.PremiumAccess;
import br.com.supermercados.prices.user.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TokenService {

    private static final Pattern TOKEN_FORMAT = Pattern.compile("[A-Za-z0-9_-]{43}");

    private final AuthTokenRepository tokens;
    private final PremiumAccess premiumAccess;
    private final Clock clock;
    private final Duration tokenTtl;

    public TokenService(
        AuthTokenRepository tokens,
        PremiumAccess premiumAccess,
        Clock clock,
        @Value("${app.auth.token-ttl:PT12H}") Duration tokenTtl) {

        if (tokenTtl.isZero() || tokenTtl.isNegative()) {
            throw new IllegalArgumentException("app.auth.token-ttl must be positive");
        }

        this.tokens = tokens;
        this.premiumAccess = premiumAccess;
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
        tokens.recordSignIn(userId, now);

        return new IssuedToken(rawToken, expiresAt);
    }

    @Transactional(readOnly = true)
    public Optional<AuthenticatedUser> authenticate(String rawToken) {
        if (rawToken == null || !TOKEN_FORMAT.matcher(rawToken).matches()) {return Optional.empty();}

        Instant now = clock.instant();

        return tokens.findByTokenHashAndExpiresAtAfter(SecureTokenValues.hash(rawToken), now)
                .map(token -> toPrincipal(token, now));
    }

    @Transactional
    public void revoke(String rawToken, UUID userId) {
        tokens.revoke(SecureTokenValues.hash(rawToken), userId);
    }

    @Transactional
    public void revokeAll(UUID userId) {
        tokens.revokeAll(userId);
    }

    @Transactional
    public void revokeOthers(UUID userId, String keptRawToken) {
        tokens.revokeAllExcept(userId, SecureTokenValues.hash(keptRawToken));
    }

    private AuthenticatedUser toPrincipal(AuthToken token, Instant now) {
        User user = token.getUser();

        if (user == null) {return new AuthenticatedUser(token.getUserId());}

        return new AuthenticatedUser(
            token.getUserId(),
            user.getRole(),
            user.isEmailVerified(),
            premiumAccess.hasPremium(user, now));
    }

    public record IssuedToken(String value, Instant expiresAt) {

        @Override
        public String toString() {
            return "IssuedToken[redacted]";
        }
    }
}

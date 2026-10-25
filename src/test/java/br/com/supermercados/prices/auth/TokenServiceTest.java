package br.com.supermercados.prices.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.supermercados.prices.subscription.PremiumAccess;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-10T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Duration TTL = Duration.ofHours(12);

    @Mock
    private AuthTokenRepository tokens;

    private TokenService service;

    @BeforeEach
    void setUp() {
        service = new TokenService(tokens, PremiumAccess.withoutBeta(), CLOCK, TTL);
    }

    @Test
    void issuedTokenIsRandomAndOnlyItsSha256HashIsPersisted() throws Exception {
        UUID userId = UUID.randomUUID();

        var issued = service.issue(userId);

        ArgumentCaptor<AuthToken> saved = ArgumentCaptor.forClass(AuthToken.class);
        verify(tokens).save(saved.capture());
        verify(tokens).deleteExpiredForUser(userId, NOW);
        String expectedHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(issued.value().getBytes(StandardCharsets.UTF_8)));
        assertThat(issued.value()).matches("[A-Za-z0-9_-]{43}");
        assertThat(saved.getValue().getTokenHash()).isEqualTo(expectedHash).isNotEqualTo(issued.value());
        assertThat(saved.getValue().getUserId()).isEqualTo(userId);
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(TTL));
        assertThat(issued.expiresAt()).isEqualTo(NOW.plus(TTL));
        assertThat(service.issue(userId).value()).isNotEqualTo(issued.value());
    }

    @Test
    void authenticationRequiresAnUnexpiredStoredToken() {
        UUID userId = UUID.randomUUID();
        when(tokens.findByTokenHashAndExpiresAtAfter(anyString(), eq(NOW)))
                .thenReturn(Optional.of(new AuthToken(userId, "test-hash", NOW, NOW.plus(TTL))));

        assertThat(service.authenticate("a".repeat(43))).contains(new AuthenticatedUser(userId));
    }

    @Test
    void missingOrExpiredTokenDoesNotAuthenticate() {
        when(tokens.findByTokenHashAndExpiresAtAfter(anyString(), eq(NOW))).thenReturn(Optional.empty());

        assertThat(service.authenticate("a".repeat(43))).isEmpty();
    }

    @Test
    void malformedTokensDoNotQueryDatabase() {
        assertThat(service.authenticate(null)).isEmpty();
        assertThat(service.authenticate("short")).isEmpty();
        assertThat(service.authenticate("!".repeat(43))).isEmpty();
        assertThat(service.authenticate("a".repeat(44))).isEmpty();

        verifyNoInteractions(tokens);
    }

    @Test
    void revocationIsRestrictedToTokenHashAndOwner() throws Exception {
        UUID userId = UUID.randomUUID();
        String rawToken = "a".repeat(43);

        service.revoke(rawToken, userId);

        String expectedHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        verify(tokens).revoke(expectedHash, userId);
    }

    @Test
    void nonPositiveTokenTtlFailsAtStartup() {
        assertThatThrownBy(() -> new TokenService(tokens, PremiumAccess.withoutBeta(), CLOCK, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TokenService(tokens, PremiumAccess.withoutBeta(), CLOCK, Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

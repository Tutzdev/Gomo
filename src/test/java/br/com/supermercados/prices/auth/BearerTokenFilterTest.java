package br.com.supermercados.prices.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.supermercados.prices.common.ProblemResponses;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class BearerTokenFilterTest {

    @Mock
    private TokenService tokens;

    private SessionCookies cookies;
    private BearerTokenFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        var writer = new SecurityProblemWriter(JsonMapper.builder().build(),
                new ProblemResponses(Clock.fixed(Instant.parse("2026-09-10T12:00:00Z"), ZoneOffset.UTC)));
        cookies = new SessionCookies(Clock.fixed(Instant.parse("2026-09-10T12:00:00Z"), ZoneOffset.UTC), true);
        filter = new BearerTokenFilter(tokens, writer, cookies);
        request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validTokenCreatesPrincipalWithoutRetainingRawCredentials() throws Exception {
        String rawToken = "a".repeat(43);
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID());
        request.addHeader(HttpHeaders.AUTHORIZATION, "bearer " + rawToken);
        when(tokens.authenticate(rawToken)).thenReturn(Optional.of(user));
        AtomicReference<Authentication> authentication = new AtomicReference<>();

        filter.doFilter(request, response, (incoming, outgoing) ->
                authentication.set(SecurityContextHolder.getContext().getAuthentication()));

        assertThat(authentication.get().isAuthenticated()).isTrue();
        assertThat(authentication.get().getPrincipal()).isEqualTo(user);
        assertThat(authentication.get().getCredentials()).isNull();
    }

    @Test
    void invalidTokenReturnsProblemJsonAndDoesNotReachEndpoint() throws Exception {
        String rawToken = "a".repeat(43);
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + rawToken);
        when(tokens.authenticate(rawToken)).thenReturn(Optional.empty());
        AtomicBoolean reached = new AtomicBoolean();

        filter.doFilter(request, response, (incoming, outgoing) -> reached.set(true));

        assertThat(reached).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
        assertThat(response.getContentAsString()).contains("UNAUTHORIZED").doesNotContain(rawToken);
    }

    @Test
    void duplicateAuthorizationHeadersAreRejected() throws Exception {
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + "a".repeat(43));
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + "b".repeat(43));

        filter.doFilter(request, response, (incoming, outgoing) -> {});

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(tokens);
    }

    @Test
    void requestsWithoutAuthorizationContinueToRouteAuthorization() throws Exception {
        AtomicBoolean reached = new AtomicBoolean();
        FilterChain chain = (incoming, outgoing) -> reached.set(true);

        filter.doFilter(request, response, chain);

        assertThat(reached).isTrue();
        verifyNoInteractions(tokens);
    }

    @Test
    void databaseOutageIsReportedAsUnavailableWithoutInternalDetails() throws Exception {
        String rawToken = "a".repeat(43);
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + rawToken);
        when(tokens.authenticate(rawToken))
                .thenThrow(new DataAccessResourceFailureException("sensitive connection detail"));

        filter.doFilter(request, response, (incoming, outgoing) -> {});

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).doesNotContain("sensitive connection detail");
    }

    @Test
    void sessionCookieAuthenticatesWhenThereIsNoAuthorizationHeader() throws Exception {
        String rawToken = "c".repeat(43);
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID());
        request.setCookies(new Cookie(cookies.name(), rawToken));
        when(tokens.authenticate(rawToken)).thenReturn(Optional.of(user));
        AtomicReference<Authentication> authentication = new AtomicReference<>();

        filter.doFilter(request, response, (incoming, outgoing) ->
                authentication.set(SecurityContextHolder.getContext().getAuthentication()));

        assertThat(authentication.get().getPrincipal()).isEqualTo(user);
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }

    @Test
    void staleSessionCookieIsClearedAndTheRequestGoesOnAnonymously() throws Exception {
        String rawToken = "c".repeat(43);
        request.setCookies(new Cookie(cookies.name(), rawToken));
        when(tokens.authenticate(rawToken)).thenReturn(Optional.empty());
        AtomicReference<Authentication> authentication = new AtomicReference<>();
        AtomicBoolean reached = new AtomicBoolean();

        filter.doFilter(request, response, (incoming, outgoing) -> {
            reached.set(true);
            authentication.set(SecurityContextHolder.getContext().getAuthentication());
        });

        assertThat(reached).isTrue();
        assertThat(authentication.get()).isNull();
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
                .startsWith(cookies.name() + "=;").contains("Max-Age=0", "HttpOnly", "Secure", "SameSite=Lax");
    }

    @Test
    void bearerTokenWinsOverTheSessionCookie() throws Exception {
        String bearer = "a".repeat(43);
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID());
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + bearer);
        request.setCookies(new Cookie(cookies.name(), "c".repeat(43)));
        when(tokens.authenticate(bearer)).thenReturn(Optional.of(user));
        AtomicReference<Authentication> authentication = new AtomicReference<>();

        filter.doFilter(request, response, (incoming, outgoing) ->
                authentication.set(SecurityContextHolder.getContext().getAuthentication()));

        assertThat(authentication.get().getPrincipal()).isEqualTo(user);
    }
}

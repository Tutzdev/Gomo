package br.com.supermercados.prices.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates the opaque token from "Authorization: Bearer" (app and API clients) or, without that header,
 * from the site's session cookie. A bad Bearer token is rejected outright; a stale cookie is cleared and the
 * request goes on anonymously, so public pages keep working after the session ends.
 */
class BearerTokenFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(BearerTokenFilter.class);

    private final TokenService tokens;
    private final SecurityProblemWriter problems;
    private final SessionCookies cookies;

    BearerTokenFilter(TokenService tokens, SecurityProblemWriter problems, SessionCookies cookies) {
        this.tokens = tokens;
        this.problems = problems;
        this.cookies = cookies;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        List<String> authorization = Collections.list(request.getHeaders(HttpHeaders.AUTHORIZATION));
        if (authorization.isEmpty()) {
            Optional<String> cookie = cookies.read(request);
            if (cookie.isPresent()) {
                authenticateCookie(cookie.orElseThrow(), request, response, chain);
            } else {
                chain.doFilter(request, response);
            }
            return;
        }
        if (authorization.size() != 1 || !authorization.getFirst().regionMatches(true, 0, "Bearer ", 0, 7)) {
            reject(request, response);
            return;
        }
        Optional<AuthenticatedUser> principal;
        try {
            principal = tokens.authenticate(authorization.getFirst().substring(7));
        } catch (DataAccessException exception) {
            unavailable(request, response, exception);
            return;
        }
        if (principal.isEmpty()) {
            reject(request, response);
            return;
        }
        continueAs(principal.orElseThrow(), request, response, chain);
    }

    private void authenticateCookie(String rawToken, HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        Optional<AuthenticatedUser> principal;
        try {
            principal = tokens.authenticate(rawToken);
        } catch (DataAccessException exception) {
            unavailable(request, response, exception);
            return;
        }
        if (principal.isEmpty()) {
            cookies.clear(response);
            chain.doFilter(request, response);
            return;
        }
        continueAs(principal.orElseThrow(), request, response, chain);
    }

    private void continueAs(AuthenticatedUser principal, HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(principal.authority())));
        SecurityContextHolder.setContext(context);
        chain.doFilter(request, response);
    }

    private void unavailable(HttpServletRequest request, HttpServletResponse response, DataAccessException exception)
            throws IOException {
        log.error("Falha de persistência durante autenticação: {}", exception.getClass().getSimpleName());
        problems.write(request, response, HttpStatus.SERVICE_UNAVAILABLE, "Autenticação temporariamente indisponível.");
    }

    private void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
        SecurityContextHolder.clearContext();
        problems.write(request, response, HttpStatus.UNAUTHORIZED, "Token de acesso inválido ou expirado.");
    }
}

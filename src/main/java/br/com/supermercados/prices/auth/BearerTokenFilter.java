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

class BearerTokenFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(BearerTokenFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenService tokens;
    private final SecurityProblemWriter problems;
    private final SessionCookies cookies;

    BearerTokenFilter(TokenService tokens, SecurityProblemWriter problems, SessionCookies cookies) {
        this.tokens = tokens;
        this.problems = problems;
        this.cookies = cookies;
    }

    static String bearerToken(String authorization) {
        return authorization.substring(BEARER_PREFIX.length());
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain chain) throws ServletException, IOException {

        List<String> authorization = Collections.list(request.getHeaders(HttpHeaders.AUTHORIZATION));

        if (authorization.isEmpty()) {
            authenticateCookie(request, response, chain);
            return;
        }

        if (!isSingleBearer(authorization)) {
            reject(request, response);
            return;
        }

        authenticateBearer(bearerToken(authorization.getFirst()), request, response, chain);
    }

    private void authenticateBearer(
        String rawToken,
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain chain) throws ServletException, IOException {

        Optional<AuthenticatedUser> principal;

        try {
            principal = tokens.authenticate(rawToken);
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

    private void authenticateCookie(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain chain) throws ServletException, IOException {

        Optional<String> cookie = cookies.read(request);

        if (cookie.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }

        Optional<AuthenticatedUser> principal;

        try {
            principal = tokens.authenticate(cookie.orElseThrow());
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

    private void continueAs(
        AuthenticatedUser principal,
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain chain) throws ServletException, IOException {

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
            principal,
            null,
            List.of(principal.authority()));

        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        chain.doFilter(request, response);
    }

    private void unavailable(
        HttpServletRequest request,
        HttpServletResponse response,
        DataAccessException exception) throws IOException {

        log.error("Falha de persistência durante autenticação: {}", exception.getClass().getSimpleName());

        problems.write(
            request,
            response,
            HttpStatus.SERVICE_UNAVAILABLE,
            "Autenticação temporariamente indisponível.");
    }

    private void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
        SecurityContextHolder.clearContext();

        problems.write(request, response, HttpStatus.UNAUTHORIZED, "Token de acesso inválido ou expirado.");
    }

    private static boolean isSingleBearer(List<String> authorization) {
        return authorization.size() == 1
                && authorization.getFirst().regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length());
    }
}

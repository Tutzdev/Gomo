package br.com.supermercados.prices.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

class SessionOriginFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final SessionCookies cookies;
    private final SecurityProblemWriter problems;
    private final List<String> trustedOrigins;

    SessionOriginFilter(SessionCookies cookies, SecurityProblemWriter problems, List<String> trustedOrigins) {
        this.cookies = cookies;
        this.problems = problems;
        this.trustedOrigins = trustedOrigins;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain chain) throws ServletException, IOException {

        if (!usesCookieSession(request) || isSafeMethod(request) || isTrustedOrigin(request)) {
            chain.doFilter(request, response);
            return;
        }

        problems.write(request, response, HttpStatus.FORBIDDEN, "Pedido de outra origem recusado.");
    }

    static String originOf(URI uri) {
        String port = uri.getPort() == -1 ? "" : ":" + uri.getPort();

        return uri.getScheme() + "://" + uri.getHost() + port;
    }

    private boolean usesCookieSession(HttpServletRequest request) {
        return cookies.read(request).isPresent() || SessionCookies.requested(request);
    }

    private static boolean isSafeMethod(HttpServletRequest request) {
        return SAFE_METHODS.contains(request.getMethod().toUpperCase(Locale.ROOT));
    }

    private boolean isTrustedOrigin(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);

        if (origin == null) {origin = refererOrigin(request.getHeader(HttpHeaders.REFERER));}

        if (origin == null || "null".equals(origin)) {return false;}

        String normalized = origin.toLowerCase(Locale.ROOT);

        return normalized.equals(ownOrigin(request))
                || trustedOrigins.stream().anyMatch(trusted -> trusted.equalsIgnoreCase(normalized));
    }

    private static String ownOrigin(HttpServletRequest request) {
        String scheme = request.getScheme().toLowerCase(Locale.ROOT);
        String host = request.getServerName().toLowerCase(Locale.ROOT);
        int port = request.getServerPort();
        boolean defaultPort = ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);

        return scheme + "://" + host + (defaultPort ? "" : ":" + port);
    }

    private static String refererOrigin(String referer) {
        if (referer == null) {return null;}

        try {
            URI uri = URI.create(referer);

            if (uri.getScheme() == null || uri.getHost() == null) {return null;}

            return originOf(uri);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}

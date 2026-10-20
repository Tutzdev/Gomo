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

/**
 * CSRF protection for the cookie session. A browser attaches the cookie by itself, so a request that changes data
 * and travels with it (or asks for it) must come from the site: its Origin, or the Referer when a browser leaves
 * Origin out, has to be the API's own public origin (Nginx forwards it) or one of the listed CORS origins.
 * SameSite=Lax already keeps the cookie off cross-site POSTs; this check also covers sibling subdomains, which
 * count as the same site. Bearer-only requests (the app) carry no cookie and are not affected.
 */
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
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean cookieSession = cookies.read(request).isPresent() || SessionCookies.requested(request);
        if (!cookieSession || SAFE_METHODS.contains(request.getMethod().toUpperCase(Locale.ROOT))
                || trusted(request)) {
            chain.doFilter(request, response);
            return;
        }
        problems.write(request, response, HttpStatus.FORBIDDEN, "Pedido de outra origem recusado.");
    }

    private boolean trusted(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin == null) {
            origin = originOf(request.getHeader(HttpHeaders.REFERER));
        }
        if (origin == null || "null".equals(origin)) {
            return false;
        }
        String normalized = origin.toLowerCase(Locale.ROOT);
        return normalized.equals(ownOrigin(request))
                || trustedOrigins.stream().anyMatch(trustedOrigin -> trustedOrigin.equalsIgnoreCase(normalized));
    }

    private static String ownOrigin(HttpServletRequest request) {
        String scheme = request.getScheme().toLowerCase(Locale.ROOT);
        int port = request.getServerPort();
        boolean defaultPort = ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
        return scheme + "://" + request.getServerName().toLowerCase(Locale.ROOT) + (defaultPort ? "" : ":" + port);
    }

    private static String originOf(String referer) {
        if (referer == null) {
            return null;
        }
        try {
            URI uri = URI.create(referer);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return null;
            }
            return uri.getScheme() + "://" + uri.getHost() + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}

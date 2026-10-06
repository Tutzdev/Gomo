package br.com.supermercados.prices.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.cors.DefaultCorsProcessor;

class ProblemCorsProcessor extends DefaultCorsProcessor {

    private final SecurityProblemWriter problems;

    ProblemCorsProcessor(SecurityProblemWriter problems) {
        this.problems = problems;
    }

    @Override
    public boolean processRequest(CorsConfiguration configuration, HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        if (isUnlistedOriginOutsidePreflight(configuration, request)) {
            /*
             * Behind Nginx the site's own login arrives with "Origin: https://<domain>" while the backend
             * sees http://127.0.0.1, so a same-origin request looks cross-origin unless the proxy forwards
             * the right headers. It is served without CORS headers: a browser still keeps other sites from
             * reading the response, and any cross-site request carrying JSON or Authorization is preflighted
             * first, which is rejected below.
             */
            return true;
        }
        boolean allowed = super.processRequest(configuration, request, response);
        if (!allowed) {
            problems.write(request, response, HttpStatus.FORBIDDEN, "Origem ou parâmetros CORS não permitidos.");
        }
        return allowed;
    }

    private static boolean isUnlistedOriginOutsidePreflight(CorsConfiguration configuration, HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        return origin != null && configuration != null && !CorsUtils.isPreFlightRequest(request)
                && configuration.checkOrigin(origin) == null;
    }

    @Override
    protected void rejectRequest(ServerHttpResponse response) {
        // Defer the body until processRequest can include the original request path.
        response.setStatusCode(HttpStatus.FORBIDDEN);
    }
}

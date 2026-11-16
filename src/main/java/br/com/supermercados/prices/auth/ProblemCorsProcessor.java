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
    public boolean processRequest(
        CorsConfiguration configuration,
        HttpServletRequest request,
        HttpServletResponse response) throws IOException {

        // Atrás do Nginx, o login do próprio site parece vir de outra origem. Segue sem cabeçalhos CORS:
        // o navegador continua impedindo outros sites de ler a resposta, e o preflight é barrado abaixo.
        if (isUnlistedOriginOutsidePreflight(configuration, request)) {return true;}

        boolean allowed = super.processRequest(configuration, request, response);

        if (!allowed) {
            problems.write(request, response, HttpStatus.FORBIDDEN, "Origem ou parâmetros CORS não permitidos.");
        }

        return allowed;
    }

    @Override
    protected void rejectRequest(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.FORBIDDEN);
    }

    private static boolean isUnlistedOriginOutsidePreflight(
        CorsConfiguration configuration,
        HttpServletRequest request) {

        String origin = request.getHeader(HttpHeaders.ORIGIN);

        return origin != null
                && configuration != null
                && !CorsUtils.isPreFlightRequest(request)
                && configuration.checkOrigin(origin) == null;
    }
}

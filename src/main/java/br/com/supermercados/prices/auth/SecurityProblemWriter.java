package br.com.supermercados.prices.auth;

import br.com.supermercados.prices.common.ProblemResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
class SecurityProblemWriter {

    private final ObjectMapper objectMapper;
    private final ProblemResponses problems;

    SecurityProblemWriter(ObjectMapper objectMapper, ProblemResponses problems) {
        this.objectMapper = objectMapper;
        this.problems = problems;
    }

    void write(
        HttpServletRequest request,
        HttpServletResponse response,
        HttpStatus status,
        String detail) throws IOException {

        ProblemDetail problem = problems.create(status, detail, request);

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");

        if (status == HttpStatus.UNAUTHORIZED) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }

        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}

package br.com.supermercados.prices.common;

import br.com.supermercados.prices.subscription.PlanLimitException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private final ProblemResponses problems;

    public ApiExceptionHandler(ProblemResponses problems) {
        this.problems = problems;
    }

    @ExceptionHandler(ApiException.class)
    ProblemDetail handleApiException(ApiException exception, HttpServletRequest request) {
        return problems.create(exception.getStatus(), exception.getMessage(), request);
    }

    /** The frontend reads {@code limit} to explain what the free plan blocked and what Premium unlocks. */
    @ExceptionHandler(PlanLimitException.class)
    ProblemDetail handlePlanLimit(PlanLimitException exception, HttpServletRequest request) {
        var response = problems.create(exception.getStatus(), exception.getMessage(), request);
        response.setProperty("code", "PLAN_LIMIT");
        response.setProperty("limit", exception.getLimit());
        return response;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        var response = problems.create(HttpStatus.BAD_REQUEST, "Dados de entrada inválidos.", request);
        List<FieldError> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldError(error.getField(), error.getDefaultMessage()))
                .distinct().toList();
        response.setProperty("errors", errors);
        return response;
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            ConstraintViolationException.class, HandlerMethodValidationException.class})
    ProblemDetail handleInvalidInput(Exception exception, HttpServletRequest request) {
        return problems.create(HttpStatus.BAD_REQUEST, "Dados de entrada inválidos.", request);
    }

    @ExceptionHandler({DataIntegrityViolationException.class, OptimisticLockingFailureException.class})
    ProblemDetail handleConflict(Exception exception, HttpServletRequest request) {
        log.warn("Conflito de persistência: {}", exception.getClass().getSimpleName());
        return problems.create(HttpStatus.CONFLICT, "A operação conflita com o estado atual dos dados.", request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception exception, HttpServletRequest request) {
        if (exception instanceof ErrorResponse response) {
            HttpStatus status = HttpStatus.valueOf(response.getStatusCode().value());
            return ResponseEntity.status(status).headers(response.getHeaders())
                    .body(problems.create(status, status.getReasonPhrase(), request));
        }
        // Exception messages may contain SQL parameters or credentials from upstream systems.
        log.error("Falha inesperada: {}", exception.getClass().getName());
        return ResponseEntity.internalServerError().body(problems.create(
                HttpStatus.INTERNAL_SERVER_ERROR, "Não foi possível concluir a operação.", request));
    }

    public record FieldError(String field, String message) {
    }
}

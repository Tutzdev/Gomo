package br.com.supermercados.prices.datasource;

import org.springframework.stereotype.Component;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ObservationValidator {

    private final Validator validator;

    public <T> void validate(T observation) {
        var violations = validator.validate(observation);

        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }
}

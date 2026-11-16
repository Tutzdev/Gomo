package br.com.supermercados.prices.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record EmailVerificationConfirmRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token) {

    @Override
    public String toString() {
        return "EmailVerificationConfirmRequest[redacted]";
    }
}

package br.com.supermercados.prices.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank @Size(max = 72) String currentPassword,
        @NotBlank @Size(min = 12, max = 72) String newPassword) {

    @Override
    public String toString() {
        return "ChangePasswordRequest[redacted]";
    }
}

package br.com.supermercados.prices.auth;

import br.com.supermercados.prices.user.UserResponse;
import java.time.Instant;

public record AuthResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        UserResponse user) {

    @Override
    public String toString() {
        return "AuthResponse[redacted]";
    }
}

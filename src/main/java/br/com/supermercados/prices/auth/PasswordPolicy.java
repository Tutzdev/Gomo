package br.com.supermercados.prices.auth;

import br.com.supermercados.prices.common.ApiException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class PasswordPolicy {

    static final int BCRYPT_MAX_BYTES = 72;
    static final int MIN_LENGTH = 12;

    public void validate(String password) {
        boolean invalid = password == null
                || password.isBlank()
                || password.length() < MIN_LENGTH
                || exceedsBcryptLimit(password);

        if (invalid) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "A senha deve ter ao menos 12 caracteres e no máximo 72 bytes em UTF-8.");
        }
    }

    static boolean exceedsBcryptLimit(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES;
    }
}

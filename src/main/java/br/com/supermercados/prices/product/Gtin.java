package br.com.supermercados.prices.product;

import org.springframework.http.HttpStatus;

import br.com.supermercados.prices.common.ApiException;

public final class Gtin {

    private Gtin() {}

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {return null;}

        String digits = value.strip();

        if (!digits.matches("(?:[0-9]{8}|[0-9]{12}|[0-9]{13}|[0-9]{14})")) {
            throw invalidGtin();
        }

        int weightedSum = 0;
        int weight = 3;

        for (int index = digits.length() - 2; index >= 0; index--) {
            weightedSum += (digits.charAt(index) - '0') * weight;
            weight = weight == 3 ? 1 : 3;
        }

        int expectedCheckDigit = (10 - weightedSum % 10) % 10;

        if (digits.charAt(digits.length() - 1) - '0' != expectedCheckDigit) {
            throw invalidGtin();
        }

        return "0".repeat(14 - digits.length()) + digits;
    }

    private static ApiException invalidGtin() {
        return new ApiException(HttpStatus.BAD_REQUEST, "GTIN inválido: confira o formato e o dígito verificador");
    }
}

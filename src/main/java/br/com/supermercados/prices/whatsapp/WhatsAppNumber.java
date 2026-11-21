package br.com.supermercados.prices.whatsapp;

import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;

import br.com.supermercados.prices.common.ApiException;

record WhatsAppNumber(String value) {

    private static final Pattern BRAZILIAN_MOBILE = Pattern.compile("^[1-9]{2}9\\d{8}$");
    private static final String COUNTRY_CODE = "55";
    private static final int NATIONAL_LENGTH = 11;

    static WhatsAppNumber parse(String typed) {
        String digits = typed == null ? "" : typed.replaceAll("\\D", "");

        if (digits.length() == NATIONAL_LENGTH + COUNTRY_CODE.length() && digits.startsWith(COUNTRY_CODE)) {
            digits = digits.substring(COUNTRY_CODE.length());
        }

        if (!BRAZILIAN_MOBILE.matcher(digits).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Informe um celular com DDD, como (24) 99999-8888.");
        }

        return new WhatsAppNumber("+" + COUNTRY_CODE + digits);
    }

    String withoutPlus() {
        return value.substring(1);
    }
}

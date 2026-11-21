package br.com.supermercados.prices.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import br.com.supermercados.prices.common.ApiException;

class WhatsAppNumberTest {

    @ParameterizedTest
    @ValueSource(strings = {"(24) 99999-8888", "24999998888", "+55 24 99999-8888", "55 (24) 9 9999 8888"})
    void mobileNumbersTypedInAnyFormatBecomeTheInternationalForm(String typed) {
        assertThat(WhatsAppNumber.parse(typed).value()).isEqualTo("+5524999998888");
    }

    @ParameterizedTest
    @ValueSource(strings = {"(24) 3333-4444", "99999-8888", "(04) 99999-8888", "+1 415 555 2671", "telefone"})
    void landlinesNumbersWithoutDddAndForeignNumbersAreRefused(String typed) {
        assertThatThrownBy(() -> WhatsAppNumber.parse(typed))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("celular com DDD");
    }

    @Test
    void theApiReceivesTheNumberWithoutThePlusSign() {
        assertThat(WhatsAppNumber.parse("(24) 99999-8888").withoutPlus()).isEqualTo("5524999998888");
    }

    @Test
    void turningWhatsAppOnWithoutTheBusinessCredentialsStopsTheStartup() {
        assertThatThrownBy(() -> properties(true, "", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WHATSAPP_PHONE_NUMBER_ID")
                .hasMessageContaining("WHATSAPP_ACCESS_TOKEN");
    }

    @Test
    void whatsAppOffNeedsNoCredentials() {
        assertThat(properties(false, "", "").enabled()).isFalse();
    }

    private static WhatsAppProperties properties(boolean enabled, String phoneNumberId, String accessToken) {
        return new WhatsAppProperties(
            enabled,
            URI.create("https://graph.facebook.com/v21.0"),
            phoneNumberId,
            accessToken,
            "alerta_de_preco",
            "pt_BR",
            3,
            Duration.ofMinutes(10),
            Duration.ofHours(6),
            50,
            Duration.ofSeconds(10),
            Duration.ofSeconds(20));
    }
}

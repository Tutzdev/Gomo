package br.com.supermercados.prices.collection.vip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import br.com.supermercados.prices.collection.ServedCity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class VipStoreCollectorTest {

    private static final Instant NOW = Instant.parse("2026-10-31T12:00:00Z");
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void acceptsTheUnitOnlyInTheCityItServes() {
        VipStoreCollector barraMansa = collector(ServedCity.BARRA_MANSA);

        assertThatCode(() -> barraMansa.validateStore(store("Barra Mansa"))).doesNotThrowAnyException();
        assertThatThrownBy(() -> barraMansa.validateStore(store("Volta Redonda")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cidade");
    }

    @Test
    void storesTheUnitUnderTheCityItServes() {
        assertThat(collector(ServedCity.BARRA_MANSA).metadata().cityId()).isEqualTo(ServedCity.BARRA_MANSA.id());
        assertThat(collector(ServedCity.VOLTA_REDONDA).metadata().cityId()).isEqualTo(ServedCity.VOLTA_REDONDA.id());
    }

    private VipStoreCollector collector(ServedCity city) {
        VipStoreDefinition definition = new VipStoreDefinition("loja_teste", "Rede", "Rede Centro",
                URI.create("https://www.rede.com.br"), "/chunk-TESTE.js", 1, 2, 1, 7, "00000000000191", city);
        return new VipStoreCollector(definition, URI.create("https://services.vipcommerce.com.br"), mapper,
                Clock.fixed(NOW, ZoneOffset.UTC), Duration.ZERO, null);
    }

    private JsonNode store(String city) {
        return mapper.readTree("""
                {"id": 7, "cnpj": "00000000000191", "ativo": true, "exclusivo_televendas": false,
                 "endereco": {"cidade": "%s", "estado": "Rio de Janeiro"}}""".formatted(city));
    }
}

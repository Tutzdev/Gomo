package br.com.supermercados.prices.collection.vip;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.supermercados.prices.collection.CollectorMetadata;
import br.com.supermercados.prices.collection.ServedCity;
import tools.jackson.databind.ObjectMapper;

class VipCollectorsConfigurationTest {

    private final VipCollectorsConfiguration configuration = new VipCollectorsConfiguration();
    private final ObjectMapper mapper = new ObjectMapper();
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-31T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void barraMansaUnitsAreStoredInBarraMansa() {
        assertThat(List.of(configuration.bramilBarraMansa(mapper, clock, null).metadata(),
                configuration.perolaBarraMansa(mapper, clock, null).metadata()))
                .extracting(CollectorMetadata::cityId)
                .containsOnly(ServedCity.BARRA_MANSA.id());
    }

    @Test
    void unitsOfTheSameChainShareTheSourceButNotTheStore() {
        CollectorMetadata voltaRedonda = configuration.bramilVoltaRedonda(mapper, clock, null).metadata();
        CollectorMetadata barraMansa = configuration.bramilBarraMansa(mapper, clock, null).metadata();

        assertThat(barraMansa.sourceCode()).isEqualTo(voltaRedonda.sourceCode());
        assertThat(barraMansa.chainSourceReference()).isEqualTo(voltaRedonda.chainSourceReference());
        assertThat(barraMansa.storeSourceReference()).isNotEqualTo(voltaRedonda.storeSourceReference());
        assertThat(barraMansa.code()).isNotEqualTo(voltaRedonda.code());
    }
}

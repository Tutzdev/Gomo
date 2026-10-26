package br.com.supermercados.prices.comparison;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ItemKindTest {

    @Test
    void theSameRiceFromAnotherBrandIsTheSameKind() {
        var tioJoao = ItemKind.of("1 ARROZ BRANCO JOAO TIO TIPO|1x5000G", "Tio João").orElseThrow();
        var camil = ItemKind.of("1 ARROZ BRANCO CAMIL TIPO|1x5000G", "Camil").orElseThrow();
        var parboiled = ItemKind.of("1 ARROZ CAMIL PARBOILIZADO TIPO|1x5000G", "Camil").orElseThrow();

        assertThat(tioJoao.words()).isEqualTo(Set.of("1", "ARROZ", "BRANCO", "TIPO"));
        assertThat(tioJoao.sameKindAs(camil)).isTrue();
        assertThat(tioJoao.sameKindAs(parboiled)).isFalse();
    }

    @Test
    void sizesAreWholePackagesComparedPerKilogramLitreOrUnit() {
        var sixPack = ItemKind.of("COCA COLA REFRIGERANTE|6x350ML", "Coca-Cola").orElseThrow();
        var eggs = ItemKind.of("BRANCOS GRANDES OVOS MANTIQUEIRA|30xUN", "Mantiqueira").orElseThrow();

        assertThat(sixPack.amount()).isEqualByComparingTo("2100");
        assertThat(sixPack.pricePerMeasure(new BigDecimal("21.00"))).isEqualByComparingTo("10.00");
        assertThat(sixPack.measureUnit()).isEqualTo("L");
        assertThat(eggs.pricePerMeasure(new BigDecimal("18.00"))).isEqualByComparingTo("0.60");
        assertThat(eggs.measureUnit()).isEqualTo("UN");
    }

    @Test
    void noBrandOrNoReadableSizeIsNoKind() {
        assertThat(ItemKind.of("TOMATE|1xKG", null)).isEmpty();
        assertThat(ItemKind.of("TOMATE ITALIANO|weird", "Hortifruti Bom")).isEmpty();
        assertThat(ItemKind.of("CAMIL|1x1000G", "Camil")).isEmpty();
    }
}

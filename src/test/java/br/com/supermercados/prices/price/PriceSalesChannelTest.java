package br.com.supermercados.prices.price;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PriceSalesChannelTest {

    @Test
    void readsTheChannelFromTheSourceReference() {
        assertThat(PriceSalesChannel.fromSourceReference("vip:53:product:1")).isEqualTo(PriceSalesChannel.ONLINE);
        assertThat(PriceSalesChannel.fromSourceReference("atacadao:flyer-product:9")).isEqualTo(PriceSalesChannel.PHYSICAL_FLYER);
        assertThat(PriceSalesChannel.fromSourceReference("outra:1")).isEqualTo(PriceSalesChannel.UNSPECIFIED);
        assertThat(PriceSalesChannel.fromSourceReference(null)).isEqualTo(PriceSalesChannel.UNSPECIFIED);
    }
}

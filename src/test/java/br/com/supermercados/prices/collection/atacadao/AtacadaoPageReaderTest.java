package br.com.supermercados.prices.collection.atacadao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.supermercados.prices.collection.PublicCatalogHttp;
import tools.jackson.databind.json.JsonMapper;

class AtacadaoPageReaderTest {

    private static final String BROKEN_PRICE = """
            {"errors":[{"message":"Cannot read properties of undefined (reading 'price')"}]}
            """;

    @Test
    void isolatesBrokenSourceEntryWithoutDiscardingOtherProducts() {
        var http = mock(PublicCatalogHttp.class);
        when(http.get(any())).thenReturn(BROKEN_PRICE, """
                {"data":{"search":{"products":{"pageInfo":{"totalCount":2},"edges":[{"node":{"sku":"test-one"}}]}}}}
                """, BROKEN_PRICE);
        var reader = new AtacadaoPageReader(http, JsonMapper.builder().build(), URI.create("https://example.test"));

        var page = reader.read(List.of(), 0, 2);

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.edges()).hasSize(1);
        assertThat(page.edges().getFirst().path("node").path("sku").asString()).isEqualTo("test-one");
        assertThat(page.warnings()).singleElement().asString().contains("1–1");
        verify(http, times(3)).get(any());
    }

    @Test
    void respectsFiftyPageLimitWhenIsolatingSourceFailures() {
        var http = mock(PublicCatalogHttp.class);
        when(http.get(any())).thenReturn(BROKEN_PRICE);
        var reader = new AtacadaoPageReader(http, JsonMapper.builder().build(), URI.create("https://example.test"));

        var page = reader.read(List.of(), 980, 20);

        assertThat(page.edges()).isEmpty();
        assertThat(page.warnings()).singleElement().asString().contains("980–999");
        verify(http).get(any());
    }

    @Test
    void doesNotRetryAccessRestrictionsAsBrokenPrices() {
        var http = mock(PublicCatalogHttp.class);
        when(http.get(any())).thenThrow(new IllegalStateException("Fonte respondeu HTTP 403"));
        var reader = new AtacadaoPageReader(http, JsonMapper.builder().build(), URI.create("https://example.test"));

        assertThatThrownBy(() -> reader.read(List.of(), 0, 20)).hasMessageContaining("403");
        verify(http).get(any());
    }
}

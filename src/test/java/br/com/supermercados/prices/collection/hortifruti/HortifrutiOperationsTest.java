package br.com.supermercados.prices.collection.hortifruti;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class HortifrutiOperationsTest {

    private static final URI SITE = URI.create("https://www.hortifruti.com.br");
    private static final String PAGE = "<script src=\"/_next/static/chunks/main.js\"></script>"
            + "<script src=\"/_next/static/chunks/pages/_app-abc.js\"></script>";

    @AfterEach
    void reset() {
        HortifrutiOperations.forget();
    }

    @Test
    void readsTheCurrentHashFromTheSiteScriptsWhenTheKnownOneIsRefused() {
        String renamed = "0123456789abcdef0123456789abcdef01234567";
        var site = Map.of(
                "/bebidas", PAGE,
                "/_next/static/chunks/main.js", "export{}",
                "/_next/static/chunks/pages/_app-abc.js",
                "ea={__meta__:{operationName:\"ClientManyProductsQuery\",operationHash:\"" + renamed + "\"}}");

        assertThat(HortifrutiOperations.refresh("ClientManyProductsQuery", SITE, "/bebidas", uri -> site.get(uri.getPath())))
                .isTrue();
        assertThat(HortifrutiOperations.hash("ClientManyProductsQuery")).isEqualTo(renamed);
    }

    @Test
    void doesNotRetryWhenTheSiteStillDeclaresTheSameHash() {
        String known = HortifrutiOperations.hash("ClientPickupPointsQuery");
        var site = Map.of("/bebidas", PAGE, "/_next/static/chunks/main.js",
                "x={__meta__:{operationName:\"ClientPickupPointsQuery\",operationHash:\"" + known + "\"}}",
                "/_next/static/chunks/pages/_app-abc.js", "export{}");

        assertThat(HortifrutiOperations.refresh("ClientPickupPointsQuery", SITE, "/bebidas", uri -> site.get(uri.getPath())))
                .isFalse();
        assertThat(HortifrutiOperations.hash("ClientPickupPointsQuery")).isEqualTo(known);
    }
}

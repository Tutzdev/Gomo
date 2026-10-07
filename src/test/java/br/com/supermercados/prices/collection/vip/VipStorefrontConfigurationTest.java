package br.com.supermercados.prices.collection.vip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VipStorefrontConfigurationTest {

    private static final URI SITE = URI.create("https://www.loja.com.br");
    private static final String HOME = """
            <html><link rel="modulepreload" href="chunk-AAAA1111.js">
            <script src="polyfills-P1.js"></script><script src="main-MAIN1.js" type="module"></script></html>""";
    private static final String CONFIG = "var e={production:!0,lojaUser:\"loja\",lojaAuthJWT:\"abc\"};";

    private final List<String> requested = new ArrayList<>();

    @BeforeEach
    void reset() {
        VipStorefrontConfiguration.forget();
    }

    @Test
    void usesTheKnownPathWhileItStillHoldsTheConfiguration() {
        String script = VipStorefrontConfiguration.load(SITE, "/chunk-OLD.js", site(Map.of("/chunk-OLD.js", CONFIG)));

        assertThat(script).isEqualTo(CONFIG);
        assertThat(requested).containsExactly("/chunk-OLD.js");
    }

    @Test
    void findsTheConfigurationInTheCurrentBuildWhenTheKnownFileIsGone() {
        var site = site(Map.of(
                "/", HOME,
                "/chunk-AAAA1111.js", "export{}",
                "/main-MAIN1.js", "import(\"./chunk-BBBB2222.js\");import(\"./chunk-NEWCFG.js\")",
                "/chunk-BBBB2222.js", "export{}",
                "/chunk-NEWCFG.js", CONFIG));

        assertThat(VipStorefrontConfiguration.load(SITE, "/chunk-OLD.js", site)).isEqualTo(CONFIG);

        requested.clear();
        assertThat(VipStorefrontConfiguration.load(SITE, "/chunk-OLD.js", site)).isEqualTo(CONFIG);
        assertThat(requested).containsExactly("/chunk-NEWCFG.js");
    }

    @Test
    void doesNotMistakeTheHomePageServedForAMissingFileForTheConfiguration() {
        var site = site(Map.of("/", HOME, "/main-MAIN1.js", "export{}"));

        assertThatThrownBy(() -> VipStorefrontConfiguration.load(SITE, "/chunk-OLD.js", site))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("www.loja.com.br");
    }

    /** Like the real storefronts, any unknown path answers with the home page. */
    private java.util.function.Function<URI, String> site(Map<String, String> files) {
        return uri -> {
            requested.add(uri.getPath());
            return files.getOrDefault(uri.getPath(), HOME);
        };
    }
}

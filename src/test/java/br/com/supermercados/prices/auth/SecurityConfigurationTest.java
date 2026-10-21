package br.com.supermercados.prices.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class SecurityConfigurationTest {

    private final SecurityConfiguration security = new SecurityConfiguration();

    @Test
    void corsDefaultsToNoAllowedOrigins() {
        var source = security.corsConfigurationSource("");
        var configuration = source.getCorsConfiguration(new MockHttpServletRequest("GET", "/api/v1/products"));

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedOrigins()).isEmpty();
        assertThat(configuration.getAllowCredentials()).isTrue();
    }

    @Test
    void corsAcceptsExplicitOriginsBearerAuthorizationAndTheSessionCookie() {
        var source = security.corsConfigurationSource("https://example.test, http://localhost:3000");
        var configuration = source.getCorsConfiguration(new MockHttpServletRequest("GET", "/api/v1/products"));

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedOrigins()).containsExactly("https://example.test", "http://localhost:3000");
        assertThat(configuration.getAllowedHeaders()).contains("Authorization", SessionCookies.MODE_HEADER);
        assertThat(configuration.getAllowCredentials()).isTrue();
    }

    @Test
    void theCookieSessionTrustsThePublicSiteEvenWithoutCorsOrigins() {
        assertThat(SecurityConfiguration.sessionOrigins("", "https://Gomo.example.test/")).containsExactly("https://gomo.example.test");
        assertThat(SecurityConfiguration.sessionOrigins("http://localhost:5173", "https://gomo.example.test"))
                .containsExactly("http://localhost:5173", "https://gomo.example.test");
        assertThat(SecurityConfiguration.sessionOrigins("https://gomo.example.test", "https://gomo.example.test"))
                .containsExactly("https://gomo.example.test");
        assertThat(SecurityConfiguration.sessionOrigins("", "")).isEmpty();
    }

    @Test
    void corsRejectsWildcardsAndPaths() {
        assertThatThrownBy(() -> security.corsConfigurationSource("*"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> security.corsConfigurationSource("https://example.test/app"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> security.corsConfigurationSource("https://*.example.test"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

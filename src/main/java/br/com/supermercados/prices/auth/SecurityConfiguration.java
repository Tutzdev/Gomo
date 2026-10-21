package br.com.supermercados.prices.auth;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, TokenService tokens,
            SecurityProblemWriter problems, CorsConfigurationSource corsConfigurationSource, SessionCookies cookies,
            @Value("${app.cors.allowed-origins:}") String configuredOrigins,
            @Value("${app.site-url:}") String siteUrl) throws Exception {
        CorsFilter corsFilter = new CorsFilter(corsConfigurationSource);
        corsFilter.setCorsProcessor(new ProblemCorsProcessor(problems));
        return http
                .cors(AbstractHttpConfigurer::disable)
                // Only the cookie session needs CSRF protection; SessionOriginFilter checks its Origin instead.
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> problems.write(
                                request, response, HttpStatus.UNAUTHORIZED, "Autenticação necessária."))
                        .accessDeniedHandler((request, response, exception) -> problems.write(
                                request, response, HttpStatus.FORBIDDEN, "Acesso não permitido.")))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login",
                                "/api/v1/auth/email-verifications/confirm",
                                "/api/v1/auth/password-resets", "/api/v1/auth/password-resets/confirm").permitAll()
                        // The payment provider has no Gomo account; the webhook authenticates it by secret and HMAC.
                        .requestMatchers(HttpMethod.POST, "/api/v1/billing/abacatepay/webhook").permitAll()
                        // Answers 204 without a session, so the site can ask on every visit without a 401.
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/session").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/actuator", "/actuator/info", "/actuator/metrics", "/actuator/metrics/**")
                                .hasRole("ADMIN")
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/states", "/api/v1/states/**",
                                "/api/v1/cities", "/api/v1/cities/**",
                                "/api/v1/chains", "/api/v1/chains/**",
                                "/api/v1/stores", "/api/v1/stores/**",
                                "/api/v1/products", "/api/v1/products/**",
                                "/api/v1/catalog/items", "/api/v1/catalog/items/**",
                                "/api/v1/prices", "/api/v1/prices/**",
                                "/api/v1/comparisons/products", "/api/v1/status/collections", "/api/v1/sitemap.xml",
                                "/v3/api-docs", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterAt(corsFilter, CorsFilter.class)
                .addFilterBefore(new SessionOriginFilter(cookies, problems, sessionOrigins(configuredOrigins, siteUrl)),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new BearerTokenFilter(tokens, problems, cookies), UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:}") String configuredOrigins) {
        List<String> origins = origins(configuredOrigins);
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", SessionCookies.MODE_HEADER));
        configuration.setExposedHeaders(List.of("Location"));
        // The local site (another port) sends the session cookie; origins are always explicit, never "*".
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Where the site's cookie requests may come from: the CORS origins plus the public site (SITE_URL). The API
     * also accepts its own public origin, but that one depends on Nginx forwarding the scheme and host; with
     * SITE_URL set, a proxy missing those headers does not turn every login into a 403.
     */
    static List<String> sessionOrigins(String configuredOrigins, String siteUrl) {
        List<String> origins = new ArrayList<>(origins(configuredOrigins));
        String site = siteUrl == null ? "" : siteUrl.strip();
        if (!site.isEmpty()) {
            URI uri = URI.create(site);
            String origin = (uri.getScheme() + "://" + uri.getHost() + (uri.getPort() == -1 ? "" : ":" + uri.getPort()))
                    .toLowerCase(Locale.ROOT);
            if (!origins.contains(origin)) {
                origins.add(origin);
            }
        }
        return List.copyOf(origins);
    }

    private static List<String> origins(String configuredOrigins) {
        List<String> origins = Arrays.stream(configuredOrigins.split(","))
                .map(String::strip).filter(origin -> !origin.isEmpty()).distinct().toList();
        origins.forEach(SecurityConfiguration::validateOrigin);
        return origins;
    }

    private static void validateOrigin(String origin) {
        URI uri = URI.create(origin);
        if (origin.contains("*") || !("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                || uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                || uri.getRawFragment() != null || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())) {
            throw new IllegalArgumentException("CORS origins must be explicit HTTP(S) origins without paths");
        }
    }
}

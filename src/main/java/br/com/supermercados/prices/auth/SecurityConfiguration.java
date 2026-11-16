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

    private static final String[] PUBLIC_ACCOUNT_ACTIONS = {
        "/api/v1/auth/register",
        "/api/v1/auth/login",
        "/api/v1/auth/email-verifications/confirm",
        "/api/v1/auth/password-resets",
        "/api/v1/auth/password-resets/confirm"
    };

    private static final String PAYMENT_WEBHOOK = "/api/v1/billing/abacatepay/webhook";

    private static final String SESSION_CHECK = "/api/v1/auth/session";

    private static final String[] HEALTH_CHECK = {"/actuator/health", "/actuator/health/**"};

    private static final String[] ADMIN_ACTUATOR = {
        "/actuator",
        "/actuator/info",
        "/actuator/metrics",
        "/actuator/metrics/**"
    };

    private static final String[] PUBLIC_READS = {
        "/api/v1/states", "/api/v1/states/**",
        "/api/v1/cities", "/api/v1/cities/**",
        "/api/v1/chains", "/api/v1/chains/**",
        "/api/v1/stores", "/api/v1/stores/**",
        "/api/v1/products", "/api/v1/products/**",
        "/api/v1/catalog/items", "/api/v1/catalog/items/**",
        "/api/v1/prices", "/api/v1/prices/**",
        "/api/v1/comparisons/products",
        "/api/v1/status/collections",
        "/api/v1/sitemap.xml",
        "/api/v1/plans/offer",
        "/api/v1/deals",
        "/api/v1/images/products",
        "/v3/api-docs", "/v3/api-docs/**"
    };

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        TokenService tokens,
        SecurityProblemWriter problems,
        CorsConfigurationSource corsConfigurationSource,
        SessionCookies cookies,
        @Value("${app.cors.allowed-origins:}") String configuredOrigins,
        @Value("${app.site-url:}") String siteUrl) throws Exception {

        CorsFilter corsFilter = new CorsFilter(corsConfigurationSource);
        corsFilter.setCorsProcessor(new ProblemCorsProcessor(problems));

        List<String> trustedOrigins = sessionOrigins(configuredOrigins, siteUrl);
        SessionOriginFilter sessionOriginFilter = new SessionOriginFilter(cookies, problems, trustedOrigins);
        BearerTokenFilter bearerTokenFilter = new BearerTokenFilter(tokens, problems, cookies);

        return http
                .cors(AbstractHttpConfigurer::disable)
                // A sessão por cookie é protegida contra CSRF pelo SessionOriginFilter, que confere a origem.
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) ->
                                problems.write(request, response, HttpStatus.UNAUTHORIZED, "Autenticação necessária."))
                        .accessDeniedHandler((request, response, exception) ->
                                problems.write(request, response, HttpStatus.FORBIDDEN, "Acesso não permitido.")))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.POST, PUBLIC_ACCOUNT_ACTIONS).permitAll()
                        .requestMatchers(HttpMethod.POST, PAYMENT_WEBHOOK).permitAll()
                        .requestMatchers(HttpMethod.GET, SESSION_CHECK).permitAll()
                        .requestMatchers(HEALTH_CHECK).permitAll()
                        .requestMatchers(ADMIN_ACTUATOR).hasRole("ADMIN")
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, PUBLIC_READS).permitAll()
                        .anyRequest().authenticated())
                .addFilterAt(corsFilter, CorsFilter.class)
                .addFilterBefore(sessionOriginFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(bearerTokenFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins:}") String configuredOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(origins(configuredOrigins));

        configuration.setAllowedMethods(List.of(
            "GET",
            "POST",
            "PUT",
            "PATCH",
            "DELETE",
            "OPTIONS"));

        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", SessionCookies.MODE_HEADER));
        configuration.setExposedHeaders(List.of("Location"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    static List<String> sessionOrigins(String configuredOrigins, String siteUrl) {
        List<String> origins = new ArrayList<>(origins(configuredOrigins));
        String site = siteUrl == null ? "" : siteUrl.strip();

        if (!site.isEmpty()) {
            String origin = SessionOriginFilter.originOf(URI.create(site)).toLowerCase(Locale.ROOT);

            if (!origins.contains(origin)) {origins.add(origin);}
        }

        return List.copyOf(origins);
    }

    private static List<String> origins(String configuredOrigins) {
        List<String> origins = Arrays.stream(configuredOrigins.split(","))
                .map(String::strip)
                .filter(origin -> !origin.isEmpty())
                .distinct()
                .toList();

        origins.forEach(SecurityConfiguration::validateOrigin);

        return origins;
    }

    private static void validateOrigin(String origin) {
        if (!isExplicitHttpOrigin(origin)) {
            throw new IllegalArgumentException("CORS origins must be explicit HTTP(S) origins without paths");
        }
    }

    private static boolean isExplicitHttpOrigin(String origin) {
        URI uri = URI.create(origin);
        boolean httpScheme = "http".equals(uri.getScheme()) || "https".equals(uri.getScheme());
        boolean hasPath = uri.getRawPath() != null && !uri.getRawPath().isEmpty();

        return !origin.contains("*")
                && httpScheme
                && uri.getHost() != null
                && uri.getRawUserInfo() == null
                && uri.getRawQuery() == null
                && uri.getRawFragment() == null
                && !hasPath;
    }
}

package br.com.supermercados.prices.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class SessionCookies {

    public static final String MODE_HEADER = "X-Gomo-Session";
    public static final String MODE_COOKIE = "cookie";

    private static final String SECURE_NAME = "__Host-gomo_session";
    private static final String LOCAL_NAME = "gomo_session";

    private final Clock clock;
    private final boolean secure;
    private final String name;

    public SessionCookies(Clock clock, @Value("${app.auth.cookie.secure:true}") boolean secure) {
        this.clock = clock;
        this.secure = secure;
        this.name = secure ? SECURE_NAME : LOCAL_NAME;
    }

    public String name() {
        return name;
    }

    public static boolean requested(HttpServletRequest request) {
        return MODE_COOKIE.equalsIgnoreCase(request.getHeader(MODE_HEADER));
    }

    public Optional<String> read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();

        if (cookies == null) {return Optional.empty();}

        return Arrays.stream(cookies)
                .filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }

    public void write(HttpServletResponse response, TokenService.IssuedToken token) {
        Duration remaining = Duration.between(clock.instant(), token.expiresAt());
        Duration maxAge = remaining.isNegative() ? Duration.ZERO : remaining;

        response.addHeader(HttpHeaders.SET_COOKIE, cookie(token.value(), maxAge));
    }

    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO));
    }

    private String cookie(String value, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build()
                .toString();
    }
}

package br.com.supermercados.prices.auth;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.supermercados.prices.common.ProblemResponses;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

class SessionOriginFilterTest {

    private SessionCookies cookies;
    private SessionOriginFilter filter;
    private MockHttpServletResponse response;
    private final AtomicBoolean reached = new AtomicBoolean();

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-10T12:00:00Z"), ZoneOffset.UTC);
        var writer = new SecurityProblemWriter(JsonMapper.builder().build(), new ProblemResponses(clock));
        cookies = new SessionCookies(clock, true);
        filter = new SessionOriginFilter(cookies, writer, List.of("http://localhost:5173"));
        response = new MockHttpServletResponse();
    }

    @Test
    void cookieRequestFromTheSiteItselfPasses() throws Exception {
        var request = siteRequest("PATCH");
        request.addHeader(HttpHeaders.ORIGIN, "https://gomo.example.test");

        run(request);

        assertThat(reached).isTrue();
    }

    @Test
    void cookieRequestFromAListedCorsOriginPasses() throws Exception {
        var request = siteRequest("DELETE");
        request.addHeader(HttpHeaders.ORIGIN, "http://localhost:5173");

        run(request);

        assertThat(reached).isTrue();
    }

    @Test
    void cookieRequestFromAnotherOriginIsRefused() throws Exception {
        var request = siteRequest("POST");
        request.addHeader(HttpHeaders.ORIGIN, "https://evil.example.test");

        run(request);

        assertThat(reached).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("FORBIDDEN");
    }

    @Test
    void siblingSubdomainIsRefusedEvenThoughSameSiteLetsTheCookieThrough() throws Exception {
        var request = siteRequest("POST");
        request.addHeader(HttpHeaders.ORIGIN, "https://blog.example.test");

        run(request);

        assertThat(reached).isFalse();
    }

    @Test
    void refererStandsInWhenTheBrowserOmitsOrigin() throws Exception {
        var trusted = siteRequest("POST");
        trusted.addHeader(HttpHeaders.REFERER, "https://gomo.example.test/app/listas?x=1");
        run(trusted);
        assertThat(reached).isTrue();

        reached.set(false);
        response = new MockHttpServletResponse();
        var foreign = siteRequest("POST");
        foreign.addHeader(HttpHeaders.REFERER, "https://evil.example.test/page");
        run(foreign);
        assertThat(reached).isFalse();
    }

    @Test
    void cookieRequestWithoutOriginOrRefererIsRefused() throws Exception {
        var request = siteRequest("PUT");

        run(request);

        assertThat(reached).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void askingForTheCookieIsCheckedEvenWithoutOne() throws Exception {
        // Login CSRF: another site must not sign the visitor into an account of its choosing.
        var request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.addHeader(SessionCookies.MODE_HEADER, SessionCookies.MODE_COOKIE);
        request.addHeader(HttpHeaders.ORIGIN, "https://evil.example.test");

        run(request);

        assertThat(reached).isFalse();
    }

    @Test
    void readsAndBearerOnlyRequestsAreNotChecked() throws Exception {
        var read = siteRequest("GET");
        read.addHeader(HttpHeaders.ORIGIN, "https://evil.example.test");
        run(read);
        assertThat(reached).isTrue();

        reached.set(false);
        var bearer = new MockHttpServletRequest("POST", "/api/v1/shopping-lists");
        bearer.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + "a".repeat(43));
        run(bearer);
        assertThat(reached).isTrue();
    }

    private MockHttpServletRequest siteRequest(String method) {
        var request = new MockHttpServletRequest(method, "/api/v1/users/me");
        request.setScheme("https");
        request.setServerName("gomo.example.test");
        request.setServerPort(443);
        request.setCookies(new Cookie(cookies.name(), "c".repeat(43)));
        return request;
    }

    private void run(MockHttpServletRequest request) throws Exception {
        filter.doFilter(request, response, (incoming, outgoing) -> reached.set(true));
    }
}

package br.com.supermercados.prices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import br.com.supermercados.prices.auth.SessionCookies;
import br.com.supermercados.prices.support.PostgresTestDatabase;
import tools.jackson.databind.ObjectMapper;

/** The site's cookie session next to the Bearer token, with synthetic accounts in an isolated PostgreSQL schema. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SessionCookieIntegrationTests {

    private static final String PASSWORD = "synthetic-test-password";
    /** MockMvc serves http://localhost, which is the API's own origin here. */
    private static final String SITE = "http://localhost";
    private static final String COOKIE = "__Host-gomo_session";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry);
    }

    @Test
    void siteLoginSetsAnHttpOnlyCookieAndKeepsTheTokenOutOfTheBody() throws Exception {
        String email = register();

        MockHttpServletResponse response = siteLogin(email).andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.tokenType").value("Cookie"))
                .andExpect(jsonPath("$.user.email").value(email))
                .andReturn().getResponse();

        String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).startsWith(COOKIE + "=").contains("HttpOnly", "Secure", "SameSite=Lax", "Path=/")
                .contains("Max-Age=").doesNotContain("Domain=");
        Cookie session = sessionCookie(response);
        mvc.perform(get("/api/v1/users/me").cookie(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
        mvc.perform(get("/api/v1/auth/session").cookie(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void sessionCheckWithoutCookieAnswersNoContentInsteadOfUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/auth/session")).andExpect(status().isNoContent());
    }

    @Test
    void bearerClientsKeepTheTokenInTheBodyAndGetNoCookie() throws Exception {
        String email = register();

        MockHttpServletResponse response = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(credentials(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andReturn().getResponse();

        String token = json.readTree(response.getContentAsByteArray()).path("accessToken").asString();
        assertThat(token).hasSize(43);
        mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void siteRegistrationSignsTheNewAccountIn() throws Exception {
        String email = UUID.randomUUID() + "@example.com";

        MockHttpServletResponse response = mvc.perform(site(post("/api/v1/auth/register"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Pessoa sintética", "email", email, "password", PASSWORD))))
                .andExpect(status().isCreated())
                .andReturn().getResponse();

        mvc.perform(get("/api/v1/users/me").cookie(sessionCookie(response))).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void logoutRevokesTheSessionAndClearsTheCookie() throws Exception {
        Cookie session = sessionCookie(siteLogin(register()).andReturn().getResponse());

        MockHttpServletResponse response = mvc.perform(site(post("/api/v1/auth/logout")).cookie(session))
                .andExpect(status().isNoContent()).andReturn().getResponse();

        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).startsWith(COOKIE + "=;").contains("Max-Age=0");
        // The old cookie no longer opens the account; the API clears it again and the visitor is anonymous.
        mvc.perform(get("/api/v1/users/me").cookie(session)).andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("Max-Age=0")));
        mvc.perform(get("/api/v1/auth/session").cookie(session)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/products").cookie(session)).andExpect(status().isOk());
    }

    @Test
    void changesWithTheCookieMustComeFromTheSite() throws Exception {
        Cookie session = sessionCookie(siteLogin(register()).andReturn().getResponse());
        String rename = "{\"name\":\"Nome novo\"}";

        mvc.perform(patch("/api/v1/users/me").cookie(session).header(HttpHeaders.ORIGIN, "https://evil.example.test")
                        .contentType(MediaType.APPLICATION_JSON).content(rename))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(patch("/api/v1/users/me").cookie(session)
                        .contentType(MediaType.APPLICATION_JSON).content(rename))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/users/me").cookie(session).header(HttpHeaders.REFERER, SITE + "/app/perfil")
                        .contentType(MediaType.APPLICATION_JSON).content(rename))
                .andExpect(status().isOk());
        // The listed CORS origin (the local site on another port in development) is trusted as well.
        mvc.perform(patch("/api/v1/users/me").cookie(session).header(HttpHeaders.ORIGIN, "https://frontend.example.test")
                        .contentType(MediaType.APPLICATION_JSON).content(rename))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Nome novo"));
        mvc.perform(post("/api/v1/auth/logout").cookie(session).header(HttpHeaders.ORIGIN, "https://evil.example.test"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anotherSiteCannotSignTheVisitorIn() throws Exception {
        String email = register();

        mvc.perform(post("/api/v1/auth/login").header(SessionCookies.MODE_HEADER, SessionCookies.MODE_COOKIE)
                        .header(HttpHeaders.ORIGIN, "https://evil.example.test")
                        .contentType(MediaType.APPLICATION_JSON).content(credentials(email)))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void sessionSavedInLocalStorageMovesToTheCookie() throws Exception {
        String email = register();
        var body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(credentials(email))).andReturn().getResponse().getContentAsByteArray();
        String oldToken = json.readTree(body).path("accessToken").asString();

        MockHttpServletResponse response = mvc.perform(site(post("/api/v1/auth/session"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + oldToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andReturn().getResponse();

        mvc.perform(get("/api/v1/users/me").cookie(sessionCookie(response))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + oldToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void localSiteOnAnotherPortMayPreflightTheCookieHeader() throws Exception {
        mvc.perform(options("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, "https://frontend.example.test")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type,x-gomo-session"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    private MockHttpServletRequestBuilder site(MockHttpServletRequestBuilder request) {
        return request.header(SessionCookies.MODE_HEADER, SessionCookies.MODE_COOKIE).header(HttpHeaders.ORIGIN, SITE);
    }

    private String register() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Pessoa sintética", "email", email, "password", PASSWORD))))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        return email;
    }

    private ResultActions siteLogin(String email) throws Exception {
        return mvc.perform(site(post("/api/v1/auth/login")).contentType(MediaType.APPLICATION_JSON)
                .content(credentials(email)));
    }

    private String credentials(String email) throws Exception {
        return json.writeValueAsString(Map.of("email", email, "password", PASSWORD));
    }

    private static Cookie sessionCookie(MockHttpServletResponse response) {
        Cookie cookie = response.getCookie(COOKIE);
        assertThat(cookie).isNotNull();
        assertThat(cookie.getValue()).hasSize(43);
        return new Cookie(COOKIE, cookie.getValue());
    }
}

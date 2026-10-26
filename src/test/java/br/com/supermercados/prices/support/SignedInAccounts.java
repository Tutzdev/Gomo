package br.com.supermercados.prices.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Synthetic accounts for the API tests that run inside one test transaction: registered and signed in through the
 * API, with a confirmed e-mail and, when asked, Premium.
 */
public final class SignedInAccounts {

    private static final String PASSWORD = "synthetic-test-password";

    private final MockMvc mvc;
    private final ObjectMapper json;
    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;

    public SignedInAccounts(MockMvc mvc, ObjectMapper json, JdbcTemplate jdbc, EntityManager entityManager) {
        this.mvc = mvc;
        this.json = json;
        this.jdbc = jdbc;
        this.entityManager = entityManager;
    }

    public record Account(UUID id, String email, String token) {
    }

    public Account free() throws Exception {
        return create(false);
    }

    public Account premium() throws Exception {
        return create(true);
    }

    public ResultActions perform(MockHttpServletRequestBuilder request, Account account) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + account.token())
                .contentType(MediaType.APPLICATION_JSON));
    }

    public JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsByteArray());
    }

    public String write(Object value) {
        return json.writeValueAsString(value);
    }

    private Account create(boolean premium) throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(write(Map.of("name", "Pessoa sintética", "email", email, "password", PASSWORD))))
                .andExpect(status().isAccepted());
        JsonNode login = read(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(write(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk()));
        // The account and its new session are still in this transaction's persistence context: write them out,
        // change the account and forget both, so the next request reads them as a new request would.
        entityManager.flush();
        jdbc.update("update app_users set email_verified_at = current_timestamp, subscriber = ? where email = ?",
                premium, email);
        entityManager.clear();
        return new Account(UUID.fromString(login.path("user").path("id").asString()), email,
                login.path("accessToken").asString());
    }
}

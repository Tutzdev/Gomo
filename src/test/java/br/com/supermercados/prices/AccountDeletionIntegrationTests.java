package br.com.supermercados.prices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import br.com.supermercados.prices.support.PostgresTestDatabase;
import tools.jackson.databind.ObjectMapper;

/** Synthetic accounts in an isolated PostgreSQL schema. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountDeletionIntegrationTests {

    private static final String PASSWORD = "synthetic-test-password";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry);
    }

    @Test
    void ownerDeletesTheAccountWithEverythingItHeld() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        String token = registerAndLogin(email);
        mvc.perform(post("/api/v1/shopping-lists").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Lista sintética\",\"shoppingType\":\"CUSTOM\"}"))
                .andExpect(status().isCreated());

        deleteAccount(token, "wrong-password-123").andExpect(status().isForbidden());
        assertThat(count("select count(*) from app_users where email = ?", email)).isOne();

        deleteAccount(token, PASSWORD).andExpect(status().isNoContent());
        assertThat(count("select count(*) from app_users where email = ?", email)).isZero();
        login(email).andExpect(status().isUnauthorized());
    }

    @Test
    void accountWithPublicRecordsIsAnonymisedInsteadOfDeleted() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        String token = registerAndLogin(email);
        UUID userId = jdbc.queryForObject("select id from app_users where email = ?", UUID.class, email);
        jdbc.update("""
                insert into admin_audit_entries (id, actor_user_id, action, resource_type, resource_id, occurred_at)
                values (?, ?, 'SOURCE_REGISTERED', 'DATA_SOURCE', ?, ?)
                """, UUID.randomUUID(), userId, UUID.randomUUID(), Timestamp.from(Instant.now()));

        deleteAccount(token, PASSWORD).andExpect(status().isNoContent());

        var row = jdbc.queryForMap("select name, email, email_verified_at from app_users where id = ?", userId);
        assertThat(row.get("name")).isEqualTo("Conta excluída");
        assertThat(row.get("email")).isEqualTo("conta-excluida-" + userId + "@gomo.invalid");
        assertThat(count("select count(*) from auth_tokens where user_id = ?", userId)).isZero();
        login(email).andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.ResultActions deleteAccount(String token, String password) throws Exception {
        return mvc.perform(delete("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("password", password))));
    }

    private String registerAndLogin(String email) throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Pessoa sintética", "email", email, "password", PASSWORD))))
                .andExpect(status().isCreated());
        var response = login(email).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        return json.readTree(response).path("accessToken").asString();
    }

    private org.springframework.test.web.servlet.ResultActions login(String email) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD))));
    }

    private long count(String sql, Object argument) {
        Long value = jdbc.queryForObject(sql, Long.class, argument);
        return value == null ? 0 : value;
    }
}

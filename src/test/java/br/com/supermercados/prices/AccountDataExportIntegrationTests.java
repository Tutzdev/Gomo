package br.com.supermercados.prices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
class AccountDataExportIntegrationTests {

    private static final String PASSWORD = "synthetic-test-password";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry);
    }

    @Test
    void ownerDownloadsTheAccountDataWithoutSecrets() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        String token = registerAndLogin(email);
        mvc.perform(post("/api/v1/shopping-lists").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Lista sintética\",\"shoppingType\":\"CUSTOM\"}"))
                .andExpect(status().isCreated());

        var response = mvc.perform(get("/api/v1/users/me/export").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.matchesPattern("attachment; filename=\"gomo-meus-dados-\\d{4}-\\d{2}-\\d{2}\\.json\"")))
                .andReturn().getResponse();

        String body = new String(response.getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        var export = json.readTree(body);
        assertThat(export.path("conta").path("email").asString()).isEqualTo(email);
        assertThat(export.path("conta").path("nome").asString()).isEqualTo("Pessoa sintética");
        assertThat(export.path("conta").path("contaCriadaEm").asString()).matches("\\d{4}-\\d{2}-\\d{2}T.*Z");
        assertThat(export.path("listasDeCompras").get(0).path("nome").asString()).isEqualTo("Lista sintética");
        assertThat(export.path("listasDeCompras").get(0).path("itens").isArray()).isTrue();
        assertThat(export.path("sessoesAbertas").size()).isPositive();
        assertThat(export.path("alertasDePreco").isArray()).isTrue();
        assertThat(body).doesNotContain("password", "hash", "$2a$", token);
    }

    @Test
    void exportNeedsASignedInAccount() throws Exception {
        mvc.perform(get("/api/v1/users/me/export")).andExpect(status().isUnauthorized());
    }

    private String registerAndLogin(String email) throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Pessoa sintética", "email", email, "password", PASSWORD))))
                .andExpect(status().isAccepted());
        var response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        return json.readTree(response).path("accessToken").asString();
    }
}

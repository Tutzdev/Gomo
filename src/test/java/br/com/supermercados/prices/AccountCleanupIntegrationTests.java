package br.com.supermercados.prices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import br.com.supermercados.prices.auth.AccountCleanupService;
import br.com.supermercados.prices.subscription.FreePlan;
import br.com.supermercados.prices.support.PostgresTestDatabase;
import tools.jackson.databind.ObjectMapper;

/** Synthetic accounts in an isolated PostgreSQL schema; the cleanup runs on demand instead of on its cron. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountCleanupIntegrationTests {

    private static final Instant NOW = Instant.now();

    @Autowired private AccountCleanupService cleanup;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry);
    }

    @Test
    void removesWhatNobodyCanUseAndKeepsEverythingStillInUse() {
        UUID abandoned = user(NOW.minus(Duration.ofDays(40)), null, null);
        jdbc.update("""
                INSERT INTO shopping_lists (id, user_id, name, shopping_type, created_at, updated_at)
                VALUES (?, ?, 'Lista sintética', 'CUSTOM', ?, ?)""",
                UUID.randomUUID(), abandoned, ts(NOW.minus(Duration.ofDays(39))), ts(NOW.minus(Duration.ofDays(39))));
        UUID unverifiedButActive = user(NOW.minus(Duration.ofDays(40)), null, NOW.minus(Duration.ofDays(3)));
        UUID recentSignUp = user(NOW.minus(Duration.ofDays(5)), null, null);
        UUID verified = user(NOW.minus(Duration.ofDays(400)), NOW.minus(Duration.ofDays(399)), null);
        UUID unverifiedContributor = user(NOW.minus(Duration.ofDays(40)), null, null);
        jdbc.update("""
                INSERT INTO admin_audit_entries (id, actor_user_id, action, resource_type, resource_id, occurred_at)
                VALUES (?, ?, 'SOURCE_REGISTERED', 'DATA_SOURCE', ?, ?)""",
                UUID.randomUUID(), unverifiedContributor, UUID.randomUUID(), ts(NOW));

        UUID expiredSession = session(verified, NOW.minus(Duration.ofDays(3)), NOW.minus(Duration.ofDays(2)));
        UUID currentSession = session(verified, NOW.minus(Duration.ofHours(1)), NOW.plus(Duration.ofHours(11)));
        UUID expiredReset = accountToken("password_reset_tokens", verified, NOW.minus(Duration.ofHours(1)));
        UUID currentVerification = accountToken("email_verification_tokens", recentSignUp, NOW.plus(Duration.ofHours(20)));

        LocalDate today = LocalDate.now(FreePlan.USAGE_ZONE);
        usage(verified, today.minusDays(30));
        usage(verified, today.minusDays(7));
        usage(verified, today);

        AccountCleanupService.Result result = cleanup.cleanUp();

        assertThat(exists("app_users", abandoned)).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM shopping_lists WHERE user_id = ?", Integer.class, abandoned))
                .isZero();
        assertThat(exists("app_users", unverifiedButActive)).isTrue();
        assertThat(exists("app_users", recentSignUp)).isTrue();
        assertThat(exists("app_users", verified)).isTrue();
        assertThat(exists("app_users", unverifiedContributor)).isTrue();
        assertThat(exists("auth_tokens", expiredSession)).isFalse();
        assertThat(exists("auth_tokens", currentSession)).isTrue();
        assertThat(exists("password_reset_tokens", expiredReset)).isFalse();
        assertThat(exists("email_verification_tokens", currentVerification)).isTrue();
        assertThat(jdbc.queryForList("SELECT usage_date FROM comparison_usage WHERE user_id = ? ORDER BY usage_date",
                Date.class, verified)).extracting(Date::toLocalDate).containsExactly(today.minusDays(7), today);
        assertThat(result.unverifiedAccounts()).isGreaterThanOrEqualTo(1);
        assertThat(result.sessions()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void signingInCountsAsUseOfAnUnverifiedAccount() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                Map.of("name", "Pessoa sintética", "email", email, "password", "synthetic-test-password"))))
                .andExpect(status().is2xxSuccessful());
        UUID userId = jdbc.queryForObject("SELECT id FROM app_users WHERE email = ?", UUID.class, email);
        jdbc.update("UPDATE app_users SET created_at = ? WHERE id = ?", ts(NOW.minus(Duration.ofDays(60))), userId);

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                Map.of("email", email, "password", "synthetic-test-password")))).andExpect(status().isOk());
        cleanup.cleanUp();

        assertThat(exists("app_users", userId)).isTrue();
        assertThat(jdbc.queryForObject("SELECT last_login_at FROM app_users WHERE id = ?", Timestamp.class, userId)
                .toInstant()).isAfter(NOW.minus(Duration.ofMinutes(5)));
    }

    private UUID user(Instant createdAt, Instant verifiedAt, Instant lastLoginAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO app_users (id, name, email, password_hash, created_at, updated_at, email_verified_at,
                                       last_login_at)
                VALUES (?, 'Pessoa sintética', ?, 'not-a-hash', ?, ?, ?, ?)""",
                id, id + "@example.com", ts(createdAt), ts(createdAt), ts(verifiedAt), ts(lastLoginAt));
        return id;
    }

    private UUID session(UUID userId, Instant createdAt, Instant expiresAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO auth_tokens (id, user_id, token_hash, created_at, expires_at) VALUES (?, ?, ?, ?, ?)",
                id, userId, hash(), ts(createdAt), ts(expiresAt));
        return id;
    }

    private UUID accountToken(String table, UUID userId, Instant expiresAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO " + table + " (id, user_id, token_hash, created_at, expires_at) VALUES (?, ?, ?, ?, ?)",
                id, userId, hash(), ts(expiresAt.minus(Duration.ofHours(2))), ts(expiresAt));
        return id;
    }

    private void usage(UUID userId, LocalDate day) {
        jdbc.update("INSERT INTO comparison_usage (user_id, usage_date, subject_id, created_at) VALUES (?, ?, ?, ?)",
                userId, Date.valueOf(day), UUID.randomUUID(), ts(NOW));
    }

    private boolean exists(String table, UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM " + table + " WHERE id = ?)", Boolean.class, id));
    }

    private static String hash() {
        return (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "");
    }

    private static Timestamp ts(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}

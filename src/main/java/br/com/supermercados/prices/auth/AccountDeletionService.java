package br.com.supermercados.prices.auth;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.supermercados.prices.common.ApiException;

/**
 * Deletes an account at its owner's request (LGPD art. 18). Lists, alerts, preferences, sessions and usage go
 * with it. Price contributions and admin audit entries are public records other people relied on, so an
 * account that has them is anonymised instead: no name, no e-mail, no way to sign in.
 */
@Service
public class AccountDeletionService {

    private static final SecureRandom RANDOM = new SecureRandom();
    /** Everything that belongs only to the person; shopping list items go with their lists. */
    private static final List<String> PERSONAL_DATA = List.of(
            "DELETE FROM shopping_lists WHERE user_id = ?",
            "DELETE FROM alert_notifications WHERE user_id = ?",
            "DELETE FROM price_alerts WHERE user_id = ?",
            "DELETE FROM favorite_stores WHERE user_id = ?",
            "DELETE FROM user_preferences WHERE user_id = ?",
            "DELETE FROM comparison_usage WHERE user_id = ?",
            "DELETE FROM email_verification_tokens WHERE user_id = ?",
            "DELETE FROM password_reset_tokens WHERE user_id = ?",
            "DELETE FROM auth_tokens WHERE user_id = ?");

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public AccountDeletionService(JdbcTemplate jdbc, PasswordEncoder passwordEncoder, Clock clock) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public void delete(UUID userId, String password) {
        List<String> hashes = jdbc.queryForList("SELECT password_hash FROM app_users WHERE id = ? FOR UPDATE",
                String.class, userId);
        if (hashes.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "Usuário não encontrado.");
        if (password == null || !passwordEncoder.matches(password, hashes.getFirst())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Senha incorreta.");
        }
        PERSONAL_DATA.forEach(statement -> jdbc.update(statement, userId));
        Boolean keepsPublicRecords = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM price_contributions WHERE contributor_id = ? OR moderator_id = ?)
                    OR EXISTS (SELECT 1 FROM admin_audit_entries WHERE actor_user_id = ?)
                """, Boolean.class, userId, userId, userId);
        if (!Boolean.TRUE.equals(keepsPublicRecords)) {
            jdbc.update("DELETE FROM app_users WHERE id = ?", userId);
            return;
        }
        byte[] unusable = new byte[32];
        RANDOM.nextBytes(unusable);
        jdbc.update("""
                UPDATE app_users SET name = 'Conta excluída', email = ?, password_hash = ?, role = 'USER',
                    email_verified_at = NULL, subscriber = FALSE, preferred_billing_cycle = NULL,
                    updated_at = ?, version = version + 1
                WHERE id = ?
                """, "conta-excluida-" + userId + "@gomo.invalid",
                // Not a BCrypt hash, so no password can ever match it.
                "excluida:" + HexFormat.of().formatHex(unusable), Timestamp.from(clock.instant()), userId);
    }
}

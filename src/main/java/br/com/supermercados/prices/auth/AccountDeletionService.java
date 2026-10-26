package br.com.supermercados.prices.auth;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
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
            // Purchase items go with their purchases.
            "DELETE FROM purchases WHERE user_id = ?",
            // Leaves the lists other people shared; the person's own lists take their members and invites along.
            "DELETE FROM shopping_list_members WHERE user_id = ?",
            "DELETE FROM shopping_list_invites WHERE created_by = ?",
            "UPDATE shopping_list_items SET checked_by = NULL WHERE checked_by = ?",
            "DELETE FROM shopping_lists WHERE user_id = ?",
            "DELETE FROM alert_notifications WHERE user_id = ?",
            "DELETE FROM price_alerts WHERE user_id = ?",
            "DELETE FROM favorite_stores WHERE user_id = ?",
            "DELETE FROM user_preferences WHERE user_id = ?",
            "DELETE FROM comparison_usage WHERE user_id = ?",
            // The provider keeps the payment records; the subscription was cancelled before this point.
            "DELETE FROM billing_checkouts WHERE user_id = ?",
            "DELETE FROM billing_subscriptions WHERE user_id = ?",
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

    /** Lets the caller confirm the owner before steps that must happen outside the deletion (paid plan). */
    @Transactional(readOnly = true)
    public void verifyPassword(UUID userId, String password) {
        checkPassword(userId, password, "SELECT password_hash FROM app_users WHERE id = ?");
    }

    /**
     * An account {@code u} never confirmed, created and last signed in before the two {@code ?} instants, with no
     * public records and no payment: nobody relies on it, so it can simply go.
     */
    static final String ABANDONED_ACCOUNT = """
            u.email_verified_at IS NULL AND u.role = 'USER' AND NOT u.subscriber AND u.premium_paid_until IS NULL
                AND u.created_at < ? AND coalesce(u.last_login_at, u.created_at) < ?
                AND NOT EXISTS (SELECT 1 FROM price_contributions c WHERE c.contributor_id = u.id OR c.moderator_id = u.id)
                AND NOT EXISTS (SELECT 1 FROM admin_audit_entries a WHERE a.actor_user_id = u.id)
                AND NOT EXISTS (SELECT 1 FROM billing_subscriptions b WHERE b.user_id = u.id)
            """;

    /**
     * Removes an {@link #ABANDONED_ACCOUNT abandoned account}, checked again under a row lock so a confirmation or
     * sign-in that happens meanwhile keeps it. Returns whether the account was removed.
     */
    @Transactional
    public boolean deleteAbandonedAccount(UUID userId, Instant inactiveSince) {
        List<UUID> locked = jdbc.queryForList("SELECT u.id FROM app_users u WHERE u.id = ? AND " + ABANDONED_ACCOUNT
                + " FOR UPDATE", UUID.class, userId, Timestamp.from(inactiveSince), Timestamp.from(inactiveSince));
        if (locked.isEmpty()) return false;
        PERSONAL_DATA.forEach(statement -> jdbc.update(statement, userId));
        jdbc.update("DELETE FROM app_users WHERE id = ?", userId);
        return true;
    }

    @Transactional
    public void delete(UUID userId, String password) {
        checkPassword(userId, password, "SELECT password_hash FROM app_users WHERE id = ? FOR UPDATE");
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
                    premium_paid_until = NULL,
                    updated_at = ?, version = version + 1
                WHERE id = ?
                """, "conta-excluida-" + userId + "@gomo.invalid",
                // Not a BCrypt hash, so no password can ever match it.
                "excluida:" + HexFormat.of().formatHex(unusable), Timestamp.from(clock.instant()), userId);
    }

    private void checkPassword(UUID userId, String password, String query) {
        List<String> hashes = jdbc.queryForList(query, String.class, userId);
        if (hashes.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "Usuário não encontrado.");
        if (password == null || !passwordEncoder.matches(password, hashes.getFirst())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Senha incorreta.");
        }
    }
}

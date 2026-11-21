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

@Service
public class AccountDeletionService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final List<String> PERSONAL_DATA = List.of(
        "DELETE FROM purchases WHERE user_id = ?",
        "DELETE FROM shopping_list_members WHERE user_id = ?",
        "DELETE FROM shopping_list_invites WHERE created_by = ?",
        "UPDATE shopping_list_items SET checked_by = NULL WHERE checked_by = ?",
        "DELETE FROM shopping_lists WHERE user_id = ?",
        "DELETE FROM alert_notifications WHERE user_id = ?",
        "DELETE FROM whatsapp_contacts WHERE user_id = ?",
        "DELETE FROM price_alerts WHERE user_id = ?",
        "DELETE FROM favorite_stores WHERE user_id = ?",
        "DELETE FROM user_preferences WHERE user_id = ?",
        "DELETE FROM comparison_usage WHERE user_id = ?",
        "DELETE FROM billing_checkouts WHERE user_id = ?",
        "DELETE FROM billing_subscriptions WHERE user_id = ?",
        "DELETE FROM email_verification_tokens WHERE user_id = ?",
        "DELETE FROM password_reset_tokens WHERE user_id = ?",
        "DELETE FROM auth_tokens WHERE user_id = ?");

    static final String ABANDONED_ACCOUNT = """
            u.email_verified_at IS NULL
                AND u.role = 'USER'
                AND NOT u.subscriber
                AND u.premium_paid_until IS NULL
                AND u.created_at < ?
                AND coalesce(u.last_login_at, u.created_at) < ?
                AND NOT EXISTS (SELECT 1 FROM price_contributions c WHERE c.contributor_id = u.id OR c.moderator_id = u.id)
                AND NOT EXISTS (SELECT 1 FROM admin_audit_entries a WHERE a.actor_user_id = u.id)
                AND NOT EXISTS (SELECT 1 FROM billing_subscriptions b WHERE b.user_id = u.id)
            """;

    private static final String LOCK_ABANDONED_ACCOUNT =
            "SELECT u.id FROM app_users u WHERE u.id = ? AND " + ABANDONED_ACCOUNT + " FOR UPDATE";

    private static final String HAS_PUBLIC_RECORDS = """
            SELECT EXISTS (SELECT 1 FROM price_contributions WHERE contributor_id = ? OR moderator_id = ?)
                OR EXISTS (SELECT 1 FROM admin_audit_entries WHERE actor_user_id = ?)
            """;

    private static final String ANONYMISE_ACCOUNT = """
            UPDATE app_users SET
                name = 'Conta excluída',
                email = ?,
                password_hash = ?,
                role = 'USER',
                email_verified_at = NULL,
                subscriber = FALSE,
                preferred_billing_cycle = NULL,
                premium_paid_until = NULL,
                updated_at = ?,
                version = version + 1
            WHERE id = ?
            """;

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public AccountDeletionService(JdbcTemplate jdbc, PasswordEncoder passwordEncoder, Clock clock) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public void verifyPassword(UUID userId, String password) {
        checkPassword(userId, password, "SELECT password_hash FROM app_users WHERE id = ?");
    }

    @Transactional
    public boolean deleteAbandonedAccount(UUID userId, Instant inactiveSince) {
        Timestamp inactiveSinceTimestamp = Timestamp.from(inactiveSince);

        List<UUID> locked = jdbc.queryForList(
            LOCK_ABANDONED_ACCOUNT,
            UUID.class,
            userId,
            inactiveSinceTimestamp,
            inactiveSinceTimestamp);

        if (locked.isEmpty()) {return false;}

        deletePersonalData(userId);
        jdbc.update("DELETE FROM app_users WHERE id = ?", userId);

        return true;
    }

    @Transactional
    public void delete(UUID userId, String password) {
        checkPassword(userId, password, "SELECT password_hash FROM app_users WHERE id = ? FOR UPDATE");
        deletePersonalData(userId);

        if (hasPublicRecords(userId)) {
            anonymise(userId);
            return;
        }

        jdbc.update("DELETE FROM app_users WHERE id = ?", userId);
    }

    private void deletePersonalData(UUID userId) {
        PERSONAL_DATA.forEach(statement -> jdbc.update(statement, userId));
    }

    private boolean hasPublicRecords(UUID userId) {
        Boolean found = jdbc.queryForObject(
            HAS_PUBLIC_RECORDS,
            Boolean.class,
            userId,
            userId,
            userId);

        return Boolean.TRUE.equals(found);
    }

    private void anonymise(UUID userId) {
        jdbc.update(
            ANONYMISE_ACCOUNT,
            "conta-excluida-" + userId + "@gomo.invalid",
            unusablePasswordHash(),
            Timestamp.from(clock.instant()),
            userId);
    }

    private static String unusablePasswordHash() {
        byte[] randomBytes = new byte[32];
        RANDOM.nextBytes(randomBytes);

        return "excluida:" + HexFormat.of().formatHex(randomBytes);
    }

    private void checkPassword(UUID userId, String password, String query) {
        List<String> hashes = jdbc.queryForList(query, String.class, userId);

        if (hashes.isEmpty()) {throw new ApiException(HttpStatus.NOT_FOUND, "Usuário não encontrado.");}

        if (password == null || !passwordEncoder.matches(password, hashes.getFirst())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Senha incorreta.");
        }
    }
}

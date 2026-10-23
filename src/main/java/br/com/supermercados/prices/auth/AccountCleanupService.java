package br.com.supermercados.prices.auth;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import br.com.supermercados.prices.subscription.FreePlan;

/**
 * Daily removal of what nobody can use any more: expired sessions and account tokens of people who never came
 * back, accounts never confirmed that went unused for {@code app.cleanup.unverified-account-retention}, and
 * free-plan usage older than {@code app.cleanup.comparison-usage-retention} (only today's counts matter).
 */
@Service
public class AccountCleanupService {

    private static final Logger log = LoggerFactory.getLogger(AccountCleanupService.class);
    /** One run removes at most this many accounts; the rest go on the next day. */
    static final int ACCOUNTS_PER_RUN = 500;

    private final JdbcTemplate jdbc;
    private final AccountDeletionService deletion;
    private final Clock clock;
    private final Duration unverifiedAccountRetention;
    private final Duration comparisonUsageRetention;

    public AccountCleanupService(JdbcTemplate jdbc, AccountDeletionService deletion, Clock clock,
            @Value("${app.cleanup.unverified-account-retention:P30D}") Duration unverifiedAccountRetention,
            @Value("${app.cleanup.comparison-usage-retention:P7D}") Duration comparisonUsageRetention) {
        // The free-plan limit reads today's usage, so at least one full day has to stay.
        if (unverifiedAccountRetention.compareTo(Duration.ofDays(1)) < 0
                || comparisonUsageRetention.compareTo(Duration.ofDays(1)) < 0) {
            throw new IllegalArgumentException("app.cleanup retentions must be at least one day");
        }
        this.jdbc = jdbc;
        this.deletion = deletion;
        this.clock = clock;
        this.unverifiedAccountRetention = unverifiedAccountRetention;
        this.comparisonUsageRetention = comparisonUsageRetention;
    }

    @Scheduled(cron = "${app.cleanup.cron:0 30 4 * * *}", zone = "${app.collection.zone:America/Sao_Paulo}")
    public void scheduledCleanUp() {
        try {
            Result result = cleanUp();
            log.info("Account cleanup: {} sessions, {} account tokens, {} unverified accounts, {} usage rows removed",
                    result.sessions(), result.accountTokens(), result.unverifiedAccounts(), result.comparisonUsage());
        } catch (RuntimeException exception) {
            log.error("Account cleanup failed", exception);
        }
    }

    /** Each step commits on its own, so a failure halfway keeps what was already cleaned. */
    public Result cleanUp() {
        Instant now = clock.instant();
        Timestamp nowTimestamp = Timestamp.from(now);
        int sessions = jdbc.update("DELETE FROM auth_tokens WHERE expires_at <= ?", nowTimestamp);
        int accountTokens = jdbc.update("DELETE FROM email_verification_tokens WHERE expires_at <= ?", nowTimestamp)
                + jdbc.update("DELETE FROM password_reset_tokens WHERE expires_at <= ?", nowTimestamp);

        Instant inactiveSince = now.minus(unverifiedAccountRetention);
        List<UUID> candidates = jdbc.queryForList("SELECT u.id FROM app_users u WHERE "
                + AccountDeletionService.ABANDONED_ACCOUNT + " ORDER BY u.created_at LIMIT ?", UUID.class,
                Timestamp.from(inactiveSince), Timestamp.from(inactiveSince), ACCOUNTS_PER_RUN);
        int unverifiedAccounts = 0;
        for (UUID userId : candidates) {
            if (deletion.deleteAbandonedAccount(userId, inactiveSince)) unverifiedAccounts++;
        }

        LocalDate today = LocalDate.ofInstant(now, FreePlan.USAGE_ZONE);
        LocalDate oldestKept = today.minusDays(comparisonUsageRetention.toDays());
        int comparisonUsage = jdbc.update("DELETE FROM comparison_usage WHERE usage_date < ?", Date.valueOf(oldestKept));
        return new Result(sessions, accountTokens, unverifiedAccounts, comparisonUsage);
    }

    public record Result(int sessions, int accountTokens, int unverifiedAccounts, int comparisonUsage) {
    }
}

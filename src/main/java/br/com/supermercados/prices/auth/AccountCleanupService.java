package br.com.supermercados.prices.auth;

import br.com.supermercados.prices.subscription.FreePlan;
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

@Service
public class AccountCleanupService {

    private static final Logger log = LoggerFactory.getLogger(AccountCleanupService.class);
    private static final Duration MINIMUM_RETENTION = Duration.ofDays(1);

    static final int ACCOUNTS_PER_RUN = 500;

    private static final String ABANDONED_ACCOUNTS = "SELECT u.id FROM app_users u WHERE "
            + AccountDeletionService.ABANDONED_ACCOUNT
            + " ORDER BY u.created_at LIMIT ?";

    private final JdbcTemplate jdbc;
    private final AccountDeletionService deletion;
    private final Clock clock;
    private final Duration unverifiedAccountRetention;
    private final Duration comparisonUsageRetention;

    public AccountCleanupService(
        JdbcTemplate jdbc,
        AccountDeletionService deletion,
        Clock clock,
        @Value("${app.cleanup.unverified-account-retention:P30D}") Duration unverifiedAccountRetention,
        @Value("${app.cleanup.comparison-usage-retention:P7D}") Duration comparisonUsageRetention) {

        if (isShorterThanMinimum(unverifiedAccountRetention) || isShorterThanMinimum(comparisonUsageRetention)) {
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

            log.info(
                "Account cleanup: {} sessions, {} account tokens, {} unverified accounts, {} usage rows removed",
                result.sessions(),
                result.accountTokens(),
                result.unverifiedAccounts(),
                result.comparisonUsage());
        } catch (RuntimeException exception) {
            log.error("Account cleanup failed", exception);
        }
    }

    public Result cleanUp() {
        Instant now = clock.instant();

        return new Result(
            deleteExpiredSessions(now),
            deleteExpiredAccountTokens(now),
            deleteAbandonedAccounts(now),
            deleteOldComparisonUsage(now));
    }

    private int deleteExpiredSessions(Instant now) {
        return jdbc.update("DELETE FROM auth_tokens WHERE expires_at <= ?", Timestamp.from(now));
    }

    private int deleteExpiredAccountTokens(Instant now) {
        Timestamp nowTimestamp = Timestamp.from(now);

        return jdbc.update("DELETE FROM email_verification_tokens WHERE expires_at <= ?", nowTimestamp)
                + jdbc.update("DELETE FROM password_reset_tokens WHERE expires_at <= ?", nowTimestamp);
    }

    private int deleteAbandonedAccounts(Instant now) {
        Instant inactiveSince = now.minus(unverifiedAccountRetention);
        Timestamp inactiveSinceTimestamp = Timestamp.from(inactiveSince);

        List<UUID> candidates = jdbc.queryForList(
            ABANDONED_ACCOUNTS,
            UUID.class,
            inactiveSinceTimestamp,
            inactiveSinceTimestamp,
            ACCOUNTS_PER_RUN);

        int deleted = 0;

        for (UUID userId : candidates) {
            if (deletion.deleteAbandonedAccount(userId, inactiveSince)) {deleted++;}
        }

        return deleted;
    }

    private int deleteOldComparisonUsage(Instant now) {
        LocalDate today = LocalDate.ofInstant(now, FreePlan.USAGE_ZONE);
        LocalDate oldestKept = today.minusDays(comparisonUsageRetention.toDays());

        return jdbc.update("DELETE FROM comparison_usage WHERE usage_date < ?", Date.valueOf(oldestKept));
    }

    private static boolean isShorterThanMinimum(Duration retention) {
        return retention.compareTo(MINIMUM_RETENTION) < 0;
    }

    public record Result(int sessions, int accountTokens, int unverifiedAccounts, int comparisonUsage) {}
}

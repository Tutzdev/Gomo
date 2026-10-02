package br.com.supermercados.prices.subscription;

import br.com.supermercados.prices.auth.AuthenticatedUser;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Counts the complete product comparisons a free account makes each day. */
@Service
public class ComparisonUsageService {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public ComparisonUsageService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * Registers a comparison of {@code subjectId} (a catalog item or a product) and returns what the caller may see.
     * Comparing the same product again on the same day is free; a new product after the daily limit shows only
     * the cheapest store.
     */
    @Transactional
    public ComparisonAccess startComparison(AuthenticatedUser user, UUID subjectId) {
        if (AuthenticatedUser.hasPremium(user)) {
            return ComparisonAccess.premiumAccess();
        }
        if (user == null) {
            return ComparisonAccess.uncountedFreeAccess();
        }

        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, FreePlan.USAGE_ZONE);
        int used = countUsage(user.id(), today);
        if (wasCounted(user.id(), today, subjectId)) {
            return ComparisonAccess.freeAccess(used);
        }
        if (used >= FreePlan.DAILY_COMPARISONS) {
            return ComparisonAccess.dailyLimitReachedAccess(used);
        }

        int inserted = jdbc.update("""
                INSERT INTO comparison_usage (user_id, usage_date, subject_id, created_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT DO NOTHING
                """, user.id(), today, subjectId, Timestamp.from(now));
        return ComparisonAccess.freeAccess(used + inserted);
    }

    /** What the caller may see without registering a new comparison. */
    @Transactional(readOnly = true)
    public ComparisonAccess currentAccess(AuthenticatedUser user) {
        if (AuthenticatedUser.hasPremium(user)) {
            return ComparisonAccess.premiumAccess();
        }
        if (user == null) {
            return ComparisonAccess.uncountedFreeAccess();
        }
        return ComparisonAccess.freeAccess(countToday(user.id()));
    }

    @Transactional(readOnly = true)
    public int countToday(UUID userId) {
        return countUsage(userId, LocalDate.ofInstant(clock.instant(), FreePlan.USAGE_ZONE));
    }

    private int countUsage(UUID userId, LocalDate day) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM comparison_usage WHERE user_id = ? AND usage_date = ?",
                Integer.class, userId, day);
        return count == null ? 0 : count;
    }

    private boolean wasCounted(UUID userId, LocalDate day, UUID subjectId) {
        Boolean exists = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM comparison_usage WHERE user_id = ? AND usage_date = ? AND subject_id = ?)
                """, Boolean.class, userId, day, subjectId);
        return Boolean.TRUE.equals(exists);
    }
}

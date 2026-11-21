package br.com.supermercados.prices.whatsapp;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class WhatsAppDeliveryRepository {

    private static final int LONGEST_ERROR = 300;

    private static final String PENDING = """
            SELECT n.id, c.phone_number, p.name AS product_name, s.name AS store_name, n.unit_price
            FROM alert_notifications n
            JOIN whatsapp_contacts c ON c.user_id = n.user_id AND n.created_at >= c.consented_at
            JOIN price_alerts a ON a.id = n.alert_id
            JOIN products p ON p.id = a.product_id
            JOIN stores s ON s.id = n.store_id
            LEFT JOIN whatsapp_deliveries d ON d.notification_id = n.id
            WHERE n.created_at > ?
              AND d.sent_at IS NULL
              AND coalesce(d.attempts, 0) < ?
              AND (d.next_attempt_at IS NULL OR d.next_attempt_at <= ?)
            ORDER BY n.created_at
            LIMIT ?
            """;

    private static final String RECORD_SENT = """
            INSERT INTO whatsapp_deliveries (notification_id, attempts, sent_at)
            VALUES (?, 1, ?)
            ON CONFLICT (notification_id) DO UPDATE SET
                attempts = whatsapp_deliveries.attempts + 1,
                sent_at = EXCLUDED.sent_at,
                next_attempt_at = NULL,
                last_error = NULL
            """;

    private static final String RECORD_FAILURE = """
            INSERT INTO whatsapp_deliveries (notification_id, attempts, next_attempt_at, last_error)
            VALUES (?, 1, ?, ?)
            ON CONFLICT (notification_id) DO UPDATE SET
                attempts = whatsapp_deliveries.attempts + 1,
                next_attempt_at = EXCLUDED.next_attempt_at,
                last_error = EXCLUDED.last_error
            """;

    private final JdbcTemplate jdbc;

    WhatsAppDeliveryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    List<PendingAlert> findPending(
        Instant createdAfter,
        int maxAttempts,
        Instant now,
        int limit) {

        return jdbc.query(
            PENDING,
            (row, index) -> new PendingAlert(
                row.getObject("id", UUID.class),
                new WhatsAppNumber(row.getString("phone_number")),
                row.getString("product_name"),
                row.getString("store_name"),
                row.getBigDecimal("unit_price")),
            Timestamp.from(createdAfter),
            maxAttempts,
            Timestamp.from(now),
            limit);
    }

    void recordSent(UUID notificationId, Instant sentAt) {
        jdbc.update(RECORD_SENT, notificationId, Timestamp.from(sentAt));
    }

    void recordFailure(UUID notificationId, Instant nextAttemptAt, String error) {
        jdbc.update(
            RECORD_FAILURE,
            notificationId,
            Timestamp.from(nextAttemptAt),
            error.length() <= LONGEST_ERROR ? error : error.substring(0, LONGEST_ERROR));
    }
}

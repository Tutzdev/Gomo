package br.com.supermercados.prices.alert;

import br.com.supermercados.prices.common.ApiException;
import br.com.supermercados.prices.common.PageResponse;
import java.time.Clock;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlertNotificationService {

    private final AlertNotificationRepository notifications;
    private final Clock clock;

    public AlertNotificationService(AlertNotificationRepository notifications, Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<AlertNotificationResponse> findMine(UUID userId, Pageable pageable) {
        return PageResponse.from(notifications.findByUserId(userId, pageable)
                .map(AlertNotificationResponse::from));
    }

    @Transactional(readOnly = true)
    public UnreadNotificationsResponse countUnread(UUID userId) {
        return new UnreadNotificationsResponse(notifications.countByUserIdAndReadAtIsNull(userId));
    }

    @Transactional
    public AlertNotificationResponse markRead(UUID userId, UUID notificationId) {
        AlertNotification notification = notifications.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Notificação não encontrada."));
        notification.markRead(clock.instant());
        return AlertNotificationResponse.from(notification);
    }
}

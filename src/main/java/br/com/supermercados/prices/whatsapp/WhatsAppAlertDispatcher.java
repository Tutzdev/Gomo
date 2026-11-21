package br.com.supermercados.prices.whatsapp;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class WhatsAppAlertDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(WhatsAppAlertDispatcher.class);

    private final WhatsAppDeliveryRepository deliveries;
    private final WhatsAppCloudClient client;
    private final WhatsAppProperties properties;
    private final Clock clock;

    WhatsAppAlertDispatcher(
        WhatsAppDeliveryRepository deliveries,
        WhatsAppCloudClient client,
        WhatsAppProperties properties,
        Clock clock) {

        this.deliveries = deliveries;
        this.client = client;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.whatsapp.dispatch-delay:PT1M}", initialDelayString = "PT1M")
    void dispatch() {
        if (!properties.enabled()) {return;}

        Instant now = clock.instant();

        deliveries.findPending(
            now.minus(properties.maxDelay()),
            properties.maxAttempts(),
            now,
            properties.batchSize())
                .forEach(alert -> deliver(alert, now));
    }

    private void deliver(PendingAlert alert, Instant now) {
        try {
            client.sendPriceAlert(alert);
            deliveries.recordSent(alert.notificationId(), now);
        } catch (WhatsAppDeliveryException exception) {
            LOGGER.warn("Aviso {} não saiu no WhatsApp: {}", alert.notificationId(), exception.getMessage());
            deliveries.recordFailure(alert.notificationId(), now.plus(properties.retryDelay()), exception.getMessage());
        }
    }
}

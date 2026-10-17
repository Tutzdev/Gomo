package br.com.supermercados.prices.subscription.abacatepay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.supermercados.prices.subscription.PaidSubscriptionService;

/**
 * Receives AbacatePay webhooks. A 2xx tells the provider the event was handled; a 5xx makes it retry later,
 * which is what a database failure here should do. The body is read as bytes because the HMAC covers them.
 */
@RestController
public class AbacatePayWebhookController {

    private static final Logger log = LoggerFactory.getLogger(AbacatePayWebhookController.class);

    private final AbacatePayWebhook webhook;
    private final PaidSubscriptionService subscriptions;

    public AbacatePayWebhookController(AbacatePayWebhook webhook, PaidSubscriptionService subscriptions) {
        this.webhook = webhook;
        this.subscriptions = subscriptions;
    }

    @PostMapping("/api/v1/billing/abacatepay/webhook")
    public ResponseEntity<Void> receive(@RequestParam(name = "webhookSecret", required = false) String secret,
            @RequestHeader(name = "X-Webhook-Signature", required = false) String signature,
            @RequestBody byte[] body) {
        webhook.read(secret, signature, body).ifPresentOrElse(subscriptions::apply,
                () -> log.warn("Webhook da AbacatePay de outro ambiente ignorado"));
        return ResponseEntity.ok().build();
    }
}

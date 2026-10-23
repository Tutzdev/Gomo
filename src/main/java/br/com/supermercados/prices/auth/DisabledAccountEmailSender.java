package br.com.supermercados.prices.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "false", matchIfMissing = true)
class DisabledAccountEmailSender implements AccountEmailSender {

    private static final Logger log = LoggerFactory.getLogger(DisabledAccountEmailSender.class);

    @Override
    public void sendEmailVerification(String recipient, String token) {
        log.warn("E-mail delivery is disabled; verification message was not sent");
    }

    @Override
    public void sendPasswordReset(String recipient, String token) {
        log.warn("E-mail delivery is disabled; password reset message was not sent");
    }

    @Override
    public void sendExistingAccountNotice(String recipient) {
        log.warn("E-mail delivery is disabled; existing account notice was not sent");
    }
}

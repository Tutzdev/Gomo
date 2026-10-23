package br.com.supermercados.prices.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "true")
class SmtpAccountEmailSender implements AccountEmailSender {

    private final JavaMailSender mailSender;
    private final String sender;

    SmtpAccountEmailSender(JavaMailSender mailSender, @Value("${app.mail.from}") String sender) {
        this.mailSender = mailSender;
        this.sender = sender;
    }

    @Override
    public void sendEmailVerification(String recipient, String token) {
        send(recipient, "Confirme seu e-mail",
                "Use este token em POST /api/v1/auth/email-verifications/confirm:\n\n" + token);
    }

    @Override
    public void sendPasswordReset(String recipient, String token) {
        send(recipient, "Redefinição de senha",
                "Use este token em POST /api/v1/auth/password-resets/confirm:\n\n" + token);
    }

    @Override
    public void sendExistingAccountNotice(String recipient) {
        send(recipient, "Tentativa de cadastro com seu e-mail",
                "Alguém tentou criar uma conta no Gomo com este e-mail, mas ele já tem uma conta.\n\n"
                        + "Se foi você, entre com sua senha ou use \"Esqueci minha senha\" para criar uma nova.\n"
                        + "Se não foi você, pode ignorar esta mensagem: nada mudou na sua conta.");
    }

    private void send(String recipient, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(sender);
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
    }
}

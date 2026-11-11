package br.com.supermercados.prices.auth;

interface AccountEmailSender {

    void sendEmailVerification(String recipient, String token);

    void sendPasswordReset(String recipient, String token);

    void sendExistingAccountNotice(String recipient);
}

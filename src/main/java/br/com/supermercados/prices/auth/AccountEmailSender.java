package br.com.supermercados.prices.auth;

interface AccountEmailSender {

    void sendEmailVerification(String recipient, String token);

    void sendPasswordReset(String recipient, String token);

    /** Someone tried to sign up with an e-mail that already has an account; only its owner learns that. */
    void sendExistingAccountNotice(String recipient);
}

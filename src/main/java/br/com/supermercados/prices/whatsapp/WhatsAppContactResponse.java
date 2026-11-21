package br.com.supermercados.prices.whatsapp;

import java.time.Instant;

public record WhatsAppContactResponse(
        boolean available,
        String phoneNumber,
        Instant consentedAt) {

    static WhatsAppContactResponse of(boolean available, WhatsAppContact contact) {
        if (contact == null) {return new WhatsAppContactResponse(available, null, null);}

        return new WhatsAppContactResponse(
            available,
            contact.getPhoneNumber(),
            contact.getConsentedAt());
    }
}

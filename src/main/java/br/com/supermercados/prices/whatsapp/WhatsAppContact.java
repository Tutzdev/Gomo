package br.com.supermercados.prices.whatsapp;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "whatsapp_contacts")
class WhatsAppContact {

    @Id
    private UUID userId;

    private String phoneNumber;

    private Instant consentedAt;

    protected WhatsAppContact() {}

    WhatsAppContact(UUID userId, WhatsAppNumber number, Instant consentedAt) {
        this.userId = userId;
        this.phoneNumber = number.value();
        this.consentedAt = consentedAt;
    }

    void change(WhatsAppNumber number, Instant now) {
        if (phoneNumber.equals(number.value())) {return;}

        phoneNumber = number.value();
        consentedAt = now;
    }

    String getPhoneNumber() {
        return phoneNumber;
    }

    Instant getConsentedAt() {
        return consentedAt;
    }
}

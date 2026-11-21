package br.com.supermercados.prices.whatsapp;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.supermercados.prices.common.ApiException;

@Service
public class WhatsAppContactService {

    private final WhatsAppContactRepository contacts;
    private final WhatsAppProperties properties;
    private final Clock clock;

    public WhatsAppContactService(WhatsAppContactRepository contacts, WhatsAppProperties properties, Clock clock) {
        this.contacts = contacts;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public WhatsAppContactResponse find(UUID userId) {
        return WhatsAppContactResponse.of(properties.enabled(), contacts.findById(userId).orElse(null));
    }

    @Transactional
    public WhatsAppContactResponse save(UUID userId, WhatsAppContactRequest request) {
        requireAvailable();

        WhatsAppNumber number = WhatsAppNumber.parse(request.phoneNumber());
        Instant now = clock.instant();

        WhatsAppContact contact = contacts.findById(userId)
                .map(existing -> changed(existing, number, now))
                .orElseGet(() -> contacts.save(new WhatsAppContact(userId, number, now)));

        return WhatsAppContactResponse.of(true, contact);
    }

    @Transactional
    public WhatsAppContactResponse remove(UUID userId) {
        contacts.deleteById(userId);

        return WhatsAppContactResponse.of(properties.enabled(), null);
    }

    private static WhatsAppContact changed(WhatsAppContact contact, WhatsAppNumber number, Instant now) {
        contact.change(number, now);

        return contact;
    }

    private void requireAvailable() {
        if (!properties.enabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Os avisos no WhatsApp ainda não estão disponíveis.");
        }
    }
}

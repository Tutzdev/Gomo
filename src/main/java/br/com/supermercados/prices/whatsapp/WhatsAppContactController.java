package br.com.supermercados.prices.whatsapp;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.supermercados.prices.auth.AuthenticatedUser;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/users/me/whatsapp")
public class WhatsAppContactController {

    private final WhatsAppContactService contacts;

    public WhatsAppContactController(WhatsAppContactService contacts) {
        this.contacts = contacts;
    }

    @GetMapping
    public WhatsAppContactResponse find(@AuthenticationPrincipal AuthenticatedUser user) {
        return contacts.find(user.id());
    }

    @PutMapping
    public WhatsAppContactResponse save(
        @AuthenticationPrincipal AuthenticatedUser user,
        @Valid @RequestBody WhatsAppContactRequest request) {

        return contacts.save(user.id(), request);
    }

    @DeleteMapping
    public WhatsAppContactResponse remove(@AuthenticationPrincipal AuthenticatedUser user) {
        return contacts.remove(user.id());
    }
}

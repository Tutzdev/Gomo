package br.com.supermercados.prices.whatsapp;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WhatsAppContactRequest(
        @NotBlank @Size(max = 30) String phoneNumber,
        @AssertTrue(message = "Confirme que aceita receber os avisos no WhatsApp.") boolean consent) {}

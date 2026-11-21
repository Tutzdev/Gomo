package br.com.supermercados.prices.whatsapp;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface WhatsAppContactRepository extends JpaRepository<WhatsAppContact, UUID> {}

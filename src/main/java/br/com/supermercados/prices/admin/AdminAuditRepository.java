package br.com.supermercados.prices.admin;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AdminAuditRepository extends JpaRepository<AdminAuditEntry, UUID> {}

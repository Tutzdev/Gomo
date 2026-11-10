package br.com.supermercados.prices.admin;

import java.time.Instant;
import java.util.UUID;

public record AdminAuditResponse(
        UUID id,
        UUID actorUserId,
        AdminAction action,
        String resourceType,
        UUID resourceId,
        Instant occurredAt) {

    static AdminAuditResponse from(AdminAuditEntry entry) {
        return new AdminAuditResponse(
            entry.getId(), 
            entry.getActorUserId(), 
            entry.getAction(),
            entry.getResourceType(), 
            entry.getResourceId(), 
            entry.getOccurredAt());
    }
}

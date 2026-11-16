package br.com.supermercados.prices.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "admin_audit_entries")
class AdminAuditEntry {

    @Id
    private UUID id;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 80)
    private AdminAction action;

    @Column(nullable = false, length = 80)
    private String resourceType;

    @Column(nullable = false)
    private UUID resourceId;

    @Column(nullable = false)
    private Instant occurredAt;

    protected AdminAuditEntry() {}

    AdminAuditEntry(
        UUID actorUserId,
        AdminAction action,
        String resourceType,
        UUID resourceId,
        Instant occurredAt) {

        this.id = UUID.randomUUID();
        this.actorUserId = actorUserId;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.occurredAt = occurredAt;
    }

    UUID getId() {
        return id;
    }

    UUID getActorUserId() {
        return actorUserId;
    }

    AdminAction getAction() {
        return action;
    }

    String getResourceType() {
        return resourceType;
    }

    UUID getResourceId() {
        return resourceId;
    }

    Instant getOccurredAt() {
        return occurredAt;
    }
}

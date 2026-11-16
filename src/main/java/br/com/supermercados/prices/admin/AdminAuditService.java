package br.com.supermercados.prices.admin;

import br.com.supermercados.prices.common.PageResponse;
import java.time.Clock;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAuditService {

    private final AdminAuditRepository entries;
    private final Clock clock;

    public AdminAuditService(AdminAuditRepository entries, Clock clock) {
        this.entries = entries;
        this.clock = clock;
    }

    @Transactional
    public void record(UUID actorUserId, AdminAction action, String resourceType, UUID resourceId) {
        AdminAuditEntry entry = new AdminAuditEntry(
            actorUserId,
            action,
            resourceType,
            resourceId,
            clock.instant());

        entries.save(entry);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminAuditResponse> findAll(Pageable pageable) {
        return PageResponse.from(entries.findAll(pageable).map(AdminAuditResponse::from));
    }
}

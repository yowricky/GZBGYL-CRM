package com.gzbgyl.crm.audit.application;

import java.util.UUID;

public record AuditCommand(UUID actorId, String eventType, String aggregateType, UUID aggregateId,
        Object before, Object after, String ipAddress, String reason) {
}

package com.gzbgyl.crm.audit.application;

import java.util.Map;
import java.util.UUID;

public record AuditCommand(UUID actorId, String eventType, String aggregateType, UUID aggregateId,
        Map<String, ?> before, Map<String, ?> after, String ipAddress, String reason) {
}

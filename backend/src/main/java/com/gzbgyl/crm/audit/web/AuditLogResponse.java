package com.gzbgyl.crm.audit.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.gzbgyl.crm.audit.domain.AuditLog;
import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(UUID id, UUID actorId, String eventType, String aggregateType,
        UUID aggregateId, JsonNode before, JsonNode after, String ipAddress, String reason,
        Instant createdAt) {
    static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(log.id(), log.actorId(), log.eventType(), log.aggregateType(),
                log.aggregateId(), log.beforeJson(), log.afterJson(), log.ipAddress(), log.reason(),
                log.createdAt());
    }
}

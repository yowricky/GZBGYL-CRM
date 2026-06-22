package com.gzbgyl.crm.audit.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Immutable
@Table(name = "audit_log")
public class AuditLog {
    @Id private UUID id;
    @Column(name = "actor_id") private UUID actorId;
    @Column(name = "event_type", nullable = false, length = 100) private String eventType;
    @Column(name = "aggregate_type", nullable = false, length = 150) private String aggregateType;
    @Column(name = "aggregate_id", nullable = false) private UUID aggregateId;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "before_state", columnDefinition = "jsonb")
    private JsonNode beforeJson;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "after_state", columnDefinition = "jsonb")
    private JsonNode afterJson;
    @Column(name = "ip_address", columnDefinition = "inet")
    @ColumnTransformer(write = "?::inet")
    private String ipAddress;
    @Column(length = 1000) private String reason;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditLog() {}

    public AuditLog(UUID actorId, String eventType, String aggregateType, UUID aggregateId,
            JsonNode beforeJson, JsonNode afterJson, String ipAddress, String reason) {
        this.id = UUID.randomUUID();
        this.actorId = actorId;
        this.eventType = eventType;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.beforeJson = beforeJson;
        this.afterJson = afterJson;
        this.ipAddress = ipAddress;
        this.reason = reason;
        this.createdAt = Instant.now();
    }

    public UUID id() { return id; }
    public UUID actorId() { return actorId; }
    public String eventType() { return eventType; }
    public String aggregateType() { return aggregateType; }
    public UUID aggregateId() { return aggregateId; }
    public JsonNode beforeJson() { return beforeJson; }
    public JsonNode afterJson() { return afterJson; }
    public String ipAddress() { return ipAddress; }
    public String reason() { return reason; }
    public Instant createdAt() { return createdAt; }
}

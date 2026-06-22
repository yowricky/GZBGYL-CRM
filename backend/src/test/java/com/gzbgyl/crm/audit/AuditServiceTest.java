package com.gzbgyl.crm.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.gzbgyl.crm.audit.application.AuditCommand;
import com.gzbgyl.crm.audit.application.AuditService;
import com.gzbgyl.crm.audit.domain.AuditLog;
import com.gzbgyl.crm.audit.persistence.AuditLogRepository;
import com.gzbgyl.crm.support.PostgresIntegrationTest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class AuditServiceTest extends PostgresIntegrationTest {

    @Autowired AuditService service;
    @Autowired AuditLogRepository repository;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void cleanAudit() {
        jdbc.execute("truncate table audit_log");
    }

    @Test
    void persistsStructuredJsonAndSystemMetadata() {
        UUID aggregateId = UUID.randomUUID();
        AuditLog log = service.record(new AuditCommand(null, "ORG_RENAMED", "ORGANIZATION",
                aggregateId, Map.of("name", "Old"), Map.of("name", "New"),
                "127.0.0.1", "correction"));

        AuditLog found = repository.findById(log.id()).orElseThrow();
        assertThat(found.actorId()).isNull();
        assertThat(found.aggregateId()).isEqualTo(aggregateId);
        assertThat(found.beforeJson().path("name").asText()).isEqualTo("Old");
        assertThat(found.afterJson().path("name").asText()).isEqualTo("New");
        assertThat(found.ipAddress()).isEqualTo("127.0.0.1");
        assertThat(found.reason()).isEqualTo("correction");
        assertThat(found.createdAt()).isNotNull();
    }

    @Test
    void recursivelyRedactsSecretsWithoutMutatingCallerData() {
        Map<String, Object> nested = new LinkedHashMap<>();
        List<Object> values = new ArrayList<>();
        values.add(Map.of("Authorization", "Bearer raw", "authHeader", "raw-header",
                "safe", "visible"));
        values.add(new Object[] {Map.of("sessionId", "raw-session",
                "oauthClient", "raw-client"), "plain"});
        nested.put("profile", Map.of("PasswordHash", "raw-hash", "content_hash", "raw-digest",
                "auth", "raw-auth", "name", "Alice"));
        nested.put("items", values);

        AuditLog log = service.record(new AuditCommand(null, "USER_UPDATED", "USER",
                UUID.randomUUID(), null, nested, null, null));

        JsonNode json = log.afterJson();
        assertThat(json.at("/profile/PasswordHash").asText()).isEqualTo("[REDACTED]");
        assertThat(json.at("/profile/content_hash").asText()).isEqualTo("[REDACTED]");
        assertThat(json.at("/profile/auth").asText()).isEqualTo("[REDACTED]");
        assertThat(json.at("/items/0/Authorization").asText()).isEqualTo("[REDACTED]");
        assertThat(json.at("/items/0/authHeader").asText()).isEqualTo("[REDACTED]");
        assertThat(json.at("/items/1/0/sessionId").asText()).isEqualTo("[REDACTED]");
        assertThat(json.at("/items/1/0/oauthClient").asText()).isEqualTo("[REDACTED]");
        assertThat(json.at("/profile/name").asText()).isEqualTo("Alice");
        assertThat(((Map<?, ?>) nested.get("profile")).get("PasswordHash")).isEqualTo("raw-hash");
        assertThat(((Map<?, ?>) nested.get("profile")).get("content_hash")).isEqualTo("raw-digest");
        assertThat(((Map<?, ?>) nested.get("profile")).get("auth")).isEqualTo("raw-auth");
        assertThat(((Map<?, ?>) values.get(0)).get("Authorization")).isEqualTo("Bearer raw");
        assertThat(((Map<?, ?>) values.get(0)).get("authHeader")).isEqualTo("raw-header");
        assertThat(((Map<?, ?>) ((Object[]) values.get(1))[0]).get("oauthClient"))
                .isEqualTo("raw-client");
    }

    @Test
    void rejectsInvalidBoundsBeforeDatabaseAccess() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> service.record(new AuditCommand(null, "bad event", "USER", id,
                null, null, null, null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.record(new AuditCommand(null, "USER_UPDATED", "X".repeat(151), id,
                null, null, null, null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.record(new AuditCommand(null, "USER_UPDATED", "USER", id,
                null, null, "not-an-ip", null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.record(new AuditCommand(null, "USER_UPDATED", "USER", id,
                null, null, null, "x".repeat(1001)))).isInstanceOf(IllegalArgumentException.class);
        assertThat(repository.count()).isZero();
    }

    @Test
    void databaseAllowsInsertButRejectsUpdateAndDeleteWithStableError() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into audit_log(id,event_type,aggregate_type,aggregate_id) values (?,?,?,?)",
                id, "TEST_EVENT", "TEST", UUID.randomUUID());

        assertThatThrownBy(() -> jdbc.update("update audit_log set reason='changed' where id=?", id))
                .rootCause().hasMessageContaining("audit_log is append-only");
        assertThatThrownBy(() -> jdbc.update("delete from audit_log where id=?", id))
                .rootCause().hasMessageContaining("audit_log is append-only");
        assertThat(jdbc.queryForObject("select count(*) from audit_log", Integer.class)).isEqualTo(1);
    }
}

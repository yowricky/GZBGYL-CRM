package com.gzbgyl.crm.audit;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gzbgyl.crm.support.PostgresIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class AuditLogControllerTest extends PostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private UUID aggregateId;

    @BeforeEach
    void setUp() {
        jdbc.execute("truncate table audit_log");
        aggregateId = UUID.randomUUID();
        jdbc.update("""
                insert into audit_log(event_type, aggregate_type, aggregate_id, after_state, reason)
                values ('ORGANIZATION_RENAMED', 'ORGANIZATION', ?, '{"name":"New"}', 'correction')
                """, aggregateId);
        jdbc.update("""
                insert into audit_log(event_type, aggregate_type, aggregate_id)
                values ('USER_CREATED', 'USER', ?)
                """, UUID.randomUUID());
    }

    @Test
    void systemAdminCanPageAndFilterAuditLogs() throws Exception {
        mvc.perform(get("/api/admin/audit-logs")
                        .param("aggregateType", "ORGANIZATION")
                        .param("aggregateId", aggregateId.toString())
                        .param("page", "0").param("size", "20")
                        .with(user("admin").authorities(() -> "system:admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].eventType").value("ORGANIZATION_RENAMED"))
                .andExpect(jsonPath("$.content[0].aggregateId").value(aggregateId.toString()))
                .andExpect(jsonPath("$.content[0].after.name").value("New"))
                .andExpect(jsonPath("$.content[0].reason").value("correction"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void salesUserReceivesStableJsonForbidden() throws Exception {
        mvc.perform(get("/api/admin/audit-logs")
                        .with(user("sales").authorities(() -> "lead:read:own")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.path").value("/api/admin/audit-logs"));
    }

    @Test
    void pagingAndDateBoundsAreValidated() throws Exception {
        var admin = user("admin").authorities(() -> "system:admin");
        mvc.perform(get("/api/admin/audit-logs").param("page", "-1").with(admin))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/audit-logs").param("size", "101").with(admin))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/audit-logs").param("page", Integer.toString(Integer.MAX_VALUE))
                        .param("size", "100").with(admin))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/audit-logs")
                        .param("from", "2026-06-23T00:00:00Z")
                        .param("to", "2026-06-22T00:00:00Z").with(admin))
                .andExpect(status().isBadRequest());
    }
}

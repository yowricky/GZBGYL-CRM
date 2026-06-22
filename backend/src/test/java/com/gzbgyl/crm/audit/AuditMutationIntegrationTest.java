package com.gzbgyl.crm.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gzbgyl.crm.identity.application.CreateUserCommand;
import com.gzbgyl.crm.identity.application.OrganizationNode;
import com.gzbgyl.crm.identity.application.OrganizationService;
import com.gzbgyl.crm.identity.application.UserAdministrationService;
import com.gzbgyl.crm.identity.application.UserSummary;
import com.gzbgyl.crm.support.PostgresIntegrationTest;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class AuditMutationIntegrationTest extends PostgresIntegrationTest {
    @Autowired OrganizationService organizations;
    @Autowired UserAdministrationService users;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.execute("truncate table audit_log");
        jdbc.update("delete from app_user_role");
        jdbc.update("delete from app_user");
        jdbc.update("delete from organization_unit");
    }

    @Test
    void organizationAndUserMutationsWriteApprovedEvents() {
        OrganizationNode root = organizations.createRoot("ROOT", "Root");
        OrganizationNode target = organizations.createRoot("TARGET", "Target");
        OrganizationNode child = organizations.createChild(root.id(), "CHILD", "Child");
        OrganizationNode renamed = organizations.rename(child.id(), "Renamed", child.version());
        OrganizationNode moved = organizations.move(renamed.id(), target.id(), renamed.version());
        organizations.deactivate(moved.id(), moved.version());
        UserSummary user = users.createUser(new CreateUserCommand("alice", "Alice",
                "correct horse battery", root.id(), Set.of("SALES")));
        UserSummary deactivated = users.deactivate(user.id(), user.version());
        UserSummary activated = users.activate(user.id(), deactivated.version());
        UserSummary reset = users.resetPassword(user.id(), "replacement password", activated.version());
        users.assignRoles(user.id(), Set.of("PROJECT_MANAGER"), reset.version());

        assertThat(jdbc.queryForList("select event_type from audit_log order by created_at", String.class))
                .containsExactly("ORGANIZATION_CREATED", "ORGANIZATION_CREATED", "ORGANIZATION_CREATED",
                        "ORGANIZATION_RENAMED", "ORGANIZATION_MOVED", "ORGANIZATION_DEACTIVATED",
                        "USER_CREATED", "USER_DEACTIVATED", "USER_ACTIVATED", "USER_PASSWORD_RESET",
                        "USER_ROLES_ASSIGNED");
        Map<String, Object> passwordAudit = jdbc.queryForMap(
                "select before_state::text before_state, after_state::text after_state from audit_log "
                        + "where event_type='USER_PASSWORD_RESET'");
        assertThat(passwordAudit.toString()).doesNotContain("replacement password").doesNotContain("$2");
        assertThat(passwordAudit.get("after_state").toString()).contains("passwordChanged");
        assertThat(jdbc.queryForObject("select after_state->>'name' from audit_log "
                + "where event_type='ORGANIZATION_RENAMED'", String.class)).isEqualTo("Renamed");
    }

    @Test
    void auditInsertFailureRollsBackBusinessMutation() {
        jdbc.execute("""
                create function reject_audit_insert() returns trigger language plpgsql as $$
                begin raise exception 'forced audit failure'; end $$
                """);
        jdbc.execute("create trigger reject_audit_insert before insert on audit_log "
                + "for each row execute function reject_audit_insert()");
        try {
            assertThatThrownBy(() -> organizations.createRoot("ROLLBACK", "Rollback"))
                    .rootCause().hasMessageContaining("forced audit failure");
            assertThat(jdbc.queryForObject("select count(*) from organization_unit where code='ROLLBACK'",
                    Integer.class)).isZero();
        } finally {
            jdbc.execute("drop trigger reject_audit_insert on audit_log");
            jdbc.execute("drop function reject_audit_insert()");
        }
    }
}

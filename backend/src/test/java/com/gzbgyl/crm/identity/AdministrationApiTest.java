package com.gzbgyl.crm.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gzbgyl.crm.identity.application.CreateUserCommand;
import com.gzbgyl.crm.identity.application.OrganizationNode;
import com.gzbgyl.crm.identity.application.OrganizationService;
import com.gzbgyl.crm.identity.application.UserAdministrationService;
import com.gzbgyl.crm.identity.application.UserSummary;
import com.gzbgyl.crm.support.PostgresIntegrationTest;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class AdministrationApiTest extends PostgresIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired OrganizationService organizations;
    @Autowired UserAdministrationService users;

    private OrganizationNode root;
    private OrganizationNode sales;
    private UserSummary alice;

    @BeforeEach
    void resetState() {
        jdbc.update("delete from app_user_role");
        jdbc.update("delete from app_user");
        jdbc.update("delete from organization_unit");

        root = organizations.createRoot("HQ", "Headquarters");
        sales = organizations.createChild(root.id(), "SALES", "Sales");
        alice = users.createUser(new CreateUserCommand(
                "alice", "Alice Chen", "correct horse battery", sales.id(), Set.of("SALES")));
    }

    @Test
    @WithMockUser(authorities = "system:admin")
    void adminCanListOrganizationTree() throws Exception {
        mvc.perform(get("/api/admin/organization-units"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(root.id().toString()))
                .andExpect(jsonPath("$[0].children").isArray())
                .andExpect(jsonPath("$[0].children[0].id").value(sales.id().toString()));
    }

    @Test
    @WithMockUser(authorities = "opportunity:read:own")
    void salespersonCannotListUsers() throws Exception {
        mvc.perform(get("/api/admin/users"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void unauthenticatedAdminRequestReturnsJson401() throws Exception {
        mvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.path").value("/api/admin/users"));
    }

    @Test
    @WithMockUser(authorities = "system:admin")
    void createRenameMoveAndDeactivateOrganization() throws Exception {
        String create = """
                {"parentId":"%s","code":"EAST","name":"East Region"}
                """.formatted(root.id());
        String createdId = mvc.perform(post("/api/admin/organization-units").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(create))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("EAST"))
                .andExpect(jsonPath("$.parentId").value(root.id().toString()))
                .andReturn().getResponse().getContentAsString()
                .replaceAll(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");

        mvc.perform(patch("/api/admin/organization-units/{id}/rename", createdId).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"East Sales\",\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("East Sales"))
                .andExpect(jsonPath("$.version").value(1));

        OrganizationNode delivery = organizations.createChild(root.id(), "DELIVERY", "Delivery");
        mvc.perform(patch("/api/admin/organization-units/{id}/move", createdId).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newParentId":"%s","expectedVersion":1,"reason":"reporting line change"}
                                """.formatted(delivery.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parentId").value(delivery.id().toString()));
        assertThat(latestAuditReason("ORGANIZATION_MOVED", createdId)).isEqualTo("reporting line change");

        mvc.perform(patch("/api/admin/organization-units/{id}/deactivate", createdId).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":2,\"reason\":\"team closed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @WithMockUser(authorities = "system:admin")
    void organizationMutationsValidateReasonAndCsrf() throws Exception {
        mvc.perform(patch("/api/admin/organization-units/{id}/move", sales.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newParentId":"%s","expectedVersion":0,"reason":"move"}
                                """.formatted(root.id())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(patch("/api/admin/organization-units/{id}/move", sales.id()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"newParentId":"%s","expectedVersion":0,"reason":" "}
                                """.formatted(root.id())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.reason").exists());

        mvc.perform(patch("/api/admin/organization-units/{id}/deactivate", sales.id()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0,\"reason\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.reason").exists());
    }

    @Test
    @WithMockUser(authorities = "system:admin")
    void listCreateDeactivateResetPasswordAndAssignRolesForUsers() throws Exception {
        mvc.perform(get("/api/admin/users").param("keyword", "ALI").param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].username").value("alice"));

        String bobId = mvc.perform(post("/api/admin/users").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"bob","displayName":"Bob Li","initialPassword":"another safe password",
                                 "organizationUnitId":"%s","roleCodes":["SALES"]}
                                """.formatted(sales.id())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("bob"))
                .andReturn().getResponse().getContentAsString()
                .replaceAll(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");

        mvc.perform(patch("/api/admin/users/{id}/deactivate", bobId).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":1,\"reason\":\"left company\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mvc.perform(patch("/api/admin/users/{id}/reset-password", bobId).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"replacement password\",\"expectedVersion\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(3));

        mvc.perform(patch("/api/admin/users/{id}/roles", bobId).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleCodes\":[\"PROJECT_MANAGER\"],\"expectedVersion\":3,\"reason\":\"new duties\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("PROJECT_MANAGER"));
        assertThat(latestAuditReason("USER_ROLES_ASSIGNED", bobId)).isEqualTo("new duties");
    }

    @Test
    @WithMockUser(authorities = "system:admin")
    void userMutationsValidateReasonAndCsrf() throws Exception {
        mvc.perform(post("/api/admin/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"charlie","displayName":"Charlie","initialPassword":"another safe password",
                                 "organizationUnitId":"%s","roleCodes":["SALES"]}
                                """.formatted(sales.id())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(patch("/api/admin/users/{id}/deactivate", alice.id()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0,\"reason\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.reason").exists());

        mvc.perform(patch("/api/admin/users/{id}/roles", alice.id()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleCodes\":[\"PROJECT_MANAGER\"],\"expectedVersion\":0,\"reason\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.reason").exists());
    }

    private String latestAuditReason(String eventType, String aggregateId) {
        return jdbc.queryForObject("""
                select reason from audit_log
                where event_type = ? and aggregate_id = ?::uuid
                order by created_at desc
                limit 1
                """, String.class, eventType, aggregateId);
    }
}

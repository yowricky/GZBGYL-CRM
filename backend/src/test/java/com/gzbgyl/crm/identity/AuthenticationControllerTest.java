package com.gzbgyl.crm.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gzbgyl.crm.shared.security.CrmUserPrincipal;
import com.gzbgyl.crm.identity.application.UserAdministrationService;
import com.gzbgyl.crm.identity.web.AuthenticationController;
import com.gzbgyl.crm.support.PostgresRedisIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@AutoConfigureMockMvc
class AuthenticationControllerTest extends PostgresRedisIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired StringRedisTemplate redis;
    @Autowired UserAdministrationService userAdministration;

    private UUID organizationId;

    @BeforeEach
    void resetState() {
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
        jdbc.update("delete from app_user_role");
        jdbc.update("delete from app_user");
        jdbc.update("delete from organization_unit");
        organizationId = UUID.randomUUID();
        jdbc.update("insert into organization_unit (id, name, code, path) values (?, 'Sales', 'SALES', ?)",
                organizationId, "/" + organizationId);
    }

    @Test
    void anonymousMeRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.path").value("/api/auth/me"));
    }

    @Test
    void anonymousCsrfEndpointReturnsTokenAndReadableCookie() throws Exception {
        mvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie",
                        org.hamcrest.Matchers.containsString("XSRF-TOKEN=")))
                .andExpect(header().string("Set-Cookie",
                        org.hamcrest.Matchers.containsString("Path=/")))
                .andExpect(header().string("Set-Cookie",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("HttpOnly"))))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"));
    }


    @Test
    void loginPersistsSessionInRedisAndMeReturnsOnlySafeIdentityData() throws Exception {
        UUID userId = insertUser("Alice", "correct horse battery", true, "SALES");

        MvcResult login = login("  ALICE  ", "correct horse battery")
                .andExpect(status().isNoContent())
                .andExpect(cookie().exists("SESSION"))
                .andReturn();
        Cookie session = login.getResponse().getCookie("SESSION");

        mvc.perform(get("/api/auth/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.username").value("Alice"))
                .andExpect(jsonPath("$.displayName").value("Alice Display"))
                .andExpect(jsonPath("$.organizationUnitId").value(organizationId.toString()))
                .andExpect(jsonPath("$.roles[0]").value("SALES"))
                .andExpect(jsonPath("$.permissions").isArray())
                .andExpect(jsonPath("$.permissions.length()").value(4))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        assertThat(redis.keys("crm:session:v1:sessions:*")).isNotEmpty();
    }

    @Test
    void badPasswordAndDisabledUserReturnSameStableError() throws Exception {
        insertUser("active", "correct horse battery", true, "SALES");
        insertUser("disabled", "correct horse battery", false, "SALES");

        String wrong = login("active", "wrong password").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andReturn().getResponse().getContentAsString();
        String disabled = login("disabled", "correct horse battery").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andReturn().getResponse().getContentAsString();

        assertThat(wrong.replaceAll("\\\"timestamp\\\":\\\"[^\\\"]+\\\"", "\"timestamp\":\"ignored\""))
                .isEqualTo(disabled.replaceAll("\\\"timestamp\\\":\\\"[^\\\"]+\\\"", "\"timestamp\":\"ignored\""));
    }

    @Test
    void loginWithoutCsrfIsDeniedAsJson() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"secret\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void logoutInvalidatesRedisSession() throws Exception {
        insertUser("alice", "correct horse battery", true, "SALES");
        Cookie session = login("alice", "correct horse battery").andReturn().getResponse().getCookie("SESSION");

        mvc.perform(post("/api/auth/logout").cookie(session).with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("SESSION", 0));
        mvc.perform(get("/api/auth/me").cookie(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void secondLoginExpiresThePreviousSession() throws Exception {
        insertUser("alice", "correct horse battery", true, "SALES");
        Cookie first = login("alice", "correct horse battery").andReturn().getResponse().getCookie("SESSION");
        Cookie second = login("alice", "correct horse battery").andReturn().getResponse().getCookie("SESSION");

        assertThat(second.getValue()).isNotEqualTo(first.getValue());
        mvc.perform(get("/api/auth/me").cookie(first))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        mvc.perform(get("/api/auth/me").cookie(second)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @EnumSource(SecurityMutation.class)
    void securityMutationsRevokeExistingSession(SecurityMutation mutation) throws Exception {
        UUID userId = insertUser("alice", "correct horse battery", true, "SALES");
        Cookie session = login("alice", "correct horse battery").andReturn().getResponse().getCookie("SESSION");

        mutation.apply(userAdministration, userId);

        mvc.perform(get("/api/auth/me").cookie(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void methodSecurityDenialUsesStableJsonError() throws Exception {
        insertUser("alice", "correct horse battery", true, "SALES");
        Cookie session = login("alice", "correct horse battery").andReturn().getResponse().getCookie("SESSION");

        mvc.perform(get("/test/system-admin-only").cookie(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void notFoundDomainFailureUsesStableApiError() throws Exception {
        insertUser("alice", "correct horse battery", true, "SALES");
        Cookie session = login("alice", "correct horse battery").andReturn().getResponse().getCookie("SESSION");

        mvc.perform(get("/test/not-found").cookie(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Record not found"));
    }

    @Test
    void blankAndMalformedLoginJsonReturnStableApiErrors() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\" \",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.username").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_JSON"));
    }

    @Test
    void loginInputIsBoundedAndNeverRenderedWithPlaintextPassword() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + "u".repeat(81)
                                + "\",\"password\":\"correct horse battery\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\",\"password\":\"" + "p".repeat(73) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertThat(new AuthenticationController.LoginRequest("alice", "top-secret-password").toString())
                .doesNotContain("top-secret-password")
                .contains("[REDACTED]");
    }

    @Test
    void commonMvcFailuresUseSanitizedStableApiErrors() throws Exception {
        insertUser("alice", "correct horse battery", true, "SALES");
        Cookie session = login("alice", "correct horse battery").andReturn().getResponse().getCookie("SESSION");

        mvc.perform(get("/test/json-only").cookie(session))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        mvc.perform(post("/test/json-only").cookie(session).with(csrf())
                        .contentType(MediaType.TEXT_PLAIN).content("text"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
        mvc.perform(get("/test/required-param").cookie(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_PARAMETER"));
        mvc.perform(get("/test/no-such-handler").cookie(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mvc.perform(get("/test/internal-error").cookie(session))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("database-password"))));
    }

    @Test
    void principalSerializationAndToStringNeverLeakPasswordHash() throws Exception {
        CrmUserPrincipal principal = new CrmUserPrincipal(UUID.randomUUID(), organizationId, "alice",
                "Alice", "$2a$12$top-secret-hash", true, Set.of("SALES"), Set.of("lead:read"));
        principal.eraseCredentials();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new ObjectOutputStream(bytes).writeObject(principal);

        assertThat(principal.toString()).doesNotContain("top-secret").doesNotContain("password");
        assertThat(new String(bytes.toByteArray(), StandardCharsets.ISO_8859_1)).doesNotContain("top-secret");
    }

    private org.springframework.test.web.servlet.ResultActions login(String username, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"));
    }


    private UUID insertUser(String username, String password, boolean active, String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into app_user (id, organization_unit_id, username, normalized_username,
                    display_name, password_hash, active)
                values (?, ?, ?, ?, ?, ?, ?)
                """, id, organizationId, username, username.strip().toLowerCase(), username + " Display",
                passwordEncoder.encode(password), active);
        jdbc.update("insert into app_user_role (app_user_id, role_id) select ?, id from role where code = ?", id, role);
        return id;
    }

    @TestConfiguration
    static class MethodSecurityTestConfig {
        @Bean
        AdminOnlyTestController adminOnlyTestController() {
            return new AdminOnlyTestController();
        }
    }

    @RestController
    static class AdminOnlyTestController {
        @GetMapping("/test/system-admin-only")
        @PreAuthorize("hasAuthority('system:admin')")
        String adminOnly() {
            return "ok";
        }

        @GetMapping("/test/not-found")
        String notFound() {
            throw new java.util.NoSuchElementException("Record not found");
        }

        @PostMapping(value = "/test/json-only", consumes = MediaType.APPLICATION_JSON_VALUE)
        String jsonOnly(@RequestBody String body) {
            return body;
        }

        @GetMapping("/test/required-param")
        String requiredParam(@org.springframework.web.bind.annotation.RequestParam String value) {
            return value;
        }

        @GetMapping("/test/internal-error")
        String internalError() {
            throw new IllegalStateException("database-password=do-not-leak");
        }
    }

    private enum SecurityMutation {
        DEACTIVATE {
            @Override void apply(UserAdministrationService service, UUID id) {
                service.deactivate(id, 0);
            }
        },
        RESET_PASSWORD {
            @Override void apply(UserAdministrationService service, UUID id) {
                service.resetPassword(id, "replacement password", 0);
            }
        },
        ASSIGN_ROLES {
            @Override void apply(UserAdministrationService service, UUID id) {
                service.assignRoles(id, Set.of("PROJECT_MANAGER"), 0);
            }
        };

        abstract void apply(UserAdministrationService service, UUID id);
    }
}

package com.gzbgyl.crm.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzbgyl.crm.attachment.application.AttachmentAuthorizer;
import com.gzbgyl.crm.attachment.application.ObjectStorage;
import com.gzbgyl.crm.attachment.application.StoredObject;
import com.gzbgyl.crm.attachment.application.StorageException;
import com.gzbgyl.crm.attachment.persistence.AttachmentRepository;
import com.gzbgyl.crm.shared.security.CrmUserPrincipal;
import com.gzbgyl.crm.shared.security.SessionSecurityService;
import com.gzbgyl.crm.support.PostgresIntegrationTest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.attachments.max-size-bytes=32",
        "app.attachments.allowed-content-types=text/plain"
})
class AttachmentControllerTest extends PostgresIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AttachmentRepository repository;
    @Autowired FakeStorage storage;
    @Autowired MutableAuthorizer authorizer;
    @Autowired ObjectMapper objectMapper;

    UUID actorId;
    UUID ownerId;

    @BeforeEach
    void resetState() {
        jdbc.update("delete from attachment");
        storage.objects.clear();
        authorizer.allowRead = true;
        authorizer.allowWrite = true;
        actorId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        jdbc.update("insert into organization_unit (id, name, code, path) values (?, ?, ?, ?)",
                organizationId, "Attachment API", "API_" + organizationId.toString().replace("-", "").substring(0, 20),
                "/" + organizationId);
        jdbc.update("""
                insert into app_user (id, organization_unit_id, username, normalized_username,
                    display_name, password_hash, active)
                values (?, ?, ?, ?, 'Attachment API Actor', 'hash', true)
                """, actorId, organizationId, "api-" + actorId, "api-" + actorId);
    }

    @Test
    void uploadReturnsStableDtoWithoutStorageKeyAndRequiresCsrf() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "quote.txt",
                "text/plain", "hello".getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/api/attachments")
                        .file(file)
                        .param("ownerType", "deal")
                        .param("ownerId", ownerId.toString())
                        .with(user(principal()))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.ownerType").value("deal"))
                .andExpect(jsonPath("$.ownerId").value(ownerId.toString()))
                .andExpect(jsonPath("$.originalFilename").value("quote.txt"))
                .andExpect(jsonPath("$.sha256").isNotEmpty())
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.storageKey").doesNotExist());

        mvc.perform(multipart("/api/attachments")
                        .file(file)
                        .param("ownerType", "deal")
                        .param("ownerId", ownerId.toString())
                        .with(user(principal())))
                .andExpect(status().isForbidden());
    }

    @Test
    void downloadStreamsSafeHeadersAndDeleteUsesExpectedVersion() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "report.txt",
                "text/plain", "hello".getBytes(StandardCharsets.UTF_8));
        String response = mvc.perform(multipart("/api/attachments")
                        .file(file)
                        .param("ownerType", "deal")
                        .param("ownerId", ownerId.toString())
                        .with(user(principal()))
                .with(csrf()))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(objectMapper.readTree(response).get("id").asText());

        mvc.perform(get("/api/attachments/{id}", id).with(user(principal())))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/plain"))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, 5))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("filename=\"report.txt\"")))
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray())
                        .isEqualTo("hello".getBytes(StandardCharsets.UTF_8)));

        long version = repository.findById(id).orElseThrow().getVersion();
        mvc.perform(delete("/api/attachments/{id}", id)
                        .param("expectedVersion", Long.toString(version))
                        .with(user(principal())))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/attachments/{id}", id)
                        .param("expectedVersion", Long.toString(version))
                        .with(user(principal()))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(true));
        mvc.perform(get("/api/attachments/{id}", id).with(user(principal())))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthorizedOwnerAccessIsDeniedThroughService() throws Exception {
        authorizer.allowWrite = false;
        mvc.perform(multipart("/api/attachments")
                        .file(new MockMultipartFile("file", "a.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8)))
                        .param("ownerType", "deal")
                        .param("ownerId", ownerId.toString())
                        .with(user(principal()))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    private UserDetails principal() {
        return new CrmUserPrincipal(actorId, UUID.randomUUID(), "actor", "Actor",
                "hash", true, Set.of("SALES"), Set.of("attachment:test"));
    }

    static class MutableAuthorizer implements AttachmentAuthorizer {
        boolean allowRead = true;
        boolean allowWrite = true;
        @Override public boolean canRead(UUID actorId, String ownerType, UUID ownerId) { return allowRead; }
        @Override public boolean canWrite(UUID actorId, String ownerType, UUID ownerId) { return allowWrite; }
    }

    static class FakeStorage implements ObjectStorage {
        final Map<String, Stored> objects = new HashMap<>();
        @Override public void put(String key, String contentType, long size, InputStream in) {
            try {
                objects.put(key, new Stored(contentType, in.readAllBytes()));
            } catch (IOException exception) {
                throw new StorageException("write failed", exception);
            }
        }
        @Override public StoredObject get(String key) {
            Stored stored = objects.get(key);
            if (stored == null) throw StorageException.notFound("missing");
            return new StoredObject(stored.contentType(), stored.bytes().length,
                    new ByteArrayInputStream(stored.bytes()));
        }
        @Override public void delete(String key) {
            objects.remove(key);
        }
        record Stored(String contentType, byte[] bytes) {}
    }

    @TestConfiguration
    static class AttachmentControllerTestConfig {
        @Bean @Primary FakeStorage fakeStorage() { return new FakeStorage(); }
        @Bean @Primary MutableAuthorizer mutableAuthorizer() { return new MutableAuthorizer(); }
        @Bean @Primary SessionSecurityService sessionSecurityService() {
            return new SessionSecurityService() {
                @Override public Authentication startSession(Authentication authentication) { return authentication; }
                @Override public boolean isCurrent(CrmUserPrincipal principal) { return true; }
                @Override public void cleanupOlderSessions(String currentSessionId, CrmUserPrincipal principal) {}
                @Override public void revokeSessions(UUID userId) {}
            };
        }
    }
}

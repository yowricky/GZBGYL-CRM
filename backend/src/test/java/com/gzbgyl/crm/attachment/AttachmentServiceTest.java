package com.gzbgyl.crm.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gzbgyl.crm.attachment.application.AttachmentAuthorizer;
import com.gzbgyl.crm.attachment.application.AttachmentService;
import com.gzbgyl.crm.attachment.application.ObjectStorage;
import com.gzbgyl.crm.attachment.application.StoredObject;
import com.gzbgyl.crm.attachment.application.StorageException;
import com.gzbgyl.crm.attachment.persistence.AttachmentRepository;
import com.gzbgyl.crm.audit.application.AuditService;
import com.gzbgyl.crm.shared.api.ConflictException;
import com.gzbgyl.crm.shared.api.InvalidRequestException;
import com.gzbgyl.crm.shared.api.ResourceNotFoundException;
import com.gzbgyl.crm.support.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@TestPropertySource(properties = {
        "app.attachments.max-size-bytes=12",
        "app.attachments.allowed-content-types=text/plain,image/png"
})
class AttachmentServiceTest extends PostgresIntegrationTest {

    @Autowired AttachmentService service;
    @Autowired AttachmentRepository repository;
    @Autowired AuditService audit;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired FakeStorage storage;
    @Autowired MutableAuthorizer authorizer;

    UUID actorId;
    UUID ownerId;

    @BeforeEach
    void resetState() {
        jdbc.update("delete from attachment");
        storage.clear();
        authorizer.allowRead = true;
        authorizer.allowWrite = true;
        actorId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        jdbc.update("insert into organization_unit (id, name, code, path) values (?, ?, ?, ?)",
                organizationId, "Attachment Test", "ATTACH_" + organizationId.toString().replace("-", "").substring(0, 20),
                "/" + organizationId);
        jdbc.update("""
                insert into app_user (id, organization_unit_id, username, normalized_username,
                    display_name, password_hash, active)
                values (?, ?, ?, ?, 'Attachment Actor', 'hash', true)
                """, actorId, organizationId, "actor-" + actorId, "actor-" + actorId);
    }

    @Test
    void authorizedUploadPersistsMetadataShaAndStreamsExactBytes() throws Exception {
        byte[] bytes = "hello".getBytes(StandardCharsets.UTF_8);

        var uploaded = service.upload(actorId, "deal", ownerId,
                new MockMultipartFile("file", " Contract 2026.txt ", "text/plain", bytes));

        var found = repository.findById(uploaded.id()).orElseThrow();
        assertThat(found.getOwnerType()).isEqualTo("deal");
        assertThat(found.getOwnerId()).isEqualTo(ownerId);
        assertThat(found.getOriginalFilename()).isEqualTo("Contract 2026.txt");
        assertThat(found.getContentType()).isEqualTo("text/plain");
        assertThat(found.getSizeBytes()).isEqualTo(bytes.length);
        assertThat(found.getSha256()).isEqualTo(sha256(bytes));
        assertThat(found.getStorageKey()).doesNotContain("Contract").doesNotContain("deal");
        assertThat(storage.bytes(found.getStorageKey())).isEqualTo(bytes);
        assertThat(uploaded.toString()).doesNotContain(found.getStorageKey());
        assertThat(jdbc.queryForList("select event_type from audit_log", String.class))
                .contains("ATTACHMENT_UPLOADED");
        assertThat(jdbc.queryForList("select after_state::text from audit_log", String.class))
                .allSatisfy(json -> assertThat(json).doesNotContain(found.getStorageKey()));
    }

    @Test
    void unauthorizedUploadDownloadAndDeleteAreDenied() {
        authorizer.allowWrite = false;
        assertThatThrownBy(() -> service.upload(actorId, "deal", ownerId,
                file("a.txt", "text/plain", "hello")))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        authorizer.allowWrite = true;
        var uploaded = service.upload(actorId, "deal", ownerId, file("a.txt", "text/plain", "hello"));
        authorizer.allowRead = false;
        assertThatThrownBy(() -> service.download(actorId, uploaded.id()))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        authorizer.allowWrite = false;
        assertThatThrownBy(() -> service.delete(actorId, uploaded.id(), uploaded.version()))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void uploadRejectsInvalidInputsBeforeStorageWrite() {
        assertThatThrownBy(() -> service.upload(actorId, "bad/type", ownerId, file("a.txt", "text/plain", "hello")))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.upload(actorId, "deal", ownerId, file("", "text/plain", "hello")))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.upload(actorId, "deal", ownerId, file("a.txt", "text/plain", "")))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.upload(actorId, "deal", ownerId, file("a.txt", "text/plain", "too-large-body")))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.upload(actorId, "deal", ownerId, file("a.txt", "application/x-msdownload", "hello")))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.upload(actorId, "deal", ownerId, file("../a.txt", "text/plain", "hello")))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.upload(actorId, "deal", ownerId, file("bad\r\nname.txt", "text/plain", "hello")))
                .isInstanceOf(InvalidRequestException.class);
        assertThat(storage.objects).isEmpty();
    }

    @Test
    void databaseFailureDeletesUploadedOrphanAndSurfacesSanitizedError() {
        service = new AttachmentService(repository, java.util.List.of(authorizer), storage, audit,
                12, java.util.Set.of("text/plain")) {
            @Override
            protected void flushMetadata() {
                throw new DataAccessResourceFailureException("database password leaked");
            }
        };

        assertThatThrownBy(() -> service.upload(actorId, "deal", ownerId, file("a.txt", "text/plain", "hello")))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Attachment could not be saved");
        assertThat(storage.objects).isEmpty();
    }

    @Test
    void auditFailureDuringUploadRollsBackMetadataAndDeletesUploadedOrphan() {
        AuditService failingAudit = new AuditService(null, new ObjectMapper()) {
            @Override public com.gzbgyl.crm.audit.domain.AuditLog record(com.gzbgyl.crm.audit.application.AuditCommand command) {
                throw new DataAccessResourceFailureException("audit store unavailable");
            }
        };
        AttachmentService failingService = new AttachmentService(repository, java.util.List.of(authorizer), storage,
                failingAudit, transactionManager, 12, java.util.Set.of("text/plain"));

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                failingService.upload(actorId, "deal", ownerId, file("a.txt", "text/plain", "hello"))))
                .isInstanceOf(DataAccessResourceFailureException.class);

        assertThat(storage.objects).isEmpty();
        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void downloadReturnsSafeHeadersAndExactBytesButDeletedOrMissingObjectsAreUnavailable() throws Exception {
        var uploaded = service.upload(actorId, "deal", ownerId,
                new MockMultipartFile("file", "bad name.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8)));

        var download = service.download(actorId, uploaded.id());
        assertThat(download.filename()).isEqualTo("bad name.txt");
        assertThat(download.contentType()).isEqualTo("text/plain");
        assertThat(download.sizeBytes()).isEqualTo(5);
        assertThat(download.stream().readAllBytes()).isEqualTo("hello".getBytes(StandardCharsets.UTF_8));

        service.delete(actorId, uploaded.id(), uploaded.version());
        assertThatThrownBy(() -> service.download(actorId, uploaded.id()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Attachment not found");

        var other = service.upload(actorId, "deal", ownerId, file("a.txt", "text/plain", "hello"));
        storage.objects.clear();
        assertThatThrownBy(() -> service.download(actorId, other.id()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Attachment content is unavailable");
    }

    @Test
    void deleteSoftDeletesFirstMarksStorageStateAndHandlesRetriesIdempotently() {
        var uploaded = service.upload(actorId, "deal", ownerId, file("a.txt", "text/plain", "hello"));
        storage.failDelete = true;

        var deleted = service.delete(actorId, uploaded.id(), uploaded.version());

        assertThat(deleted.deleted()).isTrue();
        var pending = repository.findById(uploaded.id()).orElseThrow();
        assertThat(pending.isDeleted()).isTrue();
        assertThat(pending.getStorageDeletedAt()).isNull();
        assertThatThrownBy(() -> service.download(actorId, uploaded.id()))
                .isInstanceOf(ResourceNotFoundException.class);

        storage.failDelete = false;
        assertThat(service.cleanupPendingDeletes(10)).isEqualTo(1);
        assertThat(service.cleanupPendingDeletes(10)).isZero();
        assertThat(repository.findById(uploaded.id()).orElseThrow().getStorageDeletedAt()).isNotNull();
    }

    @Test
    void auditFailureDuringDeleteDoesNotDeleteStorageOrCommitSoftDelete() {
        var uploaded = service.upload(actorId, "deal", ownerId, file("a.txt", "text/plain", "hello"));
        var attachment = repository.findById(uploaded.id()).orElseThrow();
        byte[] bytes = storage.bytes(attachment.getStorageKey());
        AuditService failingAudit = new AuditService(null, new ObjectMapper()) {
            @Override public com.gzbgyl.crm.audit.domain.AuditLog record(com.gzbgyl.crm.audit.application.AuditCommand command) {
                throw new DataAccessResourceFailureException("audit store unavailable");
            }
        };
        AttachmentService failingService = new AttachmentService(repository, java.util.List.of(authorizer), storage,
                failingAudit, transactionManager, 12, java.util.Set.of("text/plain"));

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                failingService.delete(actorId, uploaded.id(), uploaded.version())))
                .isInstanceOf(DataAccessResourceFailureException.class);

        var stillActive = repository.findById(uploaded.id()).orElseThrow();
        assertThat(stillActive.isDeleted()).isFalse();
        assertThat(storage.bytes(stillActive.getStorageKey())).isEqualTo(bytes);
    }

    @Test
    void staleExpectedVersionConflicts() {
        var uploaded = service.upload(actorId, "deal", ownerId, file("a.txt", "text/plain", "hello"));

        assertThatThrownBy(() -> service.delete(actorId, uploaded.id(), uploaded.version() + 1))
                .isInstanceOf(ConflictException.class);
    }

    private static MockMultipartFile file(String name, String contentType, String body) {
        return new MockMultipartFile("file", name, contentType, body.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    static class MutableAuthorizer implements AttachmentAuthorizer {
        boolean allowRead = true;
        boolean allowWrite = true;

        @Override public boolean canRead(UUID actorId, String ownerType, UUID ownerId) {
            return allowRead;
        }

        @Override public boolean canWrite(UUID actorId, String ownerType, UUID ownerId) {
            return allowWrite;
        }
    }

    static class FakeStorage implements ObjectStorage {
        final Map<String, Stored> objects = new HashMap<>();
        boolean failDelete;

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
            if (failDelete) throw new StorageException("delete failed");
            objects.remove(key);
        }

        byte[] bytes(String key) {
            return objects.get(key).bytes();
        }

        void clear() {
            objects.clear();
            failDelete = false;
        }

        record Stored(String contentType, byte[] bytes) {}
    }

    @TestConfiguration
    static class AttachmentTestConfig {
        @Bean @Primary FakeStorage fakeStorage() { return new FakeStorage(); }
        @Bean @Primary MutableAuthorizer mutableAuthorizer() { return new MutableAuthorizer(); }
    }
}

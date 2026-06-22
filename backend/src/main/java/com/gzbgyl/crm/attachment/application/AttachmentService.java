package com.gzbgyl.crm.attachment.application;

import com.gzbgyl.crm.attachment.domain.Attachment;
import com.gzbgyl.crm.attachment.persistence.AttachmentRepository;
import com.gzbgyl.crm.audit.application.AuditCommand;
import com.gzbgyl.crm.audit.application.AuditService;
import com.gzbgyl.crm.shared.api.ConflictException;
import com.gzbgyl.crm.shared.api.InvalidRequestException;
import com.gzbgyl.crm.shared.api.ResourceNotFoundException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AttachmentService {
    private static final Pattern OWNER_TYPE = Pattern.compile("[a-z][a-z0-9_-]{0,63}");

    private final AttachmentRepository repository;
    private final List<AttachmentAuthorizer> authorizers;
    private final ObjectStorage storage;
    private final AuditService audit;
    private final long maxSizeBytes;
    private final Set<String> allowedContentTypes;

    @Autowired
    public AttachmentService(AttachmentRepository repository, List<AttachmentAuthorizer> authorizers,
            ObjectProvider<ObjectStorage> storage, AuditService audit,
            @Value("${app.attachments.max-size-bytes:20971520}") long maxSizeBytes,
            @Value("${app.attachments.allowed-content-types:text/plain,image/png,image/jpeg,application/pdf}")
            Set<String> allowedContentTypes) {
        this(repository, authorizers, storage.getIfAvailable(), audit, maxSizeBytes, allowedContentTypes);
    }

    public AttachmentService(AttachmentRepository repository, List<AttachmentAuthorizer> authorizers,
            ObjectStorage storage, AuditService audit, long maxSizeBytes, Set<String> allowedContentTypes) {
        this.repository = repository;
        this.authorizers = List.copyOf(authorizers);
        if (this.authorizers.size() > 1) {
            throw new IllegalStateException("Multiple attachment authorizers are ambiguous");
        }
        this.storage = storage;
        this.audit = audit;
        this.maxSizeBytes = maxSizeBytes;
        this.allowedContentTypes = allowedContentTypes.stream()
                .map(value -> value.toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    @Transactional
    public AttachmentDto upload(UUID actorId, String ownerType, UUID ownerId, MultipartFile file) {
        String validOwnerType = validOwnerType(ownerType);
        if (actorId == null || ownerId == null) throw new InvalidRequestException("Attachment owner is required");
        requireWrite(actorId, validOwnerType, ownerId);
        if (storage == null) throw new InvalidRequestException("Attachment storage is unavailable");
        String filename = validFilename(file);
        String contentType = validContentType(file == null ? null : file.getContentType());
        long size = file.getSize();
        if (size <= 0) throw new InvalidRequestException("Attachment file is required");
        if (size > maxSizeBytes) throw new InvalidRequestException("Attachment is too large");

        String key = "attachments/" + UUID.randomUUID() + "/" + UUID.randomUUID();
        String digest;
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            try (InputStream input = bounded(file.getInputStream(), maxSizeBytes);
                    DigestInputStream digesting = new DigestInputStream(input, sha256)) {
                storage.put(key, contentType, size, digesting);
            }
            digest = HexFormat.of().formatHex(sha256.digest());
        } catch (InvalidRequestException | StorageException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new InvalidRequestException("Attachment upload failed", exception);
        }

        Attachment attachment = new Attachment(validOwnerType, ownerId, filename, contentType, size, digest, key, actorId);
        try {
            repository.save(attachment);
            flushMetadata();
        } catch (RuntimeException exception) {
            try {
                storage.delete(key);
            } catch (RuntimeException ignored) {
            }
            throw new InvalidRequestException("Attachment could not be saved", exception);
        }
        audit.record(new AuditCommand(actorId, "ATTACHMENT_UPLOADED", "ATTACHMENT", attachment.getId(),
                null, Map.of("ownerType", validOwnerType, "ownerId", ownerId,
                        "filename", filename, "contentType", contentType, "sizeBytes", size,
                        "sha256", digest), null, null));
        return toDto(attachment);
    }

    protected void flushMetadata() {
        repository.flush();
    }

    @Transactional(readOnly = true)
    public AttachmentDownload download(UUID actorId, UUID attachmentId) {
        Attachment attachment = repository.findByIdAndDeletedFalse(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found"));
        requireRead(actorId, attachment.getOwnerType(), attachment.getOwnerId());
        try {
            StoredObject object = storage.get(attachment.getStorageKey());
            return new AttachmentDownload(attachment.getOriginalFilename(), attachment.getContentType(),
                    attachment.getSizeBytes(), object.stream());
        } catch (StorageException exception) {
            if (exception.isNotFound()) {
                throw new ResourceNotFoundException("Attachment content is unavailable");
            }
            throw new InvalidRequestException("Attachment content is unavailable", exception);
        }
    }

    @Transactional
    public AttachmentDto delete(UUID actorId, UUID attachmentId, long expectedVersion) {
        Attachment attachment = repository.findByIdForUpdate(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Attachment not found"));
        if (attachment.isDeleted()) throw new ResourceNotFoundException("Attachment not found");
        requireWrite(actorId, attachment.getOwnerType(), attachment.getOwnerId());
        if (attachment.getVersion() != expectedVersion) throw new ConflictException("Attachment was modified");
        attachment.softDelete(actorId);
        repository.flush();
        deleteStorageObject(attachment);
        audit.record(new AuditCommand(actorId, "ATTACHMENT_DELETED", "ATTACHMENT", attachment.getId(),
                Map.of("deleted", false), Map.of("deleted", true), null, null));
        return toDto(attachment);
    }

    @Transactional
    public int cleanupPendingDeletes(int batchSize) {
        int count = 0;
        for (Attachment attachment : repository.findPendingStorageDeletes(PageRequest.of(0, Math.max(1, batchSize)))) {
            if (deleteStorageObject(attachment)) count++;
        }
        return count;
    }

    private boolean deleteStorageObject(Attachment attachment) {
        try {
            storage.delete(attachment.getStorageKey());
            attachment.markStorageDeleted();
            return true;
        } catch (StorageException exception) {
            if (exception.isNotFound()) {
                attachment.markStorageDeleted();
                return true;
            }
            attachment.markStorageDeleteFailed();
            return false;
        }
    }

    private void requireRead(UUID actorId, String ownerType, UUID ownerId) {
        if (actorId == null || authorizers.size() != 1 || !authorizers.get(0).canRead(actorId, ownerType, ownerId)) {
            throw new AccessDeniedException("Access denied");
        }
    }

    private void requireWrite(UUID actorId, String ownerType, UUID ownerId) {
        if (actorId == null || authorizers.size() != 1 || !authorizers.get(0).canWrite(actorId, ownerType, ownerId)) {
            throw new AccessDeniedException("Access denied");
        }
    }

    private static String validOwnerType(String ownerType) {
        if (ownerType == null || !OWNER_TYPE.matcher(ownerType).matches()) {
            throw new InvalidRequestException("Invalid attachment owner type");
        }
        return ownerType;
    }

    private static String validFilename(MultipartFile file) {
        if (file == null || file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()) {
            throw new InvalidRequestException("Attachment filename is required");
        }
        String filename = file.getOriginalFilename().replace('\\', '/');
        int slash = filename.lastIndexOf('/');
        if (slash >= 0) filename = filename.substring(slash + 1);
        filename = filename.replace('\r', ' ').replace('\n', ' ').strip();
        if (filename.isBlank() || filename.length() > 255) {
            throw new InvalidRequestException("Attachment filename is invalid");
        }
        return filename;
    }

    private String validContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            throw new InvalidRequestException("Attachment content type is required");
        }
        String normalized = contentType.toLowerCase(Locale.ROOT);
        if (!allowedContentTypes.contains(normalized)) {
            throw new InvalidRequestException("Attachment content type is not allowed");
        }
        return normalized;
    }

    private static InputStream bounded(InputStream input, long max) {
        return new FilterInputStream(input) {
            private long read;
            @Override public int read() throws IOException {
                int result = super.read();
                if (result >= 0) increment(1);
                return result;
            }
            @Override public int read(byte[] b, int off, int len) throws IOException {
                int result = super.read(b, off, len);
                if (result > 0) increment(result);
                return result;
            }
            private void increment(long amount) {
                read += amount;
                if (read > max) throw new InvalidRequestException("Attachment is too large");
            }
        };
    }

    private static AttachmentDto toDto(Attachment attachment) {
        return new AttachmentDto(attachment.getId(), attachment.getOwnerType(), attachment.getOwnerId(),
                attachment.getOriginalFilename(), attachment.getContentType(), attachment.getSizeBytes(),
                attachment.getSha256(), attachment.isDeleted(), attachment.getVersion());
    }

    public record AttachmentDto(UUID id, String ownerType, UUID ownerId, String originalFilename,
            String contentType, long sizeBytes, String sha256, boolean deleted, long version) {}

    public record AttachmentDownload(String filename, String contentType, long sizeBytes, InputStream stream) {}
}

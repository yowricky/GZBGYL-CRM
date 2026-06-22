package com.gzbgyl.crm.attachment.domain;

import com.gzbgyl.crm.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "attachment")
public class Attachment extends BaseEntity {
    @Column(name = "owner_type", nullable = false, length = 150)
    private String ownerType;
    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;
    @Column(name = "original_filename", nullable = false, length = 500)
    private String originalFilename;
    @Column(name = "content_type", nullable = false, length = 255)
    private String contentType;
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;
    @Column(nullable = false, length = 64)
    private String sha256;
    @Column(name = "storage_key", nullable = false, length = 1000)
    private String storageKey;
    @Column(nullable = false)
    private boolean deleted;
    @Column(name = "deleted_at")
    private Instant deletedAt;
    @Column(name = "deleted_by")
    private UUID deletedBy;
    @Column(name = "storage_deleted_at")
    private Instant storageDeletedAt;
    @Column(name = "storage_delete_attempts", nullable = false)
    private int storageDeleteAttempts;

    protected Attachment() {
    }

    public Attachment(String ownerType, UUID ownerId, String originalFilename, String contentType,
            long sizeBytes, String sha256, String storageKey, UUID actorId) {
        this.ownerType = ownerType;
        this.ownerId = ownerId;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.storageKey = storageKey;
    }

    public String getOwnerType() { return ownerType; }
    public UUID getOwnerId() { return ownerId; }
    public String getOriginalFilename() { return originalFilename; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getSha256() { return sha256; }
    public String getStorageKey() { return storageKey; }
    public boolean isDeleted() { return deleted; }
    public Instant getDeletedAt() { return deletedAt; }
    public UUID getDeletedBy() { return deletedBy; }
    public Instant getStorageDeletedAt() { return storageDeletedAt; }
    public int getStorageDeleteAttempts() { return storageDeleteAttempts; }

    public void softDelete(UUID actorId) {
        deleted = true;
        deletedAt = Instant.now();
        deletedBy = actorId;
    }

    public void markStorageDeleted() {
        storageDeletedAt = Instant.now();
    }

    public void markStorageDeleteFailed() {
        storageDeleteAttempts++;
    }
}

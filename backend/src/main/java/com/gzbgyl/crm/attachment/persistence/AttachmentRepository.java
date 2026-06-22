package com.gzbgyl.crm.attachment.persistence;

import com.gzbgyl.crm.attachment.domain.Attachment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {
    Optional<Attachment> findByIdAndDeletedFalse(UUID id);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select attachment from Attachment attachment
            where attachment.deleted = true
              and attachment.storageDeletedAt is null
            order by attachment.deletedAt asc
            """)
    List<Attachment> findPendingStorageDeletes(Pageable pageable);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select attachment from Attachment attachment where attachment.id = :id")
    Optional<Attachment> findByIdForUpdate(@Param("id") UUID id);
}

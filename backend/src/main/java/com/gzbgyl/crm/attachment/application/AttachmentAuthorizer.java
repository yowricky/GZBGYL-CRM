package com.gzbgyl.crm.attachment.application;

import java.util.UUID;

public interface AttachmentAuthorizer {
    boolean canRead(UUID actorId, String ownerType, UUID ownerId);
    boolean canWrite(UUID actorId, String ownerType, UUID ownerId);
}

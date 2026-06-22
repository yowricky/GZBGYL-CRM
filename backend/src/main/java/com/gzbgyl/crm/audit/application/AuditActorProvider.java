package com.gzbgyl.crm.audit.application;

import java.util.UUID;

public interface AuditActorProvider {
    UUID actorId();
    String ipAddress();
}

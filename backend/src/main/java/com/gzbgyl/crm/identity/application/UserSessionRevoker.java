package com.gzbgyl.crm.identity.application;

import java.util.UUID;

public interface UserSessionRevoker {
    void revokeSessions(UUID userId);
}

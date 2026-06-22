package com.gzbgyl.crm.shared.security;

import java.util.UUID;
import org.springframework.security.core.Authentication;

public interface SessionSecurityService {
    Authentication startSession(Authentication authentication);

    boolean isCurrent(CrmUserPrincipal principal);

    void cleanupOlderSessions(String currentSessionId, CrmUserPrincipal principal);

    void revokeSessions(UUID userId);
}

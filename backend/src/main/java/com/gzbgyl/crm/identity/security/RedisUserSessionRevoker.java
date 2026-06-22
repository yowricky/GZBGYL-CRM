package com.gzbgyl.crm.identity.security;

import com.gzbgyl.crm.identity.application.UserSessionRevoker;
import com.gzbgyl.crm.shared.security.SessionSecurityService;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RedisUserSessionRevoker implements UserSessionRevoker {
    private final SessionSecurityService sessions;

    public RedisUserSessionRevoker(SessionSecurityService sessions) {
        this.sessions = sessions;
    }

    @Override
    public void revokeSessions(UUID userId) {
        sessions.revokeSessions(userId);
    }
}

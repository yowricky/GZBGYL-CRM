package com.gzbgyl.crm.audit.application;

import com.gzbgyl.crm.shared.security.CurrentUserService;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserAuditActorProvider implements AuditActorProvider {
    private final CurrentUserService currentUser;

    public CurrentUserAuditActorProvider(CurrentUserService currentUser) {
        this.currentUser = currentUser;
    }

    @Override
    public UUID actorId() {
        return currentUser.optional().map(user -> user.id()).orElse(null);
    }

    @Override
    public String ipAddress() {
        try {
            return currentUser.requestMetadata().remoteAddress();
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}

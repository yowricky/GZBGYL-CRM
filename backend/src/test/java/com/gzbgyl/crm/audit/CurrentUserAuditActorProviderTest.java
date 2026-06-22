package com.gzbgyl.crm.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gzbgyl.crm.audit.application.CurrentUserAuditActorProvider;
import com.gzbgyl.crm.shared.security.CurrentUser;
import com.gzbgyl.crm.shared.security.CurrentUserService;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CurrentUserAuditActorProviderTest {
    @Test
    void suppliesCurrentActorAndRequestIp() {
        UUID actorId = UUID.randomUUID();
        CurrentUserService currentUser = mock(CurrentUserService.class);
        when(currentUser.optional()).thenReturn(Optional.of(new CurrentUser(actorId, "admin", "Admin",
                UUID.randomUUID(), Set.of("SYSTEM_ADMIN"), Set.of("system:admin"))));
        when(currentUser.requestMetadata()).thenReturn(
                new CurrentUserService.RequestMetadata("203.0.113.10", "POST", "/api/admin/users"));

        CurrentUserAuditActorProvider provider = new CurrentUserAuditActorProvider(currentUser);

        assertThat(provider.actorId()).isEqualTo(actorId);
        assertThat(provider.ipAddress()).isEqualTo("203.0.113.10");
    }

    @Test
    void safelyRepresentsSystemWorkWithoutRequestContext() {
        CurrentUserService currentUser = mock(CurrentUserService.class);
        when(currentUser.optional()).thenReturn(Optional.empty());
        when(currentUser.requestMetadata()).thenThrow(new IllegalStateException("no request"));

        CurrentUserAuditActorProvider provider = new CurrentUserAuditActorProvider(currentUser);

        assertThat(provider.actorId()).isNull();
        assertThat(provider.ipAddress()).isNull();
    }
}

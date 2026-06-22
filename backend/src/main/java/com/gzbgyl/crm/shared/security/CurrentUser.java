package com.gzbgyl.crm.shared.security;

import java.util.Set;
import java.util.UUID;

public record CurrentUser(
        UUID id,
        String username,
        String displayName,
        UUID organizationUnitId,
        Set<String> roles,
        Set<String> permissions) {

    static CurrentUser from(CrmUserPrincipal principal) {
        return new CurrentUser(principal.id(), principal.getUsername(), principal.displayName(),
                principal.organizationUnitId(), principal.roles(), principal.permissions());
    }
}

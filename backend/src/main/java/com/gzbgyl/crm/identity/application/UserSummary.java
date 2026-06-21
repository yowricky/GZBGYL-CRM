package com.gzbgyl.crm.identity.application;

import java.util.Set;
import java.util.UUID;

public record UserSummary(UUID id, String username, String displayName, UUID organizationUnitId,
        boolean active, long version, Set<String> roles, Set<String> permissions) {
}

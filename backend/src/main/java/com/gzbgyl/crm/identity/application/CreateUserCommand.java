package com.gzbgyl.crm.identity.application;

import java.util.Set;
import java.util.UUID;

public record CreateUserCommand(String username, String displayName, String initialPassword,
        UUID organizationUnitId, Set<String> roleCodes) {
}

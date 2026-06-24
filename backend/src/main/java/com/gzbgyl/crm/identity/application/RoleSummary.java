package com.gzbgyl.crm.identity.application;

import java.util.List;
import java.util.UUID;

public record RoleSummary(
        UUID id,
        String code,
        String name,
        boolean systemRole,
        boolean editable,
        long version,
        List<PermissionSummary> permissions) {
}

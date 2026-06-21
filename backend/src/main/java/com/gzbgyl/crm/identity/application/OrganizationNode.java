package com.gzbgyl.crm.identity.application;

import java.util.UUID;

public record OrganizationNode(
        UUID id, UUID parentId, String code, String name, String path, boolean active, long version) {
}

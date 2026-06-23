package com.gzbgyl.crm.identity.application;

import java.util.UUID;

public record UserSearchQuery(String keyword, UUID organizationUnitId, Boolean active) {
    public UserSearchQuery {
        if (keyword != null) {
            keyword = keyword.trim();
            if (keyword.isEmpty()) {
                keyword = null;
            }
        }
    }
}

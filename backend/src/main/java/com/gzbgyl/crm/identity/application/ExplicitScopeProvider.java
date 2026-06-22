package com.gzbgyl.crm.identity.application;

import java.util.Set;
import java.util.UUID;

/** Supplies opportunity-specific access owned by the future opportunity collaboration module. */
@FunctionalInterface
public interface ExplicitScopeProvider {
    Set<UUID> opportunityIds(UUID userId);
}

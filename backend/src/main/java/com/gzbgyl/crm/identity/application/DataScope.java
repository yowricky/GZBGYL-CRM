package com.gzbgyl.crm.identity.application;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable authorization input for business repository queries. Future business modules must
 * accept this value, or a specification derived from it, rather than reconstructing identity
 * permissions inside lead, customer, opportunity, contract, or payment repositories.
 */
public record DataScope(
        UUID userId,
        Set<UUID> organizationUnitIds,
        Set<UUID> explicitOpportunityIds,
        boolean companyRead,
        boolean sensitiveFinancialFields) {

    public DataScope {
        Objects.requireNonNull(userId, "userId must not be null");
        organizationUnitIds = Set.copyOf(
                Objects.requireNonNull(organizationUnitIds, "organizationUnitIds must not be null"));
        explicitOpportunityIds = Set.copyOf(
                Objects.requireNonNull(explicitOpportunityIds, "explicitOpportunityIds must not be null"));
    }

    public static DataScope own(UUID userId) {
        return new DataScope(userId, Set.of(), Set.of(), false, false);
    }

    public boolean canReadOwnedBy(UUID ownerId) {
        return userId.equals(ownerId);
    }

    public boolean canReadOrganization(UUID organizationUnitId) {
        return companyRead || organizationUnitIds.contains(organizationUnitId);
    }

    public boolean canReadOpportunity(UUID opportunityId) {
        return companyRead || explicitOpportunityIds.contains(opportunityId);
    }
}

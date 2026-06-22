package com.gzbgyl.crm.identity.application;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable authorization input for business repository queries. Record visibility and financial
 * field visibility are intentionally independent; repositories must use the complete predicates.
 */
public record DataScope(
        UUID userId,
        boolean ownRead,
        Set<UUID> organizationUnitIds,
        Set<UUID> explicitOpportunityIds,
        boolean companyRead,
        boolean financialOwnRead,
        Set<UUID> financialOrganizationUnitIds,
        boolean financialCompanyRead) {

    public DataScope {
        Objects.requireNonNull(userId, "userId must not be null");
        organizationUnitIds = Set.copyOf(
                Objects.requireNonNull(organizationUnitIds, "organizationUnitIds must not be null"));
        explicitOpportunityIds = Set.copyOf(
                Objects.requireNonNull(explicitOpportunityIds, "explicitOpportunityIds must not be null"));
        financialOrganizationUnitIds = Set.copyOf(Objects.requireNonNull(
                financialOrganizationUnitIds, "financialOrganizationUnitIds must not be null"));
    }

    public static DataScope own(UUID userId) {
        return new DataScope(userId, true, Set.of(), Set.of(), false, false, Set.of(), false);
    }

    public boolean canReadOwnedBy(UUID ownerId) {
        return ownRead && userId.equals(ownerId);
    }

    public boolean canReadOrganization(UUID organizationUnitId) {
        return companyRead || organizationUnitIds.contains(organizationUnitId);
    }

    public boolean canReadExplicitOpportunity(UUID opportunityId) {
        return explicitOpportunityIds.contains(opportunityId);
    }

    public boolean canReadRecord(UUID ownerId, UUID organizationUnitId, UUID opportunityId) {
        return companyRead
                || canReadOwnedBy(ownerId)
                || organizationUnitIds.contains(organizationUnitId)
                || canReadExplicitOpportunity(opportunityId);
    }

    public boolean canReadSensitiveFinancial(
            UUID ownerId, UUID organizationUnitId, UUID opportunityId) {
        if (!canReadRecord(ownerId, organizationUnitId, opportunityId)) {
            return false;
        }
        return financialCompanyRead && companyRead
                || financialOwnRead && canReadOwnedBy(ownerId)
                || financialOrganizationUnitIds.contains(organizationUnitId)
                        && organizationUnitIds.contains(organizationUnitId);
    }
}

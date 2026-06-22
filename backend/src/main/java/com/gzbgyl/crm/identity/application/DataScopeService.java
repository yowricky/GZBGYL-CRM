package com.gzbgyl.crm.identity.application;

import com.gzbgyl.crm.identity.domain.AppUser;
import com.gzbgyl.crm.identity.domain.Permission;
import com.gzbgyl.crm.identity.domain.Role;
import com.gzbgyl.crm.identity.persistence.AppUserRepository;
import com.gzbgyl.crm.identity.persistence.OrganizationUnitRepository;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DataScopeService {

    private static final String USER_UNAVAILABLE = "用户不存在或已停用";
    private static final Set<String> SENSITIVE_ROLES = Set.of(
            "SALES", "SALES_MANAGER", "OPERATIONS_VIEWER", "FINANCE_VIEWER",
            "EXECUTIVE_VIEWER", "SYSTEM_ADMIN");

    private final AppUserRepository userRepository;
    private final OrganizationUnitRepository organizationRepository;
    private final ExplicitScopeProvider explicitScopeProvider;

    public DataScopeService(AppUserRepository userRepository,
            OrganizationUnitRepository organizationRepository,
            ExplicitScopeProvider explicitScopeProvider) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.explicitScopeProvider = explicitScopeProvider;
    }

    @Transactional(readOnly = true)
    public DataScope resolve(UUID userId) {
        AppUser user = userRepository.findDetailedById(userId)
                .filter(AppUser::isActive)
                .orElseThrow(() -> new DataScopeUnavailableException(USER_UNAVAILABLE));
        Set<String> roles = user.getRoles().stream().map(Role::getCode).collect(Collectors.toSet());
        Set<String> permissions = user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(Permission::getCode)
                .collect(Collectors.toSet());

        Set<UUID> organizations = permissions.contains("opportunity:read:department")
                ? organizationRepository.findActiveEffectiveSubtreeIds(user.getOrganizationUnitId())
                : Set.of();
        Set<UUID> opportunities = permissions.contains("opportunity:read:assigned")
                ? sanitizedExplicitOpportunities(userId)
                : Set.of();
        boolean companyRead = permissions.contains("performance:read:company")
                || permissions.contains("system:admin");
        boolean sensitive = roles.stream().anyMatch(SENSITIVE_ROLES::contains);
        return new DataScope(userId, organizations, opportunities, companyRead, sensitive);
    }

    private Set<UUID> sanitizedExplicitOpportunities(UUID userId) {
        Set<UUID> supplied = explicitScopeProvider.opportunityIds(userId);
        if (supplied == null || supplied.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<UUID> sanitized = new LinkedHashSet<>();
        for (UUID opportunityId : supplied) {
            if (opportunityId != null) {
                sanitized.add(opportunityId);
            }
        }
        return Set.copyOf(sanitized);
    }
}

package com.gzbgyl.crm.identity.application;

import com.gzbgyl.crm.identity.domain.AppUser;
import com.gzbgyl.crm.identity.domain.Permission;
import com.gzbgyl.crm.identity.persistence.AppUserRepository;
import com.gzbgyl.crm.identity.persistence.OrganizationUnitRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DataScopeService {

    private static final String USER_UNAVAILABLE = "用户不存在或已停用";
    private static final String MULTIPLE_PROVIDERS =
            "Multiple ExplicitScopeProvider beans configured; exactly one is supported";

    private final AppUserRepository userRepository;
    private final OrganizationUnitRepository organizationRepository;
    private final ExplicitScopeProvider explicitScopeProvider;

    public DataScopeService(AppUserRepository userRepository,
            OrganizationUnitRepository organizationRepository,
            ObjectProvider<ExplicitScopeProvider> explicitScopeProviders) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        List<ExplicitScopeProvider> providers = explicitScopeProviders.orderedStream().toList();
        if (providers.size() > 1) {
            throw new IllegalStateException(MULTIPLE_PROVIDERS);
        }
        this.explicitScopeProvider = providers.isEmpty() ? userId -> Set.of() : providers.getFirst();
    }

    @Transactional(readOnly = true)
    public DataScope resolve(UUID userId) {
        AppUser user = userRepository.findDetailedById(userId)
                .filter(AppUser::isActive)
                .orElseThrow(() -> new DataScopeUnavailableException(USER_UNAVAILABLE));
        Set<String> permissions = user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(Permission::getCode)
                .collect(Collectors.toSet());

        boolean departmentRead = permissions.contains("opportunity:read:department");
        boolean financialDepartmentRead = permissions.contains("financial:read:department");
        Set<UUID> effectiveHierarchy = departmentRead || financialDepartmentRead
                ? organizationRepository.findActiveEffectiveSubtreeIds(user.getOrganizationUnitId())
                : Set.of();
        return new DataScope(
                userId,
                permissions.contains("opportunity:read:own"),
                departmentRead ? effectiveHierarchy : Set.of(),
                permissions.contains("opportunity:read:assigned")
                        ? sanitizedExplicitOpportunities(userId) : Set.of(),
                permissions.contains("opportunity:read:company"),
                permissions.contains("financial:read:own"),
                financialDepartmentRead ? effectiveHierarchy : Set.of(),
                permissions.contains("financial:read:company"));
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

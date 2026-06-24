package com.gzbgyl.crm.identity.application;

import com.gzbgyl.crm.audit.application.AuditActorProvider;
import com.gzbgyl.crm.audit.application.AuditCommand;
import com.gzbgyl.crm.audit.application.AuditService;
import com.gzbgyl.crm.identity.domain.Permission;
import com.gzbgyl.crm.identity.domain.Role;
import com.gzbgyl.crm.identity.persistence.AppUserRepository;
import com.gzbgyl.crm.identity.persistence.PermissionRepository;
import com.gzbgyl.crm.identity.persistence.RoleRepository;
import com.gzbgyl.crm.shared.api.InvalidRequestException;
import com.gzbgyl.crm.shared.api.InvalidStateException;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleAdministrationService {

    private static final String PROTECTED_ADMIN_ROLE = "SYSTEM_ADMIN";

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final AppUserRepository userRepository;
    private final UserSessionRevoker sessionRevoker;
    private final AuditService audit;
    private final AuditActorProvider actor;

    public RoleAdministrationService(
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            AppUserRepository userRepository,
            UserSessionRevoker sessionRevoker,
            AuditService audit,
            AuditActorProvider actor) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.userRepository = userRepository;
        this.sessionRevoker = sessionRevoker;
        this.audit = audit;
        this.actor = actor;
    }

    @Transactional(readOnly = true)
    public List<RoleSummary> listRoles() {
        return roleRepository.findAllDetailedOrderByCode().stream()
                .map(RoleAdministrationService::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PermissionSummary> listPermissions() {
        return permissionRepository.findAllByOrderByCodeAsc().stream()
                .map(RoleAdministrationService::toSummary)
                .toList();
    }

    @Transactional
    public RoleSummary replacePermissions(UUID roleId, Set<String> permissionCodes, long expectedVersion,
            String reason) {
        if (roleId == null) {
            throw new InvalidRequestException("角色不存在");
        }
        Role role = roleRepository.findDetailedById(roleId)
                .orElseThrow(() -> new InvalidRequestException("角色不存在"));
        if (PROTECTED_ADMIN_ROLE.equals(role.getCode())) {
            throw new InvalidStateException("系统管理员角色权限不允许在界面中修改");
        }
        if (role.getVersion() != expectedVersion) {
            throw new IdentityConflictException("角色已被其他用户修改，请刷新后重试");
        }
        Set<Permission> permissions = resolvePermissions(permissionCodes);
        Set<String> previous = role.getPermissions().stream()
                .map(Permission::getCode)
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        role.replacePermissions(permissions);
        try {
            roleRepository.flush();
        } catch (OptimisticLockingFailureException exception) {
            throw new IdentityConflictException("角色已被其他用户修改，请刷新后重试");
        }
        Role detailed = roleRepository.findDetailedById(roleId)
                .orElseThrow(() -> new InvalidRequestException("角色不存在"));
        RoleSummary result = toSummary(detailed);
        audit.record(new AuditCommand(actor.actorId(), "ROLE_PERMISSIONS_UPDATED", "ROLE", roleId,
                Map.of("permissions", previous), Map.of("permissions", result.permissions().stream()
                        .map(PermissionSummary::code).toList()), actor.ipAddress(), reason));
        userRepository.findIdsByRoleId(roleId).forEach(sessionRevoker::revokeSessions);
        return result;
    }

    private Set<Permission> resolvePermissions(Collection<String> permissionCodes) {
        if (permissionCodes == null || permissionCodes.isEmpty()) {
            throw new InvalidRequestException("权限不能为空");
        }
        Set<String> normalized = new TreeSet<>();
        for (String code : permissionCodes) {
            if (code == null || code.isBlank()) {
                throw new InvalidRequestException("权限不能为空");
            }
            normalized.add(code.trim().toLowerCase(Locale.ROOT));
        }
        Set<Permission> permissions = permissionRepository.findAllByCodeIn(normalized);
        Set<String> found = permissions.stream().map(Permission::getCode)
                .collect(java.util.stream.Collectors.toSet());
        normalized.stream().filter(code -> !found.contains(code)).findFirst()
                .ifPresent(code -> { throw new InvalidRequestException("权限不存在: " + code); });
        return permissions;
    }

    private static RoleSummary toSummary(Role role) {
        return new RoleSummary(role.getId(), role.getCode(), role.getName(), role.isSystemRole(),
                !PROTECTED_ADMIN_ROLE.equals(role.getCode()), role.getVersion(),
                role.getPermissions().stream()
                        .sorted(Comparator.comparing(Permission::getCode))
                        .map(RoleAdministrationService::toSummary)
                        .toList());
    }

    private static PermissionSummary toSummary(Permission permission) {
        return new PermissionSummary(permission.getCode(), permission.getName(), permission.getDescription());
    }
}

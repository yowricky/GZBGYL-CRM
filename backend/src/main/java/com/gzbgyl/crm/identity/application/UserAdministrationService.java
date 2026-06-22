package com.gzbgyl.crm.identity.application;

import com.gzbgyl.crm.identity.domain.AppUser;
import com.gzbgyl.crm.identity.domain.Permission;
import com.gzbgyl.crm.identity.domain.Role;
import com.gzbgyl.crm.identity.persistence.AppUserRepository;
import com.gzbgyl.crm.identity.persistence.OrganizationUnitRepository;
import com.gzbgyl.crm.identity.persistence.RoleRepository;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAdministrationService {

    private static final String DUPLICATE_USERNAME = "用户名已存在";
    private static final String CONFLICT = "用户已被其他用户修改，请刷新后重试";
    private final AppUserRepository userRepository;
    private final RoleRepository roleRepository;
    private final OrganizationUnitRepository organizationRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final UserSessionRevoker sessionRevoker;

    public UserAdministrationService(AppUserRepository userRepository, RoleRepository roleRepository,
            OrganizationUnitRepository organizationRepository, BCryptPasswordEncoder passwordEncoder,
            UserSessionRevoker sessionRevoker) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.organizationRepository = organizationRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionRevoker = sessionRevoker;
    }

    @Transactional
    public UserSummary createUser(CreateUserCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("用户信息不能为空");
        }
        String username = validUsername(command.username());
        String normalizedUsername = username.toLowerCase(Locale.ROOT);
        String displayName = validDisplayName(command.displayName());
        String password = validPassword(command.initialPassword());
        UUID organizationId = command.organizationUnitId();
        if (organizationId == null) {
            throw new IllegalArgumentException("组织不存在");
        }
        organizationRepository.acquireHierarchyMutationLock();
        var organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("组织不存在"));
        if (!organization.isActive()) {
            throw new IllegalStateException("组织已停用");
        }
        Set<Role> roles = resolveRoles(command.roleCodes());
        if (userRepository.existsByNormalizedUsername(normalizedUsername)) {
            throw new IllegalArgumentException(DUPLICATE_USERNAME);
        }
        AppUser user = new AppUser(organizationId, username, normalizedUsername, displayName,
                passwordEncoder.encode(password), roles);
        try {
            return toSummary(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException exception) {
            if (hasConstraint(exception, "uk_app_user_normalized_username")) {
                throw new IllegalArgumentException(DUPLICATE_USERNAME, exception);
            }
            throw exception;
        }
    }

    @Transactional
    public UserSummary activate(UUID id, long expectedVersion) {
        AppUser user = existing(id);
        requireVersion(user, expectedVersion);
        user.activate();
        return flushAndSummarize(user);
    }

    @Transactional
    public UserSummary deactivate(UUID id, long expectedVersion) {
        AppUser user = existing(id);
        requireVersion(user, expectedVersion);
        user.deactivate();
        UserSummary result = flushAndSummarize(user);
        sessionRevoker.revokeSessions(id);
        return result;
    }

    @Transactional
    public UserSummary resetPassword(UUID id, String password, long expectedVersion) {
        String validatedPassword = validPassword(password);
        AppUser user = existing(id);
        requireVersion(user, expectedVersion);
        user.resetPassword(passwordEncoder.encode(validatedPassword));
        UserSummary result = flushAndSummarize(user);
        sessionRevoker.revokeSessions(id);
        return result;
    }

    @Transactional
    public UserSummary assignRoles(UUID id, Set<String> roleCodes, long expectedVersion) {
        Set<Role> roles = resolveRoles(roleCodes);
        AppUser user = existing(id);
        requireVersion(user, expectedVersion);
        user.replaceRoles(roles);
        UserSummary result = flushAndSummarize(user);
        sessionRevoker.revokeSessions(id);
        return result;
    }

    private AppUser existing(UUID id) {
        if (id == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
    }

    private Set<Role> resolveRoles(Collection<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            throw new IllegalArgumentException("角色不能为空");
        }
        Set<String> normalized = new TreeSet<>();
        for (String code : roleCodes) {
            if (code == null || code.isBlank()) {
                throw new IllegalArgumentException("角色不能为空");
            }
            normalized.add(code.trim().toUpperCase(Locale.ROOT));
        }
        Set<Role> roles = roleRepository.findAllByCodeIn(normalized);
        Set<String> found = roles.stream().map(Role::getCode).collect(java.util.stream.Collectors.toSet());
        normalized.stream().filter(code -> !found.contains(code)).findFirst()
                .ifPresent(code -> { throw new IllegalArgumentException("角色不存在: " + code); });
        return roles;
    }

    private UserSummary flushAndSummarize(AppUser user) {
        try {
            userRepository.flush();
            AppUser detailed = userRepository.findDetailedById(user.getId())
                    .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
            return toSummary(detailed);
        } catch (OptimisticLockingFailureException exception) {
            throw new IdentityConflictException(CONFLICT);
        }
    }

    private void requireVersion(AppUser user, long expectedVersion) {
        if (user.getVersion() != expectedVersion) {
            throw new IdentityConflictException(CONFLICT);
        }
    }

    private static UserSummary toSummary(AppUser user) {
        Set<String> roles = new TreeSet<>();
        Set<String> permissions = new TreeSet<>();
        for (Role role : user.getRoles()) {
            roles.add(role.getCode());
            role.getPermissions().stream().map(Permission::getCode).forEach(permissions::add);
        }
        return new UserSummary(user.getId(), user.getUsername(), user.getDisplayName(),
                user.getOrganizationUnitId(), user.isActive(), user.getVersion(),
                Set.copyOf(roles), Set.copyOf(permissions));
    }

    private static String validUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        String trimmed = username.trim();
        if (trimmed.length() > 80) {
            throw new IllegalArgumentException("用户名长度不能超过80个字符");
        }
        return trimmed;
    }

    private static String validDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("显示名称不能为空");
        }
        String trimmed = displayName.trim();
        if (trimmed.length() > 120) {
            throw new IllegalArgumentException("显示名称长度不能超过120个字符");
        }
        return trimmed;
    }

    private static String validPassword(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        if (password.length() < 12) {
            throw new IllegalArgumentException("密码长度不能少于12个字符");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("密码UTF-8编码不能超过72字节");
        }
        return password;
    }

    private static boolean hasConstraint(Throwable exception, String name) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation
                    && name.equals(violation.getConstraintName())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}

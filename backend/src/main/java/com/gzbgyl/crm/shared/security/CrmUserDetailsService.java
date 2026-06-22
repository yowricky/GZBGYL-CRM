package com.gzbgyl.crm.shared.security;

import com.gzbgyl.crm.identity.domain.AppUser;
import com.gzbgyl.crm.identity.domain.Permission;
import com.gzbgyl.crm.identity.domain.Role;
import com.gzbgyl.crm.identity.persistence.AppUserRepository;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CrmUserDetailsService implements UserDetailsService {
    private final AppUserRepository users;

    public CrmUserDetailsService(AppUserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String normalized = username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
        AppUser user = users.findByNormalizedUsername(normalized)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        Set<String> roles = user.getRoles().stream().map(Role::getCode).collect(Collectors.toSet());
        Set<String> permissions = user.getRoles().stream().flatMap(role -> role.getPermissions().stream())
                .map(Permission::getCode).collect(Collectors.toSet());
        return new CrmUserPrincipal(user.getId(), user.getOrganizationUnitId(), user.getUsername(),
                user.getDisplayName(), user.getPasswordHash(), user.isActive(), roles, permissions);
    }
}

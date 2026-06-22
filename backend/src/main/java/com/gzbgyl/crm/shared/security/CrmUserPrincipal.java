package com.gzbgyl.crm.shared.security;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.CredentialsContainer;

public final class CrmUserPrincipal implements UserDetails, CredentialsContainer, Serializable {
    @Serial private static final long serialVersionUID = 1L;

    private final UUID id;
    private final UUID organizationUnitId;
    private final String username;
    private final String displayName;
    private final boolean enabled;
    private final Set<String> roles;
    private final Set<String> permissions;
    private transient String passwordHash;

    public CrmUserPrincipal(UUID id, UUID organizationUnitId, String username, String displayName,
            String passwordHash, boolean enabled, Set<String> roles, Set<String> permissions) {
        this.id = id;
        this.organizationUnitId = organizationUnitId;
        this.username = username;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.enabled = enabled;
        this.roles = Collections.unmodifiableSet(new TreeSet<>(roles));
        this.permissions = Collections.unmodifiableSet(new TreeSet<>(permissions));
    }

    public UUID id() { return id; }
    public UUID organizationUnitId() { return organizationUnitId; }
    public String displayName() { return displayName; }
    public Set<String> roles() { return roles; }
    public Set<String> permissions() { return permissions; }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return permissions.stream().map(SimpleGrantedAuthority::new).toList();
    }
    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return username; }
    @Override public boolean isEnabled() { return enabled; }
    @Override public void eraseCredentials() { passwordHash = null; }

    @Override
    public String toString() {
        return "CrmUserPrincipal[id=" + id + ", username=" + username + ", enabled=" + enabled + "]";
    }
}

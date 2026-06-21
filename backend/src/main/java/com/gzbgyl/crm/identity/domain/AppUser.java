package com.gzbgyl.crm.identity.domain;

import com.gzbgyl.crm.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "app_user")
public class AppUser extends BaseEntity {

    @Column(name = "organization_unit_id", nullable = false)
    private UUID organizationUnitId;

    @Column(name = "username", nullable = false, length = 150)
    private String username;

    @Column(name = "normalized_username", nullable = false, unique = true, length = 150)
    private String normalizedUsername;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "app_user_role",
            joinColumns = @JoinColumn(name = "app_user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new LinkedHashSet<>();

    protected AppUser() {
    }

    public AppUser(UUID organizationUnitId, String username, String normalizedUsername,
            String displayName, String passwordHash, Set<Role> roles) {
        this.organizationUnitId = organizationUnitId;
        this.username = username;
        this.normalizedUsername = normalizedUsername;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.roles.addAll(roles);
    }

    public UUID getOrganizationUnitId() {
        return organizationUnitId;
    }

    public String getUsername() {
        return username;
    }

    public String getNormalizedUsername() {
        return normalizedUsername;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isActive() {
        return active;
    }

    public Set<Role> getRoles() {
        return Set.copyOf(roles);
    }

    public void activate() {
        active = true;
    }

    public void deactivate() {
        active = false;
    }

    public void resetPassword(String newPasswordHash) {
        passwordHash = newPasswordHash;
    }

    public void replaceRoles(Set<Role> replacement) {
        roles.clear();
        roles.addAll(replacement);
    }
}

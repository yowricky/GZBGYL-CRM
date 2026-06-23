package com.gzbgyl.crm.identity.application;

import com.gzbgyl.crm.identity.domain.AppUser;
import com.gzbgyl.crm.identity.domain.OrganizationUnit;
import com.gzbgyl.crm.identity.domain.Role;
import com.gzbgyl.crm.identity.persistence.AppUserRepository;
import com.gzbgyl.crm.identity.persistence.OrganizationUnitRepository;
import com.gzbgyl.crm.identity.persistence.RoleRepository;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class InitialAdminBootstrap implements ApplicationRunner {

    private static final String USERNAME = "admin";
    private static final String ROLE = "SYSTEM_ADMIN";

    private final String initialAdminPassword;
    private final AppUserRepository users;
    private final OrganizationUnitRepository organizations;
    private final RoleRepository roles;
    private final BCryptPasswordEncoder passwordEncoder;

    public InitialAdminBootstrap(
            @Value("${app.bootstrap.initial-admin-password:}") String initialAdminPassword,
            AppUserRepository users,
            OrganizationUnitRepository organizations,
            RoleRepository roles,
            BCryptPasswordEncoder passwordEncoder) {
        this.initialAdminPassword = initialAdminPassword;
        this.users = users;
        this.organizations = organizations;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        run();
    }

    @Transactional
    void run() {
        if (users.count() > 0) {
            return;
        }
        if (initialAdminPassword == null || initialAdminPassword.isBlank()) {
            return;
        }
        if (initialAdminPassword.length() < 12) {
            throw new IllegalStateException("INITIAL_ADMIN_PASSWORD must be at least 12 characters");
        }
        if (initialAdminPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("INITIAL_ADMIN_PASSWORD must not exceed 72 UTF-8 bytes");
        }

        OrganizationUnit root = organizations.findAll().stream().findFirst()
                .orElseGet(() -> organizations.saveAndFlush(new OrganizationUnit(null, "ROOT", "Root Organization", "/")));
        Set<Role> adminRoles = roles.findAllByCodeIn(Set.of(ROLE));
        if (adminRoles.size() != 1) {
            throw new IllegalStateException("Required role SYSTEM_ADMIN was not migrated");
        }

        AppUser admin = new AppUser(root.getId(), USERNAME, USERNAME.toLowerCase(Locale.ROOT),
                "System Administrator", passwordEncoder.encode(initialAdminPassword), adminRoles);
        users.saveAndFlush(admin);
    }
}

package com.gzbgyl.crm.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gzbgyl.crm.identity.domain.AppUser;
import com.gzbgyl.crm.identity.domain.OrganizationUnit;
import com.gzbgyl.crm.identity.domain.Role;
import com.gzbgyl.crm.identity.persistence.AppUserRepository;
import com.gzbgyl.crm.identity.persistence.OrganizationUnitRepository;
import com.gzbgyl.crm.identity.persistence.RoleRepository;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class InitialAdminBootstrapTest {

    private final AppUserRepository users = org.mockito.Mockito.mock(AppUserRepository.class);
    private final OrganizationUnitRepository organizations = org.mockito.Mockito.mock(OrganizationUnitRepository.class);
    private final RoleRepository roles = org.mockito.Mockito.mock(RoleRepository.class);
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    @Test
    void createsRootOrganizationAndAdminWhenNoUsersExist() {
        when(users.count()).thenReturn(0L);
        when(organizations.findAll()).thenReturn(List.of());
        when(organizations.saveAndFlush(any())).then(returnsFirstArg());
        when(roles.findAllByCodeIn(Set.of("SYSTEM_ADMIN"))).thenReturn(Set.of(org.mockito.Mockito.mock(Role.class)));

        InitialAdminBootstrap bootstrap = new InitialAdminBootstrap(
                "Admin#ChangeMe123", users, organizations, roles, passwordEncoder);

        bootstrap.run();

        ArgumentCaptor<OrganizationUnit> organization = ArgumentCaptor.forClass(OrganizationUnit.class);
        verify(organizations).saveAndFlush(organization.capture());
        assertThat(organization.getValue().getCode()).isEqualTo("ROOT");

        ArgumentCaptor<AppUser> admin = ArgumentCaptor.forClass(AppUser.class);
        verify(users).saveAndFlush(admin.capture());
        assertThat(admin.getValue().getUsername()).isEqualTo("admin");
        assertThat(admin.getValue().getNormalizedUsername()).isEqualTo("admin");
        assertThat(admin.getValue().getOrganizationUnitId()).isEqualTo(organization.getValue().getId());
        assertThat(passwordEncoder.matches("Admin#ChangeMe123", admin.getValue().getPasswordHash())).isTrue();
    }

    @Test
    void skipsBootstrapWhenUsersAlreadyExist() {
        when(users.count()).thenReturn(1L);

        InitialAdminBootstrap bootstrap = new InitialAdminBootstrap(
                "Admin#ChangeMe123", users, organizations, roles, passwordEncoder);

        bootstrap.run();

        org.mockito.Mockito.verify(organizations, org.mockito.Mockito.never()).saveAndFlush(any());
        org.mockito.Mockito.verify(users, org.mockito.Mockito.never()).saveAndFlush(any());
    }
}

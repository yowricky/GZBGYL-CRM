package com.gzbgyl.crm.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.gzbgyl.crm.identity.application.CreateUserCommand;
import com.gzbgyl.crm.identity.application.DataScope;
import com.gzbgyl.crm.identity.application.DataScopeService;
import com.gzbgyl.crm.identity.application.DataScopeUnavailableException;
import com.gzbgyl.crm.identity.application.ExplicitScopeProvider;
import com.gzbgyl.crm.identity.application.OrganizationNode;
import com.gzbgyl.crm.identity.application.OrganizationService;
import com.gzbgyl.crm.identity.application.UserAdministrationService;
import com.gzbgyl.crm.identity.application.UserSummary;
import com.gzbgyl.crm.support.PostgresIntegrationTest;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class DataScopeServiceTest extends PostgresIntegrationTest {

    @Autowired private DataScopeService service;
    @Autowired private OrganizationService organizations;
    @Autowired private UserAdministrationService users;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    @MockitoBean private ExplicitScopeProvider explicitScopes;

    @BeforeEach
    void cleanIdentityData() {
        jdbcTemplate.update("delete from app_user_role");
        jdbcTemplate.update("delete from app_user");
        jdbcTemplate.update("delete from organization_unit");
    }

    @Test
    void salespersonReceivesOnlyOwnScopeAndSensitiveFields() {
        OrganizationNode department = organizations.createRoot("SALES", "Sales");
        UserSummary salesperson = user("sales", department.id(), "SALES");
        when(explicitScopes.opportunityIds(salesperson.id())).thenReturn(Set.of(UUID.randomUUID()));

        DataScope scope = service.resolve(salesperson.id());

        assertThat(scope.userId()).isEqualTo(salesperson.id());
        assertThat(scope.organizationUnitIds()).isEmpty();
        assertThat(scope.explicitOpportunityIds()).isEmpty();
        assertThat(scope.companyRead()).isFalse();
        assertThat(scope.ownRead()).isTrue();
        assertThat(scope.financialOwnRead()).isTrue();
        assertThat(scope.canReadSensitiveFinancial(
                salesperson.id(), department.id(), UUID.randomUUID())).isTrue();
        assertThat(scope.canReadOwnedBy(salesperson.id())).isTrue();
        assertThat(scope.canReadOwnedBy(UUID.randomUUID())).isFalse();
    }

    @Test
    void managerReceivesActiveOwnDepartmentAndDescendantsOnly() {
        OrganizationNode company = organizations.createRoot("COMPANY", "Company");
        OrganizationNode sales = organizations.createChild(company.id(), "SALES", "Sales");
        OrganizationNode nested = organizations.createChild(sales.id(), "NESTED", "Nested");
        OrganizationNode sibling = organizations.createChild(company.id(), "SIBLING", "Sibling");
        UserSummary manager = user("manager", sales.id(), "SALES_MANAGER");

        DataScope scope = service.resolve(manager.id());

        assertThat(scope.organizationUnitIds()).containsExactlyInAnyOrder(sales.id(), nested.id())
                .doesNotContain(company.id(), sibling.id());
        assertThat(scope.companyRead()).isFalse();
        assertThat(scope.financialOrganizationUnitIds())
                .containsExactlyInAnyOrder(sales.id(), nested.id());
        assertThat(scope.canReadSensitiveFinancial(
                UUID.randomUUID(), nested.id(), UUID.randomUUID())).isTrue();
    }

    @Test
    void assignedRolesReceiveOnlyExplicitOpportunitiesWithRoleSpecificSensitivity() {
        OrganizationNode department = organizations.createRoot("DELIVERY", "Delivery");
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        for (String role : List.of("PRESALES_TECH", "PROJECT_MANAGER")) {
            UserSummary user = user(role.toLowerCase(), department.id(), role);
            when(explicitScopes.opportunityIds(user.id())).thenReturn(Set.of(first, second));

            DataScope scope = service.resolve(user.id());

            assertThat(scope.explicitOpportunityIds()).containsExactlyInAnyOrder(first, second);
            assertThat(scope.organizationUnitIds()).isEmpty();
            assertThat(scope.companyRead()).isFalse();
            assertThat(scope.canReadSensitiveFinancial(
                    UUID.randomUUID(), department.id(), first)).isFalse();
        }
    }

    @Test
    void operationsRoleDoesNotImplyExplicitCollaborationScope() {
        OrganizationNode department = organizations.createRoot("OPERATIONS", "Operations");
        UserSummary operations = user("operations", department.id(), "OPERATIONS_VIEWER");
        when(explicitScopes.opportunityIds(operations.id())).thenReturn(Set.of(UUID.randomUUID()));

        DataScope scope = service.resolve(operations.id());

        assertThat(scope.explicitOpportunityIds()).isEmpty();
        assertThat(scope.companyRead()).isFalse();
        assertThat(scope.financialOrganizationUnitIds()).contains(department.id());
        assertThat(scope.canReadSensitiveFinancial(
                UUID.randomUUID(), department.id(), UUID.randomUUID())).isFalse();
    }

    @Test
    void financeHasSensitiveFieldsButNoImplicitRecordAccess() {
        OrganizationNode department = organizations.createRoot("FINANCE", "Finance");
        UserSummary finance = user("finance", department.id(), "FINANCE_VIEWER");

        DataScope scope = service.resolve(finance.id());

        assertThat(scope.organizationUnitIds()).isEmpty();
        assertThat(scope.explicitOpportunityIds()).isEmpty();
        assertThat(scope.companyRead()).isFalse();
        assertThat(scope.financialOrganizationUnitIds()).contains(department.id());
        assertThat(scope.canReadSensitiveFinancial(
                UUID.randomUUID(), department.id(), UUID.randomUUID())).isFalse();
    }

    @Test
    void executiveReceivesCompanyRecordAndFinancialScopeButSystemAdministratorDoesNot() {
        OrganizationNode department = organizations.createRoot("HQ", "Headquarters");
        DataScope executive = service.resolve(
                user("executive", department.id(), "EXECUTIVE_VIEWER").id());
        UUID opportunity = UUID.randomUUID();
        assertThat(executive.companyRead()).isTrue();
        assertThat(executive.financialCompanyRead()).isTrue();
        assertThat(executive.canReadSensitiveFinancial(
                UUID.randomUUID(), UUID.randomUUID(), opportunity)).isTrue();

        DataScope administrator = service.resolve(
                user("administrator", department.id(), "SYSTEM_ADMIN").id());
        assertThat(administrator.ownRead()).isFalse();
        assertThat(administrator.organizationUnitIds()).isEmpty();
        assertThat(administrator.explicitOpportunityIds()).isEmpty();
        assertThat(administrator.companyRead()).isFalse();
        assertThat(administrator.canReadSensitiveFinancial(
                administrator.userId(), department.id(), opportunity)).isFalse();
    }

    @Test
    void roleCombinationUnionsIndependentAccessDeterministically() {
        OrganizationNode department = organizations.createRoot("SALES", "Sales");
        UserSummary combined = user("combined", department.id(), "SALES", "PRESALES_TECH");
        UUID assigned = UUID.randomUUID();
        when(explicitScopes.opportunityIds(combined.id())).thenReturn(Set.of(assigned));

        DataScope scope = service.resolve(combined.id());

        assertThat(scope.canReadOwnedBy(combined.id())).isTrue();
        assertThat(scope.organizationUnitIds()).isEmpty();
        assertThat(scope.explicitOpportunityIds()).containsExactly(assigned);
        assertThat(scope.companyRead()).isFalse();
        assertThat(scope.canReadSensitiveFinancial(
                combined.id(), department.id(), UUID.randomUUID())).isTrue();
        assertThat(scope.canReadRecord(
                UUID.randomUUID(), UUID.randomUUID(), assigned)).isTrue();
        assertThat(scope.canReadSensitiveFinancial(
                UUID.randomUUID(), UUID.randomUUID(), assigned)).isFalse();
    }

    @Test
    void rejectsMissingAndInactiveUsersWithStablePublicException() {
        UUID missing = UUID.randomUUID();
        assertThatThrownBy(() -> service.resolve(missing))
                .isInstanceOf(DataScopeUnavailableException.class)
                .hasMessage("用户不存在或已停用");

        OrganizationNode department = organizations.createRoot("SALES", "Sales");
        UserSummary user = user("inactive", department.id(), "SALES");
        users.deactivate(user.id(), user.version());

        assertThatThrownBy(() -> service.resolve(user.id()))
                .isInstanceOf(DataScopeUnavailableException.class)
                .hasMessage("用户不存在或已停用");
    }

    @Test
    void inactiveDepartmentOrAncestorCannotLeakDepartmentScope() {
        OrganizationNode root = organizations.createRoot("ROOT", "Root");
        OrganizationNode department = organizations.createChild(root.id(), "DEPT", "Department");
        OrganizationNode child = organizations.createChild(department.id(), "CHILD", "Child");
        UserSummary manager = user("manager", department.id(), "SALES_MANAGER");

        jdbcTemplate.update("update organization_unit set active = false where id = ?", root.id());

        DataScope scope = service.resolve(manager.id());
        assertThat(scope.organizationUnitIds()).isEmpty();
        assertThat(scope.canReadOrganization(department.id())).isFalse();
        assertThat(scope.canReadOrganization(child.id())).isFalse();
    }

    @Test
    void sanitizesNullDuplicateAndMutableExplicitProviderResults() {
        OrganizationNode department = organizations.createRoot("DELIVERY", "Delivery");
        UserSummary presales = user("presales", department.id(), "PRESALES_TECH");
        UUID opportunity = UUID.randomUUID();
        Set<UUID> mutable = new HashSet<>(Set.of(opportunity));
        when(explicitScopes.opportunityIds(presales.id())).thenReturn(mutable);

        DataScope copied = service.resolve(presales.id());
        mutable.clear();

        assertThat(copied.explicitOpportunityIds()).containsExactly(opportunity);
        assertThatThrownBy(() -> copied.explicitOpportunityIds().add(UUID.randomUUID()))
                .isInstanceOf(UnsupportedOperationException.class);

        when(explicitScopes.opportunityIds(presales.id())).thenReturn(null);
        assertThat(service.resolve(presales.id()).explicitOpportunityIds()).isEmpty();

        Set<UUID> duplicateAndNull = new AbstractSet<>() {
            @Override public Iterator<UUID> iterator() {
                return Arrays.asList(opportunity, opportunity, null).iterator();
            }
            @Override public int size() { return 3; }
        };
        when(explicitScopes.opportunityIds(presales.id())).thenReturn(duplicateAndNull);
        assertThat(service.resolve(presales.id()).explicitOpportunityIds()).containsExactly(opportunity);
    }

    @Test
    void nextResolveReflectsHierarchyMoveWithoutStaleDepartmentScope() {
        OrganizationNode first = organizations.createRoot("FIRST", "First");
        OrganizationNode second = organizations.createRoot("SECOND", "Second");
        OrganizationNode team = organizations.createChild(first.id(), "TEAM", "Team");
        UserSummary manager = user("manager", first.id(), "SALES_MANAGER");

        assertThat(service.resolve(manager.id()).organizationUnitIds()).contains(team.id());

        organizations.move(team.id(), second.id(), team.version());

        DataScope refreshed = service.resolve(manager.id());
        assertThat(refreshed.organizationUnitIds()).containsExactly(first.id());
        assertThat(refreshed.companyRead()).isFalse();
    }

    @Test
    void nextResolveReflectsRoleReplacementWithoutStaleDepartmentScope() {
        OrganizationNode department = organizations.createRoot("SALES", "Sales");
        organizations.createChild(department.id(), "TEAM", "Team");
        UserSummary manager = user("manager", department.id(), "SALES_MANAGER");

        assertThat(service.resolve(manager.id()).organizationUnitIds()).isNotEmpty();

        UserSummary replaced = users.assignRoles(
                manager.id(), Set.of("EXECUTIVE_VIEWER"), manager.version());

        DataScope refreshed = service.resolve(replaced.id());
        assertThat(refreshed.organizationUnitIds()).isEmpty();
        assertThat(refreshed.companyRead()).isTrue();
    }

    @Test
    void currentPermissionMappingsDriveScopeWithoutRoleNameAllowLists() {
        OrganizationNode department = organizations.createRoot("CUSTOM", "Custom");
        UserSummary user = user("custom", department.id(), "PRESALES_TECH");
        UUID assigned = UUID.randomUUID();
        when(explicitScopes.opportunityIds(user.id())).thenReturn(Set.of(assigned));

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbcTemplate.update("""
                    delete from role_permission
                    where role_id = (select id from role where code = 'PRESALES_TECH')
                      and permission_id = (select id from permission where code = 'opportunity:read:assigned')
                    """);
            jdbcTemplate.update("""
                    insert into role_permission (role_id, permission_id)
                    select role.id, permission.id
                    from role, permission
                    where role.code = 'PRESALES_TECH'
                      and permission.code in ('opportunity:read:company', 'financial:read:company')
                    on conflict do nothing
                    """);

            DataScope remapped = service.resolve(user.id());
            assertThat(remapped.explicitOpportunityIds()).isEmpty();
            assertThat(remapped.companyRead()).isTrue();
            assertThat(remapped.financialCompanyRead()).isTrue();
            status.setRollbackOnly();
        });
    }

    private UserSummary user(String username, UUID organizationId, String... roles) {
        return users.createUser(new CreateUserCommand(
                username, username, "correct horse battery", organizationId, Set.of(roles)));
    }
}

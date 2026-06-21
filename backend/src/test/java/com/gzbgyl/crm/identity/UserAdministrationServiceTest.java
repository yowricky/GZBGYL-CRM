package com.gzbgyl.crm.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gzbgyl.crm.identity.application.CreateUserCommand;
import com.gzbgyl.crm.identity.application.IdentityConflictException;
import com.gzbgyl.crm.identity.application.OrganizationNode;
import com.gzbgyl.crm.identity.application.OrganizationService;
import com.gzbgyl.crm.identity.application.UserAdministrationService;
import com.gzbgyl.crm.identity.application.UserSummary;
import com.gzbgyl.crm.identity.domain.AppUser;
import com.gzbgyl.crm.identity.persistence.AppUserRepository;
import com.gzbgyl.crm.support.PostgresIntegrationTest;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class UserAdministrationServiceTest extends PostgresIntegrationTest {

    @Autowired
    private UserAdministrationService service;

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanUsersAndOrganizations() {
        jdbcTemplate.update("delete from app_user_role");
        jdbcTemplate.update("delete from app_user");
        jdbcTemplate.update("delete from organization_unit");
    }

    @Test
    void migrationSeedsExactlyEightRolesAndCoreMappings() {
        assertThat(jdbcTemplate.queryForList("select code from role order by code", String.class))
                .containsExactly("EXECUTIVE_VIEWER", "FINANCE_VIEWER", "OPERATIONS_VIEWER",
                        "PRESALES_TECH", "PROJECT_MANAGER", "SALES", "SALES_MANAGER", "SYSTEM_ADMIN");
        assertThat(jdbcTemplate.queryForList("select code from permission", String.class))
                .contains("system:admin", "opportunity:read:own", "opportunity:read:department",
                        "opportunity:read:assigned", "opportunity:technical:update",
                        "lead:assign:department", "project:read:assigned",
                        "performance:read:authorized", "performance:read:company",
                        "contract:read:authorized", "payment:read:authorized");
        assertThat(permissionCodes("SYSTEM_ADMIN")).containsExactly("system:admin");
        assertThat(permissionCodes("SALES"))
                .contains("opportunity:read:own", "opportunity:read:assigned");
        assertThat(permissionCodes("SALES_MANAGER"))
                .contains("opportunity:read:department", "lead:assign:department");
        assertThat(permissionCodes("PRESALES_TECH"))
                .contains("opportunity:technical:update");
    }

    @Test
    void createsUserWithBcryptHashRolesAndEffectivePermissions() {
        OrganizationNode organization = organizationService.createRoot("SALES", "Sales");

        UserSummary created = service.createUser(new CreateUserCommand(
                "  Alice  ", " Alice Chen ", "correct horse battery", organization.id(),
                Set.of(" sales ", "PRESALES_TECH")));

        assertThat(created.username()).isEqualTo("Alice");
        assertThat(created.displayName()).isEqualTo("Alice Chen");
        assertThat(created.organizationUnitId()).isEqualTo(organization.id());
        assertThat(created.active()).isTrue();
        assertThat(created.roles()).containsExactlyInAnyOrder("SALES", "PRESALES_TECH");
        assertThat(created.permissions()).contains("opportunity:read:own", "opportunity:technical:update");
        AppUser persisted = userRepository.findById(created.id()).orElseThrow();
        assertThat(persisted.getNormalizedUsername()).isEqualTo("alice");
        assertThat(persisted.getPasswordHash()).startsWith("$2").contains("$12$")
                .doesNotContain("correct horse battery");
        assertThat(new BCryptPasswordEncoder(12).matches("correct horse battery", persisted.getPasswordHash())).isTrue();
        assertThat(created.toString()).doesNotContain(persisted.getPasswordHash());
    }

    @Test
    void rejectsDuplicateUsernameIgnoringCaseAndWhitespace() {
        OrganizationNode organization = organizationService.createRoot("ORG", "Organization");
        service.createUser(command("Alice", organization.id(), Set.of("SALES")));

        assertThatThrownBy(() -> service.createUser(command("  aLiCe  ", organization.id(), Set.of("SALES"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("用户名已存在");
    }

    @Test
    void translatesConcurrentDatabaseUsernameConflict() throws Exception {
        OrganizationNode organization = organizationService.createRoot("ORG", "Organization");
        CountDownLatch duplicateInserted = new CountDownLatch(1);
        CountDownLatch releaseDuplicate = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> winner = executor.submit(() -> new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> {
                        jdbcTemplate.update("""
                                insert into app_user
                                    (id, organization_unit_id, username, normalized_username,
                                     display_name, password_hash)
                                values (?, ?, 'ALICE', 'alice', 'Winner', '$2a$12$placeholder')
                                """, UUID.randomUUID(), organization.id());
                        duplicateInserted.countDown();
                        await(releaseDuplicate);
                    }));
            assertThat(duplicateInserted.await(5, TimeUnit.SECONDS)).isTrue();

            Future<Throwable> loser = executor.submit(() -> {
                try {
                    service.createUser(command(" Alice ", organization.id(), Set.of("SALES")));
                    return null;
                } catch (Throwable failure) {
                    return failure;
                }
            });
            awaitBlockedDatabaseLock();
            releaseDuplicate.countDown();

            winner.get();
            assertThat(loser.get()).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("用户名已存在");
        } finally {
            releaseDuplicate.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsMissingOrInactiveOrganizationAndUnknownRole() {
        assertThatThrownBy(() -> service.createUser(command("alice", UUID.randomUUID(), Set.of("SALES"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("组织不存在");
        OrganizationNode organization = organizationService.createRoot("ORG", "Organization");
        organizationService.deactivate(organization.id(), organization.version());
        assertThatThrownBy(() -> service.createUser(command("alice", organization.id(), Set.of("SALES"))))
                .isInstanceOf(IllegalStateException.class).hasMessage("组织已停用");
        OrganizationNode active = organizationService.createRoot("ACTIVE", "Active");
        assertThatThrownBy(() -> service.createUser(command("alice", active.id(), Set.of("UNKNOWN"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("角色不存在: UNKNOWN");
    }

    @Test
    void activatesDeactivatesResetsPasswordAndReplacesRolesWithVersionChecks() {
        OrganizationNode organization = organizationService.createRoot("ORG", "Organization");
        UserSummary created = service.createUser(command("alice", organization.id(), Set.of("SALES")));

        UserSummary deactivated = service.deactivate(created.id(), created.version());
        UserSummary activated = service.activate(created.id(), deactivated.version());
        UserSummary reset = service.resetPassword(
                created.id(), "replacement password", activated.version());
        UserSummary reassigned = service.assignRoles(
                created.id(), Set.of("PROJECT_MANAGER", "FINANCE_VIEWER"), reset.version());

        assertThat(deactivated.active()).isFalse();
        assertThat(activated.active()).isTrue();
        assertThat(reset.version()).isGreaterThan(activated.version());
        assertThat(reassigned.roles()).containsExactlyInAnyOrder("PROJECT_MANAGER", "FINANCE_VIEWER");
        assertThat(reassigned.permissions())
                .contains("project:read:assigned", "contract:read:authorized", "payment:read:authorized");
        AppUser persisted = userRepository.findById(created.id()).orElseThrow();
        assertThat(new BCryptPasswordEncoder(12).matches("replacement password", persisted.getPasswordHash())).isTrue();

        assertThatThrownBy(() -> service.deactivate(created.id(), created.version()))
                .isInstanceOf(IdentityConflictException.class)
                .hasMessage("用户已被其他用户修改，请刷新后重试");
    }

    @Test
    void validatesInputBeforePersistence() {
        OrganizationNode organization = organizationService.createRoot("ORG", "Organization");
        assertThatThrownBy(() -> service.createUser(command(" ", organization.id(), Set.of("SALES"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("用户名不能为空");
        assertThatThrownBy(() -> service.createUser(command("x".repeat(81), organization.id(), Set.of("SALES"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("用户名长度不能超过80个字符");
        assertThatThrownBy(() -> service.createUser(new CreateUserCommand(
                "alice", " ", "correct horse battery", organization.id(), Set.of("SALES"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("显示名称不能为空");
        assertThatThrownBy(() -> service.createUser(new CreateUserCommand(
                "alice", "x".repeat(121), "correct horse battery", organization.id(), Set.of("SALES"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("显示名称长度不能超过120个字符");
        assertThatThrownBy(() -> service.createUser(new CreateUserCommand(
                "alice", "Alice", "short", organization.id(), Set.of("SALES"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("密码长度不能少于12个字符");
    }

    private CreateUserCommand command(String username, UUID organizationId, Set<String> roles) {
        return new CreateUserCommand(username, "Alice", "correct horse battery", organizationId, roles);
    }

    private Set<String> permissionCodes(String role) {
        return Set.copyOf(jdbcTemplate.queryForList("""
                select p.code from permission p
                join role_permission rp on rp.permission_id = p.id
                join role r on r.id = rp.role_id
                where r.code = ? order by p.code
                """, String.class, role));
    }

    private void awaitBlockedDatabaseLock() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Integer blocked = jdbcTemplate.queryForObject(
                    "select count(*) from pg_locks where not granted", Integer.class);
            if (blocked != null && blocked > 0) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Timed out waiting for PostgreSQL uniqueness lock");
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}

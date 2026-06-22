package com.gzbgyl.crm.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gzbgyl.crm.identity.application.CreateUserCommand;
import com.gzbgyl.crm.identity.application.IdentityConflictException;
import com.gzbgyl.crm.identity.application.OrganizationNode;
import com.gzbgyl.crm.identity.application.OrganizationService;
import com.gzbgyl.crm.identity.application.UserAdministrationService;
import com.gzbgyl.crm.identity.application.UserSummary;
import com.gzbgyl.crm.identity.application.UserSessionRevoker;
import com.gzbgyl.crm.identity.domain.AppUser;
import com.gzbgyl.crm.identity.persistence.AppUserRepository;
import com.gzbgyl.crm.support.PostgresIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import javax.sql.DataSource;

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

    @Autowired
    private DataSource dataSource;

    @MockitoBean
    private UserSessionRevoker sessionRevoker;

    @BeforeEach
    void cleanUsersAndOrganizations() {
        jdbcTemplate.update("delete from app_user_role");
        jdbcTemplate.update("delete from app_user");
        jdbcTemplate.update("delete from organization_unit");
    }

    @Test
    void migrationSeedsExactRolesPermissionsAndMappings() {
        assertThat(jdbcTemplate.queryForList("select code from role order by code", String.class))
                .containsExactly("EXECUTIVE_VIEWER", "FINANCE_VIEWER", "OPERATIONS_VIEWER",
                        "PRESALES_TECH", "PROJECT_MANAGER", "SALES", "SALES_MANAGER", "SYSTEM_ADMIN");
        assertThat(jdbcTemplate.queryForList("select code from permission", String.class))
                .containsExactlyInAnyOrder(
                        "system:admin", "opportunity:read:own", "opportunity:read:department",
                        "opportunity:read:assigned", "opportunity:read:company",
                        "opportunity:technical:update", "financial:read:own",
                        "financial:read:department", "financial:read:company",
                        "lead:assign:department", "project:read:assigned",
                        "performance:read:authorized", "performance:read:company",
                        "contract:read:authorized", "payment:read:authorized");

        Map<String, Set<String>> expectedMappings = Map.of(
                "SYSTEM_ADMIN", Set.of("system:admin"),
                "SALES", Set.of("opportunity:read:own", "financial:read:own",
                        "contract:read:authorized", "payment:read:authorized"),
                "SALES_MANAGER", Set.of("opportunity:read:department", "lead:assign:department",
                        "performance:read:authorized", "contract:read:authorized",
                        "payment:read:authorized", "financial:read:department"),
                "PRESALES_TECH", Set.of("opportunity:read:assigned", "opportunity:technical:update"),
                "PROJECT_MANAGER", Set.of("opportunity:read:assigned", "project:read:assigned"),
                "OPERATIONS_VIEWER", Set.of("project:read:assigned", "financial:read:department"),
                "FINANCE_VIEWER", Set.of("performance:read:authorized", "contract:read:authorized",
                        "payment:read:authorized", "financial:read:department"),
                "EXECUTIVE_VIEWER", Set.of("performance:read:company", "contract:read:authorized",
                        "payment:read:authorized", "opportunity:read:company",
                        "financial:read:company"));
        expectedMappings.forEach((role, permissions) ->
                assertThat(permissionCodes(role)).as(role).isEqualTo(permissions));
    }

    @Test
    void migrationRerunRestoresSystemOwnedSeedMetadata() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbcTemplate.update("update permission set name = 'Changed', description = 'Changed' "
                    + "where code = 'system:admin'");
            jdbcTemplate.update("update role set name = 'Changed', system_role = false "
                    + "where code = 'SYSTEM_ADMIN'");

            ScriptUtils.executeSqlScript(DataSourceUtils.getConnection(dataSource),
                    new ClassPathResource("db/migration/V2__seed_identity_roles_permissions.sql"));

            assertThat(jdbcTemplate.queryForMap(
                    "select name, description from permission where code = 'system:admin'"))
                    .containsEntry("name", "System administration")
                    .containsEntry("description", "Full system administration");
            assertThat(jdbcTemplate.queryForMap(
                    "select name, system_role from role where code = 'SYSTEM_ADMIN'"))
                    .containsEntry("name", "System Administrator")
                    .containsEntry("system_role", true);
            status.setRollbackOnly();
        });
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
    void detailedRepositoryQueryFetchesRolesAndPermissions() {
        OrganizationNode organization = organizationService.createRoot("ORG", "Organization");
        UserSummary created = service.createUser(command(
                "alice", organization.id(), Set.of("SALES", "PRESALES_TECH")));

        AppUser detached = new TransactionTemplate(transactionManager).execute(
                status -> userRepository.findDetailedById(created.id()).orElseThrow());

        assertThat(detached.getRoles()).extracting(role -> role.getCode())
                .containsExactlyInAnyOrder("SALES", "PRESALES_TECH");
        assertThat(detached.getRoles()).flatExtracting(role -> role.getPermissions())
                .extracting(permission -> permission.getCode())
                .contains("opportunity:read:own", "opportunity:technical:update");
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
    void serializesUserCreationWithOrganizationDeactivation() throws Exception {
        OrganizationNode organization = organizationService.createRoot("ORG", "Organization");
        CountDownLatch deactivationFlushed = new CountDownLatch(1);
        CountDownLatch releaseDeactivation = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<OrganizationNode> deactivation = executor.submit(() ->
                    new TransactionTemplate(transactionManager).execute(status -> {
                        OrganizationNode result = organizationService.deactivate(
                                organization.id(), organization.version());
                        deactivationFlushed.countDown();
                        await(releaseDeactivation);
                        return result;
                    }));
            assertThat(deactivationFlushed.await(5, TimeUnit.SECONDS)).isTrue();

            Future<Throwable> creation = executor.submit(() -> {
                try {
                    service.createUser(command("alice", organization.id(), Set.of("SALES")));
                    return null;
                } catch (Throwable failure) {
                    return failure;
                }
            });
            awaitCreationBlocked(creation);
            releaseDeactivation.countDown();

            assertThat(deactivation.get().active()).isFalse();
            assertThat(creation.get()).isInstanceOf(IllegalStateException.class)
                    .hasMessage("组织已停用");
            assertThat(userRepository.count()).isZero();
        } finally {
            releaseDeactivation.countDown();
            executor.shutdownNow();
        }
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
        org.mockito.Mockito.verify(sessionRevoker, org.mockito.Mockito.times(3))
                .revokeSessions(created.id());

        assertThatThrownBy(() -> service.deactivate(created.id(), created.version()))
                .isInstanceOf(IdentityConflictException.class)
                .hasMessage("用户已被其他用户修改，请刷新后重试");
    }

    @Test
    void concurrentPasswordResetsTranslateFlushTimeConflict() throws Exception {
        OrganizationNode organization = organizationService.createRoot("ORG", "Organization");
        UserSummary created = service.createUser(command("alice", organization.id(), Set.of("SALES")));
        CountDownLatch rowLocked = new CountDownLatch(1);
        CountDownLatch releaseRowLock = new CountDownLatch(1);
        CountDownLatch startResets = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            Future<?> lockHolder = executor.submit(() -> new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> {
                        jdbcTemplate.queryForObject(
                                "select id from app_user where id = ? for update",
                                UUID.class, created.id());
                        rowLocked.countDown();
                        await(releaseRowLock);
                    }));
            assertThat(rowLocked.await(5, TimeUnit.SECONDS)).isTrue();

            Future<Throwable> first = executor.submit(() -> attemptPasswordReset(
                    startResets, created, "first replacement"));
            Future<Throwable> second = executor.submit(() -> attemptPasswordReset(
                    startResets, created, "second replacement"));
            startResets.countDown();
            awaitBlockedDatabaseLocks(2);
            releaseRowLock.countDown();

            lockHolder.get();
            List<Throwable> outcomes = Arrays.asList(first.get(), second.get());
            assertThat(outcomes).filteredOn(java.util.Objects::isNull).hasSize(1);
            assertThat(outcomes).filteredOn(java.util.Objects::nonNull)
                    .singleElement()
                    .satisfies(failure -> assertThat(failure)
                            .isInstanceOf(IdentityConflictException.class)
                            .hasMessage("用户已被其他用户修改，请刷新后重试"));
            assertThat(userRepository.findById(created.id()).orElseThrow().getVersion())
                    .isEqualTo(created.version() + 1);
        } finally {
            releaseRowLock.countDown();
            executor.shutdownNow();
        }
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

    @Test
    void validatesBcryptUtf8ByteBoundaryForCreateAndReset() {
        OrganizationNode organization = organizationService.createRoot("ORG", "Organization");
        String asciiBoundary = "a".repeat(72);
        String asciiTooLong = "a".repeat(73);
        String multibyteTooLong = "密".repeat(25);
        assertThat(asciiBoundary.getBytes(StandardCharsets.UTF_8)).hasSize(72);
        assertThat(multibyteTooLong).hasSize(25);
        assertThat(multibyteTooLong.getBytes(StandardCharsets.UTF_8)).hasSize(75);

        UserSummary created = service.createUser(new CreateUserCommand(
                "boundary", "Boundary", asciiBoundary, organization.id(), Set.of("SALES")));
        assertThat(new BCryptPasswordEncoder(12).matches(asciiBoundary,
                userRepository.findById(created.id()).orElseThrow().getPasswordHash())).isTrue();

        assertThatThrownBy(() -> service.createUser(new CreateUserCommand(
                "ascii-long", "ASCII Long", asciiTooLong, organization.id(), Set.of("SALES"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("密码UTF-8编码不能超过72字节");
        assertThatThrownBy(() -> service.createUser(new CreateUserCommand(
                "multibyte-long", "Multibyte Long", multibyteTooLong,
                organization.id(), Set.of("SALES"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("密码UTF-8编码不能超过72字节");
        assertThatThrownBy(() -> service.resetPassword(created.id(), asciiTooLong, created.version()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("密码UTF-8编码不能超过72字节");
        assertThatThrownBy(() -> service.resetPassword(created.id(), multibyteTooLong, created.version()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("密码UTF-8编码不能超过72字节");
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

    private void awaitCreationBlocked(Future<?> creation) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (creation.isDone()) {
                throw new AssertionError(
                        "User creation completed while organization deactivation was uncommitted");
            }
            Integer blocked = jdbcTemplate.queryForObject(
                    "select count(*) from pg_locks where not granted", Integer.class);
            if (blocked != null && blocked > 0) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Timed out waiting for user creation to block");
    }

    private void awaitBlockedDatabaseLocks(int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Integer blocked = jdbcTemplate.queryForObject(
                    "select count(*) from pg_locks where not granted", Integer.class);
            if (blocked != null && blocked >= expected) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Timed out waiting for blocked PostgreSQL transactions");
    }

    private Throwable attemptPasswordReset(
            CountDownLatch start, UserSummary user, String password) throws InterruptedException {
        start.await();
        try {
            service.resetPassword(user.id(), password, user.version());
            return null;
        } catch (Throwable failure) {
            return failure;
        }
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

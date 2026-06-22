package com.gzbgyl.crm.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gzbgyl.crm.identity.application.OrganizationNode;
import com.gzbgyl.crm.identity.application.OrganizationConflictException;
import com.gzbgyl.crm.identity.application.OrganizationService;
import com.gzbgyl.crm.identity.domain.OrganizationUnit;
import com.gzbgyl.crm.identity.persistence.OrganizationUnitRepository;
import com.gzbgyl.crm.support.PostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

class OrganizationServiceTest extends PostgresIntegrationTest {

    @Autowired private OrganizationService service;
    @Autowired private OrganizationUnitRepository repository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;
    @Autowired private TransactionTemplate transactions;

    @BeforeEach
    void clearOrganizations() {
        jdbcTemplate.update("delete from organization_unit");
    }

    @Test
    void constructsRootAndChildPathsFromAssignedIds() {
        OrganizationNode root = service.createRoot(" sales ", "Sales");
        OrganizationNode child = service.createChild(root.id(), "east", "East");

        assertThat(root.code()).isEqualTo("SALES");
        assertThat(root.path()).isEqualTo("/" + root.id() + "/");
        assertThat(child.parentId()).isEqualTo(root.id());
        assertThat(child.path()).isEqualTo(root.path() + child.id() + "/");
        OrganizationUnit persisted = repository.findById(root.id()).orElseThrow();
        assertThat(persisted.getCreatedAt()).isNotNull();
        assertThat(persisted.getUpdatedAt()).isNotNull();
    }

    @Test
    void rejectsDuplicateCodeIgnoringCaseAndWhitespace() {
        service.createRoot("Finance", "Finance");

        assertThatThrownBy(() -> service.createRoot(" finance ", "Other"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("组织编码已存在");
    }

    @Test
    void rejectsMissingAndInactiveParents() {
        assertThatThrownBy(() -> service.createChild(UUID.randomUUID(), "A", "A"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("上级组织不存在");

        OrganizationNode root = service.createRoot("ROOT", "Root");
        service.deactivate(root.id(), root.version());

        assertThatThrownBy(() -> service.createChild(root.id(), "B", "B"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("上级组织已停用");
    }

    @Test
    void renamesOrganizationAndIncrementsVersion() {
        OrganizationNode root = service.createRoot("ROOT", "Before");
        long initialVersion = repository.findById(root.id()).orElseThrow().getVersion();

        OrganizationNode renamed = service.rename(root.id(), "After", root.version());
        entityManager.clear();

        OrganizationUnit persisted = repository.findById(root.id()).orElseThrow();
        assertThat(renamed.name()).isEqualTo("After");
        assertThat(persisted.getName()).isEqualTo("After");
        assertThat(persisted.getVersion()).isGreaterThan(initialVersion);
    }

    @Test
    void findsDescendantsWithExplicitRootInclusion() {
        OrganizationNode root = service.createRoot("ROOT", "Root");
        OrganizationNode child = service.createChild(root.id(), "CHILD", "Child");
        OrganizationNode grandchild = service.createChild(child.id(), "GRAND", "Grand");
        service.createRoot("OTHER", "Other");

        assertThat(service.findDescendants(root.id()))
                .extracting(OrganizationNode::id)
                .containsExactly(child.id(), grandchild.id());
        assertThat(service.findSelfAndDescendants(root.id()))
                .extracting(OrganizationNode::id)
                .containsExactly(root.id(), child.id(), grandchild.id());
    }

    @Test
    void movingSubtreeUpdatesEveryDescendantPath() {
        OrganizationNode firstRoot = service.createRoot("ONE", "One");
        OrganizationNode secondRoot = service.createRoot("TWO", "Two");
        OrganizationNode child = service.createChild(firstRoot.id(), "CHILD", "Child");
        OrganizationNode grandchild = service.createChild(child.id(), "GRAND", "Grand");

        long grandchildVersion = repository.findById(grandchild.id()).orElseThrow().getVersion();
        OrganizationNode moved = service.move(child.id(), secondRoot.id(), child.version());

        assertThat(moved.parentId()).isEqualTo(secondRoot.id());
        assertThat(moved.path()).isEqualTo(secondRoot.path() + child.id() + "/");
        assertThat(repository.findById(grandchild.id()).orElseThrow().getPath())
                .isEqualTo(moved.path() + grandchild.id() + "/");
        assertThat(repository.findById(grandchild.id()).orElseThrow().getVersion())
                .isGreaterThan(grandchildVersion);
    }

    @Test
    void rejectsMovingUnderSelfOrDescendant() {
        OrganizationNode root = service.createRoot("ROOT", "Root");
        OrganizationNode child = service.createChild(root.id(), "CHILD", "Child");

        assertThatThrownBy(() -> service.move(root.id(), root.id(), root.version()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("组织不能移动到其下级节点");
        assertThatThrownBy(() -> service.move(root.id(), child.id(), root.version()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("组织不能移动到其下级节点");
    }

    @Test
    void deactivatesOrganization() {
        OrganizationNode root = service.createRoot("ROOT", "Root");

        OrganizationNode deactivated = service.deactivate(root.id(), root.version());

        assertThat(deactivated.active()).isFalse();
        assertThat(repository.findById(root.id()).orElseThrow().isActive()).isFalse();
    }

    @Test
    void versionMappingRejectsStaleUpdatesAcrossTransactions() {
        OrganizationNode root = service.createRoot("ROOT", "Root");
        OrganizationUnit stale = transactions.execute(status -> repository.findById(root.id()).orElseThrow());
        service.rename(root.id(), "Fresh", root.version());

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            stale.rename("Stale");
            entityManager.merge(stale);
            entityManager.flush();
        })).hasRootCauseInstanceOf(org.hibernate.StaleObjectStateException.class);
    }

    @Test
    void staleClientVersionCannotOverwriteRenameOrDeactivate() {
        OrganizationNode root = service.createRoot("ROOT", "Root");
        OrganizationNode renamed = service.rename(root.id(), "Fresh", root.version());

        assertThatThrownBy(() -> service.rename(root.id(), "Stale", root.version()))
                .isInstanceOf(OrganizationConflictException.class)
                .hasMessage("组织已被其他用户修改，请刷新后重试");
        assertThatThrownBy(() -> service.deactivate(root.id(), root.version()))
                .isInstanceOf(OrganizationConflictException.class)
                .hasMessage("组织已被其他用户修改，请刷新后重试");
        assertThat(repository.findById(root.id()).orElseThrow().getName()).isEqualTo("Fresh");
        assertThat(renamed.version()).isGreaterThan(root.version());
    }

    @RepeatedTest(5)
    void concurrentRenamesReturnStableConflictForLoser() throws Exception {
        OrganizationNode root = service.createRoot("ROOT", "Root");
        CountDownLatch rowLocked = new CountDownLatch(1);
        CountDownLatch releaseRowLock = new CountDownLatch(1);
        CountDownLatch startRenames = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            Future<?> lockHolder = executor.submit(() -> transactions.executeWithoutResult(status -> {
                jdbcTemplate.queryForObject(
                        "select id from organization_unit where id = ? for update",
                        UUID.class,
                        root.id());
                rowLocked.countDown();
                await(releaseRowLock);
            }));
            assertThat(rowLocked.await(5, TimeUnit.SECONDS)).isTrue();

            Future<RenameOutcome> first = executor.submit(
                    () -> attemptRename(startRenames, root, "First"));
            Future<RenameOutcome> second = executor.submit(
                    () -> attemptRename(startRenames, root, "Second"));
            startRenames.countDown();
            awaitBlockedDatabaseLocks(2);
            releaseRowLock.countDown();

            lockHolder.get();
            List<RenameOutcome> outcomes = List.of(first.get(), second.get());
            assertThat(outcomes).filteredOn(RenameOutcome::succeeded).hasSize(1);
            assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded())
                    .singleElement()
                    .satisfies(outcome -> assertThat(outcome.failure())
                            .isInstanceOf(OrganizationConflictException.class)
                            .hasMessage("组织已被其他用户修改，请刷新后重试"));

            OrganizationUnit persisted = repository.findById(root.id()).orElseThrow();
            String successfulName = outcomes.stream()
                    .filter(RenameOutcome::succeeded)
                    .findFirst().orElseThrow().name();
            assertThat(persisted.getName()).isEqualTo(successfulName);
            assertThat(persisted.getVersion()).isEqualTo(root.version() + 1);
        } finally {
            releaseRowLock.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void staleClientVersionCannotMoveOrganization() {
        OrganizationNode firstRoot = service.createRoot("FIRST", "First");
        OrganizationNode secondRoot = service.createRoot("SECOND", "Second");
        OrganizationNode child = service.createChild(firstRoot.id(), "CHILD", "Child");
        service.rename(child.id(), "Fresh", child.version());

        assertThatThrownBy(() -> service.move(child.id(), secondRoot.id(), child.version()))
                .isInstanceOf(OrganizationConflictException.class)
                .hasMessage("组织已被其他用户修改，请刷新后重试");
        assertThat(repository.findById(child.id()).orElseThrow().getParentId())
                .isEqualTo(firstRoot.id());
    }

    @Test
    void validatesAndTrimsOrganizationInputAtSchemaBoundaries() {
        assertThatThrownBy(() -> service.createRoot(null, "Name"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("组织编码不能为空");
        assertThatThrownBy(() -> service.createRoot("   ", "Name"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("组织编码不能为空");
        assertThatThrownBy(() -> service.createRoot("A".repeat(61), "Name"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("组织编码长度不能超过60个字符");
        assertThatThrownBy(() -> service.createRoot("CODE", "  "))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("组织名称不能为空");
        assertThatThrownBy(() -> service.createRoot("CODE", "A".repeat(121)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("组织名称长度不能超过120个字符");

        OrganizationNode boundary = service.createRoot("a".repeat(60), " Name ");
        assertThat(boundary.code()).hasSize(60).isUpperCase();
        assertThat(boundary.name()).isEqualTo("Name");
        assertThatThrownBy(() -> service.rename(boundary.id(), null, boundary.version()))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("组织名称不能为空");
    }

    @Test
    void rejectsDeactivationWhenAnActiveDescendantExists() {
        OrganizationNode root = service.createRoot("ROOT", "Root");
        service.createChild(root.id(), "CHILD", "Child");

        assertThatThrownBy(() -> service.deactivate(root.id(), root.version()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("组织存在启用的下级，不能停用");
    }

    @Test
    void rejectsChildCreationBelowInactiveAncestor() {
        OrganizationNode root = service.createRoot("ROOT", "Root");
        OrganizationNode child = service.createChild(root.id(), "CHILD", "Child");
        jdbcTemplate.update("update organization_unit set active = false where id = ?", root.id());

        assertThatThrownBy(() -> service.createChild(child.id(), "GRAND", "Grand"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("上级组织已停用");
    }

    @Test
    void entityIdentityUsesUuidAcrossPersistenceContexts() {
        OrganizationNode root = service.createRoot("ROOT", "Root");
        OrganizationUnit first = transactions.execute(
                status -> repository.findById(root.id()).orElseThrow());
        OrganizationUnit second = transactions.execute(
                status -> repository.findById(root.id()).orElseThrow());
        OrganizationUnit other = transactions.execute(status -> repository.findById(
                service.createRoot("OTHER", "Other").id()).orElseThrow());

        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second).isNotEqualTo(other);
    }

    @Test
    void rejectsCreatingPathBeyondApplicationLimit() {
        OrganizationNode parent = service.createRoot("LEVEL0", "Level 0");
        OrganizationNode almostDeepest = null;
        for (int level = 1; level <= 26; level++) {
            parent = service.createChild(parent.id(), "LEVEL" + level, "Level " + level);
            if (level == 25) {
                almostDeepest = parent;
            }
        }
        OrganizationNode deepestAllowed = parent;

        assertThat(deepestAllowed.path()).hasSize(1000);
        assertThatThrownBy(() -> service.createChild(
                deepestAllowed.id(), "TOO_DEEP", "Too deep"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("组织层级过深");
        OrganizationNode otherRoot = service.createRoot("OTHER", "Other");
        assertThatThrownBy(() -> service.move(
                otherRoot.id(), deepestAllowed.id(), otherRoot.version()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("组织层级过深");
        OrganizationNode subtreeRoot = service.createRoot("SUBTREE", "Subtree");
        service.createChild(subtreeRoot.id(), "SUBCHILD", "Subchild");
        OrganizationNode deepParent = almostDeepest;
        assertThatThrownBy(() -> service.move(
                subtreeRoot.id(), deepParent.id(), subtreeRoot.version()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("组织层级过深");
    }

    @RepeatedTest(5)
    void reciprocalConcurrentMovesCannotCreateCycle() throws Exception {
        OrganizationNode first = service.createRoot("FIRST", "First");
        OrganizationNode second = service.createRoot("SECOND", "Second");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> firstMove = executor.submit(() -> attemptMove(start, first, second));
            Future<Boolean> secondMove = executor.submit(() -> attemptMove(start, second, first));
            start.countDown();

            assertThat(List.of(firstMove.get(), secondMove.get()))
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            executor.shutdownNow();
        }

        OrganizationUnit persistedFirst = repository.findById(first.id()).orElseThrow();
        OrganizationUnit persistedSecond = repository.findById(second.id()).orElseThrow();
        assertThat(persistedFirst.getParentId() == null || persistedSecond.getParentId() == null).isTrue();
        assertThat(persistedFirst.getPath().startsWith(persistedSecond.getPath())
                && persistedSecond.getPath().startsWith(persistedFirst.getPath())).isFalse();
    }

    @RepeatedTest(3)
    void concurrentChildCreationAndMoveKeepPathsConsistent() throws Exception {
        OrganizationNode firstRoot = service.createRoot("FIRST", "First");
        OrganizationNode secondRoot = service.createRoot("SECOND", "Second");
        OrganizationNode child = service.createChild(firstRoot.id(), "CHILD", "Child");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<OrganizationNode> created = executor.submit(() -> {
                start.await();
                return service.createChild(child.id(), "GRAND", "Grand");
            });
            Future<OrganizationNode> moved = executor.submit(() -> {
                start.await();
                return service.move(child.id(), secondRoot.id(), child.version());
            });
            start.countDown();

            OrganizationNode grandchild = created.get();
            OrganizationNode movedChild = moved.get();
            OrganizationUnit persistedGrandchild = repository.findById(grandchild.id()).orElseThrow();
            assertThat(persistedGrandchild.getPath()).startsWith(movedChild.path());
            assertThat(persistedGrandchild.getParentId()).isEqualTo(movedChild.id());
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean attemptMove(CountDownLatch start, OrganizationNode unit, OrganizationNode parent)
            throws InterruptedException {
        start.await();
        try {
            service.move(unit.id(), parent.id(), unit.version());
            return true;
        } catch (IllegalArgumentException | OrganizationConflictException exception) {
            return false;
        }
    }

    private RenameOutcome attemptRename(CountDownLatch start, OrganizationNode unit, String name)
            throws InterruptedException {
        start.await();
        try {
            return new RenameOutcome(service.rename(unit.id(), name, unit.version()).name(), null);
        } catch (RuntimeException exception) {
            return new RenameOutcome(null, exception);
        }
    }

    private void awaitBlockedDatabaseLocks(int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Integer blocked = jdbcTemplate.queryForObject(
                    "select count(*) from pg_locks where not granted", Integer.class);
            if (blocked != null && blocked >= expected) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Timed out waiting for blocked PostgreSQL transactions");
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private record RenameOutcome(String name, RuntimeException failure) {
        boolean succeeded() {
            return failure == null;
        }
    }
}

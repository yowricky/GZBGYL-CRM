package com.gzbgyl.crm.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gzbgyl.crm.identity.application.OrganizationNode;
import com.gzbgyl.crm.identity.application.OrganizationService;
import com.gzbgyl.crm.identity.domain.OrganizationUnit;
import com.gzbgyl.crm.identity.persistence.OrganizationUnitRepository;
import com.gzbgyl.crm.support.PostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
        jdbcTemplate.execute("TRUNCATE organization_unit CASCADE");
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
        service.deactivate(root.id());

        assertThatThrownBy(() -> service.createChild(root.id(), "B", "B"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("上级组织已停用");
    }

    @Test
    void renamesOrganizationAndIncrementsVersion() {
        OrganizationNode root = service.createRoot("ROOT", "Before");
        long initialVersion = repository.findById(root.id()).orElseThrow().getVersion();

        OrganizationNode renamed = service.rename(root.id(), "After");
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

        OrganizationNode moved = service.move(child.id(), secondRoot.id());

        assertThat(moved.parentId()).isEqualTo(secondRoot.id());
        assertThat(moved.path()).isEqualTo(secondRoot.path() + child.id() + "/");
        assertThat(repository.findById(grandchild.id()).orElseThrow().getPath())
                .isEqualTo(moved.path() + grandchild.id() + "/");
    }

    @Test
    void rejectsMovingUnderSelfOrDescendant() {
        OrganizationNode root = service.createRoot("ROOT", "Root");
        OrganizationNode child = service.createChild(root.id(), "CHILD", "Child");

        assertThatThrownBy(() -> service.move(root.id(), root.id()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("组织不能移动到其下级节点");
        assertThatThrownBy(() -> service.move(root.id(), child.id()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("组织不能移动到其下级节点");
    }

    @Test
    void deactivatesOrganization() {
        OrganizationNode root = service.createRoot("ROOT", "Root");

        OrganizationNode deactivated = service.deactivate(root.id());

        assertThat(deactivated.active()).isFalse();
        assertThat(repository.findById(root.id()).orElseThrow().isActive()).isFalse();
    }

    @Test
    void versionMappingRejectsStaleUpdatesAcrossTransactions() {
        OrganizationNode root = service.createRoot("ROOT", "Root");
        OrganizationUnit stale = transactions.execute(status -> repository.findById(root.id()).orElseThrow());
        service.rename(root.id(), "Fresh");

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            stale.rename("Stale");
            entityManager.merge(stale);
            entityManager.flush();
        })).hasRootCauseInstanceOf(org.hibernate.StaleObjectStateException.class);
    }
}

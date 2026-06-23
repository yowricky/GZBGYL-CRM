package com.gzbgyl.crm.identity.application;

import com.gzbgyl.crm.audit.application.AuditActorProvider;
import com.gzbgyl.crm.audit.application.AuditCommand;
import com.gzbgyl.crm.audit.application.AuditService;
import com.gzbgyl.crm.identity.domain.OrganizationUnit;
import com.gzbgyl.crm.identity.persistence.OrganizationUnitRepository;
import com.gzbgyl.crm.shared.api.InvalidRequestException;
import com.gzbgyl.crm.shared.api.InvalidStateException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationService {

    private static final int MAX_CODE_LENGTH = 60;
    private static final int MAX_NAME_LENGTH = 120;
    private static final int MAX_PATH_LENGTH = 1000;
    private static final String ORGANIZATION_NOT_FOUND = "组织不存在";
    private static final String PARENT_NOT_FOUND = "上级组织不存在";
    private static final String PARENT_INACTIVE = "上级组织已停用";
    private static final String DUPLICATE_CODE = "组织编码已存在";
    private static final String INVALID_MOVE = "组织不能移动到其下级节点";
    private static final String CONFLICT = "组织已被其他用户修改，请刷新后重试";
    private static final String CODE_REQUIRED = "组织编码不能为空";
    private static final String CODE_TOO_LONG = "组织编码长度不能超过60个字符";
    private static final String NAME_REQUIRED = "组织名称不能为空";
    private static final String NAME_TOO_LONG = "组织名称长度不能超过120个字符";
    private static final String PATH_TOO_LONG = "组织层级过深";
    private static final String ACTIVE_DESCENDANT = "组织存在启用的下级，不能停用";

    private final OrganizationUnitRepository repository;
    private final AuditService audit;
    private final AuditActorProvider actor;

    public OrganizationService(OrganizationUnitRepository repository, AuditService audit,
            AuditActorProvider actor) {
        this.repository = repository;
        this.audit = audit;
        this.actor = actor;
    }

    @Transactional
    public OrganizationNode createRoot(String code, String name) {
        repository.acquireHierarchyMutationLock();
        OrganizationUnit root = new OrganizationUnit(null, uniqueCode(code), validName(name), "/");
        OrganizationNode created = saveNew(root);
        record("ORGANIZATION_CREATED", created.id(), null, organizationState(created));
        return created;
    }

    @Transactional
    public OrganizationNode createChild(UUID parentId, String code, String name) {
        repository.acquireHierarchyMutationLock();
        OrganizationUnit parent = activeParent(parentId);
        OrganizationUnit child = new OrganizationUnit(
                parentId, uniqueCode(code), validName(name), parent.getPath());
        validatePath(child.getPath());
        OrganizationNode created = saveNew(child);
        record("ORGANIZATION_CREATED", created.id(), null, organizationState(created));
        return created;
    }

    @Transactional
    public OrganizationNode rename(UUID id, String name, long expectedVersion) {
        OrganizationUnit unit = existing(id);
        requireVersion(unit, expectedVersion);
        Map<String, ?> before = Map.of("name", unit.getName());
        unit.rename(validName(name));
        flushMutation();
        OrganizationNode result = toNode(unit);
        record("ORGANIZATION_RENAMED", id, before, Map.of("name", result.name()));
        return result;
    }

    @Transactional
    public OrganizationNode move(UUID id, UUID newParentId, long expectedVersion) {
        repository.acquireHierarchyMutationLock();
        OrganizationUnit unit = existing(id);
        requireVersion(unit, expectedVersion);
        if (id.equals(newParentId)) {
            throw new InvalidRequestException(INVALID_MOVE);
        }
        OrganizationUnit newParent = activeParent(newParentId);
        if (newParent.getPath().startsWith(unit.getPath())) {
            throw new InvalidRequestException(INVALID_MOVE);
        }

        String oldPrefix = unit.getPath();
        Map<String, ?> before = mapNullable("parentId", unit.getParentId(), "path", oldPrefix);
        String newPrefix = newParent.getPath() + unit.getId() + "/";
        validatePath(newPrefix);
        int movedMaximumLength = repository.maximumSubtreePathLength(oldPrefix)
                + newPrefix.length() - oldPrefix.length();
        if (movedMaximumLength > MAX_PATH_LENGTH) {
            throw new InvalidRequestException(PATH_TOO_LONG);
        }
        unit.moveTo(newParentId, newPrefix);
        flushMutation();
        repository.replaceDescendantPathPrefix(unit.getId(), oldPrefix, newPrefix);
        OrganizationNode result = toNode(unit);
        record("ORGANIZATION_MOVED", id, before,
                mapNullable("parentId", result.parentId(), "path", result.path()));
        return result;
    }

    @Transactional
    public OrganizationNode deactivate(UUID id, long expectedVersion) {
        repository.acquireHierarchyMutationLock();
        OrganizationUnit unit = existing(id);
        requireVersion(unit, expectedVersion);
        if (repository.hasActiveDescendant(unit.getId(), unit.getPath())) {
            throw new InvalidStateException(ACTIVE_DESCENDANT);
        }
        Map<String, ?> before = Map.of("active", unit.isActive());
        unit.deactivate();
        flushMutation();
        OrganizationNode result = toNode(unit);
        record("ORGANIZATION_DEACTIVATED", id, before, Map.of("active", false));
        return result;
    }

    @Transactional(readOnly = true)
    public List<OrganizationNode> findAll() {
        return repository.findAll(Sort.by("path")).stream()
                .map(OrganizationService::toNode)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OrganizationNode> findDescendants(UUID id) {
        OrganizationUnit root = existing(id);
        return repository.findByPathStartingWithOrderByPathAsc(root.getPath()).stream()
                .filter(unit -> !unit.getId().equals(id))
                .map(OrganizationService::toNode)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OrganizationNode> findSelfAndDescendants(UUID id) {
        OrganizationUnit root = existing(id);
        return repository.findByPathStartingWithOrderByPathAsc(root.getPath()).stream()
                .map(OrganizationService::toNode)
                .toList();
    }

    private OrganizationUnit activeParent(UUID parentId) {
        OrganizationUnit parent = repository.findById(parentId)
                .orElseThrow(() -> new InvalidRequestException(PARENT_NOT_FOUND));
        if (!parent.isActive() || repository.hasInactiveAncestorOrSelf(parent.getPath())) {
            throw new InvalidStateException(PARENT_INACTIVE);
        }
        return parent;
    }

    private OrganizationUnit existing(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new InvalidRequestException(ORGANIZATION_NOT_FOUND));
    }

    private String uniqueCode(String code) {
        if (code == null || code.isBlank()) {
            throw new InvalidRequestException(CODE_REQUIRED);
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > MAX_CODE_LENGTH) {
            throw new InvalidRequestException(CODE_TOO_LONG);
        }
        if (repository.existsByCode(normalized)) {
            throw new InvalidRequestException(DUPLICATE_CODE);
        }
        return normalized;
    }

    private String validName(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidRequestException(NAME_REQUIRED);
        }
        String trimmed = name.trim();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new InvalidRequestException(NAME_TOO_LONG);
        }
        return trimmed;
    }

    private void validatePath(String path) {
        if (path.length() > MAX_PATH_LENGTH) {
            throw new InvalidRequestException(PATH_TOO_LONG);
        }
    }

    private void requireVersion(OrganizationUnit unit, long expectedVersion) {
        if (unit.getVersion() != expectedVersion) {
            throw new OrganizationConflictException(CONFLICT);
        }
    }

    private void flushMutation() {
        try {
            repository.flush();
        } catch (OptimisticLockingFailureException exception) {
            throw new OrganizationConflictException(CONFLICT);
        }
    }

    private OrganizationNode saveNew(OrganizationUnit unit) {
        try {
            return toNode(repository.saveAndFlush(unit));
        } catch (DataIntegrityViolationException exception) {
            Throwable cause = exception;
            while (cause != null) {
                if (cause instanceof ConstraintViolationException constraintViolation
                        && "uk_organization_unit_code".equals(constraintViolation.getConstraintName())) {
                    throw new InvalidRequestException(DUPLICATE_CODE, exception);
                }
                cause = cause.getCause();
            }
            throw exception;
        }
    }

    private static OrganizationNode toNode(OrganizationUnit unit) {
        return new OrganizationNode(unit.getId(), unit.getParentId(), unit.getCode(), unit.getName(),
                unit.getPath(), unit.isActive(), unit.getVersion());
    }

    private void record(String event, UUID id, Map<String, ?> before, Map<String, ?> after) {
        audit.record(new AuditCommand(actor.actorId(), event, "ORGANIZATION", id,
                before, after, actor.ipAddress(), null));
    }

    private static Map<String, ?> organizationState(OrganizationNode node) {
        return mapNullable("code", node.code(), "name", node.name(), "parentId", node.parentId(),
                "active", node.active());
    }

    private static Map<String, ?> mapNullable(Object... entries) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) result.put((String) entries[i], entries[i + 1]);
        return result;
    }
}

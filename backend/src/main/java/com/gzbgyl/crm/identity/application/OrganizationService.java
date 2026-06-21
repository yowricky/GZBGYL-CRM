package com.gzbgyl.crm.identity.application;

import com.gzbgyl.crm.identity.domain.OrganizationUnit;
import com.gzbgyl.crm.identity.persistence.OrganizationUnitRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
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

    public OrganizationService(OrganizationUnitRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public OrganizationNode createRoot(String code, String name) {
        repository.acquireHierarchyMutationLock();
        OrganizationUnit root = new OrganizationUnit(null, uniqueCode(code), validName(name), "/");
        return saveNew(root);
    }

    @Transactional
    public OrganizationNode createChild(UUID parentId, String code, String name) {
        repository.acquireHierarchyMutationLock();
        OrganizationUnit parent = activeParent(parentId);
        OrganizationUnit child = new OrganizationUnit(
                parentId, uniqueCode(code), validName(name), parent.getPath());
        validatePath(child.getPath());
        return saveNew(child);
    }

    @Transactional
    public OrganizationNode rename(UUID id, String name, long expectedVersion) {
        OrganizationUnit unit = existing(id);
        requireVersion(unit, expectedVersion);
        unit.rename(validName(name));
        repository.flush();
        return toNode(unit);
    }

    @Transactional
    public OrganizationNode move(UUID id, UUID newParentId, long expectedVersion) {
        repository.acquireHierarchyMutationLock();
        OrganizationUnit unit = existing(id);
        requireVersion(unit, expectedVersion);
        if (id.equals(newParentId)) {
            throw new IllegalArgumentException(INVALID_MOVE);
        }
        OrganizationUnit newParent = activeParent(newParentId);
        if (newParent.getPath().startsWith(unit.getPath())) {
            throw new IllegalArgumentException(INVALID_MOVE);
        }

        String oldPrefix = unit.getPath();
        String newPrefix = newParent.getPath() + unit.getId() + "/";
        validatePath(newPrefix);
        int movedMaximumLength = repository.maximumSubtreePathLength(oldPrefix)
                + newPrefix.length() - oldPrefix.length();
        if (movedMaximumLength > MAX_PATH_LENGTH) {
            throw new IllegalArgumentException(PATH_TOO_LONG);
        }
        unit.moveTo(newParentId, newPrefix);
        repository.flush();
        repository.replaceDescendantPathPrefix(unit.getId(), oldPrefix, newPrefix);
        return toNode(unit);
    }

    @Transactional
    public OrganizationNode deactivate(UUID id, long expectedVersion) {
        repository.acquireHierarchyMutationLock();
        OrganizationUnit unit = existing(id);
        requireVersion(unit, expectedVersion);
        if (repository.hasActiveDescendant(unit.getId(), unit.getPath())) {
            throw new IllegalStateException(ACTIVE_DESCENDANT);
        }
        unit.deactivate();
        repository.flush();
        return toNode(unit);
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
                .orElseThrow(() -> new IllegalArgumentException(PARENT_NOT_FOUND));
        if (!parent.isActive() || repository.hasInactiveAncestorOrSelf(parent.getPath())) {
            throw new IllegalStateException(PARENT_INACTIVE);
        }
        return parent;
    }

    private OrganizationUnit existing(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(ORGANIZATION_NOT_FOUND));
    }

    private String uniqueCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException(CODE_REQUIRED);
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > MAX_CODE_LENGTH) {
            throw new IllegalArgumentException(CODE_TOO_LONG);
        }
        if (repository.existsByCode(normalized)) {
            throw new IllegalArgumentException(DUPLICATE_CODE);
        }
        return normalized;
    }

    private String validName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(NAME_REQUIRED);
        }
        String trimmed = name.trim();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException(NAME_TOO_LONG);
        }
        return trimmed;
    }

    private void validatePath(String path) {
        if (path.length() > MAX_PATH_LENGTH) {
            throw new IllegalArgumentException(PATH_TOO_LONG);
        }
    }

    private void requireVersion(OrganizationUnit unit, long expectedVersion) {
        if (unit.getVersion() != expectedVersion) {
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
                    throw new IllegalArgumentException(DUPLICATE_CODE, exception);
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
}

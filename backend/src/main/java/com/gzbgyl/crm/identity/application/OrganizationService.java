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

    private static final String ORGANIZATION_NOT_FOUND = "组织不存在";
    private static final String PARENT_NOT_FOUND = "上级组织不存在";
    private static final String PARENT_INACTIVE = "上级组织已停用";
    private static final String DUPLICATE_CODE = "组织编码已存在";
    private static final String INVALID_MOVE = "组织不能移动到其下级节点";

    private final OrganizationUnitRepository repository;

    public OrganizationService(OrganizationUnitRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public OrganizationNode createRoot(String code, String name) {
        OrganizationUnit root = new OrganizationUnit(null, uniqueCode(code), name, "/");
        return saveNew(root);
    }

    @Transactional
    public OrganizationNode createChild(UUID parentId, String code, String name) {
        OrganizationUnit parent = activeParent(parentId);
        OrganizationUnit child = new OrganizationUnit(parentId, uniqueCode(code), name, parent.getPath());
        return saveNew(child);
    }

    @Transactional
    public OrganizationNode rename(UUID id, String name) {
        OrganizationUnit unit = existingForUpdate(id);
        unit.rename(name);
        repository.flush();
        return toNode(unit);
    }

    @Transactional
    public OrganizationNode move(UUID id, UUID newParentId) {
        OrganizationUnit unit = existingForUpdate(id);
        if (id.equals(newParentId)) {
            throw new IllegalArgumentException(INVALID_MOVE);
        }
        OrganizationUnit newParent = activeParent(newParentId);
        if (newParent.getPath().startsWith(unit.getPath())) {
            throw new IllegalArgumentException(INVALID_MOVE);
        }

        String oldPrefix = unit.getPath();
        String newPrefix = newParent.getPath() + unit.getId() + "/";
        List<OrganizationUnit> subtree = repository.findByPathStartingWithOrderByPathAsc(oldPrefix);
        unit.moveTo(newParentId, newPrefix);
        subtree.stream()
                .filter(descendant -> !descendant.getId().equals(unit.getId()))
                .forEach(descendant -> descendant.replacePathPrefix(oldPrefix, newPrefix));
        repository.flush();
        return toNode(unit);
    }

    @Transactional
    public OrganizationNode deactivate(UUID id) {
        OrganizationUnit unit = existingForUpdate(id);
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
        OrganizationUnit parent = repository.findByIdForUpdate(parentId)
                .orElseThrow(() -> new IllegalArgumentException(PARENT_NOT_FOUND));
        if (!parent.isActive()) {
            throw new IllegalStateException(PARENT_INACTIVE);
        }
        return parent;
    }

    private OrganizationUnit existing(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(ORGANIZATION_NOT_FOUND));
    }

    private OrganizationUnit existingForUpdate(UUID id) {
        return repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException(ORGANIZATION_NOT_FOUND));
    }

    private String uniqueCode(String code) {
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if (repository.existsByCode(normalized)) {
            throw new IllegalArgumentException(DUPLICATE_CODE);
        }
        return normalized;
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
                unit.getPath(), unit.isActive());
    }
}

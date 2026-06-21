package com.gzbgyl.crm.identity.domain;

import com.gzbgyl.crm.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "organization_unit")
public class OrganizationUnit extends BaseEntity {

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "code", nullable = false, unique = true, length = 100)
    private String code;

    @Column(name = "path", nullable = false, length = 2000)
    private String path;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected OrganizationUnit() {
    }

    public OrganizationUnit(UUID parentId, String code, String name, String parentPath) {
        this.parentId = parentId;
        this.code = code;
        this.name = name;
        this.path = parentPath + getId() + "/";
    }

    public UUID getParentId() {
        return parentId;
    }

    public String getName() {
        return name;
    }

    public String getCode() {
        return code;
    }

    public String getPath() {
        return path;
    }

    public boolean isActive() {
        return active;
    }

    public void rename(String name) {
        this.name = name;
    }

    public void moveTo(UUID newParentId, String newPath) {
        this.parentId = newParentId;
        this.path = newPath;
    }

    public void replacePathPrefix(String oldPrefix, String newPrefix) {
        this.path = newPrefix + path.substring(oldPrefix.length());
    }

    public void deactivate() {
        this.active = false;
    }
}

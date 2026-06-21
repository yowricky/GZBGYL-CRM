CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE organization_unit (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_id UUID,
    name VARCHAR(200) NOT NULL,
    code VARCHAR(100) NOT NULL,
    path VARCHAR(2000) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    CONSTRAINT uk_organization_unit_code UNIQUE (code),
    CONSTRAINT fk_organization_unit_parent
        FOREIGN KEY (parent_id) REFERENCES organization_unit (id)
);

CREATE INDEX idx_organization_unit_parent ON organization_unit (parent_id);
CREATE INDEX idx_organization_unit_path ON organization_unit (path);

CREATE TABLE app_user (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_unit_id UUID NOT NULL,
    username VARCHAR(150) NOT NULL,
    normalized_username VARCHAR(150) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    CONSTRAINT uk_app_user_normalized_username UNIQUE (normalized_username),
    CONSTRAINT fk_app_user_organization_unit
        FOREIGN KEY (organization_unit_id) REFERENCES organization_unit (id)
);

CREATE INDEX idx_app_user_organization_unit ON app_user (organization_unit_id);

CREATE TABLE role (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(100) NOT NULL,
    name VARCHAR(200) NOT NULL,
    system_role BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    CONSTRAINT uk_role_code UNIQUE (code)
);

CREATE TABLE permission (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(150) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    CONSTRAINT uk_permission_code UNIQUE (code)
);

CREATE TABLE app_user_role (
    app_user_id UUID NOT NULL,
    role_id UUID NOT NULL,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_by UUID,
    PRIMARY KEY (app_user_id, role_id),
    CONSTRAINT fk_app_user_role_user
        FOREIGN KEY (app_user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_app_user_role_role
        FOREIGN KEY (role_id) REFERENCES role (id) ON DELETE CASCADE
);

CREATE INDEX idx_app_user_role_role ON app_user_role (role_id);

CREATE TABLE role_permission (
    role_id UUID NOT NULL,
    permission_id UUID NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    granted_by UUID,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permission_role
        FOREIGN KEY (role_id) REFERENCES role (id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permission_permission
        FOREIGN KEY (permission_id) REFERENCES permission (id) ON DELETE CASCADE
);

CREATE INDEX idx_role_permission_permission ON role_permission (permission_id);

CREATE TABLE audit_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id UUID,
    event_type VARCHAR(100) NOT NULL,
    aggregate_type VARCHAR(150) NOT NULL,
    aggregate_id UUID NOT NULL,
    before_state JSONB,
    after_state JSONB,
    ip_address INET,
    reason VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_log_actor
        FOREIGN KEY (actor_id) REFERENCES app_user (id) ON DELETE SET NULL
);

CREATE INDEX idx_audit_log_aggregate
    ON audit_log (aggregate_type, aggregate_id, created_at DESC);
CREATE INDEX idx_audit_log_actor_created_at
    ON audit_log (actor_id, created_at DESC);

CREATE TABLE attachment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_type VARCHAR(150) NOT NULL,
    owner_id UUID NOT NULL,
    original_filename VARCHAR(500) NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 CHAR(64) NOT NULL,
    storage_key VARCHAR(1000) NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    deleted_by UUID,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID,
    CONSTRAINT ck_attachment_size_nonnegative CHECK (size_bytes >= 0),
    CONSTRAINT uk_attachment_storage_key UNIQUE (storage_key)
);

CREATE INDEX idx_attachment_owner ON attachment (owner_type, owner_id);
CREATE INDEX idx_attachment_sha256 ON attachment (sha256);

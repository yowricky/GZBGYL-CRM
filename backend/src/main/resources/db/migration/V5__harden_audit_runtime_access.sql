-- Audit recovery is forward-only: repair by appending a corrective event through a reviewed
-- migration or maintenance procedure. Never disable these triggers or rewrite prior events.
CREATE TRIGGER audit_log_append_only_truncate
BEFORE TRUNCATE ON audit_log
FOR EACH STATEMENT EXECUTE FUNCTION reject_audit_log_mutation();

CREATE INDEX idx_audit_log_created_at
    ON audit_log (created_at DESC, id DESC);
CREATE INDEX idx_audit_log_event_created_at
    ON audit_log (event_type, created_at DESC);
CREATE INDEX idx_audit_log_type_created_at
    ON audit_log (aggregate_type, created_at DESC);

REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO crm_app;

REVOKE ALL ON ALL TABLES IN SCHEMA public FROM crm_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON
    organization_unit,
    app_user,
    role,
    permission,
    app_user_role,
    role_permission,
    attachment
TO crm_app;
GRANT SELECT, INSERT ON audit_log TO crm_app;

REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM crm_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO crm_app;

-- Future tables receive no implicit runtime access. Each forward migration must grant the
-- minimum privileges for its business tables and keep audit/history tables append-only.
ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES FROM crm_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON SEQUENCES FROM crm_app;

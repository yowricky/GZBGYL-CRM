package com.gzbgyl.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gzbgyl.crm.support.PostgresIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class FoundationSchemaMigrationTest extends PostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void appliesSecurityScopePermissionMigration() {
        assertThat(jdbcTemplate.queryForObject(
                "select max(version) from flyway_schema_history where success", String.class))
                .isEqualTo("4");
        assertThat(jdbcTemplate.queryForList(
                "select code from permission where code like 'financial:%' order by code",
                String.class)).containsExactly(
                        "financial:read:company",
                        "financial:read:department",
                        "financial:read:own");
    }

    @Test
    void flywayCreatesFoundationTables() {
        List<String> tables = jdbcTemplate.queryForList("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public'
                """, String.class);

        assertThat(tables).contains(
                "organization_unit",
                "app_user",
                "role",
                "permission",
                "app_user_role",
                "role_permission",
                "audit_log",
                "attachment");
    }

    @Test
    void organizationUnitUsesContractedPathColumn() {
        List<String> columns = jdbcTemplate.queryForList("""
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'organization_unit'
                """, String.class);

        assertThat(columns)
                .contains("path")
                .doesNotContain("materialized_path");
    }

    @Test
    void permissionIncludesMutableAuditColumns() {
        List<String> columns = jdbcTemplate.queryForList("""
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'permission'
                """, String.class);

        assertThat(columns).contains("version", "updated_at", "updated_by");
    }

    @Test
    void attachmentSha256AcceptsOnlyLowercaseHexDigest() {
        String insert = """
                INSERT INTO attachment (
                    owner_type, owner_id, original_filename, content_type,
                    size_bytes, sha256, storage_key
                ) VALUES (?, gen_random_uuid(), 'contract.txt', 'text/plain', 1, ?, ?)
                """;

        assertThatNoException().isThrownBy(() -> jdbcTemplate.update(
                insert, "contract", "a".repeat(64), "valid-digest-object"));
        assertThatThrownBy(() -> jdbcTemplate.update(
                insert, "contract", "abc", "padded-invalid-digest-object"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void organizationPathIndexSupportsPrefixLookups() {
        String definition = jdbcTemplate.queryForObject("""
                SELECT indexdef
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND indexname = 'idx_organization_unit_path'
                """, String.class);

        assertThat(definition).contains("(path varchar_pattern_ops)");
    }

    @Test
    void criticalForeignKeysUseExpectedTargetsAndDeleteActions() {
        List<String> foreignKeys = jdbcTemplate.queryForList("""
                SELECT constraint_record.conname || ':'
                    || referenced_table.relname || ':'
                    || constraint_record.confdeltype::text
                FROM pg_constraint constraint_record
                JOIN pg_class referenced_table
                  ON referenced_table.oid = constraint_record.confrelid
                WHERE constraint_record.connamespace = 'public'::regnamespace
                  AND constraint_record.contype = 'f'
                """, String.class);

        assertThat(foreignKeys).contains(
                "fk_organization_unit_parent:organization_unit:a",
                "fk_app_user_organization_unit:organization_unit:a",
                "fk_app_user_role_user:app_user:c",
                "fk_app_user_role_role:role:c",
                "fk_role_permission_role:role:c",
                "fk_role_permission_permission:permission:c",
                "fk_audit_log_actor:app_user:r");
    }

    @Test
    void foundationSchemaEnforcesCriticalRelationshipsAndLookupPaths() {
        List<String> constraints = jdbcTemplate.queryForList("""
                SELECT conname
                FROM pg_constraint
                WHERE connamespace = 'public'::regnamespace
                """, String.class);
        List<String> indexDefinitions = jdbcTemplate.queryForList("""
                SELECT indexdef
                FROM pg_indexes
                WHERE schemaname = 'public'
                """, String.class);

        assertThat(constraints).contains(
                "uk_organization_unit_code",
                "fk_organization_unit_parent",
                "uk_app_user_normalized_username",
                "fk_app_user_organization_unit",
                "uk_role_code",
                "uk_permission_code",
                "fk_app_user_role_user",
                "fk_app_user_role_role",
                "fk_role_permission_role",
                "fk_role_permission_permission",
                "uk_attachment_storage_key");
        assertThat(indexDefinitions)
                .anySatisfy(definition -> assertThat(definition)
                        .contains("idx_audit_log_aggregate")
                        .contains("(aggregate_type, aggregate_id, created_at DESC)"))
                .anySatisfy(definition -> assertThat(definition)
                        .contains("idx_audit_log_actor_created_at")
                        .contains("(actor_id, created_at DESC)"))
                .anySatisfy(definition -> assertThat(definition)
                        .contains("idx_attachment_owner")
                        .contains("(owner_type, owner_id)"));
    }
}

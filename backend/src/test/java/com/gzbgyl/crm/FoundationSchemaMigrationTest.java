package com.gzbgyl.crm;

import static org.assertj.core.api.Assertions.assertThat;

import com.gzbgyl.crm.support.PostgresIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class FoundationSchemaMigrationTest extends PostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    void foundationSchemaEnforcesCriticalRelationshipsAndLookupPaths() {
        List<String> constraints = jdbcTemplate.queryForList("""
                SELECT conname
                FROM pg_constraint
                WHERE connamespace = 'public'::regnamespace
                """, String.class);
        List<String> indexes = jdbcTemplate.queryForList("""
                SELECT indexname
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
        assertThat(indexes).contains(
                "idx_audit_log_aggregate",
                "idx_audit_log_actor_created_at",
                "idx_attachment_owner");
    }
}

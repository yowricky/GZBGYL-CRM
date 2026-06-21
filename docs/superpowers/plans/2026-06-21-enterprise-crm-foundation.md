# Enterprise CRM Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a deployable CRM foundation with organization hierarchy, users, roles, session authentication, department data scopes, audit logging, attachment infrastructure, and an authenticated Vue application shell.

**Architecture:** Use a monorepo with a Vue 3 SPA and a Spring Boot modular monolith. PostgreSQL stores business facts, Redis stores sessions, MinIO stores file content, Flyway owns schema evolution, and every business module accesses shared capabilities through narrow application interfaces.

**Tech Stack:** Java 21, Spring Boot 3.5.7, Spring Security, Spring Data JPA, Flyway, PostgreSQL 17, Redis 7.4, MinIO `RELEASE.2025-04-22T22-12-26Z`, Maven 3.9, Vue 3.5.22, TypeScript 5.9.2, Vite 7.1.7, Pinia 3.0.3, Vue Router 4.6.3, Element Plus 2.11.5, Vitest 3.2.4, Playwright 1.55.1, Docker Compose.

---

## Scope Decomposition

The approved CRM specification contains multiple independently testable subsystems. Implement them as separate plans in this order:

1. **Foundation (this plan):** engineering skeleton, organization, users, roles, sessions, data scope, audit, attachments, deployment.
2. **CRM master data:** leads, customers, contacts, relationship network, duplicate detection, Excel import.
3. **Opportunity war room:** stages, team, milestones, decision chain, sales map, actions, costs, forecast.
4. **Product and quote:** catalog, free-form lines, immutable quote versions, quote attachments.
5. **Contract and payment:** contracts, payment plans, receipts, overdue warnings, corrections.
6. **Performance workspace:** annual/quarterly/monthly targets, team/personal completion, funnel, risk dashboards.
7. **Release hardening:** mobile responsive flows, performance baseline, security verification, backup restore acceptance.

Each later plan begins only after the previous increment is running and its public module interfaces are stable.

## File Structure

```text
CRM/
  compose.yaml                         # Local/private runtime services
  .env.example                        # Required deployment variables
  backend/
    pom.xml                            # Backend dependencies and test plugins
    Dockerfile                         # Production backend image
    src/main/java/com/gzbgyl/crm/
      CrmApplication.java              # Spring Boot entry point
      shared/                          # Cross-cutting primitives only
        api/ApiError.java
        api/GlobalExceptionHandler.java
        security/SecurityConfig.java
        security/JsonAuthenticationEntryPoint.java
        security/JsonAccessDeniedHandler.java
        security/CurrentUser.java
        security/CurrentUserService.java
        persistence/BaseEntity.java
      identity/                        # Organization, users, roles, sessions
        domain/OrganizationUnit.java
        domain/AppUser.java
        domain/Role.java
        domain/Permission.java
        application/OrganizationService.java
        application/UserAdministrationService.java
        application/DataScopeService.java
        application/ExplicitScopeProvider.java
        web/AuthenticationController.java
        web/OrganizationController.java
        web/UserAdministrationController.java
        persistence/*Repository.java
      audit/                           # Append-only audit capability
        domain/AuditLog.java
        application/AuditService.java
        web/AuditLogController.java
        persistence/AuditLogRepository.java
      attachment/                      # File metadata and storage port
        domain/Attachment.java
        application/AttachmentService.java
        application/AttachmentAuthorizer.java
        infrastructure/MinioStorageAdapter.java
        web/AttachmentController.java
        persistence/AttachmentRepository.java
    src/main/resources/
      application.yml
      application-local.yml
      db/migration/V1__foundation_schema.sql
    src/test/java/com/gzbgyl/crm/
      support/PostgresIntegrationTest.java
      identity/*Test.java
      audit/AuditServiceTest.java
      attachment/AttachmentServiceTest.java
  frontend/
    package.json                        # SPA scripts and locked dependencies
    pnpm-lock.yaml
    vite.config.ts
    tsconfig.json
    Dockerfile
    src/
      main.ts
      App.vue
      api/http.ts
      api/auth.ts
      router/index.ts
      stores/auth.ts
      layouts/AppShell.vue
      views/LoginView.vue
      views/admin/OrganizationView.vue
      views/admin/UserView.vue
      components/PermissionGate.vue
    tests/
      auth-store.spec.ts
      permission-gate.spec.ts
    e2e/login.spec.ts
  infra/nginx/nginx.conf               # Same-origin SPA/API routing
  .github/workflows/ci.yml             # Backend, frontend and container checks
```

## Task 1: Bootstrap the Backend and Frontend

**Files:**
- Create: `backend/pom.xml`
- Create: `backend/src/main/java/com/gzbgyl/crm/CrmApplication.java`
- Create: `backend/src/test/java/com/gzbgyl/crm/CrmApplicationTest.java`
- Create: `frontend/package.json`
- Create: `frontend/src/main.ts`
- Create: `frontend/src/App.vue`

- [ ] **Step 1: Write the failing Spring context test**

```java
package com.gzbgyl.crm;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CrmApplicationTest {
    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 2: Run the test and verify the missing project fails**

Run: `cd backend && mvn -q -Dtest=CrmApplicationTest test`

Expected: FAIL because `pom.xml` and `CrmApplication` do not exist.

- [ ] **Step 3: Create the Maven project and application entry point**

Use Spring Boot `3.5.7`, Java 21, and include these dependencies in `backend/pom.xml`: web, validation, actuator, security, data-jpa, session-data-redis, flyway-core, flyway-database-postgresql, PostgreSQL driver, MinIO client, test, security-test, and Testcontainers PostgreSQL/JUnit.

```java
package com.gzbgyl.crm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CrmApplication {
    public static void main(String[] args) {
        SpringApplication.run(CrmApplication.class, args);
    }
}
```

Configure Surefire to run `*Test` and Failsafe to run `*IT`. Use Spring Boot parent `3.5.7`, Testcontainers BOM `1.21.3`, and MinIO client `8.5.17`; let the Spring dependency BOM manage Spring library versions.

- [ ] **Step 4: Run the backend test**

Run: `cd backend && mvn -q -Dtest=CrmApplicationTest test`

Expected: PASS with `Tests run: 1, Failures: 0, Errors: 0`.

- [ ] **Step 5: Create the Vue application skeleton**

`frontend/package.json` must expose `dev`, `build`, `test`, `test:run`, and `e2e` scripts. Pin Vue `3.5.22`, TypeScript `5.9.2`, Vite `7.1.7`, Vue Router `4.6.3`, Pinia `3.0.3`, Element Plus `2.11.5`, Vitest `3.2.4`, and Playwright `1.55.1`; lock the resolved Axios, Vue Test Utils, and jsdom versions in `pnpm-lock.yaml`.

```ts
// frontend/src/main.ts
import { createApp } from "vue";
import { createPinia } from "pinia";
import App from "./App.vue";

createApp(App).use(createPinia()).mount("#app");
```

```vue
<!-- frontend/src/App.vue -->
<template><main>CRM foundation</main></template>
```

- [ ] **Step 6: Verify the frontend**

Run: `cd frontend && pnpm install --frozen-lockfile=false && pnpm test:run && pnpm build`

Expected: Vitest exits 0 and Vite creates `frontend/dist`.

- [ ] **Step 7: Commit**

```bash
git add backend frontend
git commit -m "build: bootstrap CRM backend and frontend"
```

## Task 2: Add Local Infrastructure and Database Migrations

**Files:**
- Create: `compose.yaml`
- Create: `.env.example`
- Create: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/resources/application-local.yml`
- Create: `backend/src/main/resources/db/migration/V1__foundation_schema.sql`
- Create: `backend/src/test/java/com/gzbgyl/crm/support/PostgresIntegrationTest.java`

- [ ] **Step 1: Write a failing migration integration test**

```java
package com.gzbgyl.crm.support;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest
class PostgresIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired DataSource dataSource;

    @Test
    void flywayCreatesFoundationTables() {
        var names = new JdbcTemplate(dataSource).queryForList(
            "select table_name from information_schema.tables where table_schema='public'",
            String.class
        );
        assertThat(names).contains("organization_unit", "app_user", "role", "permission", "audit_log", "attachment");
    }
}
```

- [ ] **Step 2: Run the integration test and verify it fails**

Run: `cd backend && mvn -q -Dtest=PostgresIntegrationTest test`

Expected: FAIL because the foundation tables are absent.

- [ ] **Step 3: Create infrastructure configuration**

`compose.yaml` must define PostgreSQL 17, Redis 7, MinIO, backend, frontend/Nginx, named volumes, health checks, and environment variables sourced from `.env`. `.env.example` must contain non-secret examples for database, Redis, session cookie, MinIO endpoint, bucket, access key, and secret key.

```yaml
# backend/src/main/resources/application.yml
spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/crm}
    username: ${DB_USER:crm}
    password: ${DB_PASSWORD:crm_local_only}
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
  flyway:
    enabled: true
  session:
    store-type: redis
server:
  servlet:
    session:
      cookie:
        http-only: true
        same-site: lax
management:
  endpoints:
    web:
      exposure:
        include: health,info
```

- [ ] **Step 4: Create `V1__foundation_schema.sql`**

The migration must create UUID-keyed tables for organization units, users, roles, permissions, user-role, role-permission, audit logs, and attachments. Add unique indexes for normalized username, permission code, role code, and object storage key. Add `version`, `created_at`, `created_by`, `updated_at`, and `updated_by` columns to mutable tables.

```sql
create extension if not exists pgcrypto;

create table organization_unit (
  id uuid primary key default gen_random_uuid(),
  parent_id uuid references organization_unit(id),
  name varchar(120) not null,
  code varchar(60) not null unique,
  path varchar(1000) not null,
  active boolean not null default true,
  version bigint not null default 0,
  created_at timestamptz not null,
  created_by uuid,
  updated_at timestamptz not null,
  updated_by uuid
);
```

Continue the same naming and audit conventions for the remaining tables; do not use Hibernate schema generation.

- [ ] **Step 5: Run the integration test**

Run: `cd backend && mvn -q -Dtest=PostgresIntegrationTest test`

Expected: PASS and Flyway reports one applied migration.

- [ ] **Step 6: Commit**

```bash
git add compose.yaml .env.example backend/src/main/resources backend/src/test/java/com/gzbgyl/crm/support
git commit -m "build: add CRM runtime infrastructure"
```

## Task 3: Implement Organization Hierarchy

**Files:**
- Create: `backend/src/main/java/com/gzbgyl/crm/shared/persistence/BaseEntity.java`
- Create: `backend/src/main/java/com/gzbgyl/crm/identity/domain/OrganizationUnit.java`
- Create: `backend/src/main/java/com/gzbgyl/crm/identity/persistence/OrganizationUnitRepository.java`
- Create: `backend/src/main/java/com/gzbgyl/crm/identity/application/OrganizationService.java`
- Create: `backend/src/test/java/com/gzbgyl/crm/identity/OrganizationServiceTest.java`

- [ ] **Step 1: Write failing organization tests**

```java
@Test
void createsChildWithMaterializedPath() {
    var company = service.createRoot("GZBGYL", "葛洲坝供应链");
    var sales = service.createChild(company.id(), "SALES", "销售部");
    assertThat(sales.path()).isEqualTo("/" + company.id() + "/" + sales.id() + "/");
}

@Test
void rejectsMovingUnitUnderItsDescendant() {
    var company = service.createRoot("GZBGYL", "葛洲坝供应链");
    var sales = service.createChild(company.id(), "SALES", "销售部");
    assertThatThrownBy(() -> service.move(company.id(), sales.id()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("组织不能移动到其下级节点");
}
```

- [ ] **Step 2: Verify tests fail**

Run: `cd backend && mvn -q -Dtest=OrganizationServiceTest test`

Expected: FAIL because the organization domain does not exist.

- [ ] **Step 3: Implement the organization aggregate and service**

```java
public record OrganizationNode(UUID id, UUID parentId, String code, String name, String path, boolean active) {}

@Transactional
public OrganizationNode createChild(UUID parentId, String code, String name) {
    var parent = repository.findById(parentId).orElseThrow(() -> new EntityNotFoundException("上级组织不存在"));
    var child = OrganizationUnit.createChild(parent, code, name);
    child = repository.save(child);
    child.assignPath(parent.path() + child.id() + "/");
    return new OrganizationNode(child.id(), child.parentId(), child.code(), child.name(), child.path(), child.active());
}
```

Implement root creation, child creation, rename, move with cycle rejection, deactivate, descendant query by materialized path, and optimistic locking.

- [ ] **Step 4: Run tests**

Run: `cd backend && mvn -q -Dtest=OrganizationServiceTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/gzbgyl/crm/shared backend/src/main/java/com/gzbgyl/crm/identity backend/src/test/java/com/gzbgyl/crm/identity/OrganizationServiceTest.java
git commit -m "feat: add organization hierarchy"
```

## Task 4: Implement Users, Roles, and Permissions

**Files:**
- Create: `backend/src/main/java/com/gzbgyl/crm/identity/domain/AppUser.java`
- Create: `backend/src/main/java/com/gzbgyl/crm/identity/domain/Role.java`
- Create: `backend/src/main/java/com/gzbgyl/crm/identity/domain/Permission.java`
- Create: `backend/src/main/java/com/gzbgyl/crm/identity/persistence/AppUserRepository.java`
- Create: `backend/src/main/java/com/gzbgyl/crm/identity/persistence/RoleRepository.java`
- Create: `backend/src/main/java/com/gzbgyl/crm/identity/application/UserAdministrationService.java`
- Create: `backend/src/test/java/com/gzbgyl/crm/identity/UserAdministrationServiceTest.java`

- [ ] **Step 1: Write failing user administration tests**

```java
@Test
void createsUserWithEncodedPasswordAndAssignedRole() {
    var user = service.createUser(new CreateUserCommand(
        "sales01", "销售一号", "Initial#Pass123", salesDepartmentId, Set.of("SALES")
    ));
    assertThat(user.username()).isEqualTo("sales01");
    assertThat(passwordEncoder.matches("Initial#Pass123", repository.findById(user.id()).orElseThrow().passwordHash())).isTrue();
    assertThat(user.permissions()).contains("opportunity:read:own");
}

@Test
void rejectsDuplicateUsernameIgnoringCase() {
    service.createUser(new CreateUserCommand(
        "Sales01", "销售一号", "Initial#Pass123", salesDepartmentId, Set.of("SALES")
    ));
    assertThatThrownBy(() -> service.createUser(new CreateUserCommand(
        "sales01", "销售二号", "Initial#Pass456", salesDepartmentId, Set.of("SALES")
    )))
        .hasMessage("用户名已存在");
}
```

- [ ] **Step 2: Verify tests fail**

Run: `cd backend && mvn -q -Dtest=UserAdministrationServiceTest test`

Expected: FAIL because user and role services are absent.

- [ ] **Step 3: Implement user, role, and permission aggregates**

```java
public record CreateUserCommand(
    String username,
    String displayName,
    String initialPassword,
    UUID organizationUnitId,
    Set<String> roleCodes
) {}

public record UserSummary(
    UUID id,
    String username,
    String displayName,
    UUID organizationUnitId,
    boolean active,
    Set<String> roles,
    Set<String> permissions
) {}
```

Normalize usernames with `Locale.ROOT`, encode passwords with `BCryptPasswordEncoder(12)`, reject inactive organizations, and provide activate/deactivate/reset-password/assign-role operations. Seed exactly these approved role codes: `SALES`, `SALES_MANAGER`, `PRESALES_TECH`, `PROJECT_MANAGER`, `OPERATIONS_VIEWER`, `FINANCE_VIEWER`, `EXECUTIVE_VIEWER`, `SYSTEM_ADMIN`.

- [ ] **Step 4: Run tests**

Run: `cd backend && mvn -q -Dtest=UserAdministrationServiceTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/gzbgyl/crm/identity backend/src/test/java/com/gzbgyl/crm/identity/UserAdministrationServiceTest.java
git commit -m "feat: add users roles and permissions"
```

## Task 5: Add Session Authentication and API Error Handling

**Files:**
- Create: `backend/src/main/java/com/gzbgyl/crm/shared/api/ApiError.java`
- Create: `backend/src/main/java/com/gzbgyl/crm/shared/api/GlobalExceptionHandler.java`
- Create: `backend/src/main/java/com/gzbgyl/crm/shared/security/SecurityConfig.java`
- Create: `backend/src/main/java/com/gzbgyl/crm/shared/security/JsonAuthenticationEntryPoint.java`
- Create: `backend/src/main/java/com/gzbgyl/crm/shared/security/JsonAccessDeniedHandler.java`
- Create: `backend/src/main/java/com/gzbgyl/crm/shared/security/CurrentUser.java`
- Create: `backend/src/main/java/com/gzbgyl/crm/shared/security/CurrentUserService.java`
- Create: `backend/src/main/java/com/gzbgyl/crm/identity/web/AuthenticationController.java`
- Create: `backend/src/test/java/com/gzbgyl/crm/identity/AuthenticationControllerTest.java`

- [ ] **Step 1: Write failing authentication API tests**

```java
@Test
void loginCreatesSessionAndMeReturnsPermissions() throws Exception {
    mockMvc.perform(post("/api/auth/login").with(csrf())
            .contentType(APPLICATION_JSON)
            .content("""{"username":"sales01","password":"Initial#Pass123"}"""))
        .andExpect(status().isNoContent())
        .andExpect(cookie().exists("SESSION"));
}

@Test
void unauthenticatedBusinessRequestReturnsJson401() throws Exception {
    mockMvc.perform(get("/api/admin/users"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
}
```

- [ ] **Step 2: Verify tests fail**

Run: `cd backend && mvn -q -Dtest=AuthenticationControllerTest test`

Expected: FAIL with 404 or missing security beans.

- [ ] **Step 3: Implement same-origin session security**

```java
@Bean
SecurityFilterChain security(HttpSecurity http) throws Exception {
    return http
        .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/actuator/health", "/api/auth/login", "/api/auth/csrf").permitAll()
            .anyRequest().authenticated())
        .exceptionHandling(errors -> errors
            .authenticationEntryPoint(new JsonAuthenticationEntryPoint())
            .accessDeniedHandler(new JsonAccessDeniedHandler()))
        .build();
}
```

Expose `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/auth/me`, and `GET /api/auth/csrf`. Return stable error codes and field errors through `ApiError`; never return stack traces.

`JsonAuthenticationEntryPoint` and `JsonAccessDeniedHandler` must serialize `ApiError` with the shared `ObjectMapper`, content type `application/json`, and codes `AUTHENTICATION_REQUIRED` and `ACCESS_DENIED` respectively.

- [ ] **Step 4: Run authentication and context tests**

Run: `cd backend && mvn -q -Dtest=AuthenticationControllerTest,CrmApplicationTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/gzbgyl/crm/shared backend/src/main/java/com/gzbgyl/crm/identity/web backend/src/test/java/com/gzbgyl/crm/identity/AuthenticationControllerTest.java
git commit -m "feat: add secure session authentication"
```

## Task 6: Enforce Department Data Scopes

**Files:**
- Create: `backend/src/main/java/com/gzbgyl/crm/identity/application/DataScopeService.java`
- Create: `backend/src/main/java/com/gzbgyl/crm/identity/application/DataScope.java`
- Create: `backend/src/main/java/com/gzbgyl/crm/identity/application/ExplicitScopeProvider.java`
- Create: `backend/src/test/java/com/gzbgyl/crm/identity/DataScopeServiceTest.java`

- [ ] **Step 1: Write failing data-scope tests**

```java
@Test
void salespersonReceivesOwnScope() {
    assertThat(service.resolve(salespersonId)).isEqualTo(DataScope.own(salespersonId));
}

@Test
void managerReceivesDepartmentAndDescendants() {
    var scope = service.resolve(managerId);
    assertThat(scope.organizationUnitIds()).containsExactlyInAnyOrder(salesDepartmentId, southTeamId, northTeamId);
}

@Test
void explicitOpportunityCollaborationDoesNotGrantGlobalCustomerAccess() {
    when(explicitScopeProvider.opportunityIds(presalesId)).thenReturn(Set.of(opportunityId));
    var scope = service.resolve(presalesId);
    assertThat(scope.explicitOpportunityIds()).contains(opportunityId);
    assertThat(scope.globalCustomerRead()).isFalse();
}
```

- [ ] **Step 2: Verify tests fail**

Run: `cd backend && mvn -q -Dtest=DataScopeServiceTest test`

Expected: FAIL because `DataScopeService` does not exist.

- [ ] **Step 3: Implement immutable scope resolution**

```java
public record DataScope(
    UUID userId,
    Set<UUID> organizationUnitIds,
    Set<UUID> explicitOpportunityIds,
    boolean companyRead,
    boolean sensitiveFinancialFields
) {
    public static DataScope own(UUID userId) {
        return new DataScope(userId, Set.of(), Set.of(), false, false);
    }
}

public interface ExplicitScopeProvider {
    Set<UUID> opportunityIds(UUID userId);
}
```

Resolve role permissions, descendant departments, explicit collaboration from `ExplicitScopeProvider`, and sensitive-field flags once per request. Register an empty provider in the foundation; the opportunity module will replace it with its adapter. Business repositories in later plans must accept a `DataScope` parameter or a scope-derived specification; they must not infer authorization independently.

- [ ] **Step 4: Run tests**

Run: `cd backend && mvn -q -Dtest=DataScopeServiceTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/gzbgyl/crm/identity/application backend/src/test/java/com/gzbgyl/crm/identity/DataScopeServiceTest.java
git commit -m "feat: add hierarchical data scopes"
```

## Task 7: Add Append-Only Audit Logging

**Files:**
- Create: `backend/src/main/java/com/gzbgyl/crm/audit/domain/AuditLog.java`
- Create: `backend/src/main/java/com/gzbgyl/crm/audit/persistence/AuditLogRepository.java`
- Create: `backend/src/main/java/com/gzbgyl/crm/audit/application/AuditService.java`
- Create: `backend/src/main/java/com/gzbgyl/crm/audit/web/AuditLogController.java`
- Create: `backend/src/test/java/com/gzbgyl/crm/audit/AuditServiceTest.java`

- [ ] **Step 1: Write the failing audit test**

```java
@Test
void recordsStructuredBeforeAndAfterValues() {
    service.record(new AuditCommand(
        actorId, "USER_ROLE_CHANGED", "AppUser", userId,
        Map.of("roles", List.of("SALES")),
        Map.of("roles", List.of("SALES", "SALES_MANAGER")),
        "127.0.0.1", "role assignment"
    ));
    var saved = repository.findAll().getFirst();
    assertThat(saved.eventType()).isEqualTo("USER_ROLE_CHANGED");
    assertThat(saved.beforeJson()).contains("SALES");
    assertThat(saved.afterJson()).contains("SALES_MANAGER");
}
```

- [ ] **Step 2: Verify the test fails**

Run: `cd backend && mvn -q -Dtest=AuditServiceTest test`

Expected: FAIL because audit classes are absent.

- [ ] **Step 3: Implement append-only audit storage**

```java
public record AuditCommand(
    UUID actorId,
    String eventType,
    String aggregateType,
    UUID aggregateId,
    Map<String, ?> before,
    Map<String, ?> after,
    String ipAddress,
    String reason
) {}
```

Serialize maps with the application `ObjectMapper`, redact password/session/secret fields before storage, expose a paged read-only admin endpoint, and provide no update/delete repository methods.

- [ ] **Step 4: Integrate audit calls into organization, user, and role changes**

```java
auditService.record(new AuditCommand(
    currentUser.id(), "USER_DISABLED", "AppUser", user.id(),
    Map.of("active", true), Map.of("active", false),
    currentUserService.remoteAddress(), command.reason()
));
```

- [ ] **Step 5: Run audit and identity tests**

Run: `cd backend && mvn -q -Dtest=AuditServiceTest,OrganizationServiceTest,UserAdministrationServiceTest test`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/gzbgyl/crm/audit backend/src/main/java/com/gzbgyl/crm/identity backend/src/test/java/com/gzbgyl/crm/audit
git commit -m "feat: add append-only audit logging"
```

## Task 8: Add Attachment Metadata and MinIO Storage

**Files:**
- Create: `backend/src/main/java/com/gzbgyl/crm/attachment/domain/Attachment.java`
- Create: `backend/src/main/java/com/gzbgyl/crm/attachment/application/AttachmentAuthorizer.java`
- Create: `backend/src/main/java/com/gzbgyl/crm/attachment/application/AttachmentService.java`
- Create: `backend/src/main/java/com/gzbgyl/crm/attachment/infrastructure/MinioStorageAdapter.java`
- Create: `backend/src/main/java/com/gzbgyl/crm/attachment/web/AttachmentController.java`
- Create: `backend/src/main/java/com/gzbgyl/crm/attachment/persistence/AttachmentRepository.java`
- Create: `backend/src/test/java/com/gzbgyl/crm/attachment/AttachmentServiceTest.java`

- [ ] **Step 1: Write failing attachment tests**

```java
@Test
void storesContentOnlyAfterAuthorization() {
    when(authorizer.canWrite(actorId, "OPPORTUNITY", opportunityId)).thenReturn(true);
    var result = service.upload(actorId, "OPPORTUNITY", opportunityId,
        "方案.pdf", "application/pdf", new ByteArrayInputStream("pdf".getBytes(UTF_8)));
    assertThat(result.originalFilename()).isEqualTo("方案.pdf");
    verify(storage).put(result.storageKey(), "application/pdf", "pdf".getBytes(UTF_8));
}

@Test
void deniesDownloadWithoutBusinessObjectPermission() {
    when(authorizer.canRead(actorId, "OPPORTUNITY", opportunityId)).thenReturn(false);
    assertThatThrownBy(() -> service.download(actorId, attachmentId))
        .isInstanceOf(AccessDeniedException.class);
}
```

- [ ] **Step 2: Verify tests fail**

Run: `cd backend && mvn -q -Dtest=AttachmentServiceTest test`

Expected: FAIL because attachment services are absent.

- [ ] **Step 3: Implement storage port, metadata, and authorization**

```java
public interface ObjectStorage {
    void put(String key, String contentType, byte[] content);
    StoredObject get(String key);
    void delete(String key);
}

public record StoredObject(String contentType, byte[] content) {}

public interface AttachmentAuthorizer {
    boolean canRead(UUID actorId, String ownerType, UUID ownerId);
    boolean canWrite(UUID actorId, String ownerType, UUID ownerId);
}
```

Use generated storage keys, preserve the original filename as metadata, cap upload size from configuration, allow an explicit MIME allowlist, calculate SHA-256, reject empty files, and soft-delete metadata before removing object content through a retryable cleanup job.

- [ ] **Step 4: Implement the MinIO adapter and HTTP endpoints**

Expose multipart upload, authenticated streaming download, and soft-delete endpoints. Do not return raw bucket URLs; every download passes through authorization.

- [ ] **Step 5: Run tests**

Run: `cd backend && mvn -q -Dtest=AttachmentServiceTest test`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/gzbgyl/crm/attachment backend/src/test/java/com/gzbgyl/crm/attachment
git commit -m "feat: add authorized attachment storage"
```

## Task 9: Expose Organization and User Administration APIs

**Files:**
- Create: `backend/src/main/java/com/gzbgyl/crm/identity/web/OrganizationController.java`
- Create: `backend/src/main/java/com/gzbgyl/crm/identity/web/UserAdministrationController.java`
- Create: `backend/src/test/java/com/gzbgyl/crm/identity/AdministrationApiTest.java`

- [ ] **Step 1: Write failing API authorization tests**

```java
@Test
@WithMockUser(authorities = "system:admin")
void adminCanListOrganizationTree() throws Exception {
    mockMvc.perform(get("/api/admin/organization-units"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].children").isArray());
}

@Test
@WithMockUser(authorities = "opportunity:read:own")
void salespersonCannotListUsers() throws Exception {
    mockMvc.perform(get("/api/admin/users"))
        .andExpect(status().isForbidden());
}
```

- [ ] **Step 2: Verify tests fail**

Run: `cd backend && mvn -q -Dtest=AdministrationApiTest test`

Expected: FAIL because admin endpoints are absent.

- [ ] **Step 3: Implement validated admin endpoints**

```java
@PreAuthorize("hasAuthority('system:admin')")
@GetMapping("/api/admin/users")
Page<UserSummary> listUsers(@Valid UserSearchQuery query, Pageable pageable) {
    return users.search(query, pageable);
}

public record UserSearchQuery(String keyword, UUID organizationUnitId, Boolean active) {}
```

Add organization tree read/create/rename/move/deactivate and user search/create/activate/deactivate/reset-password/assign-role endpoints. Require a nonblank reason for deactivate, role change, and move operations.

- [ ] **Step 4: Run all identity tests**

Run: `cd backend && mvn -q -Dtest='*Identity*Test,AdministrationApiTest,AuthenticationControllerTest,OrganizationServiceTest,UserAdministrationServiceTest,DataScopeServiceTest' test`

Expected: PASS with no 401/403 expectation failures.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/gzbgyl/crm/identity/web backend/src/test/java/com/gzbgyl/crm/identity/AdministrationApiTest.java
git commit -m "feat: add identity administration APIs"
```

## Task 10: Build the Authenticated Vue Shell and Admin Screens

**Files:**
- Create: `frontend/src/api/http.ts`
- Create: `frontend/src/api/auth.ts`
- Create: `frontend/src/stores/auth.ts`
- Create: `frontend/src/router/index.ts`
- Create: `frontend/src/layouts/AppShell.vue`
- Create: `frontend/src/views/LoginView.vue`
- Create: `frontend/src/views/admin/OrganizationView.vue`
- Create: `frontend/src/views/admin/UserView.vue`
- Create: `frontend/src/components/PermissionGate.vue`
- Create: `frontend/tests/auth-store.spec.ts`
- Create: `frontend/tests/permission-gate.spec.ts`

- [ ] **Step 1: Write failing auth-store and permission tests**

```ts
it("loads the current session and exposes permissions", async () => {
  vi.mocked(authApi.me).mockResolvedValue({
    id: "u1", username: "admin", displayName: "管理员", permissions: ["system:admin"]
  });
  const store = useAuthStore();
  await store.restore();
  expect(store.has("system:admin")).toBe(true);
});

it("does not render protected content without permission", () => {
  const wrapper = mount(PermissionGate, {
    props: { permission: "system:admin" },
    slots: { default: "管理入口" }
  });
  expect(wrapper.text()).not.toContain("管理入口");
});
```

- [ ] **Step 2: Verify tests fail**

Run: `cd frontend && pnpm test:run`

Expected: FAIL because the store and component do not exist.

- [ ] **Step 3: Implement the same-origin HTTP and auth store**

```ts
export const http = axios.create({ baseURL: "/api", withCredentials: true });
http.defaults.xsrfCookieName = "XSRF-TOKEN";
http.defaults.xsrfHeaderName = "X-XSRF-TOKEN";

export const useAuthStore = defineStore("auth", {
  state: () => ({ user: null as CurrentUser | null, restored: false }),
  actions: {
    async restore() {
      try { this.user = await authApi.me(); }
      catch { this.user = null; }
      finally { this.restored = true; }
    },
    has(permission: string) { return this.user?.permissions.includes(permission) ?? false; }
  }
});
```

Add a router guard that waits for `restore()`, redirects anonymous users to `/login`, and redirects unauthorized users to `/403`.

- [ ] **Step 4: Implement the application shell and admin screens**

`AppShell.vue` must provide the approved left navigation, top search placeholder, notifications placeholder, user menu, responsive collapse, and `<RouterView>`. Organization and user screens must call the admin APIs, show validation errors inline, and require a reason dialog for moves, deactivation, and role changes.

```vue
<PermissionGate permission="system:admin">
  <el-menu-item index="/admin/users">系统管理</el-menu-item>
</PermissionGate>
```

- [ ] **Step 5: Run frontend tests and build**

Run: `cd frontend && pnpm test:run && pnpm build`

Expected: PASS and `dist` is produced.

- [ ] **Step 6: Commit**

```bash
git add frontend
git commit -m "feat: add authenticated CRM application shell"
```

## Task 11: Add Production Containers, CI, and Login Smoke Test

**Files:**
- Create: `backend/Dockerfile`
- Create: `frontend/Dockerfile`
- Create: `infra/nginx/nginx.conf`
- Create: `.github/workflows/ci.yml`
- Create: `frontend/playwright.config.ts`
- Create: `frontend/e2e/login.spec.ts`
- Modify: `compose.yaml`
- Create: `README.md`

- [ ] **Step 1: Write the failing browser smoke test**

```ts
import { test, expect } from "@playwright/test";

test("administrator logs in and opens user administration", async ({ page }) => {
  await page.goto("/login");
  await page.getByLabel("用户名").fill("admin");
  await page.getByLabel("密码").fill("Admin#ChangeMe123");
  await page.getByRole("button", { name: "登录" }).click();
  await expect(page).toHaveURL(/\/workspace/);
  await page.getByText("系统管理").click();
  await expect(page.getByRole("heading", { name: "用户管理" })).toBeVisible();
});
```

- [ ] **Step 2: Run the smoke test and verify it fails**

Run: `cd frontend && pnpm e2e --project=chromium`

Expected: FAIL because production routing and the seeded administrator are not available.

- [ ] **Step 3: Build production images and same-origin routing**

```nginx
server {
  listen 80;
  root /usr/share/nginx/html;
  location /api/ { proxy_pass http://backend:8080; }
  location /actuator/ { proxy_pass http://backend:8080; }
  location / { try_files $uri $uri/ /index.html; }
}
```

Use a multi-stage Maven/JRE image for the backend and a Node/Nginx multi-stage image for the frontend. Add a startup seed command that creates the initial administrator only when no users exist and requires `INITIAL_ADMIN_PASSWORD` from the environment.

- [ ] **Step 4: Add CI**

`.github/workflows/ci.yml` must run on pull requests and pushes to `main`: backend unit/integration tests, frontend unit tests, frontend build, Docker Compose config validation, and Playwright smoke tests against the composed stack. Upload test reports only on failure.

- [ ] **Step 5: Document local and private deployment**

`README.md` must include prerequisites, copying `.env.example` to `.env`, mandatory secret replacement, `docker compose up --build`, initial login, test commands, database backup command, restore command, and the health endpoint.

- [ ] **Step 6: Verify the complete foundation**

Run:

```bash
cd backend && mvn verify
cd ../frontend && pnpm test:run && pnpm build
cd .. && docker compose config --quiet
docker compose up -d --build
cd frontend && pnpm e2e --project=chromium
```

Expected: all Maven and Vitest tests pass, Compose validation exits 0, every container becomes healthy, and the Playwright login test passes.

- [ ] **Step 7: Commit**

```bash
git add backend/Dockerfile frontend/Dockerfile frontend/playwright.config.ts frontend/e2e infra .github compose.yaml README.md
git commit -m "build: ship deployable CRM foundation"
```

## Foundation Completion Gate

Before starting the CRM master-data plan, verify all of the following:

- [ ] A fresh database migrates from zero without Hibernate DDL changes.
- [ ] An administrator can create departments, users, roles, and deactivate accounts.
- [ ] A salesperson cannot call administration endpoints.
- [ ] A manager's `DataScope` contains only the manager's department and descendants.
- [ ] Authentication uses a secure server-side session and CSRF protection.
- [ ] Organization, user, role, login, export-ready audit events are queryable and append-only.
- [ ] Attachment download always performs owner-object authorization.
- [ ] Backend tests, frontend tests, production builds, Compose health checks, and login E2E pass.
- [ ] Private deployment and backup/restore commands are documented and exercised.

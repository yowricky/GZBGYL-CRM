# Role Permission Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let administrators assign multiple roles to users through a multi-select UI and configure permissions for each role.

**Architecture:** Reuse the existing identity schema: users already have many roles, and roles already have many permissions. Add role/permission read APIs and a role permission update API under Admin, then build one Admin page for role permissions and replace free-text role entry in user management with role multi-selects.

**Tech Stack:** Spring Boot, Spring Data JPA, PostgreSQL/Flyway schema already present, Vue 3, Pinia, Element Plus, Vitest.

---

### Task 1: Backend role permission API

**Files:**
- Create: `backend/src/main/java/com/gzbgyl/crm/identity/persistence/PermissionRepository.java`
- Create: `backend/src/main/java/com/gzbgyl/crm/identity/application/RoleAdministrationService.java`
- Create: `backend/src/main/java/com/gzbgyl/crm/identity/application/RoleSummary.java`
- Create: `backend/src/main/java/com/gzbgyl/crm/identity/application/PermissionSummary.java`
- Create: `backend/src/main/java/com/gzbgyl/crm/identity/web/RoleAdministrationController.java`
- Modify: `backend/src/main/java/com/gzbgyl/crm/identity/domain/Role.java`
- Modify: `backend/src/main/java/com/gzbgyl/crm/identity/persistence/RoleRepository.java`
- Test: `backend/src/test/java/com/gzbgyl/crm/identity/AdministrationApiTest.java`

- [ ] Add failing API tests for listing roles, listing permissions, and updating a role's permission codes.
- [ ] Add repositories/service/controller and the minimal role mutator required to replace permissions.
- [ ] Verify tests or, if local Maven is unavailable, verify through Docker build and live API checks.

### Task 2: Frontend user role multi-select

**Files:**
- Modify: `frontend/src/views/admin/UserView.vue`
- Test: `frontend/tests/admin-user-view.spec.ts`

- [ ] Add a failing component test that role assignment uses a multi-select instead of raw comma-separated input.
- [ ] Load Admin roles from `/admin/roles` and bind create/assign dialogs to selected role arrays.
- [ ] Show readable role names with codes in the table.

### Task 3: Frontend role permission page

**Files:**
- Create: `frontend/src/views/admin/RolePermissionView.vue`
- Modify: `frontend/src/router/index.ts`
- Modify: `frontend/src/layouts/AppShell.vue`
- Modify: `frontend/src/styles.css`
- Test: `frontend/tests/role-permission-view.spec.ts`

- [ ] Add Admin nav route `角色权限`.
- [ ] Build a restrained two-panel page: roles on the left, grouped permission checkboxes on the right.
- [ ] Save changed permission codes to `/admin/roles/{id}/permissions`.
- [ ] Treat `SYSTEM_ADMIN` as visible but not editable to reduce lockout risk.

### Task 4: Verification

- [ ] Run frontend tests and build.
- [ ] Run backend tests when available; otherwise build backend in Docker and run live smoke checks.
- [ ] Rebuild/restart containers and verify Admin routes return 200.

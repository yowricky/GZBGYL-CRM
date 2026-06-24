import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

import AppShell from '../layouts/AppShell.vue'
import BusinessModuleView from '../views/BusinessModuleView.vue'
import ForbiddenView from '../views/ForbiddenView.vue'
import LoginView from '../views/LoginView.vue'
import WorkspaceView from '../views/WorkspaceView.vue'
import OrganizationView from '../views/admin/OrganizationView.vue'
import RolePermissionView from '../views/admin/RolePermissionView.vue'
import UserView from '../views/admin/UserView.vue'
import { useAuthStore } from '../stores/auth'

const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/workspace' },
  { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
  { path: '/403', name: 'forbidden', component: ForbiddenView, meta: { public: true } },
  {
    path: '/',
    component: AppShell,
    children: [
      { path: 'workspace', name: 'workspace', component: WorkspaceView },
      { path: 'reports', name: 'reports', component: BusinessModuleView, props: { moduleKind: 'reports' } },
      { path: 'leads', name: 'leads', component: BusinessModuleView, props: { moduleKind: 'leads' } },
      { path: 'accounts', name: 'accounts', component: BusinessModuleView, props: { moduleKind: 'accounts' } },
      { path: 'partners', name: 'partners', component: BusinessModuleView, props: { moduleKind: 'partners' } },
      { path: 'opportunities', name: 'opportunities', component: BusinessModuleView, props: { moduleKind: 'opportunities' } },
      { path: 'projects', name: 'projects', component: BusinessModuleView, props: { moduleKind: 'projects' } },
      { path: 'quotes', name: 'quotes', component: BusinessModuleView, props: { moduleKind: 'quotes' } },
      { path: 'contracts', name: 'contracts', component: BusinessModuleView, props: { moduleKind: 'contracts' } },
      { path: 'activities', name: 'activities', component: BusinessModuleView, props: { moduleKind: 'activities' } },
      {
        path: 'admin/organization-units',
        name: 'organization-units',
        component: OrganizationView,
        meta: { permission: 'system:admin' },
      },
      {
        path: 'admin/users',
        name: 'users',
        component: UserView,
        meta: { permission: 'system:admin' },
      },
      {
        path: 'admin/role-permissions',
        name: 'role-permissions',
        component: RolePermissionView,
        meta: { permission: 'system:admin' },
      },
    ],
  },
]

export const router = createRouter({
  history: createWebHistory(),
  routes,
})

let restorePromise: Promise<void> | null = null

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (!auth.restored) {
    restorePromise ??= auth.restore()
    await restorePromise
  }

  if (to.meta.public) {
    if (to.name === 'login' && auth.user) {
      return '/workspace'
    }
    return true
  }

  if (!auth.user) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  const permission = to.meta.permission
  if (typeof permission === 'string' && !auth.has(permission)) {
    return '/403'
  }

  return true
})

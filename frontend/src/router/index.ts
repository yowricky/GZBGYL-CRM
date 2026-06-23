import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

import AppShell from '../layouts/AppShell.vue'
import LoginView from '../views/LoginView.vue'
import OrganizationView from '../views/admin/OrganizationView.vue'
import UserView from '../views/admin/UserView.vue'
import { useAuthStore } from '../stores/auth'

const ForbiddenView = {
  template: '<main class="simple-page"><h1>403</h1><p>当前账号没有访问该页面的权限。</p></main>',
}

const WorkspaceView = {
  template: '<section class="workspace-page"><h2>工作台</h2><p>业务模块建设中，当前页面用于进入认证后的 CRM 工作区。</p></section>',
}

const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/workspace' },
  { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
  { path: '/403', name: 'forbidden', component: ForbiddenView, meta: { public: true } },
  {
    path: '/',
    component: AppShell,
    children: [
      { path: 'workspace', name: 'workspace', component: WorkspaceView },
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

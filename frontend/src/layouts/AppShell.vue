<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, RouterView, useRouter } from 'vue-router'

import PermissionGate from '../components/PermissionGate.vue'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const router = useRouter()
const collapsed = ref(false)

const displayName = computed(() => auth.user?.displayName ?? auth.user?.username ?? '未登录')

const workbenchCards = [
  '年度团队目标',
  '季度团队目标',
  '月度团队目标',
  '年度个人目标',
  '季度个人目标',
  '月度个人目标',
]

async function logout() {
  await auth.logout()
  await router.push('/login')
}
</script>

<template>
  <div class="shell" :class="{ 'is-collapsed': collapsed }">
    <aside class="shell__nav">
      <div class="shell__brand">
        <span class="brand-mark">CRM</span>
        <span v-if="!collapsed" class="brand-text">客户关系管理</span>
      </div>

      <button class="nav-toggle" type="button" @click="collapsed = !collapsed">
        {{ collapsed ? '展开' : '收起' }}
      </button>

      <nav aria-label="主导航">
        <RouterLink to="/workspace">工作台</RouterLink>
        <PermissionGate permission="system:admin">
          <RouterLink to="/admin/organization-units">组织架构</RouterLink>
          <RouterLink to="/admin/users">用户管理</RouterLink>
        </PermissionGate>
      </nav>
    </aside>

    <div class="shell__main">
      <header class="shell__top">
        <el-input class="shell-search" placeholder="搜索客户、项目、联系人" disabled />
        <div class="shell__actions">
          <el-button plain disabled>通知待接入</el-button>
          <el-dropdown>
            <el-button plain>{{ displayName }}</el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <section class="workbench-grid" aria-label="绩效工作台占位">
        <article v-for="(card, index) in workbenchCards" :key="card" class="kpi-card">
          <span class="kpi-card__index">{{ String(index + 1).padStart(2, '0') }}</span>
          <h2>{{ card }}</h2>
          <p>待接入绩效模块</p>
        </article>
      </section>

      <main class="shell__content">
        <RouterView />
      </main>
    </div>
  </div>
</template>

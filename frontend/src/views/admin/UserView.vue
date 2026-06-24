<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'

import { getApiMessage, http } from '../../api/http'

interface UserSummary {
  id: string
  username: string
  displayName: string
  organizationUnitId: string
  active: boolean
  version: number
  roles: string[]
  permissions: string[]
}

interface UserPageResponse {
  content: UserSummary[]
  totalElements: number
  totalPages: number
  page: number
  size: number
}

interface OrganizationNode {
  id: string
  name: string
  children: OrganizationNode[]
}

interface RoleSummary {
  id: string
  code: string
  name: string
  systemRole: boolean
  editable: boolean
  version: number
  permissions: Array<{ code: string; name: string; description?: string }>
}

const users = ref<UserSummary[]>([])
const total = ref(0)
const organizations = ref<OrganizationNode[]>([])
const roles = ref<RoleSummary[]>([])
const loading = ref(false)
const error = ref('')
const success = ref('')
const selected = ref<UserSummary | null>(null)
const reasonVisible = ref(false)
const roleVisible = ref(false)
const createVisible = ref(false)
const pendingAction = ref<'deactivate' | 'roles'>('deactivate')
const reason = ref('')
const filters = reactive({ keyword: '', organizationUnitId: '', active: '', startDate: '', endDate: '' })
const createForm = reactive({
  username: '',
  displayName: '',
  organizationUnitId: '',
  roleCodes: ['SALES'] as string[],
})
const roleCodes = ref<string[]>(['SALES'])
const roleDisplayNames: Record<string, string> = {
  SALES: '销售',
  SALES_MANAGER: '销售主管',
  PRESALES_TECH: '售前技术',
  PROJECT_MANAGER: '项目经理',
  OPERATIONS_VIEWER: '运营管理',
  FINANCE_VIEWER: '财务查看',
  EXECUTIVE_VIEWER: '高级管理',
  SYSTEM_ADMIN: '系统管理员',
}
const roleMap = computed(() => new Map(roles.value.map((role) => [role.code, role])))
const activeUserCount = computed(() => users.value.filter((user) => user.active).length)
const disabledUserCount = computed(() => users.value.filter((user) => !user.active).length)

function flatten(nodes: OrganizationNode[], result: OrganizationNode[] = []) {
  for (const node of nodes) {
    result.push(node)
    flatten(node.children ?? [], result)
  }
  return result
}

async function loadOrganizations() {
  const response = await http.get<OrganizationNode[]>('/admin/organization-units')
  organizations.value = response.data
}

async function loadRoles() {
  const response = await http.get<RoleSummary[]>('/admin/roles')
  roles.value = response.data
}

async function loadUsers() {
  loading.value = true
  error.value = ''
  try {
    const response = await http.get<UserPageResponse>('/admin/users', {
      params: {
        keyword: filters.keyword || undefined,
        organizationUnitId: filters.organizationUnitId || undefined,
        active: filters.active === '' ? undefined : filters.active === 'true',
        page: 0,
        size: 20,
      },
    })
    users.value = response.data.content
    total.value = response.data.totalElements
  } catch (err) {
    error.value = getApiMessage(err)
  } finally {
    loading.value = false
  }
}

async function createUser() {
  error.value = ''
  if (!createForm.username.trim() || !createForm.displayName.trim() || !createForm.organizationUnitId) {
    error.value = '请输入用户名、显示名称并选择组织'
    return
  }
  try {
    await http.post('/admin/users', {
      username: createForm.username,
      displayName: createForm.displayName,
      organizationUnitId: createForm.organizationUnitId,
      roleCodes: createForm.roleCodes,
    })
    Object.assign(createForm, {
      username: '',
      displayName: '',
      organizationUnitId: '',
      roleCodes: ['SALES'],
    })
    createVisible.value = false
    success.value = '用户已创建'
    await loadUsers()
  } catch (err) {
    error.value = getApiMessage(err)
  }
}

function openCreateUser() {
  error.value = ''
  success.value = ''
  Object.assign(createForm, {
    username: '',
    displayName: '',
    organizationUnitId: '',
    roleCodes: ['SALES'],
    })
  createVisible.value = true
}

async function activateUser(user: UserSummary) {
  error.value = ''
  try {
    await http.patch(`/admin/users/${user.id}/activate`, { expectedVersion: user.version })
    success.value = '用户已启用'
    await loadUsers()
  } catch (err) {
    error.value = getApiMessage(err)
  }
}

function openDeactivate(user: UserSummary) {
  selected.value = user
  pendingAction.value = 'deactivate'
  reason.value = ''
  reasonVisible.value = true
}

function openRoles(user: UserSummary) {
  selected.value = user
  pendingAction.value = 'roles'
  roleCodes.value = [...(user.roles ?? [])]
  reason.value = ''
  roleVisible.value = true
}

function openCreateFromRow(user: UserSummary) {
  openCreateUser()
  createForm.organizationUnitId = user.organizationUnitId
}

function formatRole(code: string) {
  const role = roleMap.value.get(code)
  return role ? formatRoleName(role) : (roleDisplayNames[code] ?? code)
}

function formatRoleName(role: RoleSummary) {
  return roleDisplayNames[role.code] ?? role.name
}

async function submitReasonedAction() {
  if (!selected.value) return
  error.value = ''
  if (!reason.value.trim()) {
    error.value = '请输入变更原因'
    return
  }
  try {
    if (pendingAction.value === 'deactivate') {
      await http.patch(`/admin/users/${selected.value.id}/deactivate`, {
        expectedVersion: selected.value.version,
        reason: reason.value,
      })
      success.value = '用户已停用'
    } else {
      await http.patch(`/admin/users/${selected.value.id}/roles`, {
        roleCodes: roleCodes.value,
        expectedVersion: selected.value.version,
        reason: reason.value,
      })
      success.value = '角色已更新'
    }
    reasonVisible.value = false
    roleVisible.value = false
    await loadUsers()
  } catch (err) {
    error.value = getApiMessage(err)
  }
}

onMounted(async () => {
  try {
    await loadOrganizations()
    await loadRoles()
  } catch (err) {
    error.value = getApiMessage(err)
  }
  await loadUsers()
})
</script>

<template>
  <section class="admin-page">
    <header class="admin-page__header">
      <div>
        <p class="section-label">系统管理</p>
        <h1>用户管理</h1>
      </div>
      <div class="admin-header-actions">
        <el-button :loading="loading" @click="loadUsers">刷新</el-button>
        <el-button type="primary" @click="openCreateUser">新建用户</el-button>
      </div>
    </header>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <el-alert v-if="success" :title="success" type="success" show-icon :closable="false" />

    <section class="subject-content target-grid" aria-label="用户管理关键指标">
      <article class="target-card">
        <h2>用户总数</h2>
        <div class="target-value compact"><strong>{{ total }}</strong></div>
      </article>
      <article class="target-card">
        <h2>启用用户</h2>
        <div class="target-value compact"><strong>{{ activeUserCount }}</strong></div>
      </article>
      <article class="target-card">
        <h2>停用用户</h2>
        <div class="target-value compact"><strong>{{ disabledUserCount }}</strong></div>
      </article>
    </section>

    <el-card shadow="never" class="admin-filter">
      <el-form inline>
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" placeholder="用户名或显示名称" />
        </el-form-item>
        <el-form-item label="组织">
          <el-select v-model="filters.organizationUnitId" clearable filterable placeholder="全部组织">
            <el-option v-for="node in flatten(organizations)" :key="node.id" :label="node.name" :value="node.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.active" clearable placeholder="全部">
            <el-option label="启用" value="true" />
            <el-option label="停用" value="false" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期">
          <div class="admin-date-range">
            <input v-model="filters.startDate" type="date" aria-label="开始日期" />
            <span>至</span>
            <input v-model="filters.endDate" type="date" aria-label="结束日期" />
          </div>
        </el-form-item>
        <el-button type="primary" @click="loadUsers">搜索</el-button>
      </el-form>
    </el-card>

    <div class="admin-list-layout">
      <el-card shadow="never" class="admin-table-card">
        <template #header>用户列表（{{ total }}）</template>
        <el-table :data="users" v-loading="loading" row-key="id" class="admin-user-table">
          <el-table-column prop="username" label="用户名" min-width="140" />
          <el-table-column prop="displayName" label="显示名称" min-width="160" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.active ? 'success' : 'info'">{{ row.active ? '启用' : '停用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="角色" min-width="240">
            <template #default="{ row }">
              <div class="role-chip-list">
                <el-tag v-for="code in row.roles ?? []" :key="code" type="info" effect="plain">
                  {{ formatRole(code) }}
                </el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="420" fixed="right">
            <template #default="{ row }">
              <el-button size="small" @click="openCreateFromRow(row)">增加</el-button>
              <el-button v-if="row.active" size="small" type="danger" plain @click="openDeactivate(row)">删除</el-button>
              <el-button size="small" @click="openRoles(row)">分配角色</el-button>
              <el-button v-if="row.active" size="small" type="danger" plain @click="openDeactivate(row)">停用</el-button>
              <el-button v-else size="small" @click="activateUser(row)">启用</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>

    <el-dialog v-model="createVisible" title="新建用户" width="520px">
      <el-form label-position="top">
        <el-form-item label="用户名">
          <el-input v-model="createForm.username" />
        </el-form-item>
        <el-form-item label="显示名称">
          <el-input v-model="createForm.displayName" />
        </el-form-item>
        <el-form-item label="组织">
          <el-select v-model="createForm.organizationUnitId" filterable placeholder="选择组织">
            <el-option v-for="node in flatten(organizations)" :key="node.id" :label="node.name" :value="node.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="createForm.roleCodes" multiple filterable collapse-tags collapse-tags-tooltip placeholder="选择一个或多个角色">
            <el-option
              v-for="role in roles"
              :key="role.code"
              :label="formatRoleName(role)"
              :value="role.code"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="createUser">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="reasonVisible" title="填写原因" width="420px">
      <el-input v-model="reason" type="textarea" :rows="4" placeholder="请输入停用原因" />
      <template #footer>
        <el-button @click="reasonVisible = false">取消</el-button>
        <el-button type="primary" @click="submitReasonedAction">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="roleVisible" title="分配角色" width="460px">
      <el-form label-position="top">
        <el-form-item label="角色">
          <el-select v-model="roleCodes" multiple filterable collapse-tags collapse-tags-tooltip placeholder="选择一个或多个角色">
            <el-option
              v-for="role in roles"
              :key="role.code"
              :label="formatRoleName(role)"
              :value="role.code"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="变更原因">
          <el-input v-model="reason" type="textarea" :rows="4" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleVisible = false">取消</el-button>
        <el-button type="primary" @click="submitReasonedAction">保存</el-button>
      </template>
    </el-dialog>

  </section>
</template>

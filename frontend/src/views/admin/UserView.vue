<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'

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

const users = ref<UserSummary[]>([])
const total = ref(0)
const organizations = ref<OrganizationNode[]>([])
const loading = ref(false)
const error = ref('')
const success = ref('')
const selected = ref<UserSummary | null>(null)
const reasonVisible = ref(false)
const passwordVisible = ref(false)
const roleVisible = ref(false)
const pendingAction = ref<'deactivate' | 'roles'>('deactivate')
const reason = ref('')
const password = ref('')
const filters = reactive({ keyword: '', organizationUnitId: '', active: '' })
const createForm = reactive({
  username: '',
  displayName: '',
  initialPassword: '',
  organizationUnitId: '',
  roleCodes: 'SALES',
})
const roleCodes = ref('SALES')

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
      initialPassword: createForm.initialPassword,
      organizationUnitId: createForm.organizationUnitId,
      roleCodes: createForm.roleCodes.split(',').map((item) => item.trim()).filter(Boolean),
    })
    Object.assign(createForm, {
      username: '',
      displayName: '',
      initialPassword: '',
      organizationUnitId: '',
      roleCodes: 'SALES',
    })
    success.value = '用户已创建'
    await loadUsers()
  } catch (err) {
    error.value = getApiMessage(err)
  }
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
  roleCodes.value = (user.roles ?? []).join(',')
  reason.value = ''
  roleVisible.value = true
}

function openPassword(user: UserSummary) {
  selected.value = user
  password.value = ''
  passwordVisible.value = true
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
        roleCodes: roleCodes.value.split(',').map((item) => item.trim()).filter(Boolean),
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

async function resetPassword() {
  if (!selected.value) return
  error.value = ''
  if (password.value.length < 12) {
    error.value = '新密码至少 12 个字符'
    return
  }
  try {
    await http.patch(`/admin/users/${selected.value.id}/reset-password`, {
      password: password.value,
      expectedVersion: selected.value.version,
    })
    passwordVisible.value = false
    success.value = '密码已重置'
    await loadUsers()
  } catch (err) {
    error.value = getApiMessage(err)
  }
}

onMounted(async () => {
  try {
    await loadOrganizations()
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
      <el-button :loading="loading" @click="loadUsers">搜索</el-button>
    </header>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <el-alert v-if="success" :title="success" type="success" show-icon :closable="false" />

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
        <el-button type="primary" @click="loadUsers">搜索</el-button>
      </el-form>
    </el-card>

    <div class="admin-grid">
      <el-card shadow="never">
        <template #header>用户列表（{{ total }}）</template>
        <el-table :data="users" v-loading="loading" row-key="id">
          <el-table-column prop="username" label="用户名" />
          <el-table-column prop="displayName" label="显示名称" />
          <el-table-column label="状态">
            <template #default="{ row }">
              <el-tag :type="row.active ? 'success' : 'info'">{{ row.active ? '启用' : '停用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="角色">
            <template #default="{ row }">{{ (row.roles ?? []).join(', ') }}</template>
          </el-table-column>
          <el-table-column label="操作" width="300">
            <template #default="{ row }">
              <el-button size="small" @click="openRoles(row)">分配角色</el-button>
              <el-button size="small" @click="openPassword(row)">重置密码</el-button>
              <el-button v-if="row.active" size="small" type="danger" plain @click="openDeactivate(row)">停用</el-button>
              <el-button v-else size="small" @click="activateUser(row)">启用</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <el-card shadow="never">
        <template #header>新建用户</template>
        <el-form label-position="top">
          <el-form-item label="用户名">
            <el-input v-model="createForm.username" />
          </el-form-item>
          <el-form-item label="显示名称">
            <el-input v-model="createForm.displayName" />
          </el-form-item>
          <el-form-item label="初始密码">
            <el-input v-model="createForm.initialPassword" type="password" show-password />
          </el-form-item>
          <el-form-item label="组织">
            <el-select v-model="createForm.organizationUnitId" filterable placeholder="选择组织">
              <el-option v-for="node in flatten(organizations)" :key="node.id" :label="node.name" :value="node.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="角色编码">
            <el-input v-model="createForm.roleCodes" placeholder="多个角色用英文逗号分隔" />
          </el-form-item>
          <el-button type="primary" @click="createUser">保存</el-button>
        </el-form>
      </el-card>
    </div>

    <el-dialog v-model="reasonVisible" title="填写原因" width="420px">
      <el-input v-model="reason" type="textarea" :rows="4" placeholder="请输入停用原因" />
      <template #footer>
        <el-button @click="reasonVisible = false">取消</el-button>
        <el-button type="primary" @click="submitReasonedAction">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="roleVisible" title="分配角色" width="460px">
      <el-form label-position="top">
        <el-form-item label="角色编码">
          <el-input v-model="roleCodes" placeholder="多个角色用英文逗号分隔" />
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

    <el-dialog v-model="passwordVisible" title="重置密码" width="420px">
      <el-input v-model="password" type="password" show-password placeholder="新密码至少 12 个字符" />
      <template #footer>
        <el-button @click="passwordVisible = false">取消</el-button>
        <el-button type="primary" @click="resetPassword">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import { getApiMessage, http } from '../../api/http'

interface PermissionSummary {
  code: string
  name: string
  description?: string
}

interface RoleSummary {
  id: string
  code: string
  name: string
  systemRole: boolean
  editable: boolean
  version: number
  permissions: PermissionSummary[]
}

const roles = ref<RoleSummary[]>([])
const permissions = ref<PermissionSummary[]>([])
const selectedRoleId = ref('')
const selectedPermissionCodes = ref<string[]>([])
const roleKeyword = ref('')
const reason = ref('')
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const success = ref('')
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
const permissionDisplayNames: Record<string, string> = {
  'system:admin': '系统管理',
  'opportunity:read:own': '查看本人商机',
  'opportunity:read:department': '查看部门商机',
  'opportunity:read:assigned': '查看分配商机',
  'opportunity:technical:update': '维护商机技术信息',
  'opportunity:read:company': '查看公司商机',
  'lead:assign:department': '分配部门线索',
  'project:read:assigned': '查看分配项目',
  'performance:read:authorized': '查看授权业绩',
  'performance:read:company': '查看公司业绩',
  'contract:read:authorized': '查看授权合同',
  'payment:read:authorized': '查看授权回款',
  'financial:read:own': '查看本人财务字段',
  'financial:read:department': '查看部门财务字段',
  'financial:read:company': '查看公司财务字段',
  'business:lead:create': '新建线索',
  'business:lead:read': '查看线索',
  'business:lead:update': '编辑线索',
  'business:lead:delete': '删除线索',
  'business:account:create': '新建客户',
  'business:account:read': '查看客户',
  'business:account:update': '编辑客户',
  'business:account:delete': '删除客户',
  'business:contact:create': '新建联系人',
  'business:contact:read': '查看联系人',
  'business:contact:update': '编辑联系人',
  'business:contact:delete': '删除联系人',
  'business:opportunity:create': '新建商机',
  'business:opportunity:read': '查看商机',
  'business:opportunity:update': '编辑商机',
  'business:opportunity:delete': '删除商机',
  'business:quote:create': '新建报价',
  'business:quote:read': '查看报价',
  'business:quote:update': '编辑报价',
  'business:quote:delete': '删除报价',
  'business:contract:create': '新建合同',
  'business:contract:read': '查看合同',
  'business:contract:update': '编辑合同',
  'business:contract:delete': '删除合同',
}

const selectedRole = computed(() => roles.value.find((role) => role.id === selectedRoleId.value) ?? null)
const selectedRoleName = computed(() => (selectedRole.value ? formatRoleName(selectedRole.value) : '选择角色'))
const editableRoleCount = computed(() => roles.value.filter((role) => role.editable).length)
const protectedRoleCount = computed(() => roles.value.filter((role) => !role.editable).length)
const filteredRoles = computed(() => {
  const keyword = roleKeyword.value.trim().toLowerCase()
  if (!keyword) return roles.value
  return roles.value.filter((role) => [formatRoleName(role), role.name, role.code].join(' ').toLowerCase().includes(keyword))
})
const groupedPermissions = computed(() => {
  const groupLabels: Record<string, string> = {
    system: '系统管理',
    opportunity: '商机',
    financial: '商务/财务',
    lead: '线索',
    project: '项目',
    performance: '业绩',
    contract: '合同',
    payment: '回款',
  }
  const groups = new Map<string, PermissionSummary[]>()
  for (const permission of permissions.value) {
    const prefix = permission.code.split(':')[0] ?? 'other'
    const label = groupLabels[prefix] ?? '其他'
    groups.set(label, [...(groups.get(label) ?? []), permission])
  }
  return [...groups.entries()].map(([label, items]) => ({
    label,
    items: items.sort((a, b) => a.code.localeCompare(b.code)),
  }))
})

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [roleResponse, permissionResponse] = await Promise.all([
      http.get<RoleSummary[]>('/admin/roles'),
      http.get<PermissionSummary[]>('/admin/permissions'),
    ])
    roles.value = roleResponse.data
    permissions.value = permissionResponse.data
    if (!selectedRoleId.value && roles.value.length > 0) {
      selectRole(roles.value[0])
    } else {
      const current = selectedRole.value
      if (current) selectRole(current)
    }
  } catch (err) {
    error.value = getApiMessage(err)
  } finally {
    loading.value = false
  }
}

function selectRole(role: RoleSummary) {
  selectedRoleId.value = role.id
  selectedPermissionCodes.value = role.permissions.map((permission) => permission.code)
  reason.value = ''
  success.value = ''
  error.value = ''
}

function formatRoleName(role: RoleSummary) {
  return roleDisplayNames[role.code] ?? role.name
}

function formatPermissionName(permission: PermissionSummary) {
  return permissionDisplayNames[permission.code] ?? permission.name
}

function openRoleCreateLike(role: RoleSummary) {
  selectRole(role)
  success.value = '请基于当前角色配置权限；新增角色将在后续接入角色字典维护。'
}

function requestRoleDelete(role: RoleSummary) {
  selectRole(role)
  success.value = role.editable
    ? '当前版本暂不硬删除角色，请通过权限调整或停用用户角色分配控制使用范围。'
    : '系统保护角色不允许删除。'
}

async function savePermissions() {
  if (!selectedRole.value) return
  error.value = ''
  success.value = ''
  if (!reason.value.trim()) {
    error.value = '请输入权限变更原因'
    return
  }
  saving.value = true
  try {
    const response = await http.patch<RoleSummary>(`/admin/roles/${selectedRole.value.id}/permissions`, {
      permissionCodes: selectedPermissionCodes.value,
      expectedVersion: selectedRole.value.version,
      reason: reason.value,
    })
    roles.value = roles.value.map((role) => (role.id === response.data.id ? response.data : role))
    selectRole(response.data)
    success.value = '角色权限已更新，相关用户需要重新登录后生效'
  } catch (err) {
    error.value = getApiMessage(err)
  } finally {
    saving.value = false
  }
}

onMounted(loadData)
</script>

<template>
  <section class="admin-page role-permission-page">
    <header class="admin-page__header">
      <div>
        <p class="section-label">系统管理</p>
        <h1>角色权限</h1>
      </div>
      <div class="admin-header-actions">
        <el-button :loading="loading" @click="loadData">刷新</el-button>
        <el-button type="primary" :loading="saving" :disabled="!selectedRole?.editable" @click="savePermissions">
          保存权限
        </el-button>
      </div>
    </header>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <el-alert v-if="success" :title="success" type="success" show-icon :closable="false" />

    <section class="subject-content target-grid" aria-label="角色权限关键指标">
      <article class="target-card">
        <h2>角色总数</h2>
        <div class="target-value compact"><strong>{{ roles.length }}</strong></div>
      </article>
      <article class="target-card">
        <h2>可配置角色</h2>
        <div class="target-value compact"><strong>{{ editableRoleCount }}</strong></div>
      </article>
      <article class="target-card">
        <h2>保护角色</h2>
        <div class="target-value compact"><strong>{{ protectedRoleCount }}</strong></div>
      </article>
    </section>

    <el-card shadow="never" class="admin-filter">
      <el-form inline>
        <el-form-item label="关键词">
          <el-input v-model="roleKeyword" placeholder="角色名称或编码" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select model-value="" clearable placeholder="全部角色">
            <el-option label="全部角色" value="" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期">
          <div class="admin-date-range">
            <input type="date" aria-label="开始日期" />
            <span>至</span>
            <input type="date" aria-label="结束日期" />
          </div>
        </el-form-item>
      </el-form>
    </el-card>

    <div class="role-permission-layout">
      <el-card shadow="never" class="role-list-card">
        <template #header>角色</template>
        <article
          v-for="role in filteredRoles"
          :key="role.id"
          class="role-list-row"
          :class="{ 'is-active': selectedRoleId === role.id }"
        >
          <button class="role-list-item" type="button" @click="selectRole(role)">
            <span>{{ formatRoleName(role) }}</span>
          </button>
          <div class="record-actions">
            <button class="text-action record-action" type="button" @click="openRoleCreateLike(role)">增加</button>
            <button class="text-action record-action is-danger" type="button" @click="requestRoleDelete(role)">删除</button>
          </div>
        </article>
      </el-card>

      <el-card shadow="never" class="permission-editor-card">
        <template #header>
          <div class="permission-editor-title">
            <span>{{ selectedRoleName }}</span>
            <el-tag v-if="selectedRole && !selectedRole.editable" type="warning" effect="plain">受保护</el-tag>
          </div>
        </template>

        <el-alert
          v-if="selectedRole && !selectedRole.editable"
          title="系统管理员角色为保护角色，权限不允许在界面中修改。"
          type="warning"
          show-icon
          :closable="false"
        />

        <el-checkbox-group v-model="selectedPermissionCodes" :disabled="!selectedRole?.editable">
          <section v-for="group in groupedPermissions" :key="group.label" class="permission-group">
            <h2>{{ group.label }}</h2>
            <div class="permission-grid">
              <el-checkbox v-for="permission in group.items" :key="permission.code" :label="permission.code">
                <span>{{ formatPermissionName(permission) }}</span>
              </el-checkbox>
            </div>
          </section>
        </el-checkbox-group>

        <el-form label-position="top" class="permission-reason-form">
          <el-form-item label="变更原因">
            <el-input
              v-model="reason"
              type="textarea"
              :rows="3"
              :disabled="!selectedRole?.editable"
              placeholder="例如：项目经理需要查看项目相关财务字段"
            />
          </el-form-item>
        </el-form>
      </el-card>
    </div>
  </section>
</template>

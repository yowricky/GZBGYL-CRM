<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'

import { getApiMessage, http } from '../../api/http'

interface OrganizationNode {
  id: string
  parentId: string | null
  code: string
  name: string
  path: string
  active: boolean
  version: number
  children: OrganizationNode[]
}

const tree = ref<OrganizationNode[]>([])
const selected = ref<OrganizationNode | null>(null)
const loading = ref(false)
const error = ref('')
const success = ref('')
const reasonVisible = ref(false)
const pendingReasonAction = ref<'move' | 'deactivate'>('move')
const reason = ref('')
const createForm = reactive({ code: '', name: '', parentId: '' })
const renameForm = reactive({ name: '' })
const moveForm = reactive({ newParentId: '' })

function flattenTree(nodes: OrganizationNode[]): OrganizationNode[] {
  return nodes.flatMap((node) => [node, ...flattenTree(node.children)])
}

const flatNodes = computed(() => flattenTree(tree.value))

const moveTargets = computed(() => {
  if (!selected.value) {
    return flatNodes.value
  }
  const blockedPath = selected.value.path
  return flatNodes.value.filter((node) => node.id !== selected.value?.id && !node.path.startsWith(blockedPath))
})

function syncSelectedNode() {
  if (!selected.value) {
    return
  }

  const updated = flatNodes.value.find((node) => node.id === selected.value?.id)
  if (!updated) {
    selected.value = null
    renameForm.name = ''
    createForm.parentId = ''
    moveForm.newParentId = ''
    return
  }

  selected.value = updated
  renameForm.name = updated.name
  createForm.parentId = updated.id
  moveForm.newParentId = ''
}

function selectNode(node: OrganizationNode) {
  selected.value = node
  createForm.parentId = node.id
  renameForm.name = node.name
  moveForm.newParentId = ''
  error.value = ''
  success.value = ''
}

async function loadTree() {
  loading.value = true
  error.value = ''
  try {
    const response = await http.get<OrganizationNode[]>('/admin/organization-units')
    tree.value = response.data
    syncSelectedNode()
  } catch (err) {
    error.value = getApiMessage(err)
  } finally {
    loading.value = false
  }
}

async function createOrganization() {
  error.value = ''
  if (!createForm.code.trim() || !createForm.name.trim()) {
    error.value = '请输入组织编码和名称'
    return
  }
  try {
    await http.post('/admin/organization-units', {
      parentId: createForm.parentId || null,
      code: createForm.code,
      name: createForm.name,
    })
    createForm.code = ''
    createForm.name = ''
    success.value = '组织已创建'
    await loadTree()
  } catch (err) {
    error.value = getApiMessage(err)
  }
}

async function renameOrganization() {
  error.value = ''
  if (!selected.value) {
    error.value = '请选择组织'
    return
  }
  if (!renameForm.name.trim()) {
    error.value = '请输入组织名称'
    return
  }
  try {
    await http.patch(`/admin/organization-units/${selected.value.id}/rename`, {
      name: renameForm.name,
      expectedVersion: selected.value.version,
    })
    success.value = '组织已重命名'
    await loadTree()
  } catch (err) {
    error.value = getApiMessage(err)
  }
}

function openReason(action: 'move' | 'deactivate') {
  error.value = ''
  if (!selected.value) {
    error.value = '请选择组织'
    return
  }
  if (action === 'move' && !moveForm.newParentId) {
    error.value = '请选择新的上级组织'
    return
  }
  pendingReasonAction.value = action
  reason.value = ''
  reasonVisible.value = true
}

async function submitReasonedAction() {
  error.value = ''
  if (!selected.value) {
    error.value = '请选择组织'
    return
  }
  if (!reason.value.trim()) {
    error.value = '请输入调整原因'
    return
  }
  try {
    if (pendingReasonAction.value === 'move') {
      await http.patch(`/admin/organization-units/${selected.value.id}/move`, {
        newParentId: moveForm.newParentId,
        expectedVersion: selected.value.version,
        reason: reason.value,
      })
      success.value = '组织已移动'
    } else {
      await http.patch(`/admin/organization-units/${selected.value.id}/deactivate`, {
        expectedVersion: selected.value.version,
        reason: reason.value,
      })
      success.value = '组织已停用'
    }
    reasonVisible.value = false
    await loadTree()
  } catch (err) {
    error.value = getApiMessage(err)
  }
}

onMounted(loadTree)
</script>

<template>
  <section class="admin-page">
    <header class="admin-page__header">
      <div>
        <p class="section-label">系统管理</p>
        <h1>组织架构</h1>
      </div>
      <el-button :loading="loading" @click="loadTree">刷新</el-button>
    </header>

    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <el-alert v-if="success" :title="success" type="success" show-icon :closable="false" />

    <div class="admin-grid">
      <el-card shadow="never">
        <template #header>组织树</template>
        <el-tree
          :data="tree"
          node-key="id"
          default-expand-all
          :props="{ label: 'name', children: 'children' }"
          @node-click="selectNode"
        >
          <template #default="{ data }">
            <span class="tree-node">
              <strong>{{ data.name }}</strong>
              <span>{{ data.code }}</span>
              <el-tag v-if="!data.active" size="small" type="info">已停用</el-tag>
            </span>
          </template>
        </el-tree>
      </el-card>

      <div class="admin-stack">
        <el-card shadow="never">
          <template #header>新建组织</template>
          <el-form label-position="top">
            <el-form-item label="上级组织">
              <el-select v-model="createForm.parentId" clearable placeholder="不选择则创建根组织">
                <el-option v-for="node in flatNodes" :key="node.id" :label="node.name" :value="node.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="组织编码">
              <el-input v-model="createForm.code" />
            </el-form-item>
            <el-form-item label="组织名称">
              <el-input v-model="createForm.name" />
            </el-form-item>
            <el-button type="primary" @click="createOrganization">保存</el-button>
          </el-form>
        </el-card>

        <el-card shadow="never">
          <template #header>组织调整</template>
          <p class="muted">当前选择：{{ selected?.name ?? '未选择' }}</p>
          <el-form label-position="top">
            <el-form-item label="新名称">
              <el-input v-model="renameForm.name" />
            </el-form-item>
            <el-button @click="renameOrganization">保存名称</el-button>
            <el-form-item label="新的上级组织">
              <el-select v-model="moveForm.newParentId" filterable placeholder="选择上级组织">
                <el-option v-for="node in moveTargets" :key="node.id" :label="node.name" :value="node.id" />
              </el-select>
            </el-form-item>
            <div class="button-row">
              <el-button @click="openReason('move')">移动组织</el-button>
              <el-button type="danger" plain @click="openReason('deactivate')">停用组织</el-button>
            </div>
          </el-form>
        </el-card>
      </div>
    </div>

    <el-dialog v-model="reasonVisible" title="填写原因" width="420px">
      <el-input v-model="reason" type="textarea" :rows="4" placeholder="请输入组织调整原因" />
      <template #footer>
        <el-button @click="reasonVisible = false">取消</el-button>
        <el-button type="primary" @click="submitReasonedAction">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

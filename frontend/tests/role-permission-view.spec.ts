import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { http } from '../src/api/http'
import RolePermissionView from '../src/views/admin/RolePermissionView.vue'
import UserView from '../src/views/admin/UserView.vue'

vi.mock('../src/api/http', () => ({
  getApiMessage: (error: unknown) => (error instanceof Error ? error.message : '请求失败'),
  http: {
    get: vi.fn(),
    patch: vi.fn(),
    post: vi.fn(),
  },
}))

const roles = [
  {
    id: 'role-sales',
    code: 'SALES',
    name: 'Sales',
    systemRole: true,
    editable: true,
    version: 0,
    permissions: [{ code: 'opportunity:read:own', name: 'Read own opportunities' }],
  },
]

const permissions = [
  { code: 'opportunity:read:own', name: 'Read own opportunities' },
  { code: 'financial:read:department', name: 'Read department financial fields' },
]

function installElementStubs() {
  return {
    'el-alert': { props: ['title'], template: '<p>{{ title }}</p>' },
    'el-button': { template: '<button @click="$emit(\'click\')"><slot /></button>' },
    'el-card': { template: '<section><slot name="header" /><slot /></section>' },
    'el-checkbox': { props: ['label'], template: '<label><input type="checkbox" :value="label" /><slot /></label>' },
    'el-checkbox-group': { template: '<div><slot /></div>' },
    'el-dialog': { template: '<section><slot /><slot name="footer" /></section>' },
    'el-form': { template: '<form><slot /></form>' },
    'el-form-item': { props: ['label'], template: '<label>{{ label }}<slot /></label>' },
    'el-input': { template: '<input />' },
    'el-option': { props: ['label'], template: '<option>{{ label }}</option>' },
    'el-select': { template: '<select multiple><slot /></select>' },
    'el-table': { template: '<table><slot /></table>' },
    'el-table-column': { template: '<td></td>' },
    'el-tag': { template: '<span><slot /></span>' },
  }
}

describe('role permission administration', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(http.get).mockImplementation((url: string) => {
      if (url === '/admin/roles') return Promise.resolve({ data: roles })
      if (url === '/admin/permissions') return Promise.resolve({ data: permissions })
      if (url === '/admin/organization-units') return Promise.resolve({ data: [] })
      if (url === '/admin/users') {
        return Promise.resolve({ data: { content: [], totalElements: 0, totalPages: 0, page: 0, size: 20 } })
      }
      return Promise.reject(new Error(`Unexpected URL ${url}`))
    })
  })

  it('loads roles and permissions for role configuration', async () => {
    const wrapper = mount(RolePermissionView, {
      global: { stubs: installElementStubs() },
    })

    await flushPromises()

    expect(wrapper.text()).toContain('角色权限')
    expect(wrapper.text()).toContain('销售')
    expect(wrapper.text()).not.toContain('SALES')
    expect(wrapper.text()).toContain('查看本人商机')
    expect(wrapper.text()).toContain('查看部门财务字段')
    expect(wrapper.text()).not.toContain('Read own opportunities')
    expect(wrapper.text()).not.toContain('opportunity:read:own')
    expect(wrapper.text()).toContain('商务/财务')
  })

  it('uses role multi-selects in user management instead of role code text input', async () => {
    const wrapper = mount(UserView, {
      global: {
        directives: { loading: () => {} },
        stubs: installElementStubs(),
      },
    })

    await flushPromises()

    expect(wrapper.text()).toContain('销售')
    expect(wrapper.text()).not.toContain('SALES')
    expect(wrapper.text()).not.toContain('角色编码')
    expect(wrapper.findAll('select[multiple]').length).toBeGreaterThanOrEqual(2)
  })
})

import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'

import PermissionGate from '../src/components/PermissionGate.vue'
import { useAuthStore } from '../src/stores/auth'

const baseUser = {
  id: '5e092947-40b5-4937-b5b5-d24762819c3b',
  username: 'sales',
  displayName: 'Sales User',
  organizationUnitId: '4f68c48e-0f7d-4f6a-b840-e91fd88f20c9',
  roles: ['SALES'],
  permissions: [] as string[],
}

describe('PermissionGate', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('does not render protected content without permission', () => {
    const store = useAuthStore()
    store.user = baseUser

    const wrapper = mount(PermissionGate, {
      props: { permission: 'system:admin' },
      slots: { default: '<section>Organization Admin</section>' },
    })

    expect(wrapper.text()).not.toContain('Organization Admin')
  })

  it('renders protected content with permission', () => {
    const store = useAuthStore()
    store.user = { ...baseUser, permissions: ['system:admin'] }

    const wrapper = mount(PermissionGate, {
      props: { permission: 'system:admin' },
      slots: { default: '<section>Organization Admin</section>' },
    })

    expect(wrapper.text()).toContain('Organization Admin')
  })
})

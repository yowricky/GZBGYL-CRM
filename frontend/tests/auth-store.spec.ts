import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { authApi } from '../src/api/auth'
import { useAuthStore } from '../src/stores/auth'

vi.mock('../src/api/auth', () => ({
  authApi: {
    csrf: vi.fn(),
    login: vi.fn(),
    logout: vi.fn(),
    me: vi.fn(),
  },
}))

describe('auth store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('loads the current session and exposes permissions', async () => {
    vi.mocked(authApi.me).mockResolvedValue({
      id: '0d2f3bb2-77a1-4c93-9083-e19bc17cb510',
      username: 'admin',
      displayName: '系统管理员',
      organizationUnitId: '4f68c48e-0f7d-4f6a-b840-e91fd88f20c9',
      roles: ['ADMIN'],
      permissions: ['system:admin'],
    })

    const store = useAuthStore()

    await store.restore()

    expect(store.user?.displayName).toBe('系统管理员')
    expect(store.restored).toBe(true)
    expect(store.has('system:admin')).toBe(true)
  })
})

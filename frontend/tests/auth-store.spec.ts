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

const currentUser = {
  id: '0d2f3bb2-77a1-4c93-9083-e19bc17cb510',
  username: 'admin',
  displayName: 'System Admin',
  organizationUnitId: '4f68c48e-0f7d-4f6a-b840-e91fd88f20c9',
  roles: ['ADMIN'],
  permissions: ['system:admin'],
}

describe('auth store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('loads the current session and exposes permissions', async () => {
    vi.mocked(authApi.me).mockResolvedValue(currentUser)

    const store = useAuthStore()

    await store.restore()

    expect(store.user?.displayName).toBe('System Admin')
    expect(store.restored).toBe(true)
    expect(store.has('system:admin')).toBe(true)
  })

  it('refreshes csrf before and after login session rotation', async () => {
    vi.mocked(authApi.me).mockResolvedValue(currentUser)

    const store = useAuthStore()

    await store.login('admin', 'secret')

    expect(authApi.csrf).toHaveBeenCalledTimes(2)
    expect(authApi.login).toHaveBeenCalledWith('admin', 'secret')
    expect(store.user).toEqual(currentUser)
  })

  it('submits an empty password by default for test-stage login', async () => {
    vi.mocked(authApi.me).mockResolvedValue(currentUser)

    const store = useAuthStore()

    await store.login('admin')

    expect(authApi.login).toHaveBeenCalledWith('admin', '')
  })

  it('refreshes csrf before logout and clears local session', async () => {
    const store = useAuthStore()
    store.user = currentUser

    await store.logout()

    expect(authApi.csrf).toHaveBeenCalledTimes(1)
    expect(authApi.logout).toHaveBeenCalledTimes(1)
    expect(store.user).toBeNull()
    expect(store.restored).toBe(true)
  })

  it('clears local session even when server logout is already unauthenticated', async () => {
    vi.mocked(authApi.logout).mockRejectedValue(new Error('Authentication required'))
    const store = useAuthStore()
    store.user = currentUser

    await store.logout()

    expect(store.user).toBeNull()
    expect(store.restored).toBe(true)
  })
})

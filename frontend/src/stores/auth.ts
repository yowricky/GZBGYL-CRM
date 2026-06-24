import { defineStore } from 'pinia'

import { authApi, type CurrentUser } from '../api/auth'

interface AuthState {
  user: CurrentUser | null
  restored: boolean
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    user: null,
    restored: false,
  }),

  actions: {
    async restore() {
      try {
        this.user = await authApi.me()
      } catch {
        this.user = null
      } finally {
        this.restored = true
      }
    },

    async login(username: string, password = '') {
      await authApi.csrf()
      await authApi.login(username, password)
      await authApi.csrf()
      await this.restore()
    },

    async logout() {
      try {
        await authApi.csrf()
        await authApi.logout()
      } catch {
        // Local sign-out should still proceed when the server session has already expired.
      }
      this.user = null
      this.restored = true
    },

    has(permission: string) {
      return this.user?.permissions.includes(permission) ?? false
    },
  },
})

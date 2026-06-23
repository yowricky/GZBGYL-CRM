import { http } from './http'

export interface CurrentUser {
  id: string
  username: string
  displayName: string
  organizationUnitId: string
  roles: string[]
  permissions: string[]
}

export interface CsrfTokenResponse {
  token: string
  headerName: string
  parameterName: string
}

export const authApi = {
  async csrf(): Promise<CsrfTokenResponse> {
    const response = await http.get<CsrfTokenResponse>('/auth/csrf')
    return response.data
  },

  async login(username: string, password: string): Promise<void> {
    await http.post('/auth/login', { username, password })
  },

  async logout(): Promise<void> {
    await http.post('/auth/logout')
  },

  async me(): Promise<CurrentUser> {
    const response = await http.get<CurrentUser>('/auth/me')
    return response.data
  },
}

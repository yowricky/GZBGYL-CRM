import axios from 'axios'

export const http = axios.create({
  baseURL: '/api',
  withCredentials: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
})

const unsafeMethods = new Set(['post', 'put', 'patch', 'delete'])

http.interceptors.request.use(async (config) => {
  const method = config.method?.toLowerCase()
  if (method && unsafeMethods.has(method) && config.url !== '/auth/csrf') {
    await http.get('/auth/csrf')
  }
  return config
})

export function getApiMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as {
      message?: string
      detail?: string
      errors?: Record<string, string>
      fieldErrors?: Record<string, string>
    } | undefined
    const fieldErrors = data?.fieldErrors ?? data?.errors
    if (fieldErrors) {
      return Object.values(fieldErrors).join('；')
    }
    return data?.message ?? data?.detail ?? error.message
  }
  return error instanceof Error ? error.message : '请求失败'
}

import axios from 'axios'

export const http = axios.create({
  baseURL: '/api',
  withCredentials: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
})

export function getApiMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as { message?: string; detail?: string; errors?: Record<string, string> } | undefined
    if (data?.errors) {
      return Object.values(data.errors).join('；')
    }
    return data?.message ?? data?.detail ?? error.message
  }
  return error instanceof Error ? error.message : '请求失败'
}

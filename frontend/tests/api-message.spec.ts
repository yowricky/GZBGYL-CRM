import { describe, expect, it } from 'vitest'

import { getApiMessage } from '../src/api/http'

describe('getApiMessage', () => {
  it('reads backend validation fieldErrors', () => {
    const error = {
      isAxiosError: true,
      message: 'Request failed with status code 400',
      response: {
        data: {
          fieldErrors: {
            reason: 'Reason is required',
            expectedVersion: 'Version is required',
          },
        },
      },
    }

    expect(getApiMessage(error)).toBe('Reason is required；Version is required')
  })

  it('localizes authentication required responses', () => {
    const error = {
      isAxiosError: true,
      message: 'Request failed with status code 401',
      response: {
        status: 401,
        data: {
          code: 'AUTHENTICATION_REQUIRED',
          message: 'Authentication required',
        },
      },
    }

    expect(getApiMessage(error)).toBe('登录已失效，请重新登录')
  })
})

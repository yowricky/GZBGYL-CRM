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
})

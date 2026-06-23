import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import App from './App.vue'

describe('App', () => {
  it('renders the active route', () => {
    const wrapper = mount(App, {
      global: {
        stubs: {
          RouterView: { template: '<main>CRM foundation</main>' },
        },
      },
    })

    expect(wrapper.get('main').text()).toContain('CRM foundation')
  })
})

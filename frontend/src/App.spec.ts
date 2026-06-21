import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import App from './App.vue'

describe('App', () => {
  it('renders the CRM foundation main landmark', () => {
    const wrapper = mount(App)

    expect(wrapper.get('main').text()).toContain('CRM foundation')
  })
})

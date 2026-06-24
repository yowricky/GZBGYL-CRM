import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import AppShell from '../src/layouts/AppShell.vue'
import { useAuthStore } from '../src/stores/auth'

vi.mock('vue-router', () => ({
  RouterLink: {
    props: ['to'],
    template: '<a :href="to"><slot /></a>',
  },
  RouterView: {
    template: '<section data-testid="route-outlet">Route content</section>',
  },
  useRoute: () => ({
    path: '/workspace',
  }),
  useRouter: () => ({
    push: vi.fn(),
  }),
}))

describe('AppShell', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('renders the branded navigation, floating context panel, and AI panel', () => {
    const auth = useAuthStore()
    auth.user = {
      id: '0d2f3bb2-77a1-4c93-9083-e19bc17cb510',
      username: 'admin',
      displayName: '管理员',
      organizationUnitId: '4f68c48e-0f7d-4f6a-b840-e91fd88f20c9',
      roles: ['SYSTEM_ADMIN'],
      permissions: ['system:admin'],
    }

    const wrapper = mount(AppShell)

    expect(wrapper.get('img[alt="葛洲坝集团供应链管理有限公司"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('客户关系管理平台')
    expect(wrapper.find('.topbar-actions .primary-action').exists()).toBe(false)
    expect(wrapper.text()).toContain('工作台')
    expect(wrapper.text()).toContain('客户')
    expect(wrapper.text()).toContain('合作伙伴')
    expect(wrapper.find('.subject-nav-trigger[href="/accounts"]').exists()).toBe(true)
    expect(wrapper.find('.subject-nav-trigger[href="/partners"]').exists()).toBe(true)
    expect(wrapper.find('a[href="/partners"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('商机')
    expect(wrapper.text()).toContain('项目')
    expect(wrapper.text()).toContain('项目跟进')
    expect(wrapper.text()).toContain('待办与活动')
    expect(wrapper.text()).toContain('合同与回款')
    expect(wrapper.find('.admin-nav-menu').text()).toContain('Admin')
    expect(wrapper.find('.admin-nav-menu').text()).toContain('组织架构')
    expect(wrapper.find('.admin-nav-menu').text()).toContain('用户管理')
    expect(wrapper.find('.admin-nav-menu').text()).not.toContain('商机')
    expect(wrapper.text()).toContain('更多信息')
    expect(wrapper.text()).toContain('处理重点')
    expect(wrapper.text()).toContain('项目进度需要按节点跟踪')
    expect(wrapper.text()).toContain('AI 项目助手')
    expect(wrapper.text()).toContain('生成本周跟进计划')
    expect(wrapper.get('[data-testid="route-outlet"]').exists()).toBe(true)
  })
})

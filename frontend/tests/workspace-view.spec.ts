import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import WorkspaceView from '../src/views/WorkspaceView.vue'

describe('WorkspaceView', () => {
  it('renders the CRM operating workspace with brand-aligned metrics', () => {
    const wrapper = mount(WorkspaceView)

    expect(wrapper.text()).not.toContain('数据口径')
    expect(wrapper.text()).not.toContain('经营驾驶舱')
    expect(wrapper.text()).toContain('年度团队目标')
    expect(wrapper.text()).toContain('季度团队目标')
    expect(wrapper.text()).toContain('团队与个人业绩')
    expect(wrapper.text()).toContain('目标完成情况')
    expect(wrapper.text()).toContain('今日必须处理')
    expect(wrapper.text()).toContain('商机阶段')
    expect(wrapper.text()).toContain('报价单等待客户确认')
    expect(wrapper.findAll('details.subject-card').length).toBe(3)
  })
})

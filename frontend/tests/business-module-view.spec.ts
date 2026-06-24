import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import BusinessModuleView from '../src/views/BusinessModuleView.vue'

describe('BusinessModuleView', () => {
  it('keeps module pages ordered by metrics, filters, then the subject list', () => {
    const wrapper = mount(BusinessModuleView, {
      props: { moduleKind: 'accounts' },
    })

    const isBefore = (first: Element, second: Element) =>
      Boolean(first.compareDocumentPosition(second) & Node.DOCUMENT_POSITION_FOLLOWING)

    const metrics = wrapper.get('.target-grid').element
    const toolbar = wrapper.get('.business-toolbar').element
    const subjectList = wrapper.get('.record-list').element

    expect(isBefore(metrics, toolbar)).toBe(true)
    expect(isBefore(toolbar, subjectList)).toBe(true)
    expect(wrapper.find('.action-list').exists()).toBe(false)
  })

  it('renders the opportunity module with clear CRM sections', () => {
    const wrapper = mount(BusinessModuleView, {
      props: { moduleKind: 'opportunities' },
    })

    expect(wrapper.text()).toContain('商机')
    expect(wrapper.text()).toContain('待识别线索')
    expect(wrapper.text()).toContain('待转项目')
    expect(wrapper.text()).toContain('区域供应链数字化咨询')
    expect(wrapper.text()).not.toContain('项目销售闭环')
    expect(wrapper.text()).not.toContain('数据口径：项目销售闭环')
    expect(wrapper.text()).not.toContain('选择统计周期')
    expect(wrapper.findAll('details.subject-card').length).toBe(2)
  })

  it('renders projects as the follow-up process management module', () => {
    const wrapper = mount(BusinessModuleView, {
      props: { moduleKind: 'projects' },
    })

    expect(wrapper.text()).toContain('项目')
    expect(wrapper.text()).toContain('跟进中项目')
    expect(wrapper.text()).toContain('阶段延迟')
    expect(wrapper.text()).not.toContain('项目进度需要按节点跟踪')
    expect(wrapper.text()).toContain('华中区域供应链协同平台')
  })

  it('filters records by keyword and status', async () => {
    const wrapper = mount(BusinessModuleView, {
      props: { moduleKind: 'contracts' },
    })

    expect(wrapper.get('[data-testid="business-date"]').attributes('type')).toBe('date')

    await wrapper.get('[data-testid="business-search"]').setValue('长江')

    expect(wrapper.text()).toContain('长江生态材料项目合同')
    expect(wrapper.text()).not.toContain('供应链协同服务合同')

    await wrapper.get('[data-testid="business-search"]').setValue('')
    await wrapper.get('[data-testid="business-status"]').setValue('风险')

    expect(wrapper.text()).toContain('长江生态材料项目合同')
    expect(wrapper.text()).not.toContain('仓储系统升级服务合同')
  })

  it('creates a new local business record from the module action', async () => {
    const wrapper = mount(BusinessModuleView, {
      props: { moduleKind: 'leads' },
    })

    await wrapper.get('[data-testid="business-create"]').trigger('click')
    await wrapper.get('[data-testid="record-name"]').setValue('测试线索')
    await wrapper.get('[data-testid="record-owner"]').setValue('销售测试')
    await wrapper.get('[data-testid="record-stage"]').setValue('初步接触')
    await wrapper.get('[data-testid="record-amount"]').setValue('预计 50 万')
    await wrapper.get('[data-testid="record-status"]').setValue('新建')
    await wrapper.get('[data-testid="record-save"]').trigger('click')

    expect(wrapper.text()).toContain('测试线索')
    expect(wrapper.text()).toContain('销售测试')
    expect(wrapper.text()).toContain('记录已新增')
  })

  it('supports adding and deleting records from the subject list actions', async () => {
    const wrapper = mount(BusinessModuleView, {
      props: { moduleKind: 'projects' },
    })

    expect(wrapper.findAll('[data-testid="record-add"]').length).toBe(3)
    expect(wrapper.findAll('[data-testid="record-delete"]').length).toBe(3)

    await wrapper.findAll('[data-testid="record-add"]')[0].trigger('click')
    expect(wrapper.get('[data-testid="record-name"]').exists()).toBe(true)

    await wrapper.findAll('[data-testid="record-delete"]')[0].trigger('click')

    expect(wrapper.text()).not.toContain('华中区域供应链协同平台')
    expect(wrapper.text()).toContain('记录已删除')
    expect(wrapper.findAll('[data-testid="record-delete"]').length).toBe(2)
  })

  it('uses detail actions instead of add actions for customer and partner lists', async () => {
    const customers = mount(BusinessModuleView, {
      props: { moduleKind: 'accounts' },
    })
    const partners = mount(BusinessModuleView, {
      props: { moduleKind: 'partners' },
    })

    expect(customers.findAll('[data-testid="record-add"]').length).toBe(0)
    expect(customers.findAll('[data-testid="record-detail"]').length).toBe(3)
    expect(partners.findAll('[data-testid="record-add"]').length).toBe(0)
    expect(partners.findAll('[data-testid="record-detail"]').length).toBe(3)

    await partners.findAll('[data-testid="record-detail"]')[1].trigger('click')

    expect(partners.findAll('.record-row')[1].classes()).toContain('is-selected')
    expect(partners.find('.object-detail-card').text()).toContain('供应链软件服务商')
  })

  it('shows customer maintenance fields from the project registration workbook', () => {
    const wrapper = mount(BusinessModuleView, {
      props: { moduleKind: 'accounts' },
    })

    expect(wrapper.text()).toContain('客户维护信息')
    expect(wrapper.text()).toContain('公司简介')
    expect(wrapper.text()).toContain('组织架构')
    expect(wrapper.text()).toContain('决策模式分析')
    expect(wrapper.text()).toContain('竞争对手')
    expect(wrapper.text()).toContain('支持者')
    expect(wrapper.text()).toContain('行动目标及规划')
    expect(wrapper.text()).toContain('关联列表')
    expect(wrapper.text()).toContain('联系人角色')
    expect(wrapper.text()).toContain('关联业务')
  })

  it('renders partners as an independent Salesforce-style object module', () => {
    const wrapper = mount(BusinessModuleView, {
      props: { moduleKind: 'partners' },
    })

    expect(wrapper.text()).toContain('合作伙伴')
    expect(wrapper.text()).toContain('华中系统集成伙伴')
    expect(wrapper.text()).toContain('伙伴能力')
    expect(wrapper.text()).toContain('协同业务')
    expect(wrapper.text()).not.toContain('客户维护信息')
  })

  it('shows object details for customer and partner records', async () => {
    const wrapper = mount(BusinessModuleView, {
      props: { moduleKind: 'partners' },
    })

    expect(wrapper.find('.object-detail-card').exists()).toBe(true)
    expect(wrapper.find('.object-detail-fields').exists()).toBe(true)
    expect(wrapper.findAll('.object-detail-activity article').length).toBe(3)
    expect(wrapper.findAll('.record-row')[0].classes()).toContain('is-selected')

    await wrapper.findAll('.record-row')[1].trigger('click')

    expect(wrapper.findAll('.record-row')[1].classes()).toContain('is-selected')
    expect(wrapper.find('.object-detail-card').text()).toContain('供应链软件服务商')
  })

  it('keeps project pages focused on lists without object detail panels', () => {
    const wrapper = mount(BusinessModuleView, {
      props: { moduleKind: 'projects' },
    })

    expect(wrapper.find('.object-detail-card').exists()).toBe(false)
  })

  it('uses all customer basic fields when creating a customer', async () => {
    const wrapper = mount(BusinessModuleView, {
      props: { moduleKind: 'accounts' },
    })

    await wrapper.get('[data-testid="business-create"]').trigger('click')

    const expectedFields = [
      'customer-customerName',
      'customer-organizationCode',
      'customer-companyProfile',
      'customer-industryNature',
      'customer-establishedAt',
      'customer-website',
      'customer-chairman',
      'customer-generalManager',
      'customer-parentGroup',
      'customer-registeredCapital',
      'customer-lastYearRevenue',
      'customer-employeeCount',
      'customer-organizationStructure',
      'customer-otherNotes',
    ]

    for (const testId of expectedFields) {
      expect(wrapper.get(`[data-testid="${testId}"]`).exists()).toBe(true)
    }

    await wrapper.get('[data-testid="customer-customerName"]').setValue('测试客户有限公司')
    await wrapper.get('[data-testid="customer-organizationCode"]').setValue('91430100TEST000001')
    await wrapper.get('[data-testid="customer-industryNature"]').setValue('能源')
    await wrapper.get('[data-testid="customer-parentGroup"]').setValue('测试集团')
    await wrapper.get('[data-testid="customer-lastYearRevenue"]').setValue('5000 万')
    await wrapper.get('[data-testid="record-save"]').trigger('click')

    expect(wrapper.text()).toContain('测试客户有限公司')
    expect(wrapper.text()).toContain('行业：能源')
    expect(wrapper.text()).toContain('代码：91430100TEST000001')
    expect(wrapper.text()).toContain('客户已新增')
  })
})

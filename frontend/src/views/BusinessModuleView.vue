<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'

type ModuleKind = 'reports' | 'leads' | 'accounts' | 'partners' | 'opportunities' | 'projects' | 'quotes' | 'contracts' | 'activities'

interface ModuleRecord {
  name: string
  meta: string
  owner: string
  stage: string
  amount: string
  status: string
  tone?: 'risk' | 'ok' | 'blue'
}

interface ModuleConfig {
  title: string
  category: string
  summary: string
  primaryAction: string
  metrics: Array<{ label: string; value: string; note: string; progress: number; risk?: boolean }>
  focus: Array<{ title: string; meta: string; tag: string; tone?: 'risk' | 'ok' | 'blue' }>
  records: ModuleRecord[]
  related?: Array<{ title: string; note: string; items: Array<{ label: string; value: string; tone?: 'risk' | 'ok' | 'blue' }> }>
}

interface MaintenanceSection {
  title: string
  note: string
  fields: string[]
}

interface CustomerFormField {
  key: keyof CustomerForm
  label: string
  type?: 'text' | 'date' | 'textarea'
  placeholder?: string
}

interface CustomerForm {
  customerName: string
  organizationCode: string
  companyProfile: string
  industryNature: string
  establishedAt: string
  website: string
  chairman: string
  generalManager: string
  parentGroup: string
  registeredCapital: string
  lastYearRevenue: string
  employeeCount: string
  organizationStructure: string
  otherNotes: string
}

const props = defineProps<{
  moduleKind: ModuleKind
}>()

const customerMaintenanceSections: MaintenanceSection[] = [
  {
    title: '客户基础信息',
    note: '来自附件“客户情况摘要”，用于形成客户档案主数据。',
    fields: ['客户名称', '组织机构代码', '公司简介', '行业性质', '成立时间', '官网', '董事长', '总经理', '所属集团', '注册资本', '上一年营业额', '员工人数', '组织架构', '其他说明'],
  },
  {
    title: '关联项目情况',
    note: '记录客户相关项目的销售、售前、预算和关键推进节点。',
    fields: ['销售经理', '售前支持', '项目类型', '预计签约时间', '项目需求', '项目进度', '决策模式分析', '项目必上的理由', '项目预算', '预算清单', '项目小组', '复盘记录'],
  },
  {
    title: '销售地图',
    note: '把“知彼、知己、知客户”沉淀为可维护结构。',
    fields: ['竞争对手', '对方销售经理', '对方方案经理', '竞争方案', '竞争优势', '竞争弱势', '竞争关系', '竞争策略', '竞争潜在危险', '我方方案', '合作伙伴方案', '我方优势', '我方弱势', '我方策略'],
  },
  {
    title: '客户关系与态度',
    note: '记录支持者、中立者、反对者及其影响原因和转化策略。',
    fields: ['支持者', '中立者', '反对者', '姓名', '职位', '角色', '原因', '策略', '效果'],
  },
  {
    title: '行动计划与执行',
    note: '跟踪计划、执行、资源投入和执行成本。',
    fields: ['行动目标及规划', '负责人', '资源', '开始时间', '结束时间', '是否执行', '实际开始时间', '实际结束时间', '差旅成本', '人工成本', '办公及杂项', '小计'],
  },
]

const modules: Record<ModuleKind, ModuleConfig> = {
  reports: {
    title: '报表分析',
    category: '经营驾驶舱',
    summary: '按团队、个人、阶段、行业和回款口径查看经营结果。',
    primaryAction: '导出报表',
    metrics: [
      { label: '本月确认收入', value: '410 万', note: '较目标差 90 万', progress: 82, risk: true },
      { label: '季度预测收入', value: '2,226 万', note: '完成季度目标 74.2%', progress: 74.2 },
      { label: '年度预测收入', value: '8,208 万', note: '完成年度目标 68.4%', progress: 68.4 },
    ],
    focus: [
      { title: '报价转合同周期偏长', meta: '投标/报价阶段平均停留 18 天，需要主管介入推动。', tag: '阶段风险', tone: 'risk' },
      { title: '回款复核事项集中', meta: '5 个合同存在回款节点需要财务确认。', tag: '待复核', tone: 'blue' },
      { title: '重点行业增长稳定', meta: '供应链协同服务类项目本季贡献较高。', tag: '增长', tone: 'ok' },
    ],
    records: [
      { name: '团队月度经营看板', meta: '销售团队 / 2026 年 06 月', owner: '运营管理', stage: '月报', amount: '410 万', status: '已更新', tone: 'ok' },
      { name: '商机阶段转化分析', meta: '初步接触至商务谈判', owner: '销售主管', stage: '阶段分析', amount: '21 项', status: '需查看', tone: 'blue' },
      { name: '合同回款风险清单', meta: '合同与财务协同', owner: '财务', stage: '回款分析', amount: '5 项', status: '待复核', tone: 'risk' },
    ],
  },
  leads: {
    title: '线索',
    category: '客户增长',
    summary: '统一管理市场、伙伴、销售自拓等来源线索，并推进到客户或商机。',
    primaryAction: '新建线索',
    metrics: [
      { label: '开放线索', value: '12 个', note: '其中 4 个超过 7 天未跟进', progress: 66, risk: true },
      { label: '本月转化', value: '5 个', note: '转客户 3 个，转商机 2 个', progress: 42 },
      { label: '有效来源', value: '4 类', note: '伙伴推荐贡献最高', progress: 70 },
    ],
    focus: [
      { title: '伙伴推荐线索需补充预算', meta: '缺少采购周期和预算负责人信息。', tag: '待补齐', tone: 'risk' },
      { title: '本周应优先清理存量线索', meta: '建议先处理超过 7 天未跟进的线索。', tag: '跟进', tone: 'blue' },
      { title: '高质量线索来源稳定', meta: '系统集成伙伴推荐线索转化率较高。', tag: '有效', tone: 'ok' },
    ],
    records: [
      { name: '区域供应链数字化咨询', meta: '伙伴推荐 / 华中区域', owner: '李明', stage: '待确认需求', amount: '预计 180 万', status: '高意向', tone: 'blue' },
      { name: '仓储系统升级咨询', meta: '销售自拓 / 制造客户', owner: '王珊', stage: '初步沟通', amount: '预计 90 万', status: '待跟进', tone: 'risk' },
      { name: '项目协同平台试用', meta: '市场活动 / 央企客户', owner: '赵宁', stage: '已转客户', amount: '预计 120 万', status: '已转化', tone: 'ok' },
    ],
  },
  accounts: {
    title: '客户与联系人',
    category: '客户增长',
    summary: '沉淀客户档案、组织关系、联系人角色和合作历史。',
    primaryAction: '新建客户',
    metrics: [
      { label: '客户总数', value: '48 家', note: '重点客户 16 家', progress: 72 },
      { label: '活跃联系人', value: '126 位', note: '本月新增 18 位', progress: 76 },
      { label: '资料完整度', value: '81%', note: '部分客户缺少决策链', progress: 81 },
    ],
    focus: [
      { title: '重点客户决策链未完整', meta: '需要补充技术、采购、财务等角色。', tag: '资料', tone: 'blue' },
      { title: '沉默客户需要唤醒', meta: '9 家客户 30 天内无跟进记录。', tag: '风险', tone: 'risk' },
      { title: '集团客户合作记录清晰', meta: '合同、回款、项目记录已形成闭环。', tag: '完整', tone: 'ok' },
    ],
    records: [
      { name: '长江生态材料有限公司', meta: '重点客户 / 制造与供应链', owner: '张华', stage: '合作中', amount: '286 万', status: '活跃', tone: 'ok' },
      { name: '华中区域建设单位', meta: '集团客户 / 系统集成', owner: '李明', stage: '需求沟通', amount: '待评估', status: '跟进中', tone: 'blue' },
      { name: '新能源仓储服务公司', meta: '潜在客户 / 仓储物流', owner: '王珊', stage: '待唤醒', amount: '90 万', status: '沉默', tone: 'risk' },
    ],
  },
  partners: {
    title: '合作伙伴',
    category: '渠道与生态',
    summary: '沉淀系统集成商、软件厂商、咨询服务商等合作伙伴档案，管理推荐线索、联合项目和协同活动。',
    primaryAction: '新建合作伙伴',
    metrics: [
      { label: '合作伙伴总数', value: '32 家', note: '核心伙伴 8 家', progress: 72 },
      { label: '本月推荐线索', value: '7 条', note: '其中 3 条已进入商机识别', progress: 58 },
      { label: '联合项目', value: '11 项', note: '4 项需要明确分工边界', progress: 66, risk: true },
    ],
    focus: [
      { title: '核心伙伴需要建立分级维护机制', meta: '按线索质量、项目协同能力、交付资源和结算记录划分伙伴层级。', tag: '分级', tone: 'blue' },
      { title: '伙伴推荐线索需补齐客户角色', meta: '推荐线索进入商机前，需要明确客户需求提出人、预算负责人和采购联系人。', tag: '补齐', tone: 'risk' },
      { title: '联合项目协同边界要前置确认', meta: '在方案交流前明确我方、伙伴、客户三方职责和收益口径。', tag: '协同', tone: 'ok' },
    ],
    records: [
      { name: '华中系统集成伙伴', meta: '核心伙伴 / 政企系统集成', owner: '李明', stage: '联合拓展', amount: '推荐 7 条线索', status: '活跃', tone: 'ok' },
      { name: '供应链软件服务商', meta: '软件厂商 / 仓储与协同平台', owner: '赵宁', stage: '方案共创', amount: '联合项目 3 项', status: '协同中', tone: 'blue' },
      { name: '区域咨询服务机构', meta: '咨询伙伴 / 数字化规划', owner: '王纪', stage: '待复盘', amount: '结算待确认', status: '风险', tone: 'risk' },
    ],
    related: [
      {
        title: '伙伴能力',
        note: '沉淀资质、覆盖区域、交付资源和行业经验。',
        items: [
          { label: '具备交付团队', value: '18 家', tone: 'ok' },
          { label: '覆盖重点区域', value: '11 家', tone: 'blue' },
          { label: '资质待补齐', value: '5 家', tone: 'risk' },
        ],
      },
      {
        title: '协同业务',
        note: '聚合伙伴推荐线索、联合项目、报价协同和结算事项。',
        items: [
          { label: '推荐线索', value: '12 条', tone: 'blue' },
          { label: '联合项目', value: '11 项', tone: 'ok' },
          { label: '结算待确认', value: '2 项', tone: 'risk' },
        ],
      },
    ],
  },
  opportunities: {
    title: '商机',
    category: '线索管理',
    summary: '管理线索来源、客户意向、需求确认和转项目判断。',
    primaryAction: '新建商机',
    metrics: [
      { label: '待识别线索', value: '18 条', note: '伙伴推荐 7 条', progress: 68 },
      { label: '高意向商机', value: '9 项', note: '已明确客户需求', progress: 64 },
      { label: '待转项目', value: '4 项', note: '需确认预算和决策链', progress: 48, risk: true },
    ],
    focus: [
      { title: '伙伴推荐线索需要确认客户角色', meta: '补齐需求提出人、预算负责人和采购联系人后再转入项目。', tag: '线索补齐', tone: 'risk' },
      { title: '高意向商机需要明确转项目条件', meta: '预算、时间窗口、客户决策链满足后进入项目跟进。', tag: '转项目', tone: 'blue' },
      { title: '线索来源需要区分市场、伙伴和销售自拓', meta: '不同来源使用不同跟进节奏和转化口径。', tag: '来源', tone: 'ok' },
    ],
    records: [
      { name: '区域供应链数字化咨询', meta: '伙伴推荐 / 待确认预算', owner: '李明', stage: '需求识别', amount: '预计 180 万', status: '高意向', tone: 'blue' },
      { name: '仓储系统升级咨询', meta: '销售自拓 / 待确认采购周期', owner: '王珊', stage: '线索培育', amount: '预计 90 万', status: '待跟进', tone: 'risk' },
      { name: '项目协同平台试用', meta: '市场活动 / 可转入项目', owner: '赵宁', stage: '转项目评估', amount: '预计 120 万', status: '可转项目', tone: 'ok' },
    ],
  },
  projects: {
    title: '项目',
    category: '跟进过程管理',
    summary: '管理项目立项后的阶段推进、行动计划、报价、合同和回款协同。',
    primaryAction: '新建项目',
    metrics: [
      { label: '跟进中项目', value: '16 项', note: '重点项目 6 项', progress: 72 },
      { label: '阶段延迟', value: '4 项', note: '需主管或项目经理介入', progress: 36, risk: true },
      { label: '本月可推进', value: '410 万', note: '依赖报价确认和回款复核', progress: 82 },
    ],
    focus: [
      { title: '项目进度需要按节点跟踪', meta: '立项、预算、招采、合同、到货、验收等节点应形成过程记录。', tag: '进度', tone: 'blue' },
      { title: '行动计划执行成本需要沉淀', meta: '差旅、人工、办公及杂项应跟随行动计划记录。', tag: '成本', tone: 'risk' },
      { title: '项目经理、运营管理和财务共同查看', meta: '报价、合同、回款字段在项目视角形成协同。', tag: '协同', tone: 'ok' },
    ],
    records: [
      { name: '华中区域供应链协同平台', meta: '方案交流 / 14 天未更新', owner: '李明', stage: '方案交流', amount: '360 万', status: '高风险', tone: 'risk' },
      { name: '长江生态材料报价项目', meta: '投标/报价 / 等待客户确认', owner: '张华', stage: '投标/报价', amount: '286 万', status: '待报价', tone: 'blue' },
      { name: '供应链协同服务年度合同', meta: '商务谈判 / 本月可确认', owner: '赵宁', stage: '商务谈判', amount: '120 万', status: '可推进', tone: 'ok' },
    ],
  },
  quotes: {
    title: '报价',
    category: '项目销售闭环',
    summary: '管理报价版本、审批状态、客户确认和转合同进度。',
    primaryAction: '新建报价',
    metrics: [
      { label: '进行中报价', value: '8 份', note: '2 份超过承诺时间', progress: 64, risk: true },
      { label: '待客户确认', value: '4 份', note: '预计金额 622 万', progress: 50 },
      { label: '已转合同', value: '3 份', note: '本月转化稳定', progress: 76 },
    ],
    focus: [
      { title: '报价说明不完整', meta: '部分报价缺少交付周期和付款条件。', tag: '待补充', tone: 'risk' },
      { title: '审批口径需要统一', meta: '高金额报价建议增加运营复核。', tag: '审批', tone: 'blue' },
      { title: '报价转合同链路清晰', meta: '已确认报价可直接进入合同草拟。', tag: '转合同', tone: 'ok' },
    ],
    records: [
      { name: '长江生态材料报价单 V2', meta: '等待客户确认 / 补充交付周期', owner: '张华', stage: '客户确认', amount: '286 万', status: '待确认', tone: 'blue' },
      { name: '华中区域平台服务报价', meta: '内部审批 / 付款条款待复核', owner: '李明', stage: '审批中', amount: '360 万', status: '需复核', tone: 'risk' },
      { name: '协同服务年度报价', meta: '已确认 / 准备转合同', owner: '赵宁', stage: '已确认', amount: '120 万', status: '可转合同', tone: 'ok' },
    ],
  },
  contracts: {
    title: '合同与回款',
    category: '项目销售闭环',
    summary: '跟踪合同签署、履约节点、发票、回款计划和财务复核。',
    primaryAction: '新建合同',
    metrics: [
      { label: '进行中合同', value: '5 份', note: '2 份存在回款风险', progress: 68, risk: true },
      { label: '计划回款', value: '520 万', note: '本月需确认 120 万', progress: 73 },
      { label: '已确认回款', value: '410 万', note: '完成月目标 82%', progress: 82 },
    ],
    focus: [
      { title: '回款节点需项目经理确认', meta: '项目经理、运营管理、财务共同查看关键字段。', tag: '协同', tone: 'blue' },
      { title: '发票信息待补充', meta: '2 份合同缺少完整开票信息。', tag: '风险', tone: 'risk' },
      { title: '已签合同履约稳定', meta: '当前无重大交付异常。', tag: '正常', tone: 'ok' },
    ],
    records: [
      { name: '供应链协同服务合同', meta: '计划回款 120 万 / 财务复核', owner: '赵宁', stage: '履约中', amount: '120 万', status: '待复核', tone: 'blue' },
      { name: '长江生态材料项目合同', meta: '发票信息待补充', owner: '张华', stage: '签署中', amount: '286 万', status: '风险', tone: 'risk' },
      { name: '仓储系统升级服务合同', meta: '已签署 / 正常履约', owner: '王珊', stage: '履约中', amount: '90 万', status: '正常', tone: 'ok' },
    ],
  },
  activities: {
    title: '待办与活动',
    category: '项目销售闭环',
    summary: '聚合电话、会议、任务、报价、合同回款等待处理事项。',
    primaryAction: '新建待办',
    metrics: [
      { label: '今日待办', value: '9 项', note: '3 项影响本月目标', progress: 64, risk: true },
      { label: '逾期事项', value: '2 项', note: '均来自报价确认', progress: 32, risk: true },
      { label: '已完成', value: '14 项', note: '本周完成率 78%', progress: 78 },
    ],
    focus: [
      { title: '今日优先处理报价确认', meta: '报价确认直接影响本月可确认收入。', tag: '优先', tone: 'risk' },
      { title: '会议纪要需要同步到商机', meta: '客户反馈应沉淀到对应阶段记录。', tag: '记录', tone: 'blue' },
      { title: '任务分派清晰', meta: '销售、项目经理、运营管理可按职责协同处理。', tag: '协同', tone: 'ok' },
    ],
    records: [
      { name: '确认长江生态材料报价反馈', meta: '今天 16:00 前 / 客户采购负责人', owner: '张华', stage: '报价确认', amount: '286 万', status: '优先', tone: 'risk' },
      { name: '整理华中区域方案会议纪要', meta: '同步技术方案反馈', owner: '李明', stage: '方案交流', amount: '360 万', status: '进行中', tone: 'blue' },
      { name: '复核供应链协同合同回款', meta: '财务与项目经理共同确认', owner: '赵宁', stage: '回款复核', amount: '120 万', status: '待复核', tone: 'ok' },
    ],
  },
}

const config = computed(() => modules[props.moduleKind])
const records = ref<ModuleRecord[]>([])
const selectedRecord = ref<ModuleRecord | null>(null)
const search = ref('')
const status = ref('')
const dateRange = ref('')
const createVisible = ref(false)
const success = ref('')
const error = ref('')
const recordForm = reactive({
  name: '',
  meta: '',
  owner: '',
  stage: '',
  amount: '',
  status: '',
})
const customerForm = reactive<CustomerForm>({
  customerName: '',
  organizationCode: '',
  companyProfile: '',
  industryNature: '',
  establishedAt: '',
  website: '',
  chairman: '',
  generalManager: '',
  parentGroup: '',
  registeredCapital: '',
  lastYearRevenue: '',
  employeeCount: '',
  organizationStructure: '',
  otherNotes: '',
})

const customerBasicFields: CustomerFormField[] = [
  { key: 'customerName', label: '客户名称', placeholder: '请输入客户全称' },
  { key: 'organizationCode', label: '组织机构代码', placeholder: '统一社会信用代码或组织机构代码' },
  { key: 'companyProfile', label: '公司简介', type: 'textarea', placeholder: '主营业务、行业地位、合作背景等' },
  { key: 'industryNature', label: '行业性质', placeholder: '例如：民航、能源、制造' },
  { key: 'establishedAt', label: '成立时间', type: 'date' },
  { key: 'website', label: '官网', placeholder: 'https://example.com' },
  { key: 'chairman', label: '董事长' },
  { key: 'generalManager', label: '总经理' },
  { key: 'parentGroup', label: '所属集团' },
  { key: 'registeredCapital', label: '注册资本' },
  { key: 'lastYearRevenue', label: '上一年营业额' },
  { key: 'employeeCount', label: '员工人数' },
  { key: 'organizationStructure', label: '组织架构', type: 'textarea', placeholder: '关键部门、决策链、上下级关系等' },
  { key: 'otherNotes', label: '其他说明', type: 'textarea' },
]

const accountRelatedSections = [
  {
    title: '联系人角色',
    note: '记录决策人、影响人、采购和财务联系人。',
    items: [
      { label: '关键联系人', value: '126 位', tone: 'blue' as const },
      { label: '决策链完整客户', value: '31 家', tone: 'ok' as const },
      { label: '待补充角色', value: '9 家', tone: 'risk' as const },
    ],
  },
  {
    title: '关联业务',
    note: '按客户对象聚合商机、项目、报价、合同与回款。',
    items: [
      { label: '跟进中项目', value: '16 项', tone: 'blue' as const },
      { label: '待确认报价', value: '4 份', tone: 'risk' as const },
      { label: '履约中合同', value: '5 份', tone: 'ok' as const },
    ],
  },
]

const relatedSections = computed(() => config.value.related ?? (props.moduleKind === 'accounts' ? accountRelatedSections : []))
const hasObjectDetail = computed(() => props.moduleKind === 'accounts' || props.moduleKind === 'partners')
const detailActivities = computed(() => {
  if (props.moduleKind === 'partners') {
    return [
      { title: '推荐线索复核', meta: '确认推荐客户的预算负责人和采购联系人。' },
      { title: '联合方案会议', meta: '同步伙伴资源、我方交付边界和客户下一步安排。' },
      { title: '结算事项确认', meta: '核对当前联合项目的返点与结算口径。' },
    ]
  }
  return [
    { title: '客户拜访记录', meta: '补充本周沟通纪要、客户关注点和下一步责任人。' },
    { title: '决策链维护', meta: '确认技术、采购、财务和业务负责人是否完整。' },
    { title: '关联项目复盘', meta: '同步商机、报价、合同和回款的最新状态。' },
  ]
})

function resetRecords() {
  records.value = modules[props.moduleKind].records.map((record) => ({ ...record }))
  selectedRecord.value = records.value[0] ?? null
  search.value = ''
  status.value = ''
  dateRange.value = ''
  createVisible.value = false
  success.value = ''
  error.value = ''
}

watch(() => props.moduleKind, resetRecords, { immediate: true })

const statusOptions = computed(() => [...new Set(records.value.map((record) => record.status))])
const filteredRecords = computed(() => {
  const keyword = search.value.trim().toLowerCase()
  return records.value.filter((record) => {
    const matchesKeyword = !keyword || [record.name, record.meta, record.owner, record.stage, record.amount, record.status]
      .join(' ')
      .toLowerCase()
      .includes(keyword)
    const matchesStatus = !status.value || record.status === status.value
    return matchesKeyword && matchesStatus
  })
})

function openCreate() {
  Object.assign(recordForm, {
    name: '',
    meta: '',
    owner: '',
    stage: '',
    amount: '',
    status: '',
  })
  resetCustomerForm()
  error.value = ''
  success.value = ''
  createVisible.value = true
}

function selectRecord(record: ModuleRecord) {
  selectedRecord.value = record
}

function deleteRecord(recordToDelete: ModuleRecord) {
  records.value = records.value.filter((record) => record !== recordToDelete)
  if (selectedRecord.value === recordToDelete) {
    selectedRecord.value = records.value[0] ?? null
  }
  createVisible.value = false
  error.value = ''
  success.value = '记录已删除'
}

function resetCustomerForm() {
  Object.assign(customerForm, {
    customerName: '',
    organizationCode: '',
    companyProfile: '',
    industryNature: '',
    establishedAt: '',
    website: '',
    chairman: '',
    generalManager: '',
    parentGroup: '',
    registeredCapital: '',
    lastYearRevenue: '',
    employeeCount: '',
    organizationStructure: '',
    otherNotes: '',
  })
}

function createCustomerRecord() {
  error.value = ''
  if (!customerForm.customerName.trim()) {
    error.value = '请输入客户名称'
    return
  }

  const profileItems = [
    customerForm.industryNature && `行业：${customerForm.industryNature.trim()}`,
    customerForm.organizationCode && `代码：${customerForm.organizationCode.trim()}`,
    customerForm.parentGroup && `所属集团：${customerForm.parentGroup.trim()}`,
    customerForm.website && `官网：${customerForm.website.trim()}`,
  ].filter(Boolean)

  records.value = [{
    name: customerForm.customerName.trim(),
    meta: profileItems.join(' / ') || customerForm.companyProfile.trim() || '客户基础信息已录入',
    owner: customerForm.generalManager.trim() || customerForm.chairman.trim() || '待维护',
    stage: customerForm.organizationStructure.trim() ? '组织架构已维护' : '基础信息',
    amount: customerForm.lastYearRevenue.trim() || customerForm.registeredCapital.trim() || '待评估',
    status: '新建',
    tone: 'blue',
  }, ...records.value]
  selectedRecord.value = records.value[0] ?? null
  status.value = ''
  createVisible.value = false
  resetCustomerForm()
  success.value = '客户已新增'
}

function createRecord() {
  error.value = ''
  if (props.moduleKind === 'accounts') {
    createCustomerRecord()
    return
  }

  if (!recordForm.name.trim() || !recordForm.owner.trim() || !recordForm.stage.trim() || !recordForm.amount.trim()) {
    error.value = '请输入名称、负责人、阶段和金额'
    return
  }
  records.value = [{
    name: recordForm.name.trim(),
    meta: recordForm.meta.trim() || `${config.value.title} / 手工新增`,
    owner: recordForm.owner.trim(),
    stage: recordForm.stage.trim(),
    amount: recordForm.amount.trim(),
    status: recordForm.status.trim() || '新建',
    tone: 'blue',
  }, ...records.value]
  selectedRecord.value = records.value[0] ?? null
  status.value = ''
  createVisible.value = false
  success.value = '记录已新增'
}
</script>

<template>
  <section class="business-page">
    <section class="object-record">
      <details class="subject-card" open>
        <summary>
          <span>关键指标</span>
        </summary>
        <div class="subject-content target-grid" :aria-label="`${config.title}关键指标`">
          <article v-for="metric in config.metrics" :key="metric.label" class="target-card">
            <h2>{{ metric.label }}</h2>
            <div class="target-value compact">
              <strong>{{ metric.value }}</strong>
            </div>
            <p :class="{ 'is-risk': metric.risk }">{{ metric.note }}</p>
            <div
              class="target-progress"
              role="progressbar"
              :aria-label="`${metric.label} ${metric.progress}%`"
              aria-valuemin="0"
              aria-valuemax="100"
              :aria-valuenow="metric.progress"
            >
              <i :style="{ width: `${metric.progress}%` }" />
            </div>
          </article>
        </div>
      </details>

      <section class="panel-card business-toolbar" aria-label="业务筛选">
        <label>
          <span>搜索</span>
          <input
            v-model="search"
            data-testid="business-search"
            type="search"
            placeholder="输入名称、负责人、阶段或金额"
          />
        </label>
        <label>
          <span>状态</span>
          <select v-model="status" data-testid="business-status">
            <option value="">全部状态</option>
            <option v-for="item in statusOptions" :key="item" :value="item">{{ item }}</option>
          </select>
        </label>
        <label>
          <span>日期</span>
          <input v-model="dateRange" data-testid="business-date" type="date" />
        </label>
        <span class="business-toolbar__count">共 {{ filteredRecords.length }} 条</span>
        <button class="primary-action" type="button" data-testid="business-create" @click="openCreate">
          {{ config.primaryAction }}
        </button>
      </section>

      <p v-if="success" class="inline-success">{{ success }}</p>
      <p v-if="error" class="inline-error">{{ error }}</p>

      <details class="subject-card" open>
        <summary>
          <span>主体列表</span>
        </summary>
        <div class="record-list" :aria-label="`${config.title}主体列表`">
          <article
            v-for="record in filteredRecords"
            :key="record.name"
            class="record-row"
            :class="{ 'is-selected': selectedRecord === record }"
            @click="selectRecord(record)"
          >
            <div class="record-main">
              <strong>{{ record.name }}</strong>
              <span>{{ record.meta }}</span>
            </div>
            <dl class="record-fields">
              <div>
                <dt>负责人</dt>
                <dd>{{ record.owner }}</dd>
              </div>
              <div>
                <dt>阶段</dt>
                <dd>{{ record.stage }}</dd>
              </div>
              <div>
                <dt>金额</dt>
                <dd>{{ record.amount }}</dd>
              </div>
            </dl>
            <div class="record-actions">
              <span class="status-tag" :class="`tone-${record.tone ?? 'blue'}`">{{ record.status }}</span>
              <button
                v-if="hasObjectDetail"
                class="text-action record-action"
                type="button"
                data-testid="record-detail"
                @click.stop="selectRecord(record)"
              >
                详情
              </button>
              <button v-else class="text-action record-action" type="button" data-testid="record-add" @click.stop="openCreate">
                增加
              </button>
              <button
                class="text-action record-action is-danger"
                type="button"
                data-testid="record-delete"
                @click.stop="deleteRecord(record)"
              >
                删除
              </button>
            </div>
          </article>
          <p v-if="filteredRecords.length === 0" class="empty-records">没有符合条件的记录</p>
        </div>
      </details>

      <details v-if="hasObjectDetail && selectedRecord" class="subject-card object-detail-card" open>
        <summary>
          <span>对象详情</span>
        </summary>
        <section class="object-detail">
          <div class="object-detail__header">
            <div>
              <h2>{{ selectedRecord.name }}</h2>
              <span>{{ selectedRecord.meta }}</span>
            </div>
            <span class="status-tag" :class="`tone-${selectedRecord.tone ?? 'blue'}`">{{ selectedRecord.status }}</span>
          </div>

          <dl class="object-detail-fields">
            <div>
              <dt>负责人</dt>
              <dd>{{ selectedRecord.owner }}</dd>
            </div>
            <div>
              <dt>阶段</dt>
              <dd>{{ selectedRecord.stage }}</dd>
            </div>
            <div>
              <dt>金额/规模</dt>
              <dd>{{ selectedRecord.amount }}</dd>
            </div>
            <div>
              <dt>当前状态</dt>
              <dd>{{ selectedRecord.status }}</dd>
            </div>
          </dl>

          <div class="object-detail-activity">
            <article v-for="activity in detailActivities" :key="activity.title">
              <strong>{{ activity.title }}</strong>
              <span>{{ activity.meta }}</span>
            </article>
          </div>
        </section>
      </details>

      <details v-if="relatedSections.length > 0" class="subject-card" open>
        <summary>
          <span>关联列表</span>
        </summary>
        <div class="object-related-grid" aria-label="关联对象列表">
          <article v-for="section in relatedSections" :key="section.title" class="object-related-card">
            <h2>{{ section.title }}</h2>
            <dl>
              <div v-for="item in section.items" :key="item.label">
                <dt>{{ item.label }}</dt>
                <dd :class="`tone-${item.tone ?? 'blue'}`">{{ item.value }}</dd>
              </div>
            </dl>
          </article>
        </div>
      </details>

      <section
        v-if="createVisible && moduleKind === 'accounts'"
        class="panel-card business-create-form customer-create-form"
        aria-label="新建客户基础信息"
      >
        <div class="business-create-form__header">
          <h2>{{ config.primaryAction }}</h2>
          <button class="text-action" type="button" @click="createVisible = false">取消</button>
        </div>
        <label
          v-for="field in customerBasicFields"
          :key="field.key"
          :class="{ 'is-wide': field.type === 'textarea' }"
        >
          <span>{{ field.label }}</span>
          <textarea
            v-if="field.type === 'textarea'"
            v-model="customerForm[field.key]"
            :data-testid="`customer-${field.key}`"
            rows="3"
            :placeholder="field.placeholder"
          />
          <input
            v-else
            v-model="customerForm[field.key]"
            :data-testid="`customer-${field.key}`"
            :type="field.type ?? 'text'"
            :placeholder="field.placeholder"
          />
        </label>
        <button class="primary-action" type="button" data-testid="record-save" @click="createRecord">保存</button>
      </section>

      <section v-else-if="createVisible" class="panel-card business-create-form" aria-label="新建业务记录">
        <div class="business-create-form__header">
          <h2>{{ config.primaryAction }}</h2>
          <button class="text-action" type="button" @click="createVisible = false">取消</button>
        </div>
        <label>
          <span>名称</span>
          <input v-model="recordForm.name" data-testid="record-name" type="text" />
        </label>
        <label>
          <span>说明</span>
          <input v-model="recordForm.meta" type="text" placeholder="来源、客户、下一步等补充信息" />
        </label>
        <label>
          <span>负责人</span>
          <input v-model="recordForm.owner" data-testid="record-owner" type="text" />
        </label>
        <label>
          <span>阶段</span>
          <input v-model="recordForm.stage" data-testid="record-stage" type="text" />
        </label>
        <label>
          <span>金额</span>
          <input v-model="recordForm.amount" data-testid="record-amount" type="text" />
        </label>
        <label>
          <span>状态</span>
          <input v-model="recordForm.status" data-testid="record-status" type="text" placeholder="新建" />
        </label>
        <button class="primary-action" type="button" data-testid="record-save" @click="createRecord">保存</button>
      </section>

      <details v-if="moduleKind === 'accounts'" class="subject-card" open>
        <summary>
          <span>客户维护信息</span>
        </summary>
        <div class="maintenance-grid" aria-label="客户维护字段">
          <article v-for="section in customerMaintenanceSections" :key="section.title" class="maintenance-card">
            <h2>{{ section.title }}</h2>
            <div class="field-chip-list">
              <span v-for="field in section.fields" :key="field">{{ field }}</span>
            </div>
          </article>
        </div>
      </details>

    </section>
  </section>
</template>

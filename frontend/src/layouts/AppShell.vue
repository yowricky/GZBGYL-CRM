<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'

import logoUrl from '../assets/gez-logo.png'
import PermissionGate from '../components/PermissionGate.vue'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const displayName = computed(() => auth.user?.displayName ?? auth.user?.username ?? '未登录')
const isAdminActive = computed(() => route.path.startsWith('/admin'))

const primaryNavItems = [
  { label: '工作台', to: '/workspace' },
  { label: '报表分析', to: '/reports' },
]

const subjectNavGroups = [
  {
    label: '客户',
    to: '/accounts',
    items: [
      { label: '客户档案', to: '/accounts' },
      { label: '待办与活动', to: '/activities' },
      { label: '报价', to: '/quotes' },
      { label: '合同与回款', to: '/contracts' },
    ],
  },
  {
    label: '合作伙伴',
    to: '/partners',
    items: [
      { label: '伙伴线索', to: '/leads' },
      { label: '伙伴档案', to: '/partners' },
      { label: '待办与活动', to: '/activities' },
      { label: '报价', to: '/quotes' },
      { label: '合同与回款', to: '/contracts' },
    ],
  },
  {
    label: '商机',
    to: '/opportunities',
    items: [
      { label: '线索管理', to: '/leads' },
      { label: '商机池', to: '/opportunities' },
      { label: '待办与活动', to: '/activities' },
    ],
  },
  {
    label: '项目',
    to: '/projects',
    items: [
      { label: '项目跟进', to: '/projects' },
      { label: '待办与活动', to: '/activities' },
      { label: '报价', to: '/quotes' },
      { label: '合同与回款', to: '/contracts' },
    ],
  },
]

const adminItems = [
  { label: '组织架构', to: '/admin/organization-units' },
  { label: '用户管理', to: '/admin/users' },
  { label: '角色权限', to: '/admin/role-permissions' },
]

const contextHints = [
  { title: '当前选中', meta: '中间主内容区域。点击不同主体后，左侧显示对应信息汇总。' },
  { title: '信息完整度', meta: '客户、阶段、金额已具备；预算口径和接口范围需要继续补齐。' },
  { title: '权限提示', meta: '项目经理、运营管理、财务可查看关键字段与协同事项。' },
]

const relatedSummary = [
  ['本月经营缺口', '90 万'],
  ['待转项目商机', '4 个'],
  ['阶段延迟项目', '4 个'],
  ['待报价事项', '4 个'],
  ['回款复核', '2 个'],
]

const focusByPath: Record<string, Array<{ title: string; meta: string; tag: string; tone?: 'risk' | 'ok' | 'blue' }>> = {
  '/reports': [
    { title: '报价转合同周期偏长', meta: '投标/报价阶段平均停留 18 天，需要主管介入推动。', tag: '阶段风险', tone: 'risk' },
    { title: '回款复核事项集中', meta: '合同与财务需确认 5 个回款节点。', tag: '待复核', tone: 'blue' },
    { title: '重点行业增长稳定', meta: '供应链协同服务类项目本季贡献较高。', tag: '增长', tone: 'ok' },
  ],
  '/leads': [
    { title: '伙伴推荐线索需补充预算', meta: '缺少采购周期和预算负责人信息。', tag: '待补齐', tone: 'risk' },
    { title: '本周优先清理存量线索', meta: '建议先处理超过 7 天未跟进的线索。', tag: '跟进', tone: 'blue' },
    { title: '高质量线索来源稳定', meta: '系统集成伙伴推荐线索转化率较高。', tag: '有效', tone: 'ok' },
  ],
  '/accounts': [
    { title: '重点客户决策链未完整', meta: '需要补充技术、采购、财务等关键角色。', tag: '资料', tone: 'blue' },
    { title: '沉默客户需要唤醒', meta: '9 家客户 30 天内无跟进记录。', tag: '风险', tone: 'risk' },
    { title: '集团客户合作记录清晰', meta: '合同、回款、项目记录已形成闭环。', tag: '完整', tone: 'ok' },
  ],
  '/partners': [
    { title: '核心伙伴需要分级维护', meta: '按线索质量、交付资源、覆盖区域和结算记录划分伙伴层级。', tag: '分级', tone: 'blue' },
    { title: '推荐线索需补齐客户角色', meta: '伙伴推荐进入商机前，需要明确客户需求提出人、预算负责人和采购联系人。', tag: '补齐', tone: 'risk' },
    { title: '联合项目协同边界清晰', meta: '方案交流前确认我方、伙伴、客户三方职责和收益口径。', tag: '协同', tone: 'ok' },
  ],
  '/opportunities': [
    { title: '伙伴推荐线索需要确认客户角色', meta: '补齐需求提出人、预算负责人和采购联系人后再转入项目。', tag: '线索补齐', tone: 'risk' },
    { title: '高意向商机需要明确转项目条件', meta: '预算、时间窗口、客户决策链满足后进入项目跟进。', tag: '转项目', tone: 'blue' },
    { title: '线索来源需要区分市场、伙伴和销售自拓', meta: '不同来源使用不同跟进节奏和转化口径。', tag: '来源', tone: 'ok' },
  ],
  '/projects': [
    { title: '项目进度需要按节点跟踪', meta: '立项、预算、招采、合同、到货、验收等节点应形成过程记录。', tag: '进度', tone: 'blue' },
    { title: '行动计划执行成本需要沉淀', meta: '差旅、人工、办公及杂项应跟随行动计划记录。', tag: '成本', tone: 'risk' },
    { title: '项目经理、运营管理和财务共同查看', meta: '报价、合同、回款字段在项目视角形成协同。', tag: '协同', tone: 'ok' },
  ],
  '/quotes': [
    { title: '报价说明不完整', meta: '部分报价缺少交付周期和付款条件。', tag: '待补充', tone: 'risk' },
    { title: '审批口径需要统一', meta: '高金额报价建议增加运营复核。', tag: '审批', tone: 'blue' },
    { title: '报价转合同链路清晰', meta: '已确认报价可直接进入合同草拟。', tag: '转合同', tone: 'ok' },
  ],
  '/contracts': [
    { title: '回款节点需项目经理确认', meta: '项目经理、运营管理、财务共同查看关键字段与协同事项。', tag: '协同', tone: 'blue' },
    { title: '发票信息待补充', meta: '2 份合同缺少完整开票信息。', tag: '风险', tone: 'risk' },
    { title: '已签合同履约稳定', meta: '当前无重大交付异常。', tag: '正常', tone: 'ok' },
  ],
  '/activities': [
    { title: '今日优先处理报价确认', meta: '报价确认直接影响本月可确认收入。', tag: '优先', tone: 'risk' },
    { title: '会议纪要需要同步到商机', meta: '客户反馈应沉淀到对应阶段记录。', tag: '记录', tone: 'blue' },
    { title: '任务分派清晰', meta: '销售、项目经理、运营管理可按职责协同处理。', tag: '协同', tone: 'ok' },
  ],
}

const currentFocus = computed(() => focusByPath[route.path] ?? focusByPath['/projects'])

const aiPrompts = [
  '生成本周跟进计划',
  '分析当前项目风险',
  '整理推进到下一阶段的待办',
]

function isSubjectGroupActive(items: Array<{ to: string }>) {
  return items.some((item) => route.path === item.to)
}

async function logout() {
  await auth.logout()
  await router.push('/login')
}
</script>

<template>
  <div class="app-shell">
    <header class="app-topbar">
      <RouterLink class="brand-strip" to="/workspace" aria-label="葛洲坝供应链 CRM 首页">
          <img :src="logoUrl" alt="葛洲坝集团供应链管理有限公司" />
          <span class="brand-divider" aria-hidden="true"></span>
          <span class="app-name">客户关系管理平台</span>
      </RouterLink>

      <label class="global-search">
        <span class="sr-only">全局搜索</span>
        <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="11" cy="11" r="7" /><path d="m20 20-3.5-3.5" /></svg>
        <input type="search" aria-label="搜索客户、商机、项目、合同、联系人" placeholder="搜索客户、商机、项目、合同、联系人" />
      </label>

      <div class="topbar-actions">
        <span class="account-name">{{ displayName }}</span>
        <button class="text-action" type="button" @click="logout">退出</button>
      </div>
    </header>

    <nav class="object-nav" aria-label="主导航">
      <RouterLink v-for="item in primaryNavItems" :key="item.to" :to="item.to">{{ item.label }}</RouterLink>
      <div
        v-for="group in subjectNavGroups"
        :key="group.label"
        class="subject-nav-menu"
        :class="{ 'is-active': isSubjectGroupActive(group.items) }"
      >
        <RouterLink class="subject-nav-trigger" :to="group.to" aria-haspopup="true">
          {{ group.label }}
        </RouterLink>
        <div class="subject-nav-panel" :aria-label="`${group.label}菜单`">
          <RouterLink v-for="item in group.items" :key="`${group.label}-${item.label}`" :to="item.to">
            {{ item.label }}
          </RouterLink>
        </div>
      </div>
      <PermissionGate permission="system:admin">
        <div class="admin-nav-menu" :class="{ 'is-active': isAdminActive }">
          <RouterLink class="admin-nav-trigger" to="/admin/organization-units" aria-haspopup="true">Admin</RouterLink>
          <div class="admin-nav-panel" aria-label="Admin 菜单">
            <RouterLink v-for="item in adminItems" :key="item.to" :to="item.to">{{ item.label }}</RouterLink>
          </div>
        </div>
      </PermissionGate>
    </nav>

    <PermissionGate permission="system:admin">
      <nav v-if="isAdminActive" class="admin-subnav" aria-label="Admin 子菜单">
        <RouterLink v-for="item in adminItems" :key="item.to" :to="item.to">{{ item.label }}</RouterLink>
      </nav>
    </PermissionGate>

    <main class="app-main">
      <aside v-if="!isAdminActive" class="floating-panel left-drawer" aria-label="更多信息提示">
        <section class="context-panel">
          <div class="panel-heading">
            <h2>处理重点</h2>
          </div>
          <div class="action-list side-action-list">
            <article v-for="item in currentFocus" :key="item.title" class="action-item">
              <span class="action-rank" :class="`tone-${item.tone ?? 'blue'}`">•</span>
              <div>
                <strong>{{ item.title }}</strong>
                <span>{{ item.meta }}</span>
              </div>
              <span class="status-tag" :class="`tone-${item.tone ?? 'blue'}`">{{ item.tag }}</span>
            </article>
          </div>
        </section>

        <section class="context-panel">
          <div class="panel-heading">
            <h2>更多信息</h2>
          </div>
          <article v-for="hint in contextHints" :key="hint.title" class="hint-card">
            <strong>{{ hint.title }}</strong>
          </article>
        </section>

        <section class="context-panel">
          <div class="panel-heading">
            <h2>相关汇总</h2>
          </div>
          <dl class="summary-list">
            <div v-for="[label, value] in relatedSummary" :key="label">
              <dt>{{ label }}</dt>
              <dd>{{ value }}</dd>
            </div>
          </dl>
        </section>
      </aside>

      <section class="workspace-frame" :class="{ 'is-admin-frame': isAdminActive }">
        <RouterView />
      </section>

      <aside v-if="!isAdminActive" class="floating-panel right-drawer" aria-label="AI 交互">
        <section class="context-panel ai-panel">
          <div class="panel-heading">
            <h2>AI 项目助手</h2>
          </div>
          <article class="ai-message assistant">
            <strong>当前判断</strong>
          </article>
          <article class="ai-message">
            <strong>你可以问</strong>
          </article>
          <div class="prompt-grid" aria-label="推荐问题">
            <button v-for="prompt in aiPrompts" :key="prompt" class="text-action prompt-button" type="button">
              {{ prompt }}
            </button>
          </div>
          <label class="ai-input">
            <span>向 AI 提问</span>
            <textarea rows="4">请根据当前页面，生成下一步处理建议。</textarea>
          </label>
          <button class="primary-action" type="button">发送</button>
        </section>
      </aside>
    </main>
  </div>
</template>

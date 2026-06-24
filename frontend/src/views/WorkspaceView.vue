<script setup lang="ts">
const targets = [
  {
    label: '年度团队目标',
    value: '68.4%',
    meta: '目标 12,000 万 / 已确认 8,208 万',
    note: '缺口 3,792 万',
    progress: 68.4,
  },
  {
    label: '季度团队目标',
    value: '74.2%',
    meta: '目标 3,000 万 / 已确认 2,226 万',
    note: '缺口 774 万',
    progress: 74.2,
  },
  {
    label: '个人月度目标',
    value: '82.0%',
    meta: '目标 500 万 / 已确认 410 万',
    note: '缺口 90 万',
    progress: 82,
    risk: true,
  },
]

const stages = [
  { label: '初步接触', amount: 18, count: 38, countText: '12 项', rate: '0%' },
  { label: '方案交流', amount: 28, count: 26, countText: '8 项', rate: '40%' },
  { label: '立项/预算', amount: 22, count: 19, countText: '6 项', rate: '50%' },
  { label: '投标/报价', amount: 20, count: 10, countText: '4 项', rate: '60%' },
  { label: '商务谈判', amount: 12, count: 7, countText: '3 项', rate: '85%' },
]

const actionItems = [
  {
    rank: '1',
    title: '重点商机 14 天未更新',
    meta: '华中区域项目 / 方案交流 / 确认技术方案反馈',
    tag: '高风险',
    tone: 'risk',
  },
  {
    rank: '2',
    title: '报价单等待客户确认',
    meta: '长江生态材料 / 286 万 / 补充交付周期说明',
    tag: '待报价',
    tone: 'blue',
  },
  {
    rank: '3',
    title: '本月回款节点需复核',
    meta: '供应链协同服务合同 / 计划回款 120 万',
    tag: '回款',
    tone: 'ok',
  },
]
</script>

<template>
  <section class="workspace-page">
    <section class="object-record">
      <article class="panel-card record-header">
        <div>
          <h1>团队与个人业绩</h1>
        </div>
        <button class="primary-action" type="button">生成跟进计划</button>
      </article>

      <details class="subject-card" open>
        <summary>
          <span>目标完成情况</span>
        </summary>
        <div class="subject-content target-grid" aria-label="经营目标完成情况">
          <article v-for="target in targets" :key="target.label" class="target-card">
            <h2>{{ target.label }}</h2>
            <div class="target-value">
              <strong>{{ target.value }}</strong>
              <span>已完成</span>
            </div>
            <p>{{ target.meta }}</p>
            <p :class="{ 'is-risk': target.risk }">{{ target.note }}</p>
            <div
              class="target-progress"
              role="progressbar"
              :aria-label="`${target.label}完成率 ${target.value}`"
              aria-valuemin="0"
              aria-valuemax="100"
              :aria-valuenow="target.progress"
            >
              <i :style="{ width: `${target.progress}%` }" />
            </div>
          </article>
        </div>
      </details>

      <details class="subject-card" open>
        <summary>
          <span>今日必须处理</span>
        </summary>
        <div class="action-list">
          <article v-for="item in actionItems" :key="item.title" class="action-item">
            <span class="action-rank" :class="`tone-${item.tone}`">{{ item.rank }}</span>
            <div>
              <strong>{{ item.title }}</strong>
              <span>{{ item.meta }}</span>
            </div>
            <span class="status-tag" :class="`tone-${item.tone}`">{{ item.tag }}</span>
          </article>
        </div>
      </details>

      <details class="subject-card" open>
        <summary>
          <span>商机阶段</span>
        </summary>
        <div class="stage-list detailed">
          <div v-for="stage in stages" :key="stage.label" class="stage-row">
            <span>{{ stage.label }}</span>
            <div class="stage-bars" :aria-label="`${stage.label} 金额占比 ${stage.amount}%，数量占比 ${stage.count}%`">
              <div class="stage-bar amount"><i :style="{ width: `${stage.amount}%` }" /></div>
              <div class="stage-bar count"><i :style="{ width: `${stage.count}%` }" /></div>
            </div>
            <span>{{ stage.countText }}</span>
            <span>{{ stage.rate }}</span>
          </div>
        </div>
      </details>
    </section>
  </section>
</template>

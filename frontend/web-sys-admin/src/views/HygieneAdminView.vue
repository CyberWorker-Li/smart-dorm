<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { apiBase } from '@/apiBase'

interface HygieneRuleVO {
  key: string
  value: string
  remark?: string
}

interface HygieneRankingItemVO {
  rank: number
  roomId: number
  roomDisplay: string
  avgScore: number
  latestScore?: number
  boardType: string
}

const route = useRoute()
const router = useRouter()
const API = apiBase()

const msg = ref('')
const msgType = ref<'success' | 'error'>('success')

const rules = ref<HygieneRuleVO[]>([])
const loadingRules = ref(false)
const savingRules = ref(false)

const month = ref(getMonthStr(new Date()))
const ranking = ref<HygieneRankingItemVO[]>([])
const loadingRank = ref(false)

const weekStart = ref(getMonday(new Date()))
const weekRanking = ref<HygieneRankingItemVO[]>([])
const loadingWeek = ref(false)

const ruleMeta: Record<string, { name: string; defaultRemark?: string }> = {
  'hygiene.deadlineHour': { name: '值日截止小时', defaultRemark: '0-23，超过后系统会判定未完成并记入日评分' },
  'hygiene.doneScore': { name: '全部完成的系统日评分', defaultRemark: '当日所有值日任务都完成时的系统评分' },
  'hygiene.missPenaltyScore': { name: '未完成任务的系统日评分', defaultRemark: '当日存在未完成值日任务时的系统评分' },
  'hygiene.redThreshold': { name: '红榜阈值（含）', defaultRemark: '月均分达到该值进入红榜' },
  'hygiene.blackThreshold': { name: '黑榜阈值（不含）', defaultRemark: '月均分低于该值进入黑榜' },
  'hygiene.systemWeight': { name: '月度汇总：系统评分权重', defaultRemark: '系统自动打卡评分在月度汇总中的权重' },
  'hygiene.manualWeight': { name: '月度汇总：宿管评分权重', defaultRemark: '宿管现场检查评分在月度汇总中的权重' },
}

function ruleName(key: string): string {
  return ruleMeta[key]?.name ?? '其他规则'
}

function boardTypeLabel(boardType: string): string {
  const t = (boardType || '').toUpperCase()
  if (t === 'RED') return '红榜'
  if (t === 'BLACK') return '黑榜'
  return '正常'
}

function showMsg(text: string, type: 'success' | 'error' = 'success') {
  msg.value = text
  msgType.value = type
  setTimeout(() => (msg.value = ''), 5000)
}

function getMonthStr(d: Date): string {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  return `${y}-${m}`
}

function getMonday(d: Date): string {
  const date = new Date(d)
  const day = (date.getDay() + 6) % 7
  date.setDate(date.getDate() - day)
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const dd = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${dd}`
}

async function loadRules() {
  loadingRules.value = true
  try {
    const res = await fetch(`${API}/api/hygiene/admin/rules`)
    const r = await res.json()
    if (r.code === 200) rules.value = r.data
    else showMsg(r.message || '加载规则失败', 'error')
  } finally {
    loadingRules.value = false
  }
}

async function saveRules() {
  const adminId = Number(route.query.userId || 0) || 1
  savingRules.value = true
  try {
    const res = await fetch(`${API}/api/hygiene/admin/rules`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        adminId,
        items: rules.value.map(r => ({ key: r.key, value: String(r.value ?? ''), remark: r.remark })),
      }),
    })
    const r = await res.json()
    if (r.code === 200) showMsg('保存成功')
    else showMsg(r.message || '保存失败', 'error')
  } finally {
    savingRules.value = false
  }
}

async function loadRanking() {
  loadingRank.value = true
  try {
    const res = await fetch(`${API}/api/hygiene/admin/ranking?month=${month.value}`)
    const r = await res.json()
    if (r.code === 200) ranking.value = r.data
    else showMsg(r.message || '加载榜单失败', 'error')
  } finally {
    loadingRank.value = false
  }
}

const redList = computed(() => ranking.value.filter(x => x.boardType === 'RED'))
const blackList = computed(() => ranking.value.filter(x => x.boardType === 'BLACK'))

function exportCsv() {
  window.open(`${API}/api/hygiene/admin/ranking/export?month=${encodeURIComponent(month.value)}`, '_blank')
}

async function loadWeekly() {
  loadingWeek.value = true
  try {
    const res = await fetch(`${API}/api/hygiene/admin/weekly?weekStart=${encodeURIComponent(weekStart.value)}`)
    const r = await res.json()
    if (r.code === 200) weekRanking.value = r.data
    else showMsg(r.message || '加载周报失败', 'error')
  } finally {
    loadingWeek.value = false
  }
}

function exportWeeklyCsv() {
  window.open(`${API}/api/hygiene/admin/weekly/export?weekStart=${encodeURIComponent(weekStart.value)}`, '_blank')
}

onMounted(async () => {
  await loadRules()
  await loadRanking()
  await loadWeekly()
})
</script>

<template>
  <div class="page">
    <div class="panel">
      <div class="header-row">
        <div>
          <h1>卫生管理</h1>
          <p class="desc">配置卫生评分规则、查看红黑榜与月度统计。</p>
        </div>
        <button class="back-btn" @click="router.push({ path: '/', query: route.query })">返回首页</button>
      </div>

      <p v-if="msg" :class="['tip-msg', msgType]">{{ msg }}</p>

      <section class="card">
        <div class="section-title">规则配置</div>
        <div v-if="loadingRules" class="tip">加载中...</div>
        <div v-else class="rule-table">
          <table>
            <thead>
              <tr>
                <th>规则项</th>
                <th>参数值</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="r in rules" :key="r.key">
                <td>
                  <div class="rule-name">{{ ruleName(r.key) }}</div>
                  <div class="rule-key">{{ r.key }}</div>
                </td>
                <td><input v-model="r.value" /></td>
              </tr>
            </tbody>
          </table>
          <div class="actions">
            <button class="btn" :disabled="savingRules" @click="loadRules">重载</button>
            <button class="btn primary" :disabled="savingRules" @click="saveRules">{{ savingRules ? '保存中...' : '保存' }}</button>
          </div>
        </div>
      </section>

      <section class="card">
        <div class="section-title">红黑榜（按月）</div>
        <div class="row">
          <input v-model="month" placeholder="如 2026-05" />
          <button class="btn" :disabled="loadingRank" @click="loadRanking">{{ loadingRank ? '加载中...' : '刷新榜单' }}</button>
          <button class="btn" @click="exportCsv">导出CSV</button>
        </div>

        <div class="boards">
          <div class="board">
            <div class="board-title red">红榜</div>
            <div v-if="redList.length === 0" class="tip">暂无</div>
            <div v-else class="list">
              <div v-for="i in redList" :key="i.roomId" class="item">
                <div class="name">{{ i.rank }}. {{ i.roomDisplay }}</div>
                <div class="score">{{ i.avgScore.toFixed(1) }}</div>
              </div>
            </div>
          </div>

          <div class="board">
            <div class="board-title black">黑榜</div>
            <div v-if="blackList.length === 0" class="tip">暂无</div>
            <div v-else class="list">
              <div v-for="i in blackList" :key="i.roomId" class="item">
                <div class="name">{{ i.rank }}. {{ i.roomDisplay }}</div>
                <div class="score">{{ i.avgScore.toFixed(1) }}</div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section class="card">
        <div class="section-title">卫生周报（按周一日期）</div>
        <div class="row">
          <input v-model="weekStart" placeholder="如 2026-05-25" />
          <button class="btn" :disabled="loadingWeek" @click="loadWeekly">{{ loadingWeek ? '加载中...' : '刷新周报' }}</button>
          <button class="btn" @click="exportWeeklyCsv">导出CSV</button>
        </div>
        <div v-if="weekRanking.length === 0" class="tip">暂无</div>
        <div v-else class="week-list">
          <div v-for="i in weekRanking.slice(0, 10)" :key="i.roomId" class="week-item">
            <div class="name">{{ i.rank }}. {{ i.roomDisplay }}</div>
            <div class="score">{{ i.avgScore.toFixed(1) }}</div>
            <div class="tag" :class="i.boardType.toLowerCase()">{{ boardTypeLabel(i.boardType) }}</div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.page { min-height: 100vh; padding: 32px; background: #f5f7fb; }
.panel { max-width: 1100px; margin: 0 auto; background: #fff; border-radius: 20px; padding: 32px; box-shadow: 0 16px 36px rgba(15,23,42,.08); }
.header-row { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 12px; }
h1 { margin: 0; color: #1e40af; }
.desc { color: #667085; margin: 10px 0 0; }
.back-btn { border: none; border-radius: 10px; padding: 10px 18px; background: #dbeafe; color: #1e40af; cursor: pointer; font-weight: 700; white-space: nowrap; }
.tip-msg { font-size: 14px; margin: 10px 0; }
.tip-msg.success { color: #027a48; }
.tip-msg.error { color: #d92d20; }
.card { background: #f8fafc; border: 1px solid #e4e7ec; border-radius: 16px; padding: 20px; margin-top: 16px; }
.section-title { font-size: 16px; font-weight: 800; color: #1f2937; margin-bottom: 12px; }
.tip { color: #667085; font-size: 14px; }
.rule-table { overflow: auto; border-radius: 12px; border: 1px solid #e4e7ec; background: #fff; }
table { width: 100%; border-collapse: collapse; min-width: 760px; }
th, td { padding: 10px 12px; border-bottom: 1px solid #e4e7ec; font-size: 14px; text-align: left; }
th { background: #f3f4f6; color: #344054; font-weight: 800; }
input { width: 100%; height: 36px; border: 1px solid #d0d5dd; border-radius: 10px; padding: 0 10px; background: #fff; }
.rule-name { font-weight: 900; color: #1f2937; }
.rule-key { margin-top: 4px; font-size: 12px; color: #667085; font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace; }
.actions { display: flex; gap: 10px; padding: 12px; justify-content: flex-end; }
.btn { height: 38px; border: none; border-radius: 10px; padding: 0 16px; background: #eef2ff; color: #3730a3; cursor: pointer; font-weight: 800; white-space: nowrap; }
.btn.primary { background: #1d4ed8; color: #fff; }
.row { display: flex; gap: 10px; align-items: center; }
.row input { width: 160px; }
.boards { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; margin-top: 12px; }
.board { background: #fff; border: 1px solid #e4e7ec; border-radius: 14px; padding: 14px; }
.board-title { font-weight: 900; margin-bottom: 10px; }
.board-title.red { color: #b42318; }
.board-title.black { color: #101828; }
.list { display: grid; gap: 10px; }
.item { display: flex; justify-content: space-between; align-items: center; padding: 10px; border: 1px solid #e4e7ec; border-radius: 12px; }
.name { font-weight: 800; color: #1f2937; }
.score { font-weight: 900; color: #1d4ed8; }
.week-list { display: grid; gap: 10px; margin-top: 12px; }
.week-item { display: grid; grid-template-columns: 1fr auto auto; align-items: center; gap: 12px; padding: 10px; background: #fff; border: 1px solid #e4e7ec; border-radius: 12px; }
.tag { font-size: 12px; font-weight: 900; padding: 4px 10px; border-radius: 999px; background: #f3f4f6; color: #344054; }
.tag.red { background: #fef3f2; color: #b42318; }
.tag.black { background: #101828; color: #fff; }
</style>


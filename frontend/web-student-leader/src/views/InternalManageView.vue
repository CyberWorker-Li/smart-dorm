<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { apiBase } from '@/apiBase'

const router = useRouter()
const query = Object.fromEntries(new URLSearchParams(window.location.search))
const API = apiBase()

const studentId = computed(() => {
  const raw = query.userId
  if (!raw) return null
  const n = Number(raw)
  return Number.isFinite(n) ? n : null
})

const msg = ref('')
const msgType = ref<'success' | 'error'>('success')

function showMsg(text: string, type: 'success' | 'error' = 'success') {
  msg.value = text
  msgType.value = type
  setTimeout(() => (msg.value = ''), 4000)
}

async function readJson<T>(url: string, init?: RequestInit): Promise<T | null> {
  try {
    const res = await fetch(url, init)
    const r = await res.json()
    if (r && r.code === 200) return r.data as T
    showMsg(r?.message || '请求失败', 'error')
    return null
  } catch {
    showMsg('网络异常，请稍后重试', 'error')
    return null
  }
}

interface MyAssignmentResultVO {
  batchId: number
  academicYear: string
  building: string
  roomNo: string
  roomGender: string
  bedNo: number
  roommates: Array<{
    studentId: number
    realName: string
    userNo: string
    gender: string
    wakeUpTime: string
    sleepTime: string
    personality: string
    hobbies: string
    hometown: string
    bedNo: number
  }>
}

interface RoommateVO {
  studentId: number
  realName: string
  userNo: string
  gender?: string
  bedNo: number
}

interface HygieneRoomSummaryVO {
  roomId: number
  roomDisplay: string
  totalTasks: number
  completedTasks: number
  overdueTasks: number
  latestScore: number | null
  avgScore: number | null
}

const myAssignment = ref<MyAssignmentResultVO | null>(null)
const hygieneSummary = ref<HygieneRoomSummaryVO | null>(null)
const members = ref<RoommateVO[]>([])

const dormDisplay = computed(() => {
  if (!myAssignment.value) return ''
  const building = myAssignment.value.building ? `${myAssignment.value.building} ` : ''
  return `${building}${myAssignment.value.roomNo || ''}`.trim()
})

const memberCount = computed(() => {
  return members.value.length
})

const memberLabels = computed(() => {
  const list = (members.value || []).slice().sort((a, b) => (a.bedNo ?? 0) - (b.bedNo ?? 0))
  return list.map(m => {
    const n = (m.realName || m.userNo || '室友').toString()
    return m.bedNo ? `${n}（${m.bedNo}号床）` : n
  })
})

interface DormRuleVO {
  id: number
  content: string
  status: string
  approvalRate: number
  voted: boolean
  createTime: string
}

const ruleDraft = ref('')
const rules = ref<DormRuleVO[]>([])
const ruleSubmitting = ref(false)
const votingRuleId = ref<number | null>(null)

async function loadRules() {
  if (!studentId.value) return
  const data = await readJson<DormRuleVO[]>(`${API}/api/internal/rules?studentId=${studentId.value}`)
  rules.value = Array.isArray(data) ? data : []
}

async function submitRule() {
  if (!studentId.value) return
  if (ruleSubmitting.value) return
  const c = ruleDraft.value.trim()
  if (!c) {
    showMsg('请填写公约内容后再提交', 'error')
    return
  }
  ruleSubmitting.value = true
  try {
    const ok = await readJson<number>(`${API}/api/internal/rules`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ studentId: studentId.value, content: c }),
    })
    if (ok != null) {
      ruleDraft.value = ''
      showMsg('公约已提交，进入匿名投票')
      await loadRules()
    }
  } finally {
    ruleSubmitting.value = false
  }
}

async function voteRule(ruleId: number) {
  if (!studentId.value) return
  if (votingRuleId.value === ruleId) return
  votingRuleId.value = ruleId
  try {
    const ok = await readJson<boolean>(`${API}/api/internal/rules/${ruleId}/vote`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ studentId: studentId.value, agree: true }),
    })
    if (ok != null) {
      showMsg('投票成功')
      await loadRules()
    }
  } finally {
    if (votingRuleId.value === ruleId) votingRuleId.value = null
  }
}

interface DormFeedbackVO {
  id: number
  time: string
  content: string
  status: string
  canEscalate: boolean
}

const feedbackDraft = ref('')
const feedbacks = ref<DormFeedbackVO[]>([])
const feedbackSubmitting = ref(false)
const feedbackEscalatingId = ref<number | null>(null)

async function loadFeedbacks() {
  if (!studentId.value) return
  const data = await readJson<DormFeedbackVO[]>(`${API}/api/internal/feedback?studentId=${studentId.value}`)
  feedbacks.value = Array.isArray(data) ? data : []
}

async function submitFeedback() {
  if (!studentId.value) return
  if (feedbackSubmitting.value) return
  const c = feedbackDraft.value.trim()
  if (!c) {
    showMsg('请填写反馈内容', 'error')
    return
  }
  feedbackSubmitting.value = true
  try {
    const ok = await readJson<number>(`${API}/api/internal/feedback`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ studentId: studentId.value, content: c }),
    })
    if (ok != null) {
      feedbackDraft.value = ''
      showMsg('反馈已匿名提交')
      await loadFeedbacks()
    }
  } finally {
    feedbackSubmitting.value = false
  }
}

async function escalateFeedback(id: number) {
  if (!studentId.value) return
  if (feedbackEscalatingId.value === id) return
  feedbackEscalatingId.value = id
  try {
    const ok = await readJson<boolean>(`${API}/api/internal/feedback/${id}/escalate?studentId=${studentId.value}`, {
      method: 'PUT',
    })
    if (ok != null) {
      showMsg('已提交宿管介入')
      await loadFeedbacks()
    }
  } finally {
    if (feedbackEscalatingId.value === id) feedbackEscalatingId.value = null
  }
}

interface PeerEvalItemVO {
  targetStudentId: number
  name: string
  schedule: number
  hygiene: number
  communication: number
}

const peerEvalMonth = ref(new Date().toISOString().slice(0, 7))
const evaluationDraft = ref<PeerEvalItemVO[]>([])
const peerEvalSubmitting = ref(false)
const peerEvalLastSubmitAt = ref<string>('')

async function loadPeerEvalTemplate() {
  if (!studentId.value) return
  const data = await readJson<PeerEvalItemVO[]>(`${API}/api/internal/peer-eval/template?studentId=${studentId.value}`)
  evaluationDraft.value = Array.isArray(data) ? data : []
}

async function submitEvaluation() {
  if (!studentId.value) return
  if (peerEvalSubmitting.value) return
  if (!evaluationDraft.value.length) {
    showMsg('暂无可互评的室友', 'error')
    return
  }
  peerEvalSubmitting.value = true
  const payload = {
    studentId: studentId.value,
    month: peerEvalMonth.value,
    items: evaluationDraft.value.map(x => ({
      targetStudentId: x.targetStudentId,
      schedule: x.schedule,
      hygiene: x.hygiene,
      communication: x.communication,
    })),
  }
  try {
    const ok = await readJson<boolean>(`${API}/api/internal/peer-eval/submit`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    })
    if (ok === true) {
      peerEvalLastSubmitAt.value = new Date().toLocaleString()
      showMsg('互评已提交')
    }
  } finally {
    peerEvalSubmitting.value = false
  }
}

type RepairType = 'WATER' | 'POWER' | 'MECHANICAL'

const repairForm = ref<{ type: RepairType | ''; detail: string }>({ type: '', detail: '' })
const repairReply = ref<string>('')
const repairSubmitting = ref(false)

async function submitRepair() {
  if (!studentId.value) return
  if (repairSubmitting.value) return
  if (!repairForm.value.type) {
    showMsg('请选择故障类型', 'error')
    return
  }
  repairSubmitting.value = true
  try {
    const r = await readJson<{ id: number; reply: string }>(`${API}/api/internal/repairs`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        studentId: studentId.value,
        type: repairForm.value.type,
        detail: repairForm.value.detail || '',
      }),
    })
    if (r) {
      repairReply.value = r.reply
      showMsg('报修已提交')
    }
  } finally {
    repairSubmitting.value = false
  }
}

interface UtilityVO {
  month: string
  water: number
  power: number
  cost: number
}

interface UtilityThresholdVO {
  waterLimit: number | null
  powerLimit: number | null
}

const utilities = ref<UtilityVO[]>([])
const threshold = ref<UtilityThresholdVO>({ waterLimit: null, powerLimit: null })
const thresholdEdit = ref<UtilityThresholdVO>({ waterLimit: null, powerLimit: null })
const thresholdSaving = ref(false)

async function loadUtilities() {
  if (!studentId.value) return
  const data = await readJson<UtilityVO[]>(`${API}/api/internal/utilities?studentId=${studentId.value}`)
  utilities.value = Array.isArray(data) ? data : []
}

async function loadThreshold() {
  if (!studentId.value) return
  const data = await readJson<UtilityThresholdVO>(`${API}/api/internal/utilities/threshold?studentId=${studentId.value}`)
  if (data) {
    threshold.value = data
    thresholdEdit.value = { ...data }
  }
}

async function saveThreshold() {
  if (!studentId.value) return
  if (thresholdSaving.value) return
  thresholdSaving.value = true
  try {
    const ok = await readJson<boolean>(`${API}/api/internal/utilities/threshold`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ studentId: studentId.value, ...thresholdEdit.value }),
    })
    if (ok != null) {
      showMsg('阈值已保存')
      await loadThreshold()
    }
  } finally {
    thresholdSaving.value = false
  }
}

interface ActivityVO {
  id: number
  name: string
  status: string
  joined: boolean
}

const activities = ref<ActivityVO[]>([])
const activitySigningUpId = ref<number | null>(null)

async function loadActivities() {
  if (!studentId.value) return
  const data = await readJson<ActivityVO[]>(`${API}/api/internal/activities?studentId=${studentId.value}`)
  activities.value = Array.isArray(data) ? data : []
}

async function joinActivity(activityId: number) {
  if (!studentId.value) return
  if (activitySigningUpId.value === activityId) return
  activitySigningUpId.value = activityId
  try {
    const ok = await readJson<boolean>(`${API}/api/internal/activities/${activityId}/signup?studentId=${studentId.value}`, {
      method: 'POST',
    })
    if (ok != null) {
      showMsg('已报名')
      await loadActivities()
    }
  } finally {
    if (activitySigningUpId.value === activityId) activitySigningUpId.value = null
  }
}

async function loadDormInfo() {
  if (!studentId.value) return
  const my = await readJson<MyAssignmentResultVO>(`${API}/api/assignment/my-result?studentId=${studentId.value}`)
  myAssignment.value = my
  const hs = await readJson<HygieneRoomSummaryVO>(`${API}/api/hygiene/room-summary?studentId=${studentId.value}`)
  hygieneSummary.value = hs
  const ms = await readJson<RoommateVO[]>(`${API}/api/internal/members?studentId=${studentId.value}`)
  members.value = Array.isArray(ms) ? ms : []
}

function goHome() {
  router.push({ path: '/', query })
}

onMounted(async () => {
  if (!studentId.value) {
    showMsg('缺少学生ID，请从统一门户进入', 'error')
    return
  }
  await Promise.all([
    loadDormInfo(),
    loadRules(),
    loadFeedbacks(),
    loadPeerEvalTemplate(),
    loadUtilities(),
    loadThreshold(),
    loadActivities(),
  ])
})
</script>

<template>
  <div class="page">
    <div class="panel">
      <div class="header-row">
        <div>
          <h1>宿舍内部管理</h1>
          <p class="desc">公约、反馈、互评、报修与水电用量一站管理。</p>
        </div>
        <button class="back-btn" @click="goHome">返回首页</button>
      </div>

      <div v-if="msg" :class="['msg', msgType]">{{ msg }}</div>

      <section class="card">
        <div class="card-header">
          <h2>宿舍信息</h2>
        </div>
        <div class="info-grid">
          <div><span>宿舍号</span><strong>{{ dormDisplay || '暂无' }}</strong></div>
          <div><span>成员人数</span><strong>{{ memberCount }} 人</strong></div>
          <div><span>卫生均分</span><strong>{{ hygieneSummary?.avgScore ?? '暂无' }}</strong></div>
          <div><span>最新评分</span><strong>{{ hygieneSummary?.latestScore ?? '暂无' }}</strong></div>
        </div>
        <p class="muted">成员名单：{{ memberLabels.join('、') }}</p>
      </section>

      <section class="card">
        <div class="card-header">
          <h2>公约制定与投票</h2>
        </div>
        <div class="inline-form">
          <textarea v-model="ruleDraft" placeholder="匿名提交公约建议"></textarea>
          <button class="primary" :disabled="ruleSubmitting" @click="submitRule">
            {{ ruleSubmitting ? '提交中...' : '提交公约' }}
          </button>
        </div>
        <div class="list">
          <div v-for="rule in rules" :key="rule.id" class="list-item">
            <div>
              <strong>{{ rule.content }}</strong>
              <p class="muted">状态：{{ rule.status }} · 赞成率 {{ rule.approvalRate }}%</p>
            </div>
            <button
              v-if="rule.status === 'VOTING' && !rule.voted"
              class="ghost"
              :disabled="votingRuleId === rule.id"
              @click="voteRule(rule.id)"
            >
              {{ votingRuleId === rule.id ? '投票中...' : '匿名投票' }}
            </button>
            <span v-else class="muted">已投票/已结束</span>
          </div>
        </div>
      </section>

      <section class="card">
        <div class="card-header">
          <h2>意见反馈</h2>
        </div>
        <div class="inline-form">
          <textarea v-model="feedbackDraft" placeholder="匿名提交室友违规或建议"></textarea>
          <button class="primary" :disabled="feedbackSubmitting" @click="submitFeedback">
            {{ feedbackSubmitting ? '提交中...' : '提交反馈' }}
          </button>
        </div>
        <div class="list">
          <div v-for="item in feedbacks" :key="item.id" class="list-item">
            <div>
              <strong>{{ item.content }}</strong>
              <p class="muted">{{ item.time }} · {{ item.status }}</p>
            </div>
            <button
              class="ghost"
              :disabled="!item.canEscalate || feedbackEscalatingId === item.id"
              @click="escalateFeedback(item.id)"
            >
              {{ feedbackEscalatingId === item.id ? '提交中...' : '提交宿管' }}
            </button>
          </div>
        </div>
      </section>

      <section class="card">
        <div class="card-header">
          <h2>室友月度互评</h2>
          <div class="peer-head">
            <input v-model="peerEvalMonth" class="month" placeholder="YYYY-MM" />
            <button class="primary" :disabled="peerEvalSubmitting" @click="submitEvaluation">
              {{ peerEvalSubmitting ? '提交中...' : '提交互评' }}
            </button>
          </div>
        </div>
        <p v-if="peerEvalLastSubmitAt" class="submit-hint">已提交：{{ peerEvalLastSubmitAt }}</p>
        <div class="table">
          <div class="table-row table-head">
            <span>室友</span>
            <span>作息配合度</span>
            <span>卫生贡献</span>
            <span>沟通友好度</span>
          </div>
          <div v-for="item in evaluationDraft" :key="item.targetStudentId" class="table-row">
            <span>{{ item.name }}</span>
            <input v-model.number="item.schedule" type="number" min="1" max="10" />
            <input v-model.number="item.hygiene" type="number" min="1" max="10" />
            <input v-model.number="item.communication" type="number" min="1" max="10" />
          </div>
        </div>
        <p class="muted">低分互评会自动预警宿管，便于冲突提前介入。</p>
      </section>

      <section class="grid">
        <div class="card">
          <div class="card-header">
            <h2>一键报修</h2>
          </div>
          <div class="inline-form vertical">
            <select v-model="repairForm.type">
              <option value="">请选择故障类型</option>
              <option value="WATER">水路故障</option>
              <option value="POWER">电路故障</option>
              <option value="MECHANICAL">机械/设施故障</option>
            </select>
            <input v-model="repairForm.detail" placeholder="故障描述或位置" />
            <button class="primary" :disabled="repairSubmitting" @click="submitRepair">
              {{ repairSubmitting ? '提交中...' : '提交报修' }}
            </button>
            <p v-if="repairReply" class="muted">{{ repairReply }}</p>
          </div>
        </div>

        <div class="card">
          <div class="card-header">
            <h2>水电用量透明</h2>
          </div>
          <div class="inline-form">
            <input v-model.number="thresholdEdit.waterLimit" type="number" placeholder="水阈值(吨/月)" />
            <input v-model.number="thresholdEdit.powerLimit" type="number" placeholder="电阈值(度/月)" />
            <button class="ghost" :disabled="thresholdSaving" @click="saveThreshold">
              {{ thresholdSaving ? '保存中...' : '保存阈值' }}
            </button>
          </div>
          <p class="muted">当前阈值：水 {{ threshold.waterLimit ?? '未设置' }} · 电 {{ threshold.powerLimit ?? '未设置' }}</p>
          <div class="list">
            <div v-for="item in utilities" :key="item.month" class="list-item">
              <div>
                <strong>{{ item.month }}</strong>
                <p class="muted">水 {{ item.water }} 吨 · 电 {{ item.power }} 度 · 费用 ¥{{ item.cost }}</p>
              </div>
            </div>
            <div v-if="!utilities.length" class="muted">暂无用量数据</div>
          </div>
        </div>
      </section>

      <section class="card">
        <div class="card-header">
          <h2>文化建设与风采展示</h2>
        </div>
        <div class="grid">
          <div class="honor">
            <h3>荣誉墙</h3>
            <div class="honor-grid">
              <div class="honor-item">文明宿舍</div>
              <div class="honor-item">卫生标兵</div>
              <div class="honor-item">优秀宿舍长</div>
            </div>
          </div>
          <div class="honor">
            <h3>活动报名</h3>
            <div class="list">
              <div v-for="activity in activities" :key="activity.id" class="list-item">
                <div>
                  <strong>{{ activity.name }}</strong>
                  <p class="muted">{{ activity.joined ? '已报名' : activity.status }}</p>
                </div>
                <button
                  class="ghost"
                  :disabled="activity.joined || activity.status !== 'OPEN' || activitySigningUpId === activity.id"
                  @click="joinActivity(activity.id)"
                >
                  {{ activitySigningUpId === activity.id ? '报名中...' : '报名' }}
                </button>
              </div>
              <div v-if="!activities.length" class="muted">暂无可报名活动</div>
            </div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.page { min-height: 100vh; padding: 40px; background: #f5f7fb; }
.panel { max-width: 1100px; margin: 0 auto; background: #fff; border-radius: 20px; padding: 32px; box-shadow: 0 16px 36px rgba(15,23,42,.08); }
.header-row { display: flex; justify-content: space-between; align-items: center; gap: 16px; }
.desc { color: #667085; margin-top: 8px; }
.back-btn { border: 1px solid #d0d5dd; background: #fff; padding: 8px 16px; border-radius: 12px; cursor: pointer; }
.msg { margin: 16px 0; padding: 12px 16px; border-radius: 12px; font-weight: 600; }
.msg.success { background: #ecfdf3; color: #067647; border: 1px solid #abefc6; }
.msg.error { background: #fffbf0; color: #b54708; border: 1px solid #fedf89; }
.card { background: #f8fafc; border: 1px solid #e4e7ec; border-radius: 16px; padding: 20px; margin-top: 20px; }
.card-header { display: flex; justify-content: space-between; align-items: center; gap: 12px; margin-bottom: 12px; }
.peer-head { display: flex; gap: 10px; align-items: center; }
.month { height: 34px; border-radius: 10px; border: 1px solid #d0d5dd; padding: 0 10px; width: 110px; }
.primary { background: #2563eb; color: #fff; border: none; padding: 8px 16px; border-radius: 10px; cursor: pointer; }
.primary:disabled { opacity: .7; cursor: not-allowed; }
.submit-hint { margin-top: 6px; color: #067647; font-size: 13px; font-weight: 700; }
.ghost { background: transparent; border: 1px solid #d0d5dd; padding: 6px 12px; border-radius: 10px; cursor: pointer; }
.ghost:disabled { opacity: .7; cursor: not-allowed; }
.inline-form { display: flex; gap: 12px; align-items: center; }
.inline-form.vertical { flex-direction: column; align-items: stretch; }
.inline-form textarea { flex: 1; min-height: 80px; padding: 10px; border-radius: 12px; border: 1px solid #d0d5dd; }
.inline-form input, .inline-form select { padding: 10px; border-radius: 12px; border: 1px solid #d0d5dd; }
.list { display: grid; gap: 12px; }
.list-item { display: flex; justify-content: space-between; align-items: center; background: #fff; border-radius: 12px; padding: 12px 16px; border: 1px solid #e4e7ec; }
.info-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.info-grid div { background: #fff; padding: 12px; border-radius: 12px; border: 1px solid #e4e7ec; }
.info-grid span { display: block; color: #667085; font-size: 13px; margin-bottom: 6px; }
.muted { color: #667085; margin-top: 8px; }
.table { display: grid; gap: 8px; }
.table-row { display: grid; grid-template-columns: 1.4fr repeat(3, 1fr); gap: 12px; align-items: center; }
.table-head { font-weight: 700; color: #344054; }
.table-row input { width: 100%; padding: 8px; border-radius: 10px; border: 1px solid #d0d5dd; }
.grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 16px; }
.honor { background: #fff; border-radius: 14px; padding: 16px; border: 1px solid #e4e7ec; }
.honor-grid { display: grid; gap: 10px; margin-top: 10px; }
.honor-item { background: #f8fafc; border-radius: 10px; padding: 10px; border: 1px dashed #d0d5dd; }
</style>

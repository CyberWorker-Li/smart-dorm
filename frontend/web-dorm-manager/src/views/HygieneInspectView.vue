<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { apiBase } from '@/apiBase'

interface DormRoomVO {
  id: number
  building: string
  floorNo: number
  roomNo: number
  capacity: number
  valid: boolean
}

interface HygieneTaskVO {
  id: number
  roomId: number
  roomDisplay: string
  dutyDate: string
  dutyUserId: number
  dutyUserName: string
  dutyItem: string
  status: string
  deadlineTime: string
  checkedIn: boolean
  checkinId?: number
  checkinPhotoUrl?: string
  checkinTime?: string
  verifyStatus?: string
  checkinLocationText?: string
  checkinRemark?: string
}

const route = useRoute()
const router = useRouter()
const query = Object.fromEntries(new URLSearchParams(window.location.search))
const managerId = Number(route.query.userId || 0)
const API = apiBase()
const BACKEND_ORIGIN = import.meta.env.VITE_BACKEND_ORIGIN?.trim() || 'http://127.0.0.1:8080'

const rooms = ref<DormRoomVO[]>([])
const selectedRoomId = ref<number | null>(null)
const tasks = ref<HygieneTaskVO[]>([])
const msg = ref('')
const msgType = ref<'success' | 'error'>('success')
const loadingRooms = ref(false)
const loadingTasks = ref(false)

const inspectScore = ref<number | null>(90)
const inspectReason = ref('')

const roomLabel = computed(() => {
  const r = rooms.value.find(x => x.id === selectedRoomId.value)
  if (!r) return ''
  return `${r.building} · ${r.floorNo}层 ${r.roomNo}室`
})

function taskStatusLabel(status?: string): string {
  const s = (status || '').toUpperCase()
  if (s === 'PENDING') return '待打卡'
  if (s === 'COMPLETED') return '已完成'
  if (s === 'OVERDUE') return '已逾期'
  return '未知'
}

function verifyStatusLabel(status?: string): string {
  const s = (status || '').toUpperCase()
  if (!s) return '-'
  if (s === 'PENDING') return '待审核'
  if (s === 'AUTO_APPROVED') return '系统自动通过'
  if (s === 'APPROVED') return '人工通过'
  if (s === 'REJECTED') return '已驳回'
  return '未知'
}

function photoHref(url?: string): string {
  const u = (url || '').trim()
  if (!u) return '#'
  if (u.startsWith('http://') || u.startsWith('https://')) return u
  if (u.startsWith('/')) return `${BACKEND_ORIGIN}${u}`
  return `${BACKEND_ORIGIN}/${u}`
}

function showMsg(text: string, type: 'success' | 'error' = 'success') {
  msg.value = text
  msgType.value = type
  setTimeout(() => (msg.value = ''), 5000)
}

async function loadRooms() {
  if (!managerId) {
    showMsg('缺少 managerId，请从登录门户进入', 'error')
    return
  }
  loadingRooms.value = true
  try {
    const res = await fetch(`${API}/api/dorm-room/my-rooms?managerId=${managerId}`)
    const r = await res.json()
    if (r.code === 200) {
      rooms.value = r.data
      const first = rooms.value[0]
      if (!selectedRoomId.value && first) {
        selectedRoomId.value = first.id
      }
    } else {
      showMsg(r.message || '加载房间失败', 'error')
    }
  } finally {
    loadingRooms.value = false
  }
}

function dateRange() {
  const to = new Date()
  const from = new Date()
  from.setDate(to.getDate() - 14)
  const fmt = (d: Date) => {
    const y = d.getFullYear()
    const m = String(d.getMonth() + 1).padStart(2, '0')
    const dd = String(d.getDate()).padStart(2, '0')
    return `${y}-${m}-${dd}`
  }
  return { from: fmt(from), to: fmt(to) }
}

async function loadTasks() {
  if (!selectedRoomId.value) return
  loadingTasks.value = true
  try {
    const { from, to } = dateRange()
    const res = await fetch(`${API}/api/hygiene/manager/room-tasks?managerId=${managerId}&roomId=${selectedRoomId.value}&from=${from}&to=${to}`)
    const r = await res.json()
    if (r.code === 200) tasks.value = r.data
    else showMsg(r.message || '加载任务失败', 'error')
  } finally {
    loadingTasks.value = false
  }
}

async function verify(checkinId: number, verifyStatus: 'APPROVED' | 'REJECTED') {
  const res = await fetch(`${API}/api/hygiene/manager/verify-checkin`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ managerId, checkinId, verifyStatus }),
  })
  const r = await res.json()
  if (r.code === 200) {
    showMsg('已更新审核状态')
    await loadTasks()
  } else {
    showMsg(r.message || '更新失败', 'error')
  }
}

async function inspect() {
  if (!selectedRoomId.value) return
  if (inspectScore.value === null || inspectScore.value < 0 || inspectScore.value > 100) {
    showMsg('评分须在 0-100', 'error')
    return
  }
  const res = await fetch(`${API}/api/hygiene/manager/inspect`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ managerId, roomId: selectedRoomId.value, score: inspectScore.value, reason: inspectReason.value }),
  })
  const r = await res.json()
  if (r.code === 200) {
    showMsg(`已提交现场评分（ID：${r.data}）`)
    inspectReason.value = ''
  } else {
    showMsg(r.message || '提交失败', 'error')
  }
}

onMounted(async () => {
  await loadRooms()
  await loadTasks()
})
</script>

<template>
  <div class="page">
    <div class="panel">
      <div class="header-row">
        <div>
          <h1>卫生评分</h1>
          <p class="desc">查看值日打卡、审核记录，填写现场评分与扣分原因。</p>
        </div>
        <button class="back-btn" @click="router.push({ path: '/', query })">返回首页</button>
      </div>

      <p v-if="msg" :class="['tip-msg', msgType]">{{ msg }}</p>

      <section class="card">
        <div class="section-title">选择房间</div>
        <div v-if="loadingRooms" class="tip">加载中...</div>
        <div v-else class="row">
          <select v-model="selectedRoomId" @change="loadTasks">
            <option v-for="r in rooms" :key="r.id" :value="r.id">
              {{ r.building }} · {{ r.floorNo }}层 {{ r.roomNo }}室
            </option>
          </select>
          <button class="btn" :disabled="loadingTasks" @click="loadTasks">{{ loadingTasks ? '刷新中...' : '刷新记录' }}</button>
        </div>
        <div v-if="roomLabel" class="tip">当前：{{ roomLabel }}</div>
      </section>

      <section class="card">
        <div class="section-title">现场评分</div>
        <div class="row">
          <input v-model.number="inspectScore" type="number" min="0" max="100" placeholder="0-100" />
          <input v-model="inspectReason" placeholder="扣分原因/改进建议（可选）" />
          <button class="btn primary" @click="inspect">提交评分</button>
        </div>
      </section>

      <section class="card">
        <div class="section-title">最近值日打卡</div>
        <div v-if="tasks.length === 0" class="tip">暂无任务记录。</div>
        <div v-else class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>日期</th>
                <th>值日人</th>
                <th>状态</th>
                <th>打卡</th>
                <th>审核</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="t in tasks" :key="t.id">
                <td>{{ t.dutyDate }}</td>
                <td>{{ t.dutyUserName }}</td>
                <td>{{ taskStatusLabel(t.status) }}</td>
                <td>
                  <div v-if="t.checkedIn">
                    <a :href="photoHref(t.checkinPhotoUrl)" target="_blank" rel="noreferrer">查看照片</a>
                    <div class="sub">{{ t.checkinTime?.replace('T', ' ') }}</div>
                    <div v-if="t.checkinLocationText" class="sub">{{ t.checkinLocationText }}</div>
                    <div v-if="t.checkinRemark" class="sub">{{ t.checkinRemark }}</div>
                  </div>
                  <div v-else class="sub">未打卡</div>
                </td>
                <td>{{ verifyStatusLabel(t.verifyStatus) }}</td>
                <td>
                  <div v-if="t.checkedIn && (t.verifyStatus || '').toUpperCase() === 'PENDING'" class="op">
                    <button class="mini" @click="verify(t.checkinId!, 'APPROVED')">通过</button>
                    <button class="mini danger" @click="verify(t.checkinId!, 'REJECTED')">驳回</button>
                  </div>
                  <div v-else class="sub">{{ t.checkedIn ? '无需操作' : '-' }}</div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.page { min-height: 100vh; padding: 40px; background: #f4f8fb; }
.panel { max-width: 1060px; margin: 0 auto; background: #fff; border-radius: 20px; padding: 32px; box-shadow: 0 16px 36px rgba(15,23,42,.08); }
.header-row { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 12px; }
h1 { margin: 0; color: #0f766e; }
.desc { color: #667085; margin: 10px 0 0; }
.back-btn { border: none; border-radius: 10px; padding: 10px 18px; background: #ccfbf1; color: #0f766e; cursor: pointer; font-weight: 700; }
.tip-msg { font-size: 14px; margin: 10px 0; }
.tip-msg.success { color: #027a48; }
.tip-msg.error { color: #d92d20; }
.card { background: #f8fafc; border: 1px solid #e4e7ec; border-radius: 16px; padding: 20px; margin-top: 16px; }
.section-title { font-size: 16px; font-weight: 800; color: #1f2937; margin-bottom: 12px; }
.row { display: grid; grid-template-columns: 1.2fr 2fr auto; gap: 12px; align-items: center; }
select, input { width: 100%; height: 40px; border: 1px solid #d0d5dd; border-radius: 10px; padding: 0 12px; background: #fff; }
.btn { height: 40px; border: none; border-radius: 10px; padding: 0 16px; background: #eef2ff; color: #3730a3; cursor: pointer; font-weight: 800; white-space: nowrap; }
.btn.primary { background: #0f766e; color: #fff; }
.tip { color: #667085; font-size: 14px; margin-top: 8px; }
.table-wrap { overflow: auto; border-radius: 12px; border: 1px solid #e4e7ec; background: #fff; }
table { width: 100%; border-collapse: collapse; min-width: 860px; }
th, td { padding: 10px 12px; border-bottom: 1px solid #e4e7ec; font-size: 14px; text-align: left; }
th { background: #f3f4f6; color: #344054; font-weight: 800; }
.sub { color: #667085; font-size: 12px; margin-top: 4px; }
.op { display: flex; gap: 8px; }
.mini { height: 30px; border: none; border-radius: 8px; padding: 0 10px; background: #ecfdf3; color: #027a48; cursor: pointer; font-weight: 800; }
.mini.danger { background: #fef3f2; color: #b42318; }
</style>

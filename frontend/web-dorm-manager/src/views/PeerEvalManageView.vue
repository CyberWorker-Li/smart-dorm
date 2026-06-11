<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { apiBase } from '@/apiBase'

interface DormRoomVO {
  id: number
  building: string
  roomNo: string
  capacity: number
  gender: string
}

interface PeerEvalManagerListItemVO {
  evalId: number
  batchId: number
  roomId: number
  roomDisplay: string
  month: string
  submitterStudentId: number
  submitterName: string | null
  createTime: string
  lowRisk: boolean
  lowItemCount: number
}

interface PeerEvalManagerDetailItemVO {
  targetStudentId: number
  targetName: string | null
  scheduleScore: number
  hygieneScore: number
  communicationScore: number
  avgScore: number
  low: boolean
}

interface PeerEvalManagerDetailVO {
  evalId: number
  batchId: number
  roomId: number
  roomDisplay: string
  month: string
  submitterStudentId: number
  submitterName: string | null
  createTime: string
  items: PeerEvalManagerDetailItemVO[]
}

const route = useRoute()
const router = useRouter()
const managerId = computed(() => {
  const raw = route.query.userId
  if (!raw) return ''
  return raw.toString()
})
const query = Object.fromEntries(new URLSearchParams(window.location.search))
const API = apiBase()

const myRooms = ref<DormRoomVO[]>([])
const month = ref(new Date().toISOString().slice(0, 7))
const selectedRoomId = ref<number | null>(null)

const list = ref<PeerEvalManagerListItemVO[]>([])
const loading = ref(false)
const detailLoading = ref(false)
const expandedEvalId = ref<number | null>(null)
const detailMap = ref<Record<number, PeerEvalManagerDetailVO>>({})

const genderLabel: Record<string, string> = { MALE: '男', FEMALE: '女' }

async function readJson<T>(url: string): Promise<T | null> {
  try {
    const res = await fetch(url)
    const r = await res.json()
    return r && r.code === 200 ? (r.data as T) : null
  } catch {
    return null
  }
}

async function loadRooms() {
  if (!managerId.value) return
  const data = await readJson<DormRoomVO[]>(`${API}/api/dorm-room/my-rooms?managerId=${encodeURIComponent(managerId.value)}`)
  myRooms.value = Array.isArray(data) ? data : []
}

function buildListUrl() {
  const params = new URLSearchParams()
  params.set('managerId', managerId.value)
  if (month.value) params.set('month', month.value)
  if (selectedRoomId.value) params.set('roomId', String(selectedRoomId.value))
  return `${API}/api/internal/peer-eval/manager/list?${params.toString()}`
}

async function loadList() {
  if (!managerId.value) return
  loading.value = true
  expandedEvalId.value = null
  try {
    const data = await readJson<PeerEvalManagerListItemVO[]>(buildListUrl())
    list.value = Array.isArray(data) ? data : []
  } finally {
    loading.value = false
  }
}

async function toggleDetail(evalId: number) {
  if (expandedEvalId.value === evalId) {
    expandedEvalId.value = null
    return
  }
  expandedEvalId.value = evalId
  if (detailMap.value[evalId]) return

  detailLoading.value = true
  try {
    const url = `${API}/api/internal/peer-eval/manager/detail?managerId=${encodeURIComponent(managerId.value)}&evalId=${evalId}`
    const data = await readJson<PeerEvalManagerDetailVO>(url)
    if (data) {
      detailMap.value = { ...detailMap.value, [evalId]: data }
    }
  } finally {
    detailLoading.value = false
  }
}

function goHome() {
  router.push({ path: '/', query })
}

onMounted(async () => {
  await loadRooms()
  await loadList()
})
</script>

<template>
  <div class="page">
    <div class="panel">
      <div class="header-row">
        <div>
          <h1>室友互评</h1>
          <p class="desc">查看管辖范围内的互评记录，低分标红提醒。</p>
        </div>
        <button class="back-btn" @click="goHome">返回首页</button>
      </div>

      <div class="filter-row">
        <label>
          <span>月份</span>
          <input v-model="month" placeholder="YYYY-MM" />
        </label>
        <label>
          <span>房间</span>
          <select v-model="selectedRoomId">
            <option :value="null">全部</option>
            <option v-for="r in myRooms" :key="r.id" :value="r.id">
              {{ r.building }} {{ r.roomNo }}（{{ genderLabel[r.gender] }}，{{ r.capacity }}人间）
            </option>
          </select>
        </label>
        <button class="query-btn" :disabled="!managerId" @click="loadList">查询</button>
      </div>

      <div v-if="loading" class="tip">加载中...</div>
      <div v-else-if="list.length === 0" class="empty-box">暂无互评记录</div>
      <div v-else class="list">
        <div v-for="row in list" :key="row.evalId" class="card">
          <div class="card-head">
            <div class="left">
              <div class="title">
                <span class="room">{{ row.roomDisplay }}</span>
                <span class="month">{{ row.month }}</span>
                <span v-if="row.lowRisk" class="badge danger">低分 {{ row.lowItemCount }}</span>
                <span v-else class="badge ok">正常</span>
              </div>
              <div class="sub">
                <span>提交人：{{ row.submitterName || row.submitterStudentId }}</span>
                <span>时间：{{ row.createTime?.slice(0, 16) }}</span>
              </div>
            </div>
            <button class="ghost" @click="toggleDetail(row.evalId)">
              {{ expandedEvalId === row.evalId ? '收起' : '查看明细' }}
            </button>
          </div>

          <div v-if="expandedEvalId === row.evalId" class="detail">
            <div v-if="detailLoading && !detailMap[row.evalId]" class="tip">加载明细中...</div>
            <div v-else-if="detailMap[row.evalId]">
              <div class="table">
                <div class="tr th">
                  <span>室友</span>
                  <span>作息</span>
                  <span>卫生</span>
                  <span>沟通</span>
                  <span>均分</span>
                </div>
                <div
                  v-for="it in (detailMap[row.evalId]?.items || [])"
                  :key="it.targetStudentId"
                  :class="['tr', it.low ? 'low' : '']"
                >
                  <span>{{ it.targetName || it.targetStudentId }}</span>
                  <span>{{ it.scheduleScore }}</span>
                  <span>{{ it.hygieneScore }}</span>
                  <span>{{ it.communicationScore }}</span>
                  <span>{{ it.avgScore.toFixed(1) }}</span>
                </div>
              </div>
              <p class="muted">低分规则：单条互评三项均分 ≤ 4 视为低分。</p>
            </div>
            <div v-else class="tip">明细加载失败</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page { min-height: 100vh; padding: 32px; background: #f4f8fb; }
.panel { max-width: 1100px; margin: 0 auto; background: #fff; border-radius: 20px; padding: 32px; box-shadow: 0 16px 36px rgba(15,23,42,.08); }
.header-row { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 20px; gap: 16px; }
h1 { margin: 0; color: #0f766e; }
.desc { color: #667085; margin: 8px 0 0; }
.back-btn { border: none; border-radius: 10px; padding: 10px 18px; background: #ccfbf1; color: #0f766e; cursor: pointer; font-weight: 700; white-space: nowrap; }

.filter-row { display: flex; align-items: flex-end; gap: 16px; margin-bottom: 18px; flex-wrap: wrap; }
.filter-row label { display: flex; flex-direction: column; gap: 6px; }
.filter-row label span { color: #667085; font-size: 14px; }
.filter-row input, .filter-row select { height: 40px; border: 1px solid #d0d5dd; border-radius: 10px; padding: 0 12px; min-width: 220px; font-size: 14px; background: #fff; }
.query-btn { height: 40px; border: none; border-radius: 10px; background: #0d9488; color: #fff; padding: 0 20px; font-weight: 700; cursor: pointer; }
.query-btn:disabled { opacity: .6; cursor: not-allowed; }

.tip { color: #667085; font-size: 14px; }
.empty-box { background: #f8fafc; border: 1px solid #e4e7ec; border-radius: 12px; padding: 32px; text-align: center; color: #667085; }

.list { display: grid; gap: 14px; }
.card { border: 1px solid #e4e7ec; border-radius: 16px; background: #fff; overflow: hidden; }
.card-head { display: flex; align-items: center; justify-content: space-between; gap: 14px; padding: 14px 16px; background: #f8fafc; }
.left { display: grid; gap: 6px; }
.title { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; }
.room { font-weight: 900; color: #0f766e; }
.month { font-weight: 700; color: #344054; }
.sub { display: flex; flex-wrap: wrap; gap: 14px; color: #667085; font-size: 13px; }

.badge { border-radius: 999px; padding: 3px 10px; font-size: 12px; font-weight: 800; border: 1px solid transparent; }
.badge.ok { background: #ecfdf3; color: #067647; border-color: #abefc6; }
.badge.danger { background: #fef2f2; color: #b42318; border-color: #fecaca; }

.ghost { height: 34px; border: 1px solid #d0d5dd; background: #fff; border-radius: 10px; padding: 0 12px; cursor: pointer; font-weight: 700; color: #344054; }

.detail { padding: 14px 16px; }
.table { display: grid; gap: 8px; }
.tr { display: grid; grid-template-columns: 1.2fr repeat(4, 0.7fr); gap: 10px; align-items: center; background: #fff; border-radius: 12px; padding: 10px 12px; border: 1px solid #eaecf0; }
.tr.th { background: #f9fafb; border-color: #eaecf0; font-weight: 900; color: #344054; }
.tr.low { background: #fef2f2; border-color: #fecaca; }
.muted { margin-top: 10px; color: #667085; font-size: 13px; }
</style>

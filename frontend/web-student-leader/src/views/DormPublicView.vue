<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { apiBase } from '@/apiBase'

interface MyResultVO {
  batchId: number
  roomGender?: string
  building: string
  roomNo: string
}

interface DormPublicRoomVO {
  batchId: number
  roomId: number
  buildingName: string
  floorNo: number
  roomNo: number
  gender: string
  capacity: number
  occupied: number
  vacancies: number
  hygieneAvg: number | null
  sleepTag: string
  wakeTag: string
  hasSwapIntent: boolean
  swapIntentCount: number
}

const route = useRoute()
const router = useRouter()
const userId = computed(() => String(route.query.userId || ''))
const query = computed(() => route.query)
const API = apiBase()

const myResult = ref<MyResultVO | null>(null)
const rooms = ref<DormPublicRoomVO[]>([])
const loading = ref(false)
const onlyVacancy = ref(false)
const onlyIntent = ref(false)

function back() {
  router.push({ path: '/', query: query.value })
}

function toNoise(roomId: number) {
  router.push({ path: '/noise', query: { ...query.value, toRoomId: String(roomId) } })
}

async function loadMyResult() {
  if (!userId.value) return
  const res = await fetch(`${API}/api/assignment/my-result?studentId=${userId.value}`)
  const r = await res.json()
  if (r.code === 200 && r.data) myResult.value = r.data
}

async function loadRooms() {
  loading.value = true
  try {
    const url = API ? new URL(`${API}/api/dorm-public/rooms`) : new URL('/api/dorm-public/rooms', window.location.origin)
    if (myResult.value?.batchId) url.searchParams.set('batchId', String(myResult.value.batchId))
    if (myResult.value?.roomGender) url.searchParams.set('gender', String(myResult.value.roomGender))
    if (onlyVacancy.value) url.searchParams.set('hasVacancy', 'true')
    if (onlyIntent.value) url.searchParams.set('hasSwapIntent', 'true')
    const res = await fetch(url.toString())
    const r = await res.json()
    if (r.code === 200) rooms.value = r.data || []
  } finally {
    loading.value = false
  }
}

async function refreshAll() {
  await loadMyResult()
  await loadRooms()
}

function fmtScore(v: number | null) {
  if (v === null || v === undefined) return '暂无'
  return v.toFixed(1)
}

onMounted(refreshAll)
</script>

<template>
  <div class="page">
    <div class="panel">
      <div class="header-row">
        <div>
          <h1>宿舍信息广场</h1>
          <p class="desc">查看其他宿舍的公开信息（卫生、作息、空位、换宿意愿）。</p>
        </div>
        <button class="back-btn" @click="back">返回首页</button>
      </div>

      <div v-if="myResult" class="current-room">
        <span class="label">当前分配宿舍：</span>
        <strong>{{ myResult.building }} {{ myResult.roomNo }}</strong>
      </div>
      <div v-else class="empty-box">分配结果尚未公示，暂不支持查看宿舍广场。</div>

      <div v-if="myResult" class="toolbar">
        <label class="chk">
          <input v-model="onlyVacancy" type="checkbox" @change="loadRooms" />
          仅看有空位
        </label>
        <label class="chk">
          <input v-model="onlyIntent" type="checkbox" @change="loadRooms" />
          仅看有换宿意愿
        </label>
        <button class="btn" :disabled="loading" @click="refreshAll">{{ loading ? '刷新中...' : '刷新' }}</button>
      </div>

      <div v-if="myResult">
        <div v-if="loading" class="tip">加载中...</div>
        <div v-else-if="rooms.length === 0" class="tip">暂无可展示的宿舍</div>
        <div v-else class="list">
          <div v-for="r in rooms" :key="r.roomId" class="item">
            <div class="main">
              <div class="title">
                <strong>{{ r.buildingName }}</strong>
                <span class="sub">第{{ r.floorNo }}层 {{ r.roomNo }}号</span>
              </div>
              <div class="tags">
                <span class="tag">空位 {{ r.vacancies }}</span>
                <span class="tag dim">卫生 {{ fmtScore(r.hygieneAvg) }}</span>
                <span class="tag dim">作息 {{ r.sleepTag }}/{{ r.wakeTag }}</span>
                <span v-if="r.hasSwapIntent" class="tag dim">意愿 {{ r.swapIntentCount }}</span>
              </div>
            </div>
            <div class="actions">
              <button class="btn-mini" @click="toNoise(r.roomId)">安静请求</button>
              <button class="btn-mini ghost" @click="router.push({ path: '/swap', query })">去换宿</button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page { min-height: 100vh; padding: 32px; background: #f5f7fb; }
.panel { max-width: 1200px; margin: 0 auto; background: #fff; border-radius: 20px; padding: 32px; box-shadow: 0 16px 36px rgba(15,23,42,.08); }
.header-row { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-bottom: 18px; }
h1 { margin: 0; color: #1d4ed8; }
.desc { color: #667085; margin: 8px 0 0; }
.back-btn { border: none; border-radius: 10px; padding: 10px 18px; background: #dbeafe; color: #1e40af; cursor: pointer; font-weight: 700; white-space: nowrap; }
.current-room { background: #eff6ff; border: 1px solid #bfdbfe; border-radius: 12px; padding: 14px 18px; margin-bottom: 16px; font-size: 15px; color: #1e40af; }
.current-room .label { color: #667085; }
.toolbar { display: flex; gap: 12px; align-items: center; margin-bottom: 16px; flex-wrap: wrap; }
.chk { display: flex; gap: 6px; align-items: center; font-size: 13px; color: #475467; }
.btn { height: 36px; border: 1px solid #d0d5dd; border-radius: 10px; padding: 0 14px; background: #fff; color: #344054; cursor: pointer; font-weight: 800; font-size: 13px; }
.list { display: grid; gap: 10px; }
.item { background: #f8fafc; border: 1px solid #e4e7ec; border-radius: 16px; padding: 16px; display: flex; justify-content: space-between; gap: 12px; }
.main { display: grid; gap: 8px; }
.title { display: grid; gap: 2px; color: #111827; }
.sub { color: #667085; font-size: 12px; }
.tags { display: flex; gap: 6px; flex-wrap: wrap; }
.tag { background: #eff6ff; border: 1px solid #bfdbfe; border-radius: 999px; padding: 2px 10px; font-size: 12px; color: #1e40af; font-weight: 800; }
.tag.dim { background: #f3f4f6; border-color: #e5e7eb; color: #6b7280; }
.actions { display: flex; gap: 8px; align-items: center; }
.btn-mini { height: 32px; border: none; border-radius: 10px; padding: 0 14px; background: #2563eb; color: #fff; font-weight: 800; cursor: pointer; font-size: 13px; white-space: nowrap; }
.btn-mini.ghost { background: #fff; color: #344054; border: 1px solid #d0d5dd; }
.tip { color: #667085; font-size: 14px; }
.empty-box { background: #f8fafc; border: 1px solid #e4e7ec; border-radius: 12px; padding: 32px; text-align: center; color: #667085; }
</style>

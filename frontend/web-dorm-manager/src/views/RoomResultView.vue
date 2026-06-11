<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { apiBase } from '@/apiBase'

interface DormRoomVO {
  id: number
  building: string
  roomNo: string
  capacity: number
  gender: string
}

interface BatchVO {
  id: number
  academicYear: string
  status: string
  questionnaireTitle: string
  totalAssigned: number
  publishTime: string | null
}

interface RoommateVO {
  studentId: number
  realName: string
  userNo: string
  gender?: string
  wakeUpTime: string
  sleepTime: string
  personality: string
  hobbies: string
  hometown: string
  bedNo: number
}

const route = useRoute()
const router = useRouter()
const managerId = route.query.userId || ''

const myRooms = ref<DormRoomVO[]>([])
const batches = ref<BatchVO[]>([])
const selectedBatchId = ref<number | null>(null)
const selectedRoomId = ref<number | null>(null)
const roomResult = ref<RoommateVO[]>([])
const loading = ref(false)
const resultLoading = ref(false)

const API = apiBase()

const personalityLabel: Record<string, string> = {
  INTROVERT: '内向', EXTROVERT: '外向', AMBIVERT: '中间型',
}

async function loadData() {
  if (!managerId) return
  loading.value = true
  try {
    const [roomRes, batchRes] = await Promise.all([
      fetch(`${API}/api/dorm-room/my-rooms?managerId=${managerId}`),
      fetch(`${API}/api/assignment/batches`),
    ])
    const roomData = await roomRes.json()
    const batchData = await batchRes.json()
    if (roomData.code === 200) myRooms.value = roomData.data
    if (batchData.code === 200) {
      batches.value = batchData.data.filter((b: BatchVO) => b.status === 'PUBLISHED')
    }
  } finally {
    loading.value = false
  }
}

async function loadRoomResult() {
  if (!selectedBatchId.value || !selectedRoomId.value) return
  resultLoading.value = true
  try {
    const res = await fetch(
      `${API}/api/assignment/room-result?batchId=${selectedBatchId.value}&roomId=${selectedRoomId.value}`
    )
    const r = await res.json()
    if (r.code === 200) roomResult.value = r.data
  } finally {
    resultLoading.value = false
  }
}

const genderLabel: Record<string, string> = { MALE: '男', FEMALE: '女' }

onMounted(loadData)
</script>

<template>
  <div class="page">
    <div class="panel">
      <div class="header-row">
        <div>
          <h1>查看分配结果</h1>
          <p class="desc">查看您负责房间的宿舍分配情况。</p>
        </div>
        <button class="back-btn" @click="router.push({ path: '/', query: route.query })">返回首页</button>
      </div>

      <div v-if="loading" class="tip">加载中...</div>
      <div v-else>
        <div v-if="myRooms.length === 0" class="empty-box">您暂未被分配负责任何房间。</div>
        <div v-else>
          <!-- 筛选条件 -->
          <div class="filter-row">
            <label>
              <span>选择批次</span>
              <select v-model="selectedBatchId" @change="roomResult = []">
                <option :value="null">请选择</option>
                <option v-for="b in batches" :key="b.id" :value="b.id">
                  {{ b.academicYear }} 学年（{{ b.questionnaireTitle }}）
                </option>
              </select>
            </label>
            <label>
              <span>选择房间</span>
              <select v-model="selectedRoomId" @change="roomResult = []">
                <option :value="null">请选择</option>
                <option v-for="r in myRooms" :key="r.id" :value="r.id">
                  {{ r.building }} {{ r.roomNo }}（{{ genderLabel[r.gender] }}，{{ r.capacity }}人间）
                </option>
              </select>
            </label>
            <button class="query-btn" :disabled="!selectedBatchId || !selectedRoomId" @click="loadRoomResult">
              查询
            </button>
          </div>

          <!-- 结果展示 -->
          <div v-if="resultLoading" class="tip">加载中...</div>
          <div v-else-if="roomResult.length > 0">
            <div class="section-title">房间成员（{{ roomResult.length }} 人）</div>
            <div class="member-grid">
              <div v-for="m in roomResult" :key="m.studentId" class="member-card">
                <div class="m-header">
                  <span class="m-name">{{ m.realName }}</span>
                  <span class="m-bed">{{ m.bedNo }} 号床</span>
                </div>
                <div class="m-no">学号：{{ m.userNo || '-' }}</div>
                <div class="m-tags">
                  <span v-if="m.gender" class="tag">{{ genderLabel[m.gender] ?? m.gender }}生</span>
                  <span v-if="m.wakeUpTime" class="tag">起床 {{ m.wakeUpTime }}</span>
                  <span v-if="m.sleepTime" class="tag">睡觉 {{ m.sleepTime }}</span>
                  <span v-if="m.personality" class="tag">{{ personalityLabel[m.personality] || m.personality }}</span>
                  <span v-if="m.hometown" class="tag">{{ m.hometown }}</span>
                </div>
                <div v-if="m.hobbies" class="m-hobbies">爱好：{{ m.hobbies }}</div>
              </div>
            </div>
          </div>
          <div v-else-if="selectedBatchId && selectedRoomId" class="tip">该房间暂无分配记录</div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page { min-height: 100vh; padding: 32px; background: #f4f8fb; }
.panel { max-width: 1000px; margin: 0 auto; background: #fff; border-radius: 20px; padding: 32px; box-shadow: 0 16px 36px rgba(15,23,42,.08); }
.header-row { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 20px; }
h1 { margin: 0; color: #0f766e; }
.desc { color: #667085; margin: 8px 0 0; }
.back-btn { border: none; border-radius: 10px; padding: 10px 18px; background: #ccfbf1; color: #0f766e; cursor: pointer; font-weight: 700; white-space: nowrap; }
.filter-row { display: flex; align-items: flex-end; gap: 16px; margin-bottom: 24px; flex-wrap: wrap; }
.filter-row label { display: flex; flex-direction: column; gap: 6px; }
.filter-row label span { color: #667085; font-size: 14px; }
.filter-row select { height: 40px; border: 1px solid #d0d5dd; border-radius: 10px; padding: 0 12px; min-width: 240px; font-size: 14px; background: #fff; }
.query-btn { height: 40px; border: none; border-radius: 10px; background: #0d9488; color: #fff; padding: 0 20px; font-weight: 700; cursor: pointer; }
.query-btn:disabled { opacity: .6; cursor: not-allowed; }
.section-title { font-size: 16px; font-weight: 700; color: #0f766e; margin-bottom: 16px; border-left: 3px solid #0d9488; padding-left: 10px; }
.member-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 16px; }
.member-card { background: #f8fafc; border: 1px solid #e4e7ec; border-radius: 14px; padding: 16px; }
.m-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.m-name { font-weight: 700; color: #1f2937; font-size: 16px; }
.m-bed { background: #ccfbf1; color: #0f766e; border-radius: 6px; padding: 2px 8px; font-size: 12px; font-weight: 600; }
.m-no { font-size: 13px; color: #667085; margin-bottom: 10px; }
.m-tags { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 8px; }
.tag { background: #f0fdf4; color: #166534; border-radius: 6px; padding: 3px 8px; font-size: 12px; border: 1px solid #bbf7d0; }
.m-hobbies { font-size: 13px; color: #374151; }
.tip { color: #667085; font-size: 14px; }
.empty-box { background: #f8fafc; border: 1px solid #e4e7ec; border-radius: 12px; padding: 32px; text-align: center; color: #667085; }
</style>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { apiBase } from '@/apiBase'

interface AdjustRequestVO {
  id: number
  batchId: number
  currentBuilding: string
  currentRoomNo: string
  targetBuilding: string | null
  targetRoomNo: string | null
  reason: string
  status: string
  handleRemark: string | null
  handleTime: string | null
  createTime: string
}

interface DormRoomVO {
  id: number
  building: string
  roomNo: string
  capacity: number
  gender: string
}

interface MyResultVO {
  batchId: number
  building: string
  roomNo: string
  roomGender?: string
}

const route = useRoute()
const router = useRouter()
const studentId = route.query.userId || ''

const myResult = ref<MyResultVO | null>(null)
const myRequests = ref<AdjustRequestVO[]>([])
const rooms = ref<DormRoomVO[]>([])
const loading = ref(false)
const submitting = ref(false)
const msg = ref('')
const msgType = ref<'success' | 'error'>('success')

const form = ref({ targetRoomId: '', reason: '' })

const API = apiBase()

/** 微调目标房须与本人类型一致，由当前分配房间性别推导 */
const eligibleRooms = computed(() => {
  const g = myResult.value?.roomGender
  if (!g) return rooms.value
  return rooms.value.filter((r) => r.gender === g)
})

function showMsg(text: string, type: 'success' | 'error' = 'success') {
  msg.value = text
  msgType.value = type
  setTimeout(() => (msg.value = ''), 4000)
}

async function loadData() {
  if (!studentId) return
  loading.value = true
  try {
    const [resultRes, reqRes, roomRes] = await Promise.all([
      fetch(`${API}/api/assignment/my-result?studentId=${studentId}`),
      fetch(`${API}/api/adjust-request/my?studentId=${studentId}`),
      fetch(`${API}/api/dorm-room`),
    ])
    const resultData = await resultRes.json()
    const reqData = await reqRes.json()
    const roomData = await roomRes.json()

    if (resultData.code === 200 && resultData.data) myResult.value = resultData.data
    if (reqData.code === 200) myRequests.value = reqData.data
    if (roomData.code === 200) rooms.value = roomData.data
  } finally {
    loading.value = false
  }
}

async function submitRequest() {
  if (!form.value.reason.trim()) {
    showMsg('申请理由不能为空', 'error')
    return
  }
  if (!myResult.value) {
    showMsg('未找到您的分配记录，无法提交申请', 'error')
    return
  }
  submitting.value = true
  try {
    const body: Record<string, unknown> = {
      batchId: myResult.value.batchId,
      studentId: Number(studentId),
      reason: form.value.reason,
    }
    if (form.value.targetRoomId) body.targetRoomId = Number(form.value.targetRoomId)

    const res = await fetch(`${API}/api/adjust-request`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
    const r = await res.json()
    if (r.code === 200) {
      showMsg('申请提交成功，请等待宿管审核')
      form.value = { targetRoomId: '', reason: '' }
      await loadData()
    } else {
      showMsg(r.message || '提交失败', 'error')
    }
  } finally {
    submitting.value = false
  }
}

const statusLabel: Record<string, string> = { PENDING: '待审核', APPROVED: '已通过', REJECTED: '已拒绝' }
const statusColor: Record<string, string> = { PENDING: '#667085', APPROVED: '#027a48', REJECTED: '#d92d20' }

onMounted(loadData)
</script>

<template>
  <div class="page">
    <div class="panel">
      <div class="header-row">
        <div>
          <h1>微调申请</h1>
          <p class="desc">对分配结果有异议？可在此提交申请，由宿管审核处理。</p>
        </div>
        <button class="back-btn" @click="router.push({ path: '/', query: route.query })">返回首页</button>
      </div>

      <p v-if="msg" :class="['tip-msg', msgType]">{{ msg }}</p>

      <div v-if="loading" class="tip">加载中...</div>
      <div v-else>
        <!-- 当前分配信息 -->
        <div v-if="myResult" class="current-room">
          <span class="label">当前分配宿舍：</span>
          <strong>{{ myResult.building }} {{ myResult.roomNo }}</strong>
        </div>
        <div v-else class="empty-box">分配结果尚未公示，暂不支持提交申请。</div>

        <div v-if="myResult" class="layout">
          <!-- 左：提交申请 -->
          <section class="card">
            <div class="section-title">提交新申请</div>
            <div class="form-grid">
              <label style="grid-column: span 2">
                <span>希望调入的房间（可选，不填则仅说明诉求）</span>
                <select v-model="form.targetRoomId">
                  <option value="">不指定目标房间</option>
                  <option v-for="r in eligibleRooms" :key="r.id" :value="r.id">
                    {{ r.building }} {{ r.roomNo }}（{{ r.gender === 'MALE' ? '男' : '女' }}，{{ r.capacity }}人间）
                  </option>
                </select>
              </label>
              <label style="grid-column: span 2">
                <span>申请理由</span>
                <textarea v-model="form.reason" rows="4" placeholder="请详细说明申请原因..." />
              </label>
            </div>
            <button class="submit-btn" :disabled="submitting" @click="submitRequest">
              {{ submitting ? '提交中...' : '提交申请' }}
            </button>
          </section>

          <!-- 右：历史申请 -->
          <section class="card">
            <div class="section-title">我的申请记录</div>
            <div v-if="myRequests.length === 0" class="tip">暂无申请记录</div>
            <div v-else class="req-list">
              <div v-for="req in myRequests" :key="req.id" class="req-item">
                <div class="req-top">
                  <span class="req-room">{{ req.currentBuilding }} {{ req.currentRoomNo }}</span>
                  <span v-if="req.targetBuilding" class="req-arrow">→ {{ req.targetBuilding }} {{ req.targetRoomNo }}</span>
                  <span class="req-status" :style="{ color: statusColor[req.status] }">
                    {{ statusLabel[req.status] }}
                  </span>
                </div>
                <div class="req-reason">{{ req.reason }}</div>
                <div v-if="req.handleRemark" class="req-remark">宿管回复：{{ req.handleRemark }}</div>
                <div class="req-time">{{ req.createTime?.slice(0, 16) }}</div>
              </div>
            </div>
          </section>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page { min-height: 100vh; padding: 32px; background: #f5f7fb; }
.panel { max-width: 1100px; margin: 0 auto; background: #fff; border-radius: 20px; padding: 32px; box-shadow: 0 16px 36px rgba(15,23,42,.08); }
.header-row { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 20px; }
h1 { margin: 0; color: #1d4ed8; }
.desc { color: #667085; margin: 8px 0 0; }
.back-btn { border: none; border-radius: 10px; padding: 10px 18px; background: #dbeafe; color: #1e40af; cursor: pointer; font-weight: 700; white-space: nowrap; }
.current-room { background: #eff6ff; border: 1px solid #bfdbfe; border-radius: 12px; padding: 14px 18px; margin-bottom: 20px; font-size: 15px; color: #1e40af; }
.current-room .label { color: #667085; }
.layout { display: grid; grid-template-columns: 1fr 1fr; gap: 24px; }
.card { background: #f8fafc; border: 1px solid #e4e7ec; border-radius: 16px; padding: 20px; }
.section-title { font-size: 16px; font-weight: 700; color: #344054; margin-bottom: 16px; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
label span { display: block; color: #667085; font-size: 14px; margin-bottom: 6px; }
select, textarea { width: 100%; border: 1px solid #d0d5dd; border-radius: 10px; padding: 8px 12px; background: #fff; font-size: 14px; font-family: inherit; }
select { height: 40px; }
textarea { resize: vertical; }
.submit-btn { margin-top: 16px; width: 100%; height: 44px; border: none; border-radius: 10px; background: #2563eb; color: #fff; font-weight: 700; cursor: pointer; font-size: 15px; }
.submit-btn:disabled { opacity: .7; cursor: not-allowed; }
.req-list { display: grid; gap: 10px; }
.req-item { background: #fff; border: 1px solid #e4e7ec; border-radius: 12px; padding: 12px 14px; }
.req-top { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; flex-wrap: wrap; }
.req-room { font-weight: 700; color: #1f2937; }
.req-arrow { color: #667085; font-size: 13px; }
.req-status { font-size: 13px; font-weight: 600; margin-left: auto; }
.req-reason { font-size: 13px; color: #374151; margin-bottom: 4px; }
.req-remark { font-size: 13px; color: #065f46; background: #d1fae5; border-radius: 6px; padding: 4px 8px; margin-bottom: 4px; }
.req-time { font-size: 12px; color: #9ca3af; }
.tip { color: #667085; font-size: 14px; }
.tip-msg { font-size: 14px; margin-bottom: 12px; }
.tip-msg.success { color: #027a48; }
.tip-msg.error { color: #d92d20; }
.empty-box { background: #f8fafc; border: 1px solid #e4e7ec; border-radius: 12px; padding: 32px; text-align: center; color: #667085; }
</style>

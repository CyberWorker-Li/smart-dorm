<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { apiBase } from '@/apiBase'

interface SwapRequestVO {
  id: number; batchId: number
  studentAId: number; studentAName: string; studentANo: string
  studentABuilding: string; studentARoomNo: string
  studentBId: number; studentBName: string; studentBNo: string
  studentBBuilding: string; studentBRoomNo: string
  initiatorRemark: string | null
  status: string; bConfirmStatus: string | null
  handleRemark: string | null; handleTime: string | null
  createTime: string
}

const route = useRoute()
const router = useRouter()
const managerId = route.query.userId || ''

const requests = ref<SwapRequestVO[]>([])
const loading = ref(false)
const handling = ref<number | null>(null)
const msg = ref('')
const msgType = ref<'success' | 'error'>('success')

const remarkMap = ref<Record<number, string>>({})

const API = apiBase()

function showMsg(text: string, type: 'success' | 'error' = 'success') {
  msg.value = text
  msgType.value = type
  setTimeout(() => (msg.value = ''), 4000)
}

async function loadRequests() {
  if (!managerId) return
  loading.value = true
  try {
    const res = await fetch(`${API}/api/swap-request/pending?managerId=${managerId}`)
    const r = await res.json()
    if (r.code === 200) requests.value = r.data
  } finally {
    loading.value = false
  }
}

async function handle(requestId: number, status: 'APPROVED' | 'REJECTED_BY_MANAGER') {
  handling.value = requestId
  try {
    const res = await fetch(`${API}/api/swap-request/handle`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        requestId,
        handlerId: Number(managerId),
        status,
        handleRemark: remarkMap.value[requestId] || '',
      }),
    })
    const r = await res.json()
    if (r.code === 200) {
      showMsg(status === 'APPROVED' ? '已通过互换申请，双方房间已互换' : '已拒绝互换申请')
      await loadRequests()
    } else {
      showMsg(r.message || '操作失败', 'error')
    }
  } finally {
    handling.value = null
  }
}

onMounted(loadRequests)
</script>

<template>
  <div class="page">
    <div class="panel">
      <div class="header-row">
        <div>
          <h1>审核宿舍互换申请</h1>
          <p class="desc">处理双方均已同意互换的申请，通过后系统自动交换双方房间及床位。</p>
        </div>
        <button class="back-btn" @click="router.push({ path: '/', query: route.query })">返回首页</button>
      </div>

      <p v-if="msg" :class="['tip-msg', msgType]">{{ msg }}</p>

      <div v-if="loading" class="tip">加载中...</div>
      <div v-else-if="requests.length === 0" class="empty-box">
        <div class="empty-icon">✅</div>
        <div class="empty-title">暂无待处理互换申请</div>
        <div class="empty-desc">双方均已同意的互换申请会出现在这里。</div>
      </div>
      <div v-else>
        <div class="count-tip">共 {{ requests.length }} 条待处理互换申请</div>
        <div class="req-list">
          <div v-for="req in requests" :key="req.id" class="req-card">
            <div class="req-time">{{ req.createTime?.slice(0, 16) }}</div>

            <div class="swap-pair">
              <!-- Student A -->
              <div class="student-box a">
                <div class="student-name">{{ req.studentAName }}</div>
                <div class="student-no">{{ req.studentANo }}</div>
                <div class="room-tag a">{{ req.studentABuilding }} {{ req.studentARoomNo }}</div>
              </div>

              <div class="swap-arrow">
                <div class="arrow-icon">⇄</div>
                <div class="arrow-label">互换</div>
              </div>

              <!-- Student B -->
              <div class="student-box b">
                <div class="student-name">{{ req.studentBName }}</div>
                <div class="student-no">{{ req.studentBNo }}</div>
                <div class="room-tag b">{{ req.studentBBuilding }} {{ req.studentBRoomNo }}</div>
              </div>
            </div>

            <div v-if="req.initiatorRemark" class="req-remark">
              <span class="remark-label">发起方备注：</span>{{ req.initiatorRemark }}
            </div>

            <div class="handle-row">
              <input
                v-model="remarkMap[req.id]"
                class="remark-input"
                placeholder="处理备注（可选）"
              />
              <button
                class="btn-approve"
                :disabled="handling === req.id"
                @click="handle(req.id, 'APPROVED')"
              >
                {{ handling === req.id ? '处理中...' : '通过互换' }}
              </button>
              <button
                class="btn-reject"
                :disabled="handling === req.id"
                @click="handle(req.id, 'REJECTED_BY_MANAGER')"
              >
                拒绝
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page { min-height: 100vh; padding: 32px; background: #f4f8fb; }
.panel { max-width: 900px; margin: 0 auto; background: #fff; border-radius: 20px; padding: 32px; box-shadow: 0 16px 36px rgba(15,23,42,.08); }
.header-row { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 20px; }
h1 { margin: 0; color: #0f766e; }
.desc { color: #667085; margin: 8px 0 0; }
.back-btn { border: none; border-radius: 10px; padding: 10px 18px; background: #ccfbf1; color: #0f766e; cursor: pointer; font-weight: 700; white-space: nowrap; }
.count-tip { color: #667085; font-size: 14px; margin-bottom: 16px; }
.req-list { display: grid; gap: 16px; }
.req-card { background: #f8fafc; border: 1px solid #e4e7ec; border-radius: 16px; padding: 20px; }
.req-time { font-size: 13px; color: #9ca3af; margin-bottom: 14px; }
.swap-pair { display: flex; align-items: center; gap: 16px; margin-bottom: 16px; }
.student-box { flex: 1; background: #fff; border-radius: 12px; padding: 16px; text-align: center; }
.student-box.a { border: 1px solid #bfdbfe; background: #eff6ff; }
.student-box.b { border: 1px solid #bbf7d0; background: #f0fdf4; }
.student-name { font-size: 18px; font-weight: 700; color: #1f2937; margin-bottom: 4px; }
.student-no { font-size: 13px; color: #6b7280; margin-bottom: 10px; }
.room-tag { display: inline-block; border-radius: 8px; padding: 4px 12px; font-size: 14px; font-weight: 700; }
.room-tag.a { background: #dbeafe; color: #1e40af; }
.room-tag.b { background: #d1fae5; color: #065f46; }
.swap-arrow { text-align: center; min-width: 60px; }
.arrow-icon { font-size: 32px; color: #0d9488; }
.arrow-label { font-size: 12px; color: #9ca3af; margin-top: 4px; }
.req-remark { font-size: 14px; color: #374151; margin-bottom: 14px; line-height: 1.6; background: #f9fafb; border-radius: 8px; padding: 10px 12px; }
.remark-label { color: #667085; }
.handle-row { display: flex; align-items: center; gap: 10px; }
.remark-input { flex: 1; height: 38px; border: 1px solid #d0d5dd; border-radius: 8px; padding: 0 12px; font-size: 14px; background: #fff; }
.btn-approve, .btn-reject { height: 38px; border: none; border-radius: 8px; padding: 0 20px; font-weight: 700; cursor: pointer; font-size: 14px; white-space: nowrap; }
.btn-approve { background: #0d9488; color: #fff; }
.btn-reject { background: #fee2e2; color: #b91c1c; }
.btn-approve:disabled, .btn-reject:disabled { opacity: .6; cursor: not-allowed; }
.tip { color: #667085; font-size: 14px; }
.tip-msg { font-size: 14px; margin-bottom: 12px; }
.tip-msg.success { color: #027a48; }
.tip-msg.error { color: #d92d20; }
.empty-box { text-align: center; padding: 60px 20px; }
.empty-icon { font-size: 48px; margin-bottom: 12px; }
.empty-title { font-size: 18px; font-weight: 700; color: #344054; margin-bottom: 8px; }
.empty-desc { color: #667085; }
</style>

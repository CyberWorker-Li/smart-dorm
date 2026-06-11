<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { apiBase } from '@/apiBase'

interface AdjustRequestVO {
  id: number
  batchId: number
  studentId: number
  studentName: string
  studentNo: string
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

const route = useRoute()
const router = useRouter()
const managerId = route.query.userId || ''

const requests = ref<AdjustRequestVO[]>([])
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
    const res = await fetch(`${API}/api/adjust-request/pending?managerId=${managerId}`)
    const r = await res.json()
    if (r.code === 200) requests.value = r.data
  } finally {
    loading.value = false
  }
}

async function handle(requestId: number, status: 'APPROVED' | 'REJECTED') {
  handling.value = requestId
  try {
    const res = await fetch(`${API}/api/adjust-request/handle`, {
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
      showMsg(status === 'APPROVED' ? '已通过申请' : '已拒绝申请')
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
          <h1>审核微调申请</h1>
          <p class="desc">处理学生提交的宿舍调换申请，通过后系统自动更新分配结果。</p>
        </div>
        <button class="back-btn" @click="router.push({ path: '/', query: route.query })">返回首页</button>
      </div>

      <p v-if="msg" :class="['tip-msg', msgType]">{{ msg }}</p>

      <div v-if="loading" class="tip">加载中...</div>
      <div v-else-if="requests.length === 0" class="empty-box">
        <div class="empty-icon">✅</div>
        <div class="empty-title">暂无待处理申请</div>
        <div class="empty-desc">您负责房间的学生暂未提交微调申请。</div>
      </div>
      <div v-else>
        <div class="count-tip">共 {{ requests.length }} 条待处理申请</div>
        <div class="req-list">
          <div v-for="req in requests" :key="req.id" class="req-card">
            <div class="req-header">
              <div class="req-student">
                <span class="student-name">{{ req.studentName }}</span>
                <span class="student-no">{{ req.studentNo }}</span>
              </div>
              <div class="req-time">{{ req.createTime?.slice(0, 16) }}</div>
            </div>

            <div class="req-rooms">
              <div class="room-box current">
                <span class="room-label">当前宿舍</span>
                <span class="room-val">{{ req.currentBuilding }} {{ req.currentRoomNo }}</span>
              </div>
              <div v-if="req.targetBuilding" class="arrow">→</div>
              <div v-if="req.targetBuilding" class="room-box target">
                <span class="room-label">希望调入</span>
                <span class="room-val">{{ req.targetBuilding }} {{ req.targetRoomNo }}</span>
              </div>
              <div v-else class="room-box no-target">
                <span class="room-label">目标房间</span>
                <span class="room-val">未指定</span>
              </div>
            </div>

            <div class="req-reason">
              <span class="reason-label">申请理由：</span>{{ req.reason }}
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
                {{ handling === req.id ? '处理中...' : '通过' }}
              </button>
              <button
                class="btn-reject"
                :disabled="handling === req.id"
                @click="handle(req.id, 'REJECTED')"
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
.req-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; }
.req-student { display: flex; align-items: center; gap: 10px; }
.student-name { font-size: 18px; font-weight: 700; color: #1f2937; }
.student-no { background: #f3f4f6; color: #6b7280; border-radius: 6px; padding: 2px 8px; font-size: 13px; }
.req-time { font-size: 13px; color: #9ca3af; }
.req-rooms { display: flex; align-items: center; gap: 12px; margin-bottom: 14px; }
.room-box { background: #fff; border: 1px solid #e4e7ec; border-radius: 10px; padding: 10px 14px; min-width: 120px; }
.room-box.current { border-color: #bfdbfe; background: #eff6ff; }
.room-box.target { border-color: #bbf7d0; background: #f0fdf4; }
.room-box.no-target { border-color: #e5e7eb; background: #f9fafb; }
.room-label { display: block; font-size: 12px; color: #9ca3af; margin-bottom: 4px; }
.room-val { font-weight: 700; color: #1f2937; }
.arrow { font-size: 20px; color: #9ca3af; }
.req-reason { font-size: 14px; color: #374151; margin-bottom: 14px; line-height: 1.6; }
.reason-label { color: #667085; }
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

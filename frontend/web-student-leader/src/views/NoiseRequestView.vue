<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { apiBase } from '@/apiBase'

interface MyResultVO {
  batchId: number
  building: string
  roomNo: string
}

interface NoiseRequestVO {
  id: number
  batchId: number
  fromRoomDisplay: string
  toRoomDisplay: string
  content: string
  status: string
  ackTime: string | null
  escalateTime: string | null
  handlerName: string | null
  handleRemark: string | null
  handleTime: string | null
  createTime: string
}

const route = useRoute()
const router = useRouter()
const userId = computed(() => String(route.query.userId || ''))
const toRoomId = computed(() => String(route.query.toRoomId || ''))
const query = computed(() => route.query)
const API = apiBase()

const myResult = ref<MyResultVO | null>(null)
const content = ref('当前时间已过宿舍休息时间，请降低音量。谢谢配合。')

const outgoing = ref<NoiseRequestVO[]>([])
const incoming = ref<NoiseRequestVO[]>([])
const loading = ref(false)
const sending = ref(false)
const msg = ref('')
const msgType = ref<'success' | 'error'>('success')

function showMsg(text: string, type: 'success' | 'error' = 'success') {
  msg.value = text
  msgType.value = type
  setTimeout(() => (msg.value = ''), 4000)
}

function back() {
  router.push({ path: '/', query: query.value })
}

async function loadMyResult() {
  if (!userId.value) return
  const res = await fetch(`${API}/api/assignment/my-result?studentId=${userId.value}`)
  const r = await res.json()
  if (r.code === 200 && r.data) myResult.value = r.data
}

async function loadLists() {
  if (!userId.value) return
  loading.value = true
  try {
    const [a, b] = await Promise.all([
      fetch(`${API}/api/noise-request/my?studentId=${userId.value}`),
      fetch(`${API}/api/noise-request/incoming?studentId=${userId.value}`),
    ])
    const ra = await a.json()
    const rb = await b.json()
    if (ra.code === 200) outgoing.value = ra.data || []
    if (rb.code === 200) incoming.value = rb.data || []
  } finally {
    loading.value = false
  }
}

async function send() {
  if (!myResult.value) {
    showMsg('分配结果尚未公示，无法发起请求', 'error')
    return
  }
  if (!toRoomId.value) {
    showMsg('未选择目标宿舍', 'error')
    return
  }
  if (!content.value.trim()) {
    showMsg('请输入提醒内容', 'error')
    return
  }
  sending.value = true
  try {
    const res = await fetch(`${API}/api/noise-request`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        batchId: myResult.value.batchId,
        fromStudentId: Number(userId.value),
        toRoomId: Number(toRoomId.value),
        content: content.value,
      }),
    })
    const r = await res.json()
    if (r.code === 200) {
      showMsg('已发出安静请求')
      await loadLists()
    } else {
      showMsg(r.message || '发送失败', 'error')
    }
  } finally {
    sending.value = false
  }
}

async function ack(id: number) {
  const res = await fetch(`${API}/api/noise-request/${id}/ack?studentId=${userId.value}`, { method: 'PUT' })
  const r = await res.json()
  if (r.code === 200) {
    showMsg('已知悉并反馈给对方')
    await loadLists()
  } else {
    showMsg(r.message || '操作失败', 'error')
  }
}

async function escalate(id: number) {
  const res = await fetch(`${API}/api/noise-request/${id}/escalate?studentId=${userId.value}`, { method: 'PUT' })
  const r = await res.json()
  if (r.code === 200) {
    showMsg('已升级给宿管处理')
    await loadLists()
  } else {
    showMsg(r.message || '操作失败', 'error')
  }
}

async function cancel(id: number) {
  const res = await fetch(`${API}/api/noise-request/${id}/cancel?studentId=${userId.value}`, { method: 'PUT' })
  const r = await res.json()
  if (r.code === 200) {
    showMsg('已撤销请求')
    await loadLists()
  } else {
    showMsg(r.message || '操作失败', 'error')
  }
}

const statusLabel: Record<string, string> = {
  PENDING: '已发出',
  ACKED: '已知悉',
  ESCALATED: '宿管处理中',
  RESOLVED: '已处理',
  CANCELLED: '已撤销',
}

onMounted(async () => {
  await loadMyResult()
  await loadLists()
})
</script>

<template>
  <div class="page">
    <div class="panel">
      <div class="header-row">
        <div>
          <h1>噪音提醒</h1>
          <p class="desc">向目标宿舍发起安静请求，必要时升级宿管介入处理。</p>
        </div>
        <button class="back-btn" @click="back">返回首页</button>
      </div>

      <p v-if="msg" :class="['tip-msg', msgType]">{{ msg }}</p>

      <div v-if="myResult" class="current-room">
        <span class="label">当前分配宿舍：</span>
        <strong>{{ myResult.building }} {{ myResult.roomNo }}</strong>
      </div>
      <div v-else class="empty-box">分配结果尚未公示，暂不支持发起安静请求。</div>

      <div v-if="myResult" class="card">
        <div class="section-title">发起安静请求</div>
        <div class="form-row">
          <div class="tip">目标宿舍ID：{{ toRoomId || '未选择' }}</div>
          <button class="btn-mini ghost" @click="router.push({ path: '/dorm-public', query })">去选择目标宿舍</button>
        </div>
        <textarea v-model="content" rows="3" placeholder="请输入提醒内容..." />
        <button class="btn-primary" :disabled="sending" @click="send">{{ sending ? '发送中...' : '发送' }}</button>
      </div>

      <div class="layout">
        <section class="card">
          <div class="section-title">我发起的</div>
          <div v-if="loading" class="tip">加载中...</div>
          <div v-else-if="outgoing.length === 0" class="tip">暂无记录</div>
          <div v-else class="list">
            <div v-for="r in outgoing" :key="r.id" class="item">
              <div class="top">
                <strong>{{ r.toRoomDisplay }}</strong>
                <span class="status">{{ statusLabel[r.status] || r.status }}</span>
              </div>
              <div class="text">{{ r.content }}</div>
              <div class="time">{{ r.createTime?.slice(0, 16) }}</div>
              <div class="actions">
                <button v-if="r.status !== 'RESOLVED' && r.status !== 'CANCELLED'" class="btn-mini" @click="escalate(r.id)">升级宿管</button>
                <button v-if="r.status === 'PENDING'" class="btn-mini ghost" @click="cancel(r.id)">撤销</button>
              </div>
              <div v-if="r.handleRemark" class="remark">宿管处理：{{ r.handleRemark }}</div>
            </div>
          </div>
        </section>

        <section class="card">
          <div class="section-title">我收到的</div>
          <div v-if="loading" class="tip">加载中...</div>
          <div v-else-if="incoming.length === 0" class="tip">暂无记录</div>
          <div v-else class="list">
            <div v-for="r in incoming" :key="r.id" class="item">
              <div class="top">
                <strong>{{ r.fromRoomDisplay }}</strong>
                <span class="status">{{ statusLabel[r.status] || r.status }}</span>
              </div>
              <div class="text">{{ r.content }}</div>
              <div class="time">{{ r.createTime?.slice(0, 16) }}</div>
              <div class="actions">
                <button v-if="r.status === 'PENDING'" class="btn-mini" @click="ack(r.id)">已知悉</button>
              </div>
              <div v-if="r.handleRemark" class="remark">宿管处理：{{ r.handleRemark }}</div>
            </div>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page { min-height: 100vh; padding: 32px; background: #f5f7fb; }
.panel { max-width: 1200px; margin: 0 auto; background: #fff; border-radius: 20px; padding: 32px; box-shadow: 0 16px 36px rgba(15,23,42,.08); }
.header-row { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-bottom: 16px; }
h1 { margin: 0; color: #1d4ed8; }
.desc { color: #667085; margin: 8px 0 0; }
.back-btn { border: none; border-radius: 10px; padding: 10px 18px; background: #dbeafe; color: #1e40af; cursor: pointer; font-weight: 700; white-space: nowrap; }
.current-room { background: #eff6ff; border: 1px solid #bfdbfe; border-radius: 12px; padding: 14px 18px; margin-bottom: 16px; font-size: 15px; color: #1e40af; }
.current-room .label { color: #667085; }
.card { background: #f8fafc; border: 1px solid #e4e7ec; border-radius: 16px; padding: 18px; margin-bottom: 16px; }
.section-title { font-size: 16px; font-weight: 900; color: #344054; margin-bottom: 10px; }
.form-row { display: flex; justify-content: space-between; gap: 10px; align-items: center; margin-bottom: 10px; flex-wrap: wrap; }
.tip { color: #667085; font-size: 14px; }
textarea { width: 100%; border: 1px solid #d0d5dd; border-radius: 12px; padding: 10px 12px; font-size: 14px; font-family: inherit; background: #fff; box-sizing: border-box; }
.btn-primary { margin-top: 10px; height: 40px; border: none; border-radius: 10px; padding: 0 16px; background: #2563eb; color: #fff; font-weight: 900; cursor: pointer; }
.btn-primary:disabled { opacity: .7; cursor: not-allowed; }
.layout { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.list { display: grid; gap: 10px; }
.item { background: #fff; border: 1px solid #e4e7ec; border-radius: 12px; padding: 12px 14px; }
.top { display: flex; justify-content: space-between; gap: 10px; align-items: center; }
.status { font-size: 12px; font-weight: 900; color: #6b7280; }
.text { margin-top: 8px; font-size: 13px; color: #111827; white-space: pre-wrap; }
.time { margin-top: 6px; font-size: 12px; color: #9ca3af; }
.actions { margin-top: 10px; display: flex; gap: 8px; }
.btn-mini { height: 32px; border: none; border-radius: 10px; padding: 0 14px; background: #0d9488; color: #fff; font-weight: 900; cursor: pointer; font-size: 13px; }
.btn-mini.ghost { background: #fff; color: #344054; border: 1px solid #d0d5dd; }
.remark { margin-top: 8px; font-size: 13px; color: #065f46; background: #d1fae5; border-radius: 10px; padding: 8px 10px; }
.tip-msg { font-size: 14px; margin-bottom: 12px; }
.tip-msg.success { color: #027a48; }
.tip-msg.error { color: #d92d20; }
.empty-box { background: #f8fafc; border: 1px solid #e4e7ec; border-radius: 12px; padding: 32px; text-align: center; color: #667085; }
</style>


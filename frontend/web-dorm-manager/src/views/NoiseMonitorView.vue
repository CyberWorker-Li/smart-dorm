<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { apiBase } from '@/apiBase'

interface NoiseRequestVO {
  id: number
  fromRoomDisplay: string
  toRoomDisplay: string
  content: string
  status: string
  ackTime: string | null
  escalateTime: string | null
  handleRemark: string | null
  handleTime: string | null
  handlerName: string | null
  createTime: string
}

const route = useRoute()
const router = useRouter()
const managerId = String(route.query.userId || '')
const query = Object.fromEntries(new URLSearchParams(window.location.search))
const API = apiBase()

const list = ref<NoiseRequestVO[]>([])
const loading = ref(false)
const handling = ref<number | null>(null)
const remarkMap = ref<Record<number, string>>({})
const msg = ref('')
const msgType = ref<'success' | 'error'>('success')

function showMsg(text: string, type: 'success' | 'error' = 'success') {
  msg.value = text
  msgType.value = type
  setTimeout(() => (msg.value = ''), 4000)
}

function back() {
  router.push({ path: '/', query })
}

async function loadList() {
  if (!managerId) return
  loading.value = true
  try {
    const res = await fetch(`${API}/api/noise-request/for-manager?managerId=${managerId}`)
    const r = await res.json()
    if (r.code === 200) list.value = r.data || []
  } finally {
    loading.value = false
  }
}

async function resolve(id: number) {
  handling.value = id
  try {
    const remark = remarkMap.value[id] || ''
    const url = new URL(`${API}/api/noise-request/${id}/resolve`)
    url.searchParams.set('managerId', managerId)
    if (remark) url.searchParams.set('remark', remark)
    const res = await fetch(url.toString(), { method: 'PUT' })
    const r = await res.json()
    if (r.code === 200) {
      showMsg('已处理并通知双方宿舍')
      await loadList()
    } else {
      showMsg(r.message || '操作失败', 'error')
    }
  } finally {
    handling.value = null
  }
}

const statusLabel: Record<string, string> = {
  PENDING: '已发出',
  ACKED: '对方已知悉',
  ESCALATED: '已升级',
  RESOLVED: '已处理',
  CANCELLED: '已撤销',
}

onMounted(loadList)
</script>

<template>
  <div class="page">
    <div class="panel">
      <div class="header-row">
        <div>
          <h1>噪音提醒</h1>
          <p class="desc">实时查看并处理所管辖宿舍的安静请求。</p>
        </div>
        <button class="back-btn" @click="back">返回首页</button>
      </div>

      <p v-if="msg" :class="['tip-msg', msgType]">{{ msg }}</p>

      <div class="toolbar">
        <button class="btn-mini" :disabled="loading" @click="loadList">{{ loading ? '刷新中...' : '刷新' }}</button>
      </div>

      <div v-if="loading" class="tip">加载中...</div>
      <div v-else-if="list.length === 0" class="tip">暂无噪音提醒</div>
      <div v-else class="list">
        <div v-for="r in list" :key="r.id" class="item">
          <div class="top">
            <strong>{{ r.fromRoomDisplay }} → {{ r.toRoomDisplay }}</strong>
            <span class="status">{{ statusLabel[r.status] || r.status }}</span>
          </div>
          <div class="text">{{ r.content }}</div>
          <div class="meta">
            <span>时间：{{ r.createTime?.slice(0, 16) }}</span>
            <span v-if="r.ackTime">知悉：{{ r.ackTime?.slice(0, 16) }}</span>
            <span v-if="r.escalateTime">升级：{{ r.escalateTime?.slice(0, 16) }}</span>
          </div>
          <div v-if="r.status !== 'RESOLVED' && r.status !== 'CANCELLED'" class="handle">
            <input v-model="remarkMap[r.id]" placeholder="处理备注（可选）" />
            <button class="btn-primary" :disabled="handling === r.id" @click="resolve(r.id)">
              {{ handling === r.id ? '处理中...' : '标记已处理' }}
            </button>
          </div>
          <div v-if="r.handleRemark" class="remark">处理备注：{{ r.handleRemark }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page { min-height: 100vh; padding: 32px; background: #f4f8fb; }
.panel { max-width: 1200px; margin: 0 auto; background: #fff; border-radius: 20px; padding: 32px; box-shadow: 0 16px 36px rgba(15,23,42,.08); }
.header-row { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-bottom: 16px; }
h1 { margin: 0; color: #0f766e; }
.desc { color: #667085; margin: 8px 0 0; }
.back-btn { border: none; border-radius: 10px; padding: 10px 18px; background: #d1fae5; color: #065f46; cursor: pointer; font-weight: 800; white-space: nowrap; }
.toolbar { display: flex; justify-content: flex-end; margin-bottom: 12px; }
.btn-mini { height: 32px; border: 1px solid #d0d5dd; border-radius: 10px; padding: 0 14px; background: #fff; color: #344054; cursor: pointer; font-weight: 800; font-size: 13px; }
.list { display: grid; gap: 10px; }
.item { background: #f8fafc; border: 1px solid #e4e7ec; border-radius: 16px; padding: 16px; }
.top { display: flex; justify-content: space-between; gap: 10px; align-items: center; }
.status { font-size: 12px; font-weight: 900; color: #667085; }
.text { margin-top: 8px; font-size: 13px; color: #111827; white-space: pre-wrap; }
.meta { margin-top: 8px; display: flex; gap: 10px; flex-wrap: wrap; color: #667085; font-size: 12px; }
.handle { margin-top: 10px; display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
input { flex: 1; min-width: 240px; border: 1px solid #d0d5dd; border-radius: 12px; padding: 8px 10px; font-size: 14px; background: #fff; }
.btn-primary { height: 36px; border: none; border-radius: 10px; padding: 0 16px; background: #0d9488; color: #fff; font-weight: 900; cursor: pointer; }
.btn-primary:disabled { opacity: .7; cursor: not-allowed; }
.remark { margin-top: 10px; background: #eff6ff; border: 1px solid #bfdbfe; border-radius: 12px; padding: 10px 12px; color: #1e40af; font-weight: 900; font-size: 13px; }
.tip { color: #667085; font-size: 14px; }
.tip-msg { font-size: 14px; margin-bottom: 12px; }
.tip-msg.success { color: #027a48; }
.tip-msg.error { color: #d92d20; }
</style>


<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { apiBase } from '@/apiBase'

interface NotifyInboxVO {
  inboxId: number
  bizType: string
  bizId: number
  title: string
  summary: string | null
  readTime: string | null
  time: string
}

interface UnreadCountVO {
  total: number
}

interface NoticeVO {
  id: number
  title: string
  content: string
  publisherName: string | null
  publishTime: string | null
}

interface NoiseRequestVO {
  id: number
  fromRoomDisplay: string
  toRoomDisplay: string
  content: string
  status: string
  createTime: string
  handleRemark: string | null
}

const route = useRoute()
const router = useRouter()
const userId = computed(() => String(route.query.userId || ''))
const query = computed(() => route.query)

const API = apiBase()

const unread = ref<UnreadCountVO | null>(null)
const items = ref<NotifyInboxVO[]>([])
const loading = ref(false)
const unreadOnly = ref(false)
const bizType = ref('')

const detailTitle = ref('')
const detailText = ref('')
const detailTime = ref('')

function back() {
  router.push({ path: '/', query: query.value })
}

async function loadUnread() {
  if (!userId.value) return
  const res = await fetch(`${API}/api/notify/unread-count?userId=${userId.value}`)
  const r = await res.json()
  if (r.code === 200) unread.value = r.data
}

async function loadInbox() {
  if (!userId.value) return
  loading.value = true
  try {
    const url = new URL(`${API}/api/notify/inbox`, window.location.origin)
    url.searchParams.set('userId', userId.value)
    url.searchParams.set('limit', '100')
    if (unreadOnly.value) url.searchParams.set('unreadOnly', 'true')
    if (bizType.value) url.searchParams.set('bizType', bizType.value)
    const res = await fetch(url.toString())
    const r = await res.json()
    if (r.code === 200) items.value = r.data || []
  } finally {
    loading.value = false
  }
}

async function refreshAll() {
  await Promise.all([loadUnread(), loadInbox()])
}

async function markRead(inboxId: number) {
  if (!userId.value) return
  await fetch(`${API}/api/notify/${inboxId}/read?userId=${userId.value}`, { method: 'PUT' })
  await refreshAll()
}

async function openDetail(item: NotifyInboxVO) {
  detailTitle.value = item.title
  detailText.value = item.summary || ''
  detailTime.value = item.time?.slice(0, 16) || ''

  if (item.bizType === 'NOTICE') {
    const res = await fetch(`${API}/api/notice/${item.bizId}`)
    const r = await res.json()
    if (r.code === 200 && r.data) {
      const n: NoticeVO = r.data
      detailTitle.value = n.title
      detailText.value = n.content
      detailTime.value = (n.publishTime || item.time)?.slice(0, 16) || ''
    }
  } else if (item.bizType === 'NOISE_REQUEST') {
    const [a, b] = await Promise.all([
      fetch(`${API}/api/noise-request/my?studentId=${userId.value}`),
      fetch(`${API}/api/noise-request/incoming?studentId=${userId.value}`),
    ])
    const ra = await a.json()
    const rb = await b.json()
    const all: NoiseRequestVO[] = []
    if (ra.code === 200 && Array.isArray(ra.data)) all.push(...ra.data)
    if (rb.code === 200 && Array.isArray(rb.data)) all.push(...rb.data)
    const found = all.find(x => x.id === item.bizId)
    if (found) {
      detailTitle.value = `安静请求（${found.status}）`
      detailText.value = `${found.fromRoomDisplay} → ${found.toRoomDisplay}\n\n${found.content}${found.handleRemark ? `\n\n宿管处理：${found.handleRemark}` : ''}`
      detailTime.value = found.createTime?.slice(0, 16) || detailTime.value
    }
  }

  await markRead(item.inboxId)
}

onMounted(refreshAll)
</script>

<template>
  <div class="page">
    <div class="panel">
      <div class="header-row">
        <div>
          <h1>消息中心</h1>
          <p class="desc">查看公共通知与跨宿舍提醒，支持已读留痕。</p>
        </div>
        <button class="back-btn" @click="back">返回首页</button>
      </div>

      <div class="toolbar">
        <div class="left">
          <span class="badge">未读：{{ unread?.total ?? 0 }}</span>
          <label class="chk">
            <input v-model="unreadOnly" type="checkbox" @change="loadInbox" />
            仅未读
          </label>
          <select v-model="bizType" @change="loadInbox">
            <option value="">全部类型</option>
            <option value="NOTICE">公共通知</option>
            <option value="NOISE_REQUEST">噪音提醒</option>
          </select>
        </div>
        <button class="btn" :disabled="loading" @click="refreshAll">{{ loading ? '刷新中...' : '刷新' }}</button>
      </div>

      <div class="layout">
        <section class="card">
          <div v-if="loading" class="tip">加载中...</div>
          <div v-else-if="items.length === 0" class="tip">暂无消息</div>
          <div v-else class="list">
            <div v-for="it in items" :key="it.inboxId" class="item" :class="{ unread: !it.readTime }">
              <div class="top">
                <span class="title">{{ it.title }}</span>
                <span class="time">{{ it.time?.slice(0, 16) }}</span>
              </div>
              <div v-if="it.summary" class="summary">{{ it.summary }}</div>
              <div class="actions">
                <button class="btn-mini" @click="openDetail(it)">查看</button>
                <button v-if="!it.readTime" class="btn-mini ghost" @click="markRead(it.inboxId)">标记已读</button>
              </div>
            </div>
          </div>
        </section>

        <section class="card">
          <div class="detail-title">{{ detailTitle || '详情' }}</div>
          <div v-if="detailTime" class="detail-time">{{ detailTime }}</div>
          <pre class="detail-text">{{ detailText || '点击左侧消息查看详情' }}</pre>
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
.toolbar { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin: 12px 0 18px; }
.left { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.badge { background: #eff6ff; border: 1px solid #bfdbfe; color: #1e40af; border-radius: 999px; padding: 4px 10px; font-weight: 800; font-size: 12px; }
.chk { display: flex; gap: 6px; align-items: center; font-size: 13px; color: #475467; }
select { border: 1px solid #d0d5dd; border-radius: 10px; padding: 6px 10px; background: #fff; font-size: 13px; }
.btn { height: 36px; border: 1px solid #d0d5dd; border-radius: 10px; padding: 0 14px; background: #fff; color: #344054; cursor: pointer; font-weight: 800; font-size: 13px; }
.layout { display: grid; grid-template-columns: 1fr 1fr; gap: 18px; }
.card { background: #f8fafc; border: 1px solid #e4e7ec; border-radius: 16px; padding: 18px; }
.list { display: grid; gap: 10px; }
.item { background: #fff; border: 1px solid #e4e7ec; border-radius: 12px; padding: 12px 14px; }
.item.unread { border-color: #bfdbfe; box-shadow: 0 0 0 3px rgba(59,130,246,.12); }
.top { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.title { font-weight: 900; color: #111827; }
.time { color: #98a2b3; font-size: 12px; white-space: nowrap; }
.summary { margin-top: 6px; color: #475467; font-size: 13px; }
.actions { margin-top: 10px; display: flex; gap: 8px; }
.btn-mini { height: 30px; border: none; border-radius: 8px; padding: 0 12px; background: #2563eb; color: #fff; font-weight: 800; cursor: pointer; font-size: 12px; }
.btn-mini.ghost { background: #fff; color: #344054; border: 1px solid #d0d5dd; }
.tip { color: #667085; font-size: 14px; }
.detail-title { font-weight: 900; color: #344054; margin-bottom: 6px; }
.detail-time { color: #98a2b3; font-size: 12px; margin-bottom: 10px; }
.detail-text { white-space: pre-wrap; margin: 0; font-size: 13px; color: #111827; background: #fff; border: 1px solid #e4e7ec; border-radius: 12px; padding: 12px; min-height: 260px; }
</style>

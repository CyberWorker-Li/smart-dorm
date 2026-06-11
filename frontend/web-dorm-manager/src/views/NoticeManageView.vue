<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { apiBase } from '@/apiBase'

interface DormBuilding {
  id: number
  name: string
  gender: string
  valid: boolean
}

interface DormFloor {
  id: number
  buildingId: number
  floorNo: number
  valid: boolean
}

interface NoticeVO {
  id: number
  title: string
  content: string
  scopeType: string
  buildingId: number | null
  floorId: number | null
  status: string
  publishTime: string | null
  createTime: string
}

interface NoticeStatsVO {
  totalRecipients: number
  readCount: number
  unreadCount: number
}

const route = useRoute()
const router = useRouter()
const managerId = computed(() => String(route.query.userId || ''))
const query = computed(() => route.query)
const API = apiBase()

const buildings = ref<DormBuilding[]>([])
const floors = ref<DormFloor[]>([])

const form = ref({
  title: '',
  content: '',
  scopeType: 'ALL',
  buildingId: '',
  floorId: '',
})

const notices = ref<NoticeVO[]>([])
const statsMap = ref<Record<number, NoticeStatsVO>>({})
const loading = ref(false)
const creating = ref(false)
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

async function loadBuildings() {
  const res = await fetch(`${API}/api/dorm/settings/search/buildings`)
  const r = await res.json()
  if (r.code === 200) buildings.value = r.data || []
}

async function loadFloors() {
  floors.value = []
  if (!form.value.buildingId) return
  const res = await fetch(`${API}/api/dorm/settings/search/floors?buildingId=${form.value.buildingId}`)
  const r = await res.json()
  if (r.code === 200) floors.value = r.data || []
}

async function loadNotices() {
  if (!managerId.value) return
  loading.value = true
  try {
    const res = await fetch(`${API}/api/notice/for-manager?managerId=${managerId.value}`)
    const r = await res.json()
    if (r.code === 200) notices.value = r.data || []
  } finally {
    loading.value = false
  }
}

async function loadStats(id: number) {
  if (!managerId.value) return
  const res = await fetch(`${API}/api/notice/${id}/stats?managerId=${managerId.value}`)
  const r = await res.json()
  if (r.code === 200 && r.data) {
    statsMap.value = { ...statsMap.value, [id]: r.data }
  }
}

async function createDraft() {
  if (!managerId.value) return
  if (!form.value.title.trim()) {
    showMsg('请输入标题', 'error')
    return
  }
  if (!form.value.content.trim()) {
    showMsg('请输入内容', 'error')
    return
  }
  if (form.value.scopeType === 'BUILDING' && !form.value.buildingId) {
    showMsg('请选择楼栋', 'error')
    return
  }
  if (form.value.scopeType === 'FLOOR' && !form.value.floorId) {
    showMsg('请选择楼层', 'error')
    return
  }
  creating.value = true
  try {
    const body: Record<string, unknown> = {
      publisherId: Number(managerId.value),
      title: form.value.title,
      content: form.value.content,
      scopeType: form.value.scopeType,
    }
    if (form.value.scopeType === 'BUILDING') body.buildingId = Number(form.value.buildingId)
    if (form.value.scopeType === 'FLOOR') body.floorId = Number(form.value.floorId)

    const res = await fetch(`${API}/api/notice`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
    const r = await res.json()
    if (r.code === 200) {
      showMsg('草稿已创建')
      form.value.title = ''
      form.value.content = ''
      await loadNotices()
    } else {
      showMsg(r.message || '创建失败', 'error')
    }
  } finally {
    creating.value = false
  }
}

async function publish(id: number) {
  if (!managerId.value) return
  const res = await fetch(`${API}/api/notice/${id}/publish?managerId=${managerId.value}`, { method: 'PUT' })
  const r = await res.json()
  if (r.code === 200) {
    showMsg('已发布并推送到学生端')
    await loadNotices()
    await loadStats(id)
  } else {
    showMsg(r.message || '发布失败', 'error')
  }
}

function scopeLabel(n: NoticeVO) {
  if (n.scopeType === 'ALL') return '全体学生'
  if (n.scopeType === 'BUILDING') return `楼栋(${n.buildingId ?? '-'})`
  if (n.scopeType === 'FLOOR') return `楼层(${n.floorId ?? '-'})`
  return n.scopeType
}

onMounted(async () => {
  await loadBuildings()
  await loadNotices()
})
</script>

<template>
  <div class="page">
    <div class="panel">
      <div class="header-row">
        <div>
          <h1>公共通知</h1>
          <p class="desc">发布宿舍楼公共通知，学生端消息中心可查看并留痕已读。</p>
        </div>
        <button class="back-btn" @click="back">返回首页</button>
      </div>

      <p v-if="msg" :class="['tip-msg', msgType]">{{ msg }}</p>

      <section class="card">
        <div class="section-title">创建通知草稿</div>
        <div class="grid">
          <label>
            <span>标题</span>
            <input v-model="form.title" placeholder="例如：卫生检查时间通知" />
          </label>
          <label>
            <span>范围</span>
            <select v-model="form.scopeType" @change="loadFloors">
              <option value="ALL">全体学生</option>
              <option value="BUILDING">指定楼栋</option>
              <option value="FLOOR">指定楼层</option>
            </select>
          </label>
          <label v-if="form.scopeType === 'BUILDING' || form.scopeType === 'FLOOR'">
            <span>楼栋</span>
            <select v-model="form.buildingId" @change="loadFloors">
              <option value="">请选择楼栋</option>
              <option v-for="b in buildings" :key="b.id" :value="b.id">{{ b.name }}（{{ b.gender }}）</option>
            </select>
          </label>
          <label v-if="form.scopeType === 'FLOOR'">
            <span>楼层</span>
            <select v-model="form.floorId">
              <option value="">请选择楼层</option>
              <option v-for="f in floors" :key="f.id" :value="f.id">第{{ f.floorNo }}层</option>
            </select>
          </label>
          <label style="grid-column: 1 / -1">
            <span>内容</span>
            <textarea v-model="form.content" rows="4" placeholder="请输入通知正文..." />
          </label>
        </div>
        <button class="btn-primary" :disabled="creating" @click="createDraft">{{ creating ? '创建中...' : '创建草稿' }}</button>
      </section>

      <section class="card">
        <div class="section-title-row">
          <div class="section-title">我的通知</div>
          <button class="btn-mini" :disabled="loading" @click="loadNotices">{{ loading ? '刷新中...' : '刷新' }}</button>
        </div>
        <div v-if="loading" class="tip">加载中...</div>
        <div v-else-if="notices.length === 0" class="tip">暂无通知</div>
        <div v-else class="list">
          <div v-for="n in notices" :key="n.id" class="item">
            <div class="top">
              <strong class="title">{{ n.title }}</strong>
              <span class="status">{{ n.status }}</span>
            </div>
            <div class="meta">
              <span>{{ scopeLabel(n) }}</span>
              <span>创建：{{ n.createTime?.slice(0, 16) }}</span>
              <span v-if="n.publishTime">发布：{{ n.publishTime?.slice(0, 16) }}</span>
            </div>
            <div class="actions">
              <button v-if="n.status !== 'PUBLISHED'" class="btn-mini" @click="publish(n.id)">发布并推送</button>
              <button class="btn-mini ghost" @click="loadStats(n.id)">已读统计</button>
            </div>
            <div v-if="statsMap[n.id]" class="stats">
              已读 {{ statsMap[n.id]?.readCount ?? 0 }} / {{ statsMap[n.id]?.totalRecipients ?? 0 }}，未读 {{ statsMap[n.id]?.unreadCount ?? 0 }}
            </div>
          </div>
        </div>
      </section>
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
.card { background: #f8fafc; border: 1px solid #e4e7ec; border-radius: 16px; padding: 18px; margin-bottom: 16px; }
.section-title { font-size: 16px; font-weight: 900; color: #344054; margin-bottom: 10px; }
.section-title-row { display: flex; justify-content: space-between; align-items: center; gap: 10px; margin-bottom: 10px; }
.grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
label span { display: block; color: #667085; font-size: 13px; margin-bottom: 6px; }
input, select, textarea { width: 100%; border: 1px solid #d0d5dd; border-radius: 12px; padding: 8px 10px; font-size: 14px; background: #fff; font-family: inherit; box-sizing: border-box; }
textarea { resize: vertical; }
.btn-primary { margin-top: 12px; height: 40px; border: none; border-radius: 10px; padding: 0 16px; background: #0d9488; color: #fff; font-weight: 900; cursor: pointer; }
.btn-primary:disabled { opacity: .7; cursor: not-allowed; }
.btn-mini { height: 32px; border: none; border-radius: 10px; padding: 0 14px; background: #0d9488; color: #fff; font-weight: 900; cursor: pointer; font-size: 13px; }
.btn-mini.ghost { background: #fff; color: #344054; border: 1px solid #d0d5dd; }
.list { display: grid; gap: 10px; }
.item { background: #fff; border: 1px solid #e4e7ec; border-radius: 12px; padding: 12px 14px; }
.top { display: flex; justify-content: space-between; align-items: center; gap: 10px; }
.title { color: #111827; }
.status { font-size: 12px; font-weight: 900; color: #667085; }
.meta { display: flex; gap: 10px; flex-wrap: wrap; color: #667085; font-size: 12px; margin-top: 6px; }
.actions { display: flex; gap: 8px; margin-top: 10px; flex-wrap: wrap; }
.stats { margin-top: 10px; background: #eff6ff; border: 1px solid #bfdbfe; border-radius: 10px; padding: 8px 10px; color: #1e40af; font-weight: 900; font-size: 13px; }
.tip { color: #667085; font-size: 14px; }
.tip-msg { font-size: 14px; margin-bottom: 12px; }
.tip-msg.success { color: #027a48; }
.tip-msg.error { color: #d92d20; }
</style>

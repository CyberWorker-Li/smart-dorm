<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { apiBase } from '@/apiBase'

const params = new URLSearchParams(window.location.search)
const username = params.get('username') || ''
const realName = params.get('realName') || ''
const userType = params.get('userType') || ''
const leader = params.get('leader') === 'true'
const userId = params.get('userId') || ''
const hasLoginInfo = computed(() => !!username && userType === 'STUDENT')
const roleName = computed(() => (leader ? '宿舍长' : '普通学生'))

const router = useRouter()
const query = Object.fromEntries(new URLSearchParams(window.location.search))
const API = apiBase()

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

const pendingNoise = ref<NoiseRequestVO | null>(null)
const noisePopupVisible = ref(false)
const noisePopupLoading = ref(false)
let pollTimer: number | null = null

function dismissedKey() {
  return `smartdorm.dismissedNoise:${userId || 'unknown'}`
}

function getDismissedIds(): Set<number> {
  try {
    const raw = sessionStorage.getItem(dismissedKey())
    if (!raw) return new Set()
    const arr = JSON.parse(raw)
    if (!Array.isArray(arr)) return new Set()
    return new Set(arr.filter(x => typeof x === 'number'))
  } catch {
    return new Set()
  }
}

function addDismissedId(id: number) {
  const set = getDismissedIds()
  set.add(id)
  sessionStorage.setItem(dismissedKey(), JSON.stringify(Array.from(set)))
}

async function loadPendingNoise() {
  if (!userId) return
  noisePopupLoading.value = true
  try {
    const res = await fetch(`${API}/api/noise-request/incoming?studentId=${userId}`)
    const r = await res.json()
    const list: NoiseRequestVO[] = r.code === 200 && Array.isArray(r.data) ? r.data : []
    const dismissed = getDismissedIds()
    const pending = list
      .filter(x => x && x.status === 'PENDING' && !dismissed.has(x.id))
      .sort((a, b) => (b.id ?? 0) - (a.id ?? 0))
    pendingNoise.value = pending[0] || null
    noisePopupVisible.value = !!pendingNoise.value
  } catch {
    pendingNoise.value = null
    noisePopupVisible.value = false
  } finally {
    noisePopupLoading.value = false
  }
}

async function ackNoise(id: number) {
  if (!userId) return
  const res = await fetch(`${API}/api/noise-request/${id}/ack?studentId=${userId}`, { method: 'PUT' })
  const r = await res.json()
  if (r.code === 200) {
    addDismissedId(id)
    await loadPendingNoise()
  }
}

function closeNoisePopup() {
  if (pendingNoise.value) addDismissedId(pendingNoise.value.id)
  noisePopupVisible.value = false
}

function goNoise() {
  router.push({ path: '/noise', query })
}

function go(path: string) {
  router.push({ path, query })
}

onMounted(async () => {
  if (!hasLoginInfo.value || !userId) return
  await loadPendingNoise()
  pollTimer = window.setInterval(loadPendingNoise, 8000)
})

onBeforeUnmount(() => {
  if (pollTimer != null) {
    clearInterval(pollTimer)
    pollTimer = null
  }
})
</script>

<template>
  <div class="page">
    <div class="panel">
      <h1>学生后台</h1>
      <p class="desc">宿舍分配问卷填写、结果查询与微调申请。</p>

      <div v-if="hasLoginInfo" class="info-list">
        <div class="item"><span>当前身份</span><strong>{{ roleName }}</strong></div>
        <div class="item"><span>用户名</span><strong>{{ username }}</strong></div>
        <div class="item"><span>姓名</span><strong>{{ realName || '未传入' }}</strong></div>
        <div class="item"><span>所属系统</span><strong>高校宿舍智能管理</strong></div>
      </div>
      <p v-else class="warning">请先从统一登录门户进入本后台。</p>

      <div class="modules">
        <button class="module clickable" @click="go('/questionnaire')">填写分配问卷</button>
        <button class="module clickable" @click="go('/result')">查询分配结果</button>
        <button class="module clickable" @click="go('/adjust')">提交微调申请</button>
        <button class="module clickable" @click="go('/hygiene')">值日卫生</button>
        <button class="module clickable" @click="go('/swap')">宿舍互换</button>
        <button class="module clickable" @click="go('/dorm-public')">宿舍信息广场</button>
        <button class="module clickable" @click="go('/noise')">噪音提醒</button>
        <button class="module clickable" @click="go('/internal-manage')">宿舍内部管理</button>
        <button class="module clickable" @click="go('/messages')">消息中心</button>
      </div>
    </div>

    <div v-if="noisePopupVisible && pendingNoise" class="noise-overlay" @click.self="closeNoisePopup">
      <div class="noise-modal">
        <div class="noise-head">
          <div class="noise-title">收到安静请求</div>
          <button class="noise-close" @click="closeNoisePopup">×</button>
        </div>
        <div class="noise-sub">
          <span class="noise-from">{{ pendingNoise.fromRoomDisplay }}</span>
          <span class="noise-time">{{ pendingNoise.createTime?.slice(0, 16) }}</span>
        </div>
        <div class="noise-content">{{ pendingNoise.content }}</div>
        <div class="noise-actions">
          <button class="noise-btn ghost" :disabled="noisePopupLoading" @click="goNoise">去查看</button>
          <button class="noise-btn" :disabled="noisePopupLoading" @click="ackNoise(pendingNoise.id)">已知悉</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page { min-height: 100vh; padding: 40px; background: #f5f7fb; }
.panel { max-width: 960px; margin: 0 auto; background: #fff; border-radius: 20px; padding: 32px; box-shadow: 0 16px 36px rgba(15,23,42,.08); }
h1 { margin: 0; color: #1d4ed8; }
.desc { color: #667085; margin: 12px 0 24px; }
.info-list { display: grid; grid-template-columns: repeat(2, 1fr); gap: 16px; margin-bottom: 24px; }
.item, .module, .warning { background: #f8fafc; border: 1px solid #e4e7ec; border-radius: 14px; padding: 16px; }
.item span { display: block; color: #667085; font-size: 14px; margin-bottom: 8px; }
.modules { display: grid; grid-template-columns: repeat(2, 1fr); gap: 16px; }
.module { font-weight: 700; color: #344054; min-height: 64px; display: flex; align-items: center; }
.clickable { cursor: pointer; text-align: left; font-size: 16px; border: none; }
.warning { color: #b54708; background: #fffaeb; border-color: #fedf89; }

.noise-overlay { position: fixed; inset: 0; background: rgba(15, 23, 42, 0.45); display: flex; align-items: flex-start; justify-content: center; padding: 24px; z-index: 50; }
.noise-modal { width: 100%; max-width: 520px; background: #fff; border-radius: 16px; border: 2px solid #fecaca; box-shadow: 0 18px 42px rgba(15, 23, 42, 0.18); overflow: hidden; margin-top: 24px; }
.noise-head { display: flex; justify-content: space-between; align-items: center; padding: 14px 16px; background: #fef2f2; }
.noise-title { font-weight: 900; color: #b42318; font-size: 16px; }
.noise-close { width: 34px; height: 34px; border-radius: 10px; border: 1px solid #fda29b; background: #fff; color: #b42318; font-size: 20px; cursor: pointer; }
.noise-sub { display: flex; justify-content: space-between; gap: 10px; padding: 12px 16px; color: #667085; font-size: 13px; }
.noise-from { font-weight: 800; color: #344054; }
.noise-content { padding: 0 16px 14px; color: #101828; font-size: 14px; line-height: 1.5; white-space: pre-wrap; }
.noise-actions { display: flex; gap: 10px; justify-content: flex-end; padding: 14px 16px; border-top: 1px solid #eaecf0; background: #fff; }
.noise-btn { height: 36px; border: none; border-radius: 10px; padding: 0 14px; background: #d92d20; color: #fff; font-weight: 900; cursor: pointer; font-size: 13px; }
.noise-btn.ghost { background: #fff; color: #344054; border: 1px solid #d0d5dd; }
.noise-btn:disabled { opacity: .7; cursor: not-allowed; }
</style>

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

interface MyResultVO {
  batchId: number; building: string; roomNo: string; roomGender?: string
}

interface SwapMarketUserIntentVO {
  studentId: number
  alias: string
  remark: string | null
}

interface SwapMarketRoomVO {
  batchId: number
  roomId: number
  buildingName: string
  floorNo: number
  roomNo: number
  gender: string
  capacity: number
  occupied: number
  vacancies: number
  hasSwapIntent: boolean
  intents: SwapMarketUserIntentVO[]
}

interface SwapMarketMyIntentVO {
  batchId: number
  studentId: number
  status: string
  remark: string | null
}

interface SwapChatThreadVO {
  threadId: number
  batchId: number
  otherStudentId: number
  otherAlias: string
  lastMessage: string | null
  lastTime: string | null
}

interface SwapChatMessageVO {
  id: number
  mine: boolean
  content: string
  time: string
}

const route = useRoute()
const router = useRouter()
const studentId = route.query.userId || ''

const myResult = ref<MyResultVO | null>(null)
const myRequests = ref<SwapRequestVO[]>([])
const incomingRequests = ref<SwapRequestVO[]>([])
const marketRooms = ref<SwapMarketRoomVO[]>([])
const marketLoading = ref(false)
const myIntent = ref<SwapMarketMyIntentVO | null>(null)
const intentRemark = ref('')
const intentSaving = ref(false)

const chatThreads = ref<SwapChatThreadVO[]>([])
const chatThreadId = ref<number | null>(null)
const chatMessages = ref<SwapChatMessageVO[]>([])
const chatLoading = ref(false)
const chatText = ref('')
const loading = ref(false)
const submitting = ref(false)
const msg = ref('')
const msgType = ref<'success' | 'error'>('success')


const form = ref({ targetStudentId: '', remark: '' })

const API = apiBase()

function showMsg(text: string, type: 'success' | 'error' = 'success') {
  msg.value = text
  msgType.value = type
  setTimeout(() => (msg.value = ''), 4000)
}

async function loadData() {
  if (!studentId) return
  loading.value = true
  try {
    const [resultRes, reqRes, incomingRes] = await Promise.all([
      fetch(`${API}/api/assignment/my-result?studentId=${studentId}`),
      fetch(`${API}/api/swap-request/my?studentId=${studentId}`),
      fetch(`${API}/api/swap-request/incoming?studentId=${studentId}`),
    ])
    const resultData = await resultRes.json()
    const reqData = await reqRes.json()
    const incomingData = await incomingRes.json()

    if (resultData.code === 200 && resultData.data) myResult.value = resultData.data
    if (reqData.code === 200) myRequests.value = reqData.data
    if (incomingData.code === 200) incomingRequests.value = incomingData.data
    await Promise.all([loadMarketRooms(), loadMyIntent(), loadChatThreads()])
  } finally {
    loading.value = false
  }
}

async function loadMarketRooms() {
  if (!studentId) return
  marketLoading.value = true
  try {
    const batchId = myResult.value?.batchId
    const gender = myResult.value?.roomGender || ''
    const url = new URL(`${API}/api/swap-market/rooms`)
    if (batchId) url.searchParams.set('batchId', String(batchId))
    if (gender) url.searchParams.set('gender', String(gender))
    const res = await fetch(url.toString())
    const r = await res.json()
    if (r.code === 200) {
      marketRooms.value = r.data || []
    }
  } finally {
    marketLoading.value = false
  }
}

async function loadMyIntent() {
  if (!studentId) return
  try {
    const batchId = myResult.value?.batchId
    const url = new URL(`${API}/api/swap-market/my-intent`)
    url.searchParams.set('studentId', String(studentId))
    if (batchId) url.searchParams.set('batchId', String(batchId))
    const res = await fetch(url.toString())
    const r = await res.json()
    if (r.code === 200) {
      myIntent.value = r.data
      intentRemark.value = r.data?.remark || ''
    }
  } catch {}
}

async function publishIntent() {
  if (!myResult.value) {
    showMsg('未找到您的分配记录，无法发布意愿', 'error')
    return
  }
  intentSaving.value = true
  try {
    const body: Record<string, unknown> = {
      batchId: myResult.value.batchId,
      studentId: Number(studentId),
      remark: intentRemark.value || '',
    }
    const res = await fetch(`${API}/api/swap-market/intent`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
    const r = await res.json()
    if (r.code === 200) {
      myIntent.value = r.data
      showMsg('已发布换宿意愿（匿名）')
      await loadMarketRooms()
    } else {
      showMsg(r.message || '发布失败', 'error')
    }
  } finally {
    intentSaving.value = false
  }
}

async function closeIntent() {
  if (!myResult.value) return
  intentSaving.value = true
  try {
    const url = new URL(`${API}/api/swap-market/intent/close`)
    url.searchParams.set('studentId', String(studentId))
    url.searchParams.set('batchId', String(myResult.value.batchId))
    const res = await fetch(url.toString(), { method: 'PUT' })
    const r = await res.json()
    if (r.code === 200) {
      myIntent.value = r.data
      showMsg('已关闭换宿意愿')
      await loadMarketRooms()
    } else {
      showMsg(r.message || '操作失败', 'error')
    }
  } finally {
    intentSaving.value = false
  }
}

async function startChat(targetStudentId?: number, targetRoomId?: number) {
  if (!myResult.value) {
    showMsg('未找到您的分配记录，无法发起沟通', 'error')
    return
  }
  try {
    const body: Record<string, unknown> = {
      batchId: myResult.value.batchId,
      studentId: Number(studentId),
    }
    if (targetStudentId) body.targetStudentId = targetStudentId
    if (targetRoomId) body.targetRoomId = targetRoomId

    const res = await fetch(`${API}/api/swap-market/chat/start`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
    const r = await res.json()
    if (r.code === 200) {
      chatThreadId.value = r.data
      await loadChatThreads()
      await loadChatMessages()
    } else {
      showMsg(r.message || '发起沟通失败', 'error')
    }
  } catch {
    showMsg('发起沟通失败', 'error')
  }
}

async function loadChatThreads() {
  if (!studentId) return
  try {
    const res = await fetch(`${API}/api/swap-market/chat/threads?studentId=${studentId}`)
    const r = await res.json()
    if (r.code === 200) {
      chatThreads.value = r.data || []
      if (!chatThreadId.value && chatThreads.value.length > 0) {
        const first = chatThreads.value[0]
        if (first) chatThreadId.value = first.threadId
      }
    }
  } catch {}
}

async function loadChatMessages() {
  if (!studentId || !chatThreadId.value) return
  chatLoading.value = true
  try {
    const url = new URL(`${API}/api/swap-market/chat/messages`)
    url.searchParams.set('threadId', String(chatThreadId.value))
    url.searchParams.set('studentId', String(studentId))
    const res = await fetch(url.toString())
    const r = await res.json()
    if (r.code === 200) {
      chatMessages.value = r.data || []
    }
  } finally {
    chatLoading.value = false
  }
}

async function sendChat() {
  if (!chatThreadId.value) {
    showMsg('请先选择会话', 'error')
    return
  }
  if (!chatText.value.trim()) {
    showMsg('请输入消息内容', 'error')
    return
  }
  try {
    const body: Record<string, unknown> = {
      threadId: chatThreadId.value,
      studentId: Number(studentId),
      content: chatText.value,
    }
    const res = await fetch(`${API}/api/swap-market/chat/message`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
    const r = await res.json()
    if (r.code === 200) {
      chatText.value = ''
      await loadChatThreads()
      await loadChatMessages()
    } else {
      showMsg(r.message || '发送失败', 'error')
    }
  } catch {
    showMsg('发送失败', 'error')
  }
}

async function submitSwap() {
  if (!form.value.targetStudentId) {
    showMsg('请输入目标学生的ID', 'error')
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
      studentAId: Number(studentId),
      studentBId: Number(form.value.targetStudentId),
    }
    if (form.value.remark) body.remark = form.value.remark

    const res = await fetch(`${API}/api/swap-request`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
    const r = await res.json()
    if (r.code === 200) {
      showMsg('互换申请提交成功，请等待对方确认')
      form.value = { targetStudentId: '', remark: '' }
      await loadData()
    } else {
      showMsg(r.message || '提交失败', 'error')
    }
  } finally {
    submitting.value = false
  }
}

async function confirmSwap(reqId: number) {
  try {
    const res = await fetch(`${API}/api/swap-request/${reqId}/confirm?studentBId=${studentId}`, { method: 'PUT' })
    const r = await res.json()
    if (r.code === 200) {
      showMsg('已确认互换，请等待宿管审核')
      await loadData()
    } else {
      showMsg(r.message || '操作失败', 'error')
    }
  } catch { showMsg('操作失败', 'error') }
}

async function rejectSwap(reqId: number) {
  try {
    const res = await fetch(`${API}/api/swap-request/${reqId}/reject?studentBId=${studentId}`, { method: 'PUT' })
    const r = await res.json()
    if (r.code === 200) {
      showMsg('已拒绝该互换申请')
      await loadData()
    } else {
      showMsg(r.message || '操作失败', 'error')
    }
  } catch { showMsg('操作失败', 'error') }
}

async function cancelSwap(reqId: number) {
  try {
    const res = await fetch(`${API}/api/swap-request/${reqId}/cancel?studentAId=${studentId}`, { method: 'PUT' })
    const r = await res.json()
    if (r.code === 200) {
      showMsg('已取消申请')
      await loadData()
    } else {
      showMsg(r.message || '操作失败', 'error')
    }
  } catch { showMsg('操作失败', 'error') }
}

const statusLabel: Record<string, string> = {
  PENDING_B_CONFIRM: '待对方确认',
  PENDING_MANAGER: '待宿管审核',
  APPROVED: '已通过',
  REJECTED_BY_B: '对方已拒绝',
  REJECTED_BY_MANAGER: '宿管已拒绝',
  CANCELLED: '已取消',
}
const statusColor: Record<string, string> = {
  PENDING_B_CONFIRM: '#667085',
  PENDING_MANAGER: '#b54708',
  APPROVED: '#027a48',
  REJECTED_BY_B: '#d92d20',
  REJECTED_BY_MANAGER: '#d92d20',
  CANCELLED: '#9ca3af',
}

onMounted(loadData)
</script>

<template>
  <div class="page">
    <div class="panel">
      <div class="header-row">
        <div>
          <h1>宿舍互换</h1>
          <p class="desc">想和另一位同学互换宿舍？双方同意后由宿管审核即可完成互换。</p>
        </div>
        <button class="back-btn" @click="router.push({ path: '/', query: route.query })">返回首页</button>
      </div>

      <p v-if="msg" :class="['tip-msg', msgType]">{{ msg }}</p>

      <div v-if="loading" class="tip">加载中...</div>
      <div v-else>
        <div v-if="myResult" class="current-room">
          <span class="label">当前分配宿舍：</span>
          <strong>{{ myResult.building }} {{ myResult.roomNo }}</strong>
        </div>
        <div v-else class="empty-box">分配结果尚未公示，暂不支持提交申请。</div>

        <div v-if="myResult" class="market">
          <section class="card market-card">
            <div class="section-title-row">
              <div class="section-title">换宿广场（空位 / 换宿意愿）</div>
              <button class="btn-mini" :disabled="marketLoading" @click="loadMarketRooms">
                {{ marketLoading ? '刷新中...' : '刷新' }}
              </button>
            </div>
            <div class="intent-row">
              <div class="intent-left">
                <div class="intent-status">
                  我的换宿意愿：<strong>{{ myIntent?.status || 'NONE' }}</strong>
                </div>
                <input v-model="intentRemark" placeholder="想换到什么样的宿舍？（匿名展示，可选）" />
              </div>
              <div class="intent-actions">
                <button class="btn-primary-sm" :disabled="intentSaving" @click="publishIntent">发布/更新意愿</button>
                <button class="btn-ghost-sm" :disabled="intentSaving" @click="closeIntent">关闭意愿</button>
              </div>
            </div>

            <div v-if="marketRooms.length === 0" class="tip">暂无空位宿舍或换宿意愿</div>
            <div v-else class="room-list">
              <div v-for="room in marketRooms" :key="room.roomId" class="room-item">
                <div class="room-main">
                  <div class="room-title">
                    <strong>{{ room.buildingName }}</strong>
                    <span class="room-sub">第{{ room.floorNo }}层 {{ room.roomNo }}号</span>
                  </div>
                  <div class="room-tags">
                    <span class="tag">空位 {{ room.vacancies }}</span>
                    <span v-if="room.hasSwapIntent" class="tag dim">有人想换宿</span>
                  </div>
                </div>
                <div class="room-actions">
                  <button v-if="room.vacancies > 0" class="btn-mini" @click="startChat(undefined, room.roomId)">联系宿舍长</button>
                </div>
                <div v-if="room.hasSwapIntent && room.intents?.length" class="intent-list">
                  <div v-for="it in room.intents" :key="it.studentId" class="intent-item">
                    <span class="intent-alias">{{ it.alias }}</span>
                    <span v-if="it.remark" class="intent-remark">{{ it.remark }}</span>
                    <button class="btn-mini" @click="startChat(it.studentId)">匿名沟通</button>
                  </div>
                </div>
              </div>
            </div>
          </section>

          <section class="card market-card">
            <div class="section-title-row">
              <div class="section-title">匿名沟通</div>
              <button class="btn-mini" @click="loadChatThreads">刷新会话</button>
            </div>
            <div class="chat-top">
              <select v-model="chatThreadId" @change="loadChatMessages">
                <option v-if="chatThreads.length === 0" :value="null">暂无会话</option>
                <option v-for="t in chatThreads" :key="t.threadId" :value="t.threadId">
                  {{ t.otherAlias }}{{ t.lastMessage ? `：${t.lastMessage}` : '' }}
                </option>
              </select>
              <button class="btn-mini" :disabled="chatLoading" @click="loadChatMessages">
                {{ chatLoading ? '加载中...' : '加载消息' }}
              </button>
            </div>
            <div class="chat-box">
              <div v-if="chatMessages.length === 0" class="tip">暂无消息</div>
              <div v-else class="chat-list">
                <div v-for="m in chatMessages" :key="m.id" :class="['chat-msg', m.mine ? 'mine' : 'other']">
                  <div class="chat-content">{{ m.content }}</div>
                  <div class="chat-time">{{ m.time?.slice(0, 16) }}</div>
                </div>
              </div>
            </div>
            <div class="chat-send">
              <input v-model="chatText" placeholder="输入消息..." @keyup.enter="sendChat" />
              <button class="btn-primary-sm" @click="sendChat">发送</button>
            </div>
          </section>
        </div>

        <div v-if="myResult" class="layout">
          <!-- 左：提交互换申请 -->
          <section class="card">
            <div class="section-title">发起互换申请</div>
            <div class="form-grid">
              <label style="grid-column: span 2">
                <span>目标学生ID（要和您互换的同学ID）</span>
                <input v-model="form.targetStudentId" type="number" placeholder="请输入目标学生的用户ID..." />
              </label>
              <label style="grid-column: span 2">
                <span>申请备注（可选）</span>
                <textarea v-model="form.remark" rows="3" placeholder="想和对方说的话..." />
              </label>
            </div>
            <button class="submit-btn" :disabled="submitting" @click="submitSwap">
              {{ submitting ? '提交中...' : '提交互换申请' }}
            </button>
          </section>

          <!-- 中：待我确认的互换 -->
          <section class="card">
            <div class="section-title">待我确认</div>
            <div v-if="incomingRequests.length === 0" class="tip">暂无待确认的互换申请</div>
            <div v-else class="req-list">
              <div v-for="req in incomingRequests" :key="req.id" class="req-item">
                <div class="req-top">
                  <span class="req-student">{{ req.studentAName }}（{{ req.studentANo }}）</span>
                  <span class="req-label">想和您互换</span>
                </div>
                <div class="req-rooms">
                  <span class="room-tag current">{{ req.studentABuilding }} {{ req.studentARoomNo }}</span>
                  <span class="swap-icon">⇄</span>
                  <span class="room-tag current">您的房间</span>
                </div>
                <div v-if="req.initiatorRemark" class="req-remark-text">{{ req.initiatorRemark }}</div>
                <div class="req-actions">
                  <button class="btn-confirm" @click="confirmSwap(req.id)">同意互换</button>
                  <button class="btn-reject-sm" @click="rejectSwap(req.id)">拒绝</button>
                </div>
              </div>
            </div>
          </section>

          <!-- 右：历史 -->
          <section class="card">
            <div class="section-title">我的互换记录</div>
            <div v-if="myRequests.length === 0" class="tip">暂无互换记录</div>
            <div v-else class="req-list">
              <div v-for="req in myRequests" :key="req.id" class="req-item">
                <div class="req-top">
                  <span v-if="req.studentAId === Number(studentId)" class="req-role">我发起的</span>
                  <span v-else class="req-role dim">对方发起的</span>
                  <span class="req-status" :style="{ color: statusColor[req.status] }">
                    {{ statusLabel[req.status] }}
                  </span>
                </div>
                <div class="req-rooms-compact">
                  <span>{{ req.studentABuilding }} {{ req.studentARoomNo }}</span>
                  <span class="swap-arrow">⇄</span>
                  <span>{{ req.studentBBuilding }} {{ req.studentBRoomNo }}</span>
                </div>
                <div class="req-people">
                  <span>{{ req.studentAName }}</span> ⇄ <span>{{ req.studentBName }}</span>
                </div>
                <div v-if="req.handleRemark" class="req-remark">宿管回复：{{ req.handleRemark }}</div>
                <div class="req-time">{{ req.createTime?.slice(0, 16) }}</div>
                <button v-if="req.status === 'PENDING_B_CONFIRM' && req.studentAId === Number(studentId)"
                  class="btn-cancel" @click="cancelSwap(req.id)">取消申请</button>
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
.panel { max-width: 1200px; margin: 0 auto; background: #fff; border-radius: 20px; padding: 32px; box-shadow: 0 16px 36px rgba(15,23,42,.08); }
.header-row { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 20px; }
h1 { margin: 0; color: #1d4ed8; }
.desc { color: #667085; margin: 8px 0 0; }
.back-btn { border: none; border-radius: 10px; padding: 10px 18px; background: #dbeafe; color: #1e40af; cursor: pointer; font-weight: 700; white-space: nowrap; }
.current-room { background: #eff6ff; border: 1px solid #bfdbfe; border-radius: 12px; padding: 14px 18px; margin-bottom: 20px; font-size: 15px; color: #1e40af; }
.current-room .label { color: #667085; }
.market { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin-bottom: 20px; }
.market-card { background: #f8fafc; }
.section-title-row { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-bottom: 10px; }
.btn-mini { height: 32px; border: 1px solid #d0d5dd; border-radius: 8px; padding: 0 12px; background: #fff; color: #344054; cursor: pointer; font-weight: 700; font-size: 12px; white-space: nowrap; }
.btn-mini:disabled { opacity: .7; cursor: not-allowed; }
.intent-row { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-bottom: 12px; flex-wrap: wrap; }
.intent-left { flex: 1; min-width: 260px; display: grid; gap: 8px; }
.intent-status { font-size: 13px; color: #475467; }
.intent-actions { display: flex; gap: 8px; align-items: center; }
.btn-primary-sm { height: 34px; border: none; border-radius: 8px; padding: 0 14px; background: #2563eb; color: #fff; font-weight: 700; cursor: pointer; font-size: 13px; white-space: nowrap; }
.btn-primary-sm:disabled { opacity: .7; cursor: not-allowed; }
.btn-ghost-sm { height: 34px; border: 1px solid #d0d5dd; border-radius: 8px; padding: 0 14px; background: #fff; color: #667085; font-weight: 700; cursor: pointer; font-size: 13px; white-space: nowrap; }
.room-list { display: grid; gap: 10px; }
.room-item { background: #fff; border: 1px solid #e4e7ec; border-radius: 12px; padding: 12px 14px; }
.room-main { display: flex; align-items: flex-start; justify-content: space-between; gap: 10px; }
.room-title { display: grid; gap: 2px; color: #1f2937; }
.room-sub { color: #667085; font-size: 12px; }
.room-tags { display: flex; gap: 6px; flex-wrap: wrap; justify-content: flex-end; }
.tag { background: #eff6ff; border: 1px solid #bfdbfe; border-radius: 999px; padding: 2px 10px; font-size: 12px; color: #1e40af; font-weight: 700; }
.tag.dim { background: #f3f4f6; border-color: #e5e7eb; color: #6b7280; }
.room-actions { margin-top: 10px; display: flex; justify-content: flex-end; }
.intent-list { margin-top: 10px; display: grid; gap: 8px; }
.intent-item { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.intent-alias { font-weight: 800; color: #0f766e; font-size: 12px; }
.intent-remark { color: #374151; font-size: 12px; background: #f9fafb; border-radius: 8px; padding: 4px 8px; border: 1px solid #e5e7eb; }
.chat-top { display: flex; gap: 8px; align-items: center; margin-bottom: 10px; }
.chat-box { background: #fff; border: 1px solid #e4e7ec; border-radius: 12px; padding: 10px; min-height: 220px; max-height: 320px; overflow: auto; }
.chat-list { display: grid; gap: 8px; }
.chat-msg { max-width: 90%; border-radius: 12px; padding: 8px 10px; border: 1px solid #e5e7eb; }
.chat-msg.mine { margin-left: auto; background: #eff6ff; border-color: #bfdbfe; }
.chat-msg.other { margin-right: auto; background: #f8fafc; }
.chat-content { font-size: 13px; color: #111827; white-space: pre-wrap; }
.chat-time { font-size: 11px; color: #9ca3af; margin-top: 4px; text-align: right; }
.chat-send { display: flex; gap: 8px; margin-top: 10px; align-items: center; }
.layout { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 20px; }
.card { background: #f8fafc; border: 1px solid #e4e7ec; border-radius: 16px; padding: 20px; }
.section-title { font-size: 16px; font-weight: 700; color: #344054; margin-bottom: 16px; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
label span { display: block; color: #667085; font-size: 14px; margin-bottom: 6px; }
input, textarea, select { width: 100%; border: 1px solid #d0d5dd; border-radius: 10px; padding: 8px 12px; background: #fff; font-size: 14px; font-family: inherit; box-sizing: border-box; }
textarea { resize: vertical; }
.submit-btn { margin-top: 16px; width: 100%; height: 44px; border: none; border-radius: 10px; background: #2563eb; color: #fff; font-weight: 700; cursor: pointer; font-size: 15px; }
.submit-btn:disabled { opacity: .7; cursor: not-allowed; }
.req-list { display: grid; gap: 10px; }
.req-item { background: #fff; border: 1px solid #e4e7ec; border-radius: 12px; padding: 12px 14px; }
.req-top { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; flex-wrap: wrap; }
.req-student { font-weight: 700; color: #1f2937; }
.req-label { color: #667085; font-size: 13px; }
.req-role { font-size: 13px; color: #1d4ed8; font-weight: 600; }
.req-role.dim { color: #9ca3af; }
.req-status { font-size: 13px; font-weight: 600; margin-left: auto; }
.req-rooms { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.req-rooms-compact { font-size: 14px; color: #374151; margin-bottom: 4px; }
.req-people { font-size: 13px; color: #667085; margin-bottom: 4px; }
.room-tag { background: #eff6ff; border: 1px solid #bfdbfe; border-radius: 8px; padding: 4px 10px; font-size: 13px; font-weight: 600; color: #1e40af; }
.room-tag.current { background: #f0fdf4; border-color: #bbf7d0; color: #065f46; }
.swap-icon { font-size: 20px; color: #9ca3af; }
.swap-arrow { color: #9ca3af; margin: 0 4px; }
.req-remark-text { font-size: 13px; color: #374151; background: #f9fafb; border-radius: 6px; padding: 6px 8px; margin-bottom: 8px; }
.req-remark { font-size: 13px; color: #065f46; background: #d1fae5; border-radius: 6px; padding: 4px 8px; margin-bottom: 4px; }
.req-time { font-size: 12px; color: #9ca3af; margin-bottom: 4px; }
.req-actions { display: flex; gap: 8px; margin-top: 8px; }
.btn-confirm { height: 34px; border: none; border-radius: 8px; padding: 0 16px; background: #0d9488; color: #fff; font-weight: 700; cursor: pointer; font-size: 13px; }
.btn-reject-sm { height: 34px; border: none; border-radius: 8px; padding: 0 16px; background: #fee2e2; color: #b91c1c; font-weight: 700; cursor: pointer; font-size: 13px; }
.btn-cancel { margin-top: 6px; height: 28px; border: 1px solid #d0d5dd; border-radius: 6px; padding: 0 12px; background: #fff; color: #667085; cursor: pointer; font-size: 12px; }
.tip { color: #667085; font-size: 14px; }
.tip-msg { font-size: 14px; margin-bottom: 12px; }
.tip-msg.success { color: #027a48; }
.tip-msg.error { color: #d92d20; }
.empty-box { background: #f8fafc; border: 1px solid #e4e7ec; border-radius: 12px; padding: 32px; text-align: center; color: #667085; }
</style>

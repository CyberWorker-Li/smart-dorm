<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { apiBase } from '@/apiBase'

interface HygieneTaskVO {
  id: number
  roomId: number
  roomDisplay: string
  dutyDate: string
  dutyUserId: number
  dutyUserName: string
  dutyItem: string
  status: string
  deadlineTime: string
  checkedIn: boolean
  checkinId?: number
  checkinPhotoUrl?: string
  checkinTime?: string
  verifyStatus?: string
  checkinLocationText?: string
  checkinRemark?: string
}

interface HygieneRoomSummaryVO {
  roomId: number
  roomDisplay: string
  totalTasks: number
  completedTasks: number
  overdueTasks: number
  latestScore?: number
  avgScore?: number
  recentScores?: Array<{
    id: number
    scoreDate: string
    periodType: string
    score: number
    sourceType: string
    reason?: string
  }>
}

interface HygieneMemberVO {
  id: number
  name: string
}

const route = useRoute()
const router = useRouter()
const query = Object.fromEntries(new URLSearchParams(window.location.search))

const userId = Number(route.query.userId || 0)
const leader = route.query.leader === 'true'

const API = apiBase()

const msg = ref('')
const msgType = ref<'success' | 'error'>('success')

const roomTasks = ref<HygieneTaskVO[]>([])
const myTasks = ref<HygieneTaskVO[]>([])
const summary = ref<HygieneRoomSummaryVO | null>(null)
const members = ref<HygieneMemberVO[]>([])

const loading = ref(false)
const loadingSummary = ref(false)
const photoUrlMap = ref<Record<number, string>>({})
const locationMap = ref<Record<number, string>>({})
const uploadingMap = ref<Record<number, boolean>>({})
const locatingMap = ref<Record<number, boolean>>({})
const previewMap = ref<Record<number, string>>({})

const weekStart = ref(getMonday(new Date()))
const dutyItem = ref('宿舍公共区域卫生')

const customDutyDate = ref(getMonday(new Date()))
const customDutyUserId = ref<number | null>(null)
const customDutyItem = ref('宿舍公共区域卫生')
const customDeadline = ref<string>('')

const memberOptions = computed(() => {
  if (members.value.length) {
    return members.value.map(m => ({ id: m.id, name: m.name }))
  }
  const m = new Map<number, string>()
  for (const t of roomTasks.value) {
    m.set(t.dutyUserId, t.dutyUserName)
  }
  return Array.from(m.entries()).map(([id, name]) => ({ id, name }))
})

const weekRoomTasks = computed(() => {
  const list = [...roomTasks.value]
  list.sort((a, b) => (a.dutyDate || '').localeCompare(b.dutyDate || '') || a.id - b.id)
  return list
})

const deadlineHour = ref(22)

interface HygienePlanRow {
  dutyDate: string
  dutyUserId: number
  dutyUserName: string
  dutyItem: string
  deadlineDisplay: string
}

const previewPlan = computed<HygienePlanRow[]>(() => {
  if (!leader) return []
  if (weekRoomTasks.value.length) return []
  if (!members.value.length) return []
  const ids = [...members.value].map(m => m.id).sort((a, b) => a - b)
  if (!ids.length) return []
  const nameById = new Map(members.value.map(m => [m.id, m.name]))
  const rows: HygienePlanRow[] = []
  for (let i = 0; i < 7; i++) {
    const date = addDays(weekStart.value, i)
    const uid = ids[i % ids.length]!
    const hh = String(deadlineHour.value).padStart(2, '0')
    rows.push({
      dutyDate: date,
      dutyUserId: uid,
      dutyUserName: nameById.get(uid) || String(uid),
      dutyItem: dutyItem.value,
      deadlineDisplay: `${date} ${hh}:00`,
    })
  }
  return rows
})

type EditTarget =
  | { type: 'preview'; dutyDate: string }
  | { type: 'week'; taskId: number; dutyDate: string }
  | null

const editTarget = ref<EditTarget>(null)
const previewOverrides = ref<Record<string, Partial<HygienePlanRow>>>({})

const previewPlanMerged = computed<HygienePlanRow[]>(() => {
  if (!previewPlan.value.length) return []
  return previewPlan.value.map(r => ({
    ...r,
    ...(previewOverrides.value[r.dutyDate] || {}),
  }))
})

function setPreviewOverride(dutyDate: string, patch: Partial<HygienePlanRow>) {
  previewOverrides.value = {
    ...previewOverrides.value,
    [dutyDate]: {
      ...(previewOverrides.value[dutyDate] || {}),
      ...patch,
    },
  }
}

function clearPreviewRow(dutyDate: string) {
  const next = { ...previewOverrides.value }
  delete next[dutyDate]
  previewOverrides.value = next
}

function resetPreview() {
  previewOverrides.value = {}
  if (editTarget.value?.type === 'preview') clearEdit()
  showMsg('已重置拟定预览')
}

function previewDeadlineDisplay(dutyDate: string, datetimeLocal: string): string {
  const v = (datetimeLocal || '').trim()
  if (!v) {
    const hh = String(deadlineHour.value).padStart(2, '0')
    return `${dutyDate} ${hh}:00`
  }
  const normalized = v.includes('T') ? v.replace('T', ' ') : v
  return normalized.length >= 16 ? normalized.slice(0, 16) : normalized
}

function onPreviewUserChange(dutyDate: string, dutyUserId: number) {
  const name = memberOptions.value.find(x => x.id === dutyUserId)?.name || String(dutyUserId)
  setPreviewOverride(dutyDate, { dutyUserId, dutyUserName: name })
}

function onPreviewItemChange(dutyDate: string, dutyItem: string) {
  setPreviewOverride(dutyDate, { dutyItem })
}

function onPreviewDeadlineChange(dutyDate: string, datetimeLocal: string) {
  setPreviewOverride(dutyDate, { deadlineDisplay: previewDeadlineDisplay(dutyDate, datetimeLocal) })
}

function taskStatusLabel(status?: string): string {
  const s = (status || '').toUpperCase()
  if (s === 'PENDING') return '待打卡'
  if (s === 'COMPLETED') return '已完成'
  if (s === 'OVERDUE') return '已逾期'
  return '未知'
}

function verifyStatusLabel(status?: string): string {
  const s = (status || '').toUpperCase()
  if (!s) return ''
  if (s === 'PENDING') return '待宿管审核'
  if (s === 'AUTO_APPROVED') return '系统自动通过'
  if (s === 'APPROVED') return '人工通过'
  if (s === 'REJECTED') return '已驳回'
  return '未知'
}

function canRecheckin(t: HygieneTaskVO): boolean {
  return (t.verifyStatus || '').toUpperCase() === 'REJECTED'
}

function toDatetimeLocal(v?: string): string {
  const s = (v || '').trim()
  if (!s) return ''
  const normalized = s.includes(' ') ? s.replace(' ', 'T') : s
  return normalized.length >= 16 ? normalized.slice(0, 16) : normalized
}

function editTask(t: HygieneTaskVO) {
  editTarget.value = { type: 'week', taskId: t.id, dutyDate: t.dutyDate }
  customDutyDate.value = t.dutyDate
  customDutyUserId.value = t.dutyUserId
  customDutyItem.value = t.dutyItem || '宿舍公共区域卫生'
  customDeadline.value = toDatetimeLocal(t.deadlineTime)
  showMsg(`正在编辑：${t.dutyDate}（本周值日表）`)
}

function editPreviewRow(p: HygienePlanRow) {
  editTarget.value = { type: 'preview', dutyDate: p.dutyDate }
  customDutyDate.value = p.dutyDate
  customDutyUserId.value = p.dutyUserId
  customDutyItem.value = p.dutyItem || '宿舍公共区域卫生'
  customDeadline.value = toDatetimeLocal(p.deadlineDisplay)
  showMsg(`正在编辑：${p.dutyDate}（拟定表预览）`)
}

function clearEdit() {
  editTarget.value = null
  customDutyDate.value = weekStart.value
  customDutyUserId.value = members.value[0]?.id ?? null
  customDutyItem.value = dutyItem.value
  customDeadline.value = ''
}

async function applyPreview() {
  if (!leader) {
    showMsg('仅宿舍长可操作', 'error')
    return
  }
  if (weekRoomTasks.value.length) {
    showMsg('本周已存在值日表，请先删除或逐条编辑', 'error')
    return
  }
  const rows = previewPlanMerged.value
  if (!rows.length) {
    showMsg('暂无拟定表可生成', 'error')
    return
  }
  if (!confirm('将按“拟定表预览”的内容逐天生成本周值日表，是否继续？')) return
  for (const r of rows) {
    const fallback = `${r.dutyDate}T${String(deadlineHour.value).padStart(2, '0')}:00`
    const deadlineIso = toDatetimeLocal(r.deadlineDisplay) || fallback
    const res = await fetch(`${API}/api/hygiene/leader/upsert-task`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        leaderId: userId,
        dutyDate: r.dutyDate,
        dutyUserId: r.dutyUserId,
        dutyItem: r.dutyItem,
        deadlineTime: deadlineIso,
      }),
    })
    const rr = await res.json()
    if (rr.code !== 200) {
      showMsg(rr.message || `生成失败：${r.dutyDate}`, 'error')
      return
    }
  }
  previewOverrides.value = {}
  clearEdit()
  showMsg('已按预览生成本周值日表')
  await load()
}

function sourceLabel(sourceType?: string): string {
  const s = (sourceType || '').toUpperCase()
  if (s === 'SYSTEM') return '系统'
  if (s === 'DORM_MANAGER') return '宿管'
  return '其他'
}

function periodLabel(periodType?: string): string {
  const s = (periodType || '').toUpperCase()
  if (s === 'DAILY') return '日评分'
  if (s === 'INSPECTION') return '现场检查'
  return '其他'
}

function showMsg(text: string, type: 'success' | 'error' = 'success') {
  msg.value = text
  msgType.value = type
  setTimeout(() => (msg.value = ''), 5000)
}

function getMonday(d: Date): string {
  const date = new Date(d)
  const day = (date.getDay() + 6) % 7
  date.setDate(date.getDate() - day)
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const dd = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${dd}`
}

function addDays(iso: string, days: number): string {
  const d = new Date(`${iso}T00:00:00`)
  d.setDate(d.getDate() + days)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${dd}`
}

async function load() {
  if (!userId) {
    showMsg('缺少 userId，请从登录门户进入', 'error')
    return
  }
  loading.value = true
  try {
    const from = weekStart.value
    const to = addDays(weekStart.value, 6)
    const [roomRes, myRes] = await Promise.all([
      fetch(`${API}/api/hygiene/room-tasks?studentId=${userId}&from=${from}&to=${to}`).then(r => r.json()),
      fetch(`${API}/api/hygiene/my-tasks?studentId=${userId}&from=${from}&to=${to}`).then(r => r.json()),
    ])
    if (roomRes.code === 200) roomTasks.value = roomRes.data
    else showMsg(roomRes.message || '加载宿舍任务失败', 'error')
    if (myRes.code === 200) myTasks.value = myRes.data
    else showMsg(myRes.message || '加载我的任务失败', 'error')
  } finally {
    loading.value = false
  }
}

async function loadMembers() {
  if (!leader || !userId) return
  const res = await fetch(`${API}/api/hygiene/leader/members?leaderId=${userId}`)
  const r = await res.json()
  if (r.code === 200) {
    members.value = r.data || []
    if (!customDutyUserId.value && members.value[0]) {
      customDutyUserId.value = members.value[0].id
    }
  } else {
    showMsg(r.message || '加载宿舍成员失败', 'error')
  }
}

async function loadSummary() {
  if (!userId) return
  loadingSummary.value = true
  try {
    const res = await fetch(`${API}/api/hygiene/room-summary?studentId=${userId}`)
    const r = await res.json()
    if (r.code === 200) summary.value = r.data
    else showMsg(r.message || '加载宿舍评分失败', 'error')
  } finally {
    loadingSummary.value = false
  }
}

async function loadDeadlineRule() {
  try {
    const res = await fetch(`${API}/api/hygiene/admin/rules`)
    const r = await res.json()
    if (r.code === 200 && Array.isArray(r.data)) {
      const item = r.data.find((x: any) => (x.key || '') === 'hygiene.deadlineHour')
      const v = Number(item?.value)
      if (!Number.isNaN(v) && v >= 0 && v <= 23) deadlineHour.value = v
    }
  } catch {
  }
}

async function generateWeek() {
  if (!leader) {
    showMsg('仅宿舍长可生成值日表', 'error')
    return
  }
  const res = await fetch(`${API}/api/hygiene/leader/generate-week`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ leaderId: userId, weekStart: weekStart.value, dutyItem: dutyItem.value }),
  })
  const r = await res.json()
  if (r.code === 200) {
    if (r.data === 0) showMsg('本周已存在值日表，无需重复生成')
    else showMsg(`已生成本周值日表：${r.data} 条`)
    await load()
  } else {
    showMsg(r.message || '生成失败', 'error')
  }
}



async function deleteWeek() {
  if (!leader) {
    showMsg('仅宿舍长可删除值日表', 'error')
    return
  }
  if (!confirm('确定要删除本周值日表吗？已产生打卡/已完成的任务无法删除。')) return
  const res = await fetch(`${API}/api/hygiene/leader/delete-week`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ leaderId: userId, weekStart: weekStart.value }),
  })
  const r = await res.json()
  if (r.code === 200) {
    previewOverrides.value = {}
    clearEdit()
    showMsg(`已删除本周值日表：${r.data} 条`)
    await load()
  } else {
    showMsg(r.message || '删除失败', 'error')
  }
}

async function upsertTask() {
  if (!leader) {
    showMsg('仅宿舍长可排班', 'error')
    return
  }
  if (!customDutyDate.value) {
    showMsg('请选择日期', 'error')
    return
  }
  if (!customDutyUserId.value) {
    showMsg('请选择值日成员', 'error')
    return
  }
  const item = customDutyItem.value.trim()
  if (!item) {
    showMsg('请输入任务内容', 'error')
    return
  }
  if (editTarget.value?.type === 'preview') {
    const name = memberOptions.value.find(x => x.id === customDutyUserId.value)?.name
    const deadlineDisplay = customDeadline.value
      ? customDeadline.value.replace('T', ' ')
      : `${customDutyDate.value} ${String(deadlineHour.value).padStart(2, '0')}:00`
    previewOverrides.value = {
      ...previewOverrides.value,
      [customDutyDate.value]: {
        dutyUserId: customDutyUserId.value,
        dutyUserName: name || String(customDutyUserId.value),
        dutyItem: item,
        deadlineDisplay,
      },
    }
    showMsg(`已更新预览：${customDutyDate.value}（未保存）`)
    return
  }

  const res = await fetch(`${API}/api/hygiene/leader/upsert-task`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      leaderId: userId,
      dutyDate: customDutyDate.value,
      dutyUserId: customDutyUserId.value,
      dutyItem: item,
      deadlineTime: (customDeadline.value || '').trim() || null,
    }),
  })
  const r = await res.json()
  if (r.code === 200) {
    showMsg(`已保存排班（任务ID：${r.data}）`)
    await load()
  } else {
    showMsg(r.message || '保存排班失败', 'error')
  }
}

async function checkin(taskId: number) {
  const photoUrl = (photoUrlMap.value[taskId] || '').trim()
  const locationText = (locationMap.value[taskId] || '').trim()
  if (!photoUrl) {
    showMsg('请先选择并上传图片', 'error')
    return
  }
  const res = await fetch(`${API}/api/hygiene/checkin`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ taskId, studentId: userId, photoUrl, locationText }),
  })
  const r = await res.json()
  if (r.code === 200) {
    showMsg('打卡成功')
    await load()
    await loadSummary()
  } else {
    showMsg(r.message || '打卡失败', 'error')
  }
}

function pad2(n: number) {
  return String(n).padStart(2, '0')
}

function formatNow() {
  const d = new Date()
  const y = d.getFullYear()
  const m = pad2(d.getMonth() + 1)
  const dd = pad2(d.getDate())
  const hh = pad2(d.getHours())
  const mm = pad2(d.getMinutes())
  return `${y}-${m}-${dd} ${hh}:${mm}`
}

function loadImageFromFile(file: File): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const img = new Image()
    const url = URL.createObjectURL(file)
    img.onload = () => {
      URL.revokeObjectURL(url)
      resolve(img)
    }
    img.onerror = () => {
      URL.revokeObjectURL(url)
      reject(new Error('图片读取失败'))
    }
    img.src = url
  })
}

async function watermarkPhoto(taskId: number, file: File): Promise<File> {
  const img = await loadImageFromFile(file)
  const maxW = 1440
  const maxH = 1440
  const scale = Math.min(1, maxW / img.width, maxH / img.height)
  const w = Math.max(1, Math.round(img.width * scale))
  const h = Math.max(1, Math.round(img.height * scale))

  const canvas = document.createElement('canvas')
  canvas.width = w
  canvas.height = h
  const ctx = canvas.getContext('2d')
  if (!ctx) return file

  ctx.drawImage(img, 0, 0, w, h)

  const t = myTasks.value.find(x => x.id === taskId)
  const locationText = (locationMap.value[taskId] || '').trim()
  const lines = [
    `学生ID：${userId}  任务ID：${taskId}`,
    `任务：${t?.dutyDate || '-'}  ${t?.dutyItem || ''}`.trim(),
    `打卡时间：${formatNow()}`,
    `定位：${locationText || '未获取'}`,
  ].filter(Boolean)

  const fontSize = Math.max(14, Math.round(w / 42))
  const lineH = Math.round(fontSize * 1.35)
  ctx.font = `${fontSize}px sans-serif`
  ctx.textBaseline = 'top'

  const padding = Math.round(fontSize * 0.7)
  const blockW = Math.min(
    w - padding * 2,
    Math.max(...lines.map(s => Math.ceil(ctx.measureText(s).width))) + padding * 2,
  )
  const blockH = lines.length * lineH + padding * 2
  const x = padding
  const y = h - blockH - padding

  ctx.fillStyle = 'rgba(0,0,0,0.55)'
  ctx.fillRect(x, y, blockW, blockH)

  ctx.fillStyle = 'rgba(255,255,255,0.98)'
  ctx.shadowColor = 'rgba(0,0,0,0.35)'
  ctx.shadowBlur = 2

  let ty = y + padding
  for (const s of lines) {
    ctx.fillText(s, x + padding, ty, blockW - padding * 2)
    ty += lineH
  }
  ctx.shadowBlur = 0

  const blob: Blob = await new Promise((resolve, reject) => {
    canvas.toBlob(
      b => (b ? resolve(b) : reject(new Error('图片处理失败'))),
      'image/jpeg',
      0.92,
    )
  })
  return new File([blob], `checkin_${taskId}_${Date.now()}.jpg`, { type: 'image/jpeg' })
}

async function getLocation(taskId: number) {
  if (!navigator.geolocation) {
    showMsg('当前浏览器不支持定位', 'error')
    return
  }
  locatingMap.value[taskId] = true
  try {
    const pos = await new Promise<GeolocationPosition>((resolve, reject) => {
      navigator.geolocation.getCurrentPosition(resolve, reject, {
        enableHighAccuracy: true,
        timeout: 8000,
        maximumAge: 0,
      })
    })
    const lat = Number(pos.coords.latitude.toFixed(6))
    const lng = Number(pos.coords.longitude.toFixed(6))
    const acc = pos.coords.accuracy ? Math.round(pos.coords.accuracy) : null
    const at = formatNow()
    const accText = acc ? `，精度≈${acc}m` : ''
    locationMap.value[taskId] = `lat=${lat}, lng=${lng}${accText}，时间=${at}`
    if (previewMap.value[taskId]) {
      showMsg('定位已更新；如需写入照片水印，请重新选择图片')
    } else {
      showMsg('已获取定位')
    }
  } catch (e: any) {
    const m = (e?.message || '').toLowerCase()
    if (m.includes('permission')) showMsg('定位权限被拒绝', 'error')
    else showMsg('获取定位失败', 'error')
  } finally {
    locatingMap.value[taskId] = false
  }
}

async function uploadFile(taskId: number, file: File) {
  uploadingMap.value[taskId] = true
  try {
    const form = new FormData()
    form.append('file', file)
    const res = await fetch(`${API}/api/files/upload`, { method: 'POST', body: form })
    const r = await res.json()
    if (r.code === 200) {
      photoUrlMap.value[taskId] = r.data.url
      showMsg('图片上传成功')
    } else {
      showMsg(r.message || '上传失败', 'error')
    }
  } finally {
    uploadingMap.value[taskId] = false
  }
}

async function onPickFile(taskId: number, e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  try {
    const watermarked = await watermarkPhoto(taskId, file)
    previewMap.value[taskId] = URL.createObjectURL(watermarked)
    await uploadFile(taskId, watermarked)
  } catch {
    previewMap.value[taskId] = URL.createObjectURL(file)
    await uploadFile(taskId, file)
  }
  input.value = ''
}

onMounted(async () => {
  await load()
  await loadMembers()
  await loadDeadlineRule()
  await loadSummary()
})
</script>

<template>
  <div class="page">
    <div class="panel">
      <div class="header-row">
        <div>
          <h1>值日卫生</h1>
          <p class="desc">查看本周值日任务、进行卫生打卡、查看宿舍评分与统计。</p>
        </div>
        <button class="back-btn" @click="router.push({ path: '/', query })">返回首页</button>
      </div>

      <p v-if="msg" :class="['tip-msg', msgType]">{{ msg }}</p>

      <section class="card">
        <div class="section-title">值日排班（宿舍长）</div>
        <div class="form-row">
          <label>
            <span>周一日期</span>
            <input v-model="weekStart" type="date" />
          </label>
          <button class="btn" :disabled="loading" @click="load">刷新</button>
        </div>

        <div v-if="leader" class="leader-tools">
          <label class="grow">
            <span>默认任务内容（用于拟定/生成）</span>
            <input v-model="dutyItem" />
          </label>
          <button class="btn" @click="loadMembers">刷新成员</button>
        </div>

        <div v-if="leader" class="leader-tools">
          <button v-if="weekRoomTasks.length === 0" class="btn primary" @click="generateWeek">快速生成（轮转）</button>
          <button v-if="weekRoomTasks.length === 0" class="btn primary" @click="applyPreview">按预览生成（可编辑）</button>
          <button v-if="weekRoomTasks.length" class="btn danger" @click="deleteWeek">删除本周值日表</button>
          <button v-if="weekRoomTasks.length === 0" class="btn" @click="resetPreview">重置预览</button>
        </div>
      </section>

      <section class="card">
        <div class="section-title">本周值日表（宿舍）</div>

        <div v-if="leader" class="split">
          <div class="split-main">
            <div v-if="weekRoomTasks.length === 0" class="tip">
              本周暂无已生成的值日表。
              <span v-if="previewPlan.length">可直接编辑下方“拟定表预览”，确认后点击“按预览生成”。</span>
            </div>

            <div v-else class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>日期</th>
                    <th>值日人</th>
                    <th>任务</th>
                    <th>截止</th>
                    <th>状态</th>
                    <th>打卡/审核</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr
                    v-for="t in weekRoomTasks"
                    :key="t.id"
                    :class="{ selected: editTarget?.type === 'week' && editTarget.taskId === t.id }"
                  >
                    <td>{{ t.dutyDate }}</td>
                    <td>{{ t.dutyUserName }}</td>
                    <td>{{ t.dutyItem }}</td>
                    <td>{{ t.deadlineTime?.replace('T', ' ') }}</td>
                    <td>{{ taskStatusLabel(t.status) }}</td>
                    <td>
                      <span v-if="t.checkedIn">已打卡（{{ verifyStatusLabel(t.verifyStatus) || '已提交' }}）</span>
                      <span v-else>未打卡</span>
                    </td>
                    <td>
                      <button class="mini" @click="editTask(t)">编辑</button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>

            <div v-if="previewPlanMerged.length" class="preview-card">
              <div class="preview-title">拟定表预览（可直接编辑，未保存）</div>
              <div class="tip">修改只影响预览；点击“按预览生成”才会写入本周值日表。</div>
              <div class="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>日期</th>
                      <th>值日人</th>
                      <th>任务</th>
                      <th>截止</th>
                      <th>操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="p in previewPlanMerged" :key="p.dutyDate">
                      <td>{{ p.dutyDate }}</td>
                      <td>
                        <select
                          class="inline"
                          :value="p.dutyUserId"
                          @change="(e) => onPreviewUserChange(p.dutyDate, Number((e.target as HTMLSelectElement).value))"
                        >
                          <option v-for="m in memberOptions" :key="m.id" :value="m.id">{{ m.name }}</option>
                        </select>
                      </td>
                      <td>
                        <input class="inline" :value="p.dutyItem" @input="(e) => onPreviewItemChange(p.dutyDate, (e.target as HTMLInputElement).value)" />
                      </td>
                      <td>
                        <input
                          class="inline"
                          type="datetime-local"
                          :value="toDatetimeLocal(p.deadlineDisplay)"
                          @input="(e) => onPreviewDeadlineChange(p.dutyDate, (e.target as HTMLInputElement).value)"
                        />
                      </td>
                      <td>
                        <button class="mini" :disabled="!previewOverrides[p.dutyDate]" @click="clearPreviewRow(p.dutyDate)">还原</button>
                        <button class="mini" @click="editPreviewRow(p)">定位到编辑区</button>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>
          </div>

          <div class="split-side">
            <div class="side-box">
              <div class="section-title">编辑面板</div>
              <div class="tip">
                <span v-if="editTarget?.type === 'week'">当前编辑：{{ editTarget.dutyDate }}（本周值日表，保存会写入系统）</span>
                <span v-else-if="editTarget?.type === 'preview'">当前编辑：{{ editTarget.dutyDate }}（拟定表预览，保存仅更新预览）</span>
                <span v-else>预览表可直接编辑；如需改“本周已生成任务”，点击上表的“编辑”。</span>
              </div>
              <div class="leader-tools">
                <label class="grow">
                  <span>日期</span>
                  <input v-model="customDutyDate" type="date" />
                </label>
                <label class="grow">
                  <span>值日成员</span>
                  <select v-model="customDutyUserId">
                    <option :value="null">请选择</option>
                    <option v-for="m in memberOptions" :key="m.id" :value="m.id">
                      {{ m.name }}
                    </option>
                  </select>
                </label>
              </div>
              <div class="leader-tools">
                <label class="grow">
                  <span>任务内容</span>
                  <input v-model="customDutyItem" placeholder="如：扫地 / 拖地 / 整理桌面" />
                </label>
                <label class="grow">
                  <span>截止时间（可选）</span>
                  <input v-model="customDeadline" type="datetime-local" />
                </label>
              </div>
              <div class="leader-tools">
                <button class="btn primary" @click="upsertTask">保存</button>
                <button class="btn" @click="clearEdit">清空</button>
              </div>
            </div>
          </div>
        </div>

        <div v-else>
          <div v-if="weekRoomTasks.length === 0" class="tip">本周暂无已生成的值日表。</div>
          <div v-else class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>日期</th>
                  <th>值日人</th>
                  <th>任务</th>
                  <th>截止</th>
                  <th>状态</th>
                  <th>打卡/审核</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="t in weekRoomTasks" :key="t.id">
                  <td>{{ t.dutyDate }}</td>
                  <td>{{ t.dutyUserName }}</td>
                  <td>{{ t.dutyItem }}</td>
                  <td>{{ t.deadlineTime?.replace('T', ' ') }}</td>
                  <td>{{ taskStatusLabel(t.status) }}</td>
                  <td>
                    <span v-if="t.checkedIn">已打卡（{{ verifyStatusLabel(t.verifyStatus) || '已提交' }}）</span>
                    <span v-else>未打卡</span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </section>

      <section class="card">
        <div class="section-title">我的任务</div>
        <div v-if="myTasks.length === 0" class="tip">本周暂无分配到你的值日任务。</div>
        <div v-else class="list">
          <div v-for="t in myTasks" :key="t.id" class="task">
            <div class="task-top">
              <div class="task-title">{{ t.dutyDate }} · {{ t.dutyItem }}</div>
              <div class="badge" :class="t.status.toLowerCase()">{{ taskStatusLabel(t.status) }}</div>
            </div>
            <div class="task-sub">截止：{{ t.deadlineTime?.replace('T', ' ') }}</div>
            <div v-if="t.checkedIn" class="task-sub">
              已打卡：{{ t.checkinTime?.replace('T', ' ') }}（{{ verifyStatusLabel(t.verifyStatus) || '已提交' }}）
            </div>
            <div v-if="!t.checkedIn || canRecheckin(t)" class="checkin">
              <label class="file">
                <input type="file" accept="image/*" @change="(e) => onPickFile(t.id, e)" />
                <span class="file-btn">{{ uploadingMap[t.id] ? '上传中...' : '选择图片' }}</span>
              </label>
              <div class="file-meta">
                <div v-if="photoUrlMap[t.id]" class="file-url">已上传：{{ photoUrlMap[t.id] }}</div>
                <img v-if="previewMap[t.id]" class="preview" :src="previewMap[t.id]" alt="预览" />
              </div>
              <div class="location">
                <input v-model="locationMap[t.id]" placeholder="定位/地点描述（建议获取定位）" />
                <button class="mini" :disabled="locatingMap[t.id]" @click="getLocation(t.id)">
                  {{ locatingMap[t.id] ? '定位中...' : '获取定位' }}
                </button>
              </div>
              <button class="btn primary" @click="checkin(t.id)">{{ canRecheckin(t) ? '重新打卡' : '打卡' }}</button>
            </div>
          </div>
        </div>
      </section>

      <section class="card">
        <div class="section-title">宿舍统计</div>
        <div v-if="loadingSummary" class="tip">加载中...</div>
        <div v-else-if="!summary" class="tip">暂无统计数据。</div>
        <div v-else class="summary">
          <div class="summary-item"><span>宿舍</span><strong>{{ summary.roomDisplay }}</strong></div>
          <div class="summary-item"><span>近30天任务</span><strong>{{ summary.totalTasks }}</strong></div>
          <div class="summary-item"><span>已完成</span><strong>{{ summary.completedTasks }}</strong></div>
          <div class="summary-item"><span>逾期</span><strong>{{ summary.overdueTasks }}</strong></div>
          <div class="summary-item"><span>最新评分</span><strong>{{ summary.latestScore ?? '-' }}</strong></div>
          <div class="summary-item"><span>平均评分</span><strong>{{ summary.avgScore ? summary.avgScore.toFixed(1) : '-' }}</strong></div>
        </div>

        <div v-if="summary?.recentScores?.length" class="score-list">
          <div class="score-title">评分明细（最近）</div>
          <div v-for="s in summary.recentScores" :key="s.id" class="score-item">
            <div class="score-left">
              <strong>{{ s.scoreDate }}</strong>
              <span class="score-sub">{{ sourceLabel(s.sourceType) }} · {{ periodLabel(s.periodType) }}</span>
              <span v-if="s.reason" class="score-reason">{{ s.reason }}</span>
            </div>
            <div class="score-right">{{ s.score }}</div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.page { min-height: 100vh; padding: 40px; background: #f5f7fb; }
.panel { max-width: 980px; margin: 0 auto; background: #fff; border-radius: 20px; padding: 32px; box-shadow: 0 16px 36px rgba(15,23,42,.08); }
.header-row { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 12px; }
h1 { margin: 0; color: #1d4ed8; }
.desc { color: #667085; margin: 10px 0 0; }
.back-btn { border: none; border-radius: 10px; padding: 10px 18px; background: #dbeafe; color: #1e40af; cursor: pointer; font-weight: 700; }
.tip-msg { font-size: 14px; margin: 10px 0; }
.tip-msg.success { color: #027a48; }
.tip-msg.error { color: #d92d20; }
.card { background: #f8fafc; border: 1px solid #e4e7ec; border-radius: 16px; padding: 20px; margin-top: 16px; }
.section-title { font-size: 16px; font-weight: 800; color: #1f2937; margin-bottom: 12px; }
.form-row { display: flex; gap: 12px; align-items: flex-end; }
label span { display: block; font-size: 13px; color: #667085; margin-bottom: 6px; }
input, select { width: 100%; height: 40px; border: 1px solid #d0d5dd; border-radius: 10px; padding: 0 12px; background: #fff; }
.btn { height: 40px; border: none; border-radius: 10px; padding: 0 16px; background: #eef2ff; color: #3730a3; cursor: pointer; font-weight: 800; white-space: nowrap; }
.btn.primary { background: #1d4ed8; color: #fff; }
.btn.danger { background: #fef3f2; color: #b42318; }
.grow { flex: 1; }
.leader-tools { display: flex; gap: 12px; margin-top: 12px; align-items: flex-end; }
.tip { color: #667085; font-size: 14px; }
.list { display: grid; gap: 12px; }
.task { background: #fff; border: 1px solid #e4e7ec; border-radius: 14px; padding: 14px; }
.task-top { display: flex; justify-content: space-between; align-items: center; gap: 10px; }
.task-title { font-weight: 900; color: #111827; }
.task-sub { margin-top: 6px; color: #667085; font-size: 13px; }
.badge { font-size: 12px; padding: 4px 10px; border-radius: 999px; font-weight: 800; background: #f3f4f6; color: #344054; }
.badge.completed { background: #ecfdf3; color: #027a48; }
.badge.overdue { background: #fef3f2; color: #b42318; }
.checkin { display: grid; grid-template-columns: 220px 1fr 1.4fr auto; gap: 10px; margin-top: 10px; align-items: center; }
.file { display: inline-flex; align-items: center; }
.file input { display: none; }
.file-btn { height: 40px; display: inline-flex; align-items: center; justify-content: center; padding: 0 14px; border-radius: 10px; background: #eef2ff; color: #3730a3; font-weight: 800; cursor: pointer; border: 1px solid #e4e7ec; }
.file-meta { display: grid; gap: 6px; }
.file-url { font-size: 12px; color: #667085; }
.preview { width: 120px; height: 40px; object-fit: cover; border-radius: 10px; border: 1px solid #e4e7ec; }
.location { display: flex; gap: 8px; align-items: center; }
.location input { flex: 1; }
.summary { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
.summary-item { background: #fff; border: 1px solid #e4e7ec; border-radius: 14px; padding: 14px; }
.summary-item span { display: block; color: #667085; font-size: 13px; margin-bottom: 8px; }
.score-list { margin-top: 14px; }
.score-title { font-weight: 900; color: #1f2937; margin-bottom: 8px; }
.score-item { display: flex; justify-content: space-between; align-items: center; gap: 12px; padding: 12px; background: #fff; border: 1px solid #e4e7ec; border-radius: 14px; margin-top: 10px; }
.score-left { display: grid; gap: 4px; }
.score-sub { color: #667085; font-size: 12px; }
.score-reason { color: #344054; font-size: 13px; }
.score-right { font-weight: 900; color: #1d4ed8; }
.table-wrap { overflow: auto; border-radius: 12px; border: 1px solid #e4e7ec; background: #fff; margin-top: 10px; }
table { width: 100%; border-collapse: collapse; min-width: 900px; }
th, td { padding: 10px 12px; border-bottom: 1px solid #e4e7ec; font-size: 14px; text-align: left; }
th { background: #f3f4f6; color: #344054; font-weight: 800; }
.mini { height: 30px; border: none; border-radius: 8px; padding: 0 10px; background: #eef2ff; color: #3730a3; cursor: pointer; font-weight: 800; }
.preview-card { margin-top: 14px; padding-top: 14px; border-top: 1px dashed #d0d5dd; }
.preview-title { font-weight: 900; color: #1f2937; }
.split { display: grid; grid-template-columns: 1fr 360px; gap: 14px; margin-top: 10px; align-items: start; }
.split-side { position: sticky; top: 18px; }
.side-box { background: #fff; border: 1px solid #e4e7ec; border-radius: 14px; padding: 14px; }
tr.selected td { background: #eef2ff; }
select.inline, input.inline { height: 34px; border-radius: 10px; }
@media (max-width: 980px) {
  .split { grid-template-columns: 1fr; }
  .split-side { position: static; }
}
</style>


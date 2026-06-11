<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { apiBase } from '@/apiBase'

interface QuestionnaireVO {
  id: number
  title: string
  academicYear: string
  status: string
  deadline: string | null
  createTime: string
  submittedCount: number
  totalStudents?: number
  pendingCount?: number
}

interface AnswerItem {
  id: number
  studentId: number
  wakeUpTime: string
  sleepTime: string
  stayUpLate: boolean
  smoke: boolean
  keepClean: boolean
  hometown: string
  major: string
  personality: string
  hobbies: string
  selfDescription: string
  dormExpectation: string
  submitTime: string
}

const route = useRoute()
const router = useRouter()
const adminId = route.query.userId || '1'

const list = ref<QuestionnaireVO[]>([])
const loading = ref(false)
const msg = ref('')
const msgType = ref<'success' | 'error'>('success')

const form = ref({ title: '', academicYear: '', deadline: '', inheritFromQuestionnaireId: '' as string | number })

const submitting = ref(false)

const viewAnswers = ref<AnswerItem[]>([])
const viewTitle = ref('')
const showAnswers = ref(false)

const API = apiBase()

function showMsg(text: string, type: 'success' | 'error' = 'success') {
  msg.value = text
  msgType.value = type
  setTimeout(() => (msg.value = ''), 4000)
}

function academicYearOk(s: string): boolean {
  const t = s.trim()
  if (!/^\d{4}-\d{4}$/.test(t)) return false
  const parts = t.split('-').map(Number)
  const a = parts[0]
  const b = parts[1]
  if (a === undefined || b === undefined) return false
  return b === a + 1
}

async function loadList() {
  loading.value = true
  try {
    const res = await fetch(`${API}/api/questionnaire`)
    const r = await res.json()
    if (r.code === 200) list.value = r.data
  } finally {
    loading.value = false
  }
}

async function create() {
  if (!form.value.title || !form.value.academicYear) {
    showMsg('标题和学年不能为空', 'error')
    return
  }
  if (!academicYearOk(form.value.academicYear)) {
    showMsg('学年须为连续两学年的四位数字，例如 2026-2027', 'error')
    return
  }
  if (!form.value.deadline) {
    showMsg('截止时间必填', 'error')
    return
  }
  submitting.value = true
  try {
    const body: Record<string, unknown> = {
      title: form.value.title,
      academicYear: form.value.academicYear.trim(),
      deadline: form.value.deadline + ':00',
    }
    if (form.value.inheritFromQuestionnaireId !== '' && form.value.inheritFromQuestionnaireId != null) {
      body.inheritFromQuestionnaireId = Number(form.value.inheritFromQuestionnaireId)
    }
    const res = await fetch(`${API}/api/questionnaire?adminId=${adminId}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
    const r = await res.json()
    if (r.code === 200) {
      showMsg('创建成功（若选择继承，已复制原问卷的已提交答卷）')
      form.value = { title: '', academicYear: '', deadline: '', inheritFromQuestionnaireId: '' }
      await loadList()
    } else {
      showMsg(r.message || '创建失败', 'error')
    }
  } finally {
    submitting.value = false
  }
}

async function publish(id: number) {
  const res = await fetch(`${API}/api/questionnaire/${id}/publish`, { method: 'PUT' })
  const r = await res.json()
  r.code === 200 ? showMsg('已发布') : showMsg(r.message, 'error')
  await loadList()
}

async function close(id: number) {
  const res = await fetch(`${API}/api/questionnaire/${id}/close`, { method: 'PUT' })
  const r = await res.json()
  r.code === 200 ? showMsg('已关闭') : showMsg(r.message, 'error')
  await loadList()
}

async function removeQuestionnaire(id: number) {
  if (!confirm('确定删除该问卷？答卷数据将保留，列表中不再显示此问卷。')) return
  const res = await fetch(`${API}/api/questionnaire/${id}`, { method: 'DELETE' })
  const r = await res.json()
  r.code === 200 ? showMsg('已删除') : showMsg(r.message, 'error')
  await loadList()
}

function exportCsv(id: number, title: string) {
  const a = document.createElement('a')
  a.href = `${API}/api/questionnaire/${id}/export`
  a.download = `${title || 'questionnaire'}_answers.csv`
  a.click()
}

async function openAnswers(q: QuestionnaireVO) {
  const res = await fetch(`${API}/api/questionnaire/${q.id}/answers`)
  const r = await res.json()
  if (r.code === 200) {
    viewAnswers.value = r.data
    viewTitle.value = q.title
    showAnswers.value = true
  } else {
    showMsg(r.message || '无法加载答卷', 'error')
  }
}

const statusLabel: Record<string, string> = { DRAFT: '草稿', PUBLISHED: '已发布', CLOSED: '已关闭' }
const statusColor: Record<string, string> = { DRAFT: '#667085', PUBLISHED: '#027a48', CLOSED: '#b54708' }

onMounted(loadList)
</script>

<template>
  <div class="page">
    <div class="panel">
      <div class="header-row">
        <div>
          <h1>问卷管理</h1>
          <p class="desc">创建问卷须填写截止时间；学年须为如 2026-2027 的合法格式；可继承旧问卷答卷，学生在新问卷上再次提交会覆盖本人记录。删除问卷不会删除答卷。</p>
        </div>
        <button class="back-btn" @click="router.push({ path: '/', query: route.query })">返回首页</button>
      </div>

      <p v-if="msg" :class="['tip', msgType]">{{ msg }}</p>

      <div class="layout">
        <section class="card">
          <div class="section-title">问卷列表</div>
          <p v-if="loading" class="tip">加载中...</p>
          <p v-else-if="list.length === 0" class="tip">暂无问卷</p>
          <div v-else class="q-list">
            <div v-for="q in list" :key="q.id" class="q-item">
              <div class="q-top">
                <span class="q-title">{{ q.title }}</span>
                <span class="q-status" :style="{ color: statusColor[q.status] }">{{ statusLabel[q.status] }}</span>
              </div>
              <div class="q-meta">
                学年：{{ q.academicYear }} &nbsp;|&nbsp; 已回收：{{ q.submittedCount }} / {{ q.totalStudents ?? '—' }} 份
                <span v-if="q.pendingCount != null && q.pendingCount > 0" class="warn-pending">（待填写 {{ q.pendingCount }} 人）</span>
                <span v-if="q.deadline">&nbsp;|&nbsp; 截止：{{ q.deadline?.slice(0, 16) }}</span>
              </div>
              <div class="q-actions">
                <button v-if="q.status === 'DRAFT'" class="btn-green" @click="publish(q.id)">发布</button>
                <button v-if="q.status === 'PUBLISHED'" class="btn-orange" @click="close(q.id)">关闭</button>
                <button class="btn-blue" @click="openAnswers(q)">查看答卷 ({{ q.submittedCount }})</button>
                <button class="btn-blue" @click="exportCsv(q.id, q.title)">导出 CSV</button>
                <button class="btn-red" @click="removeQuestionnaire(q.id)">删除</button>
              </div>
            </div>
          </div>
        </section>

        <section class="card">
          <div class="section-title">新建问卷</div>
          <div class="form-grid">
            <label>
              <span>问卷标题</span>
              <input v-model="form.title" placeholder="如：2026级新生宿舍分配问卷" />
            </label>
            <label>
              <span>学年（如 2026-2027）</span>
              <input v-model="form.academicYear" placeholder="2026-2027" />
            </label>
            <label style="grid-column: span 2">
              <span>截止时间（必填）</span>
              <input v-model="form.deadline" type="datetime-local" />
            </label>
            <label style="grid-column: span 2">
              <span>继承答卷来源（可选）</span>
              <select v-model="form.inheritFromQuestionnaireId">
                <option value="">不继承</option>
                <option v-for="q in list" :key="'in' + q.id" :value="q.id">{{ q.title }}（{{ q.academicYear }}）</option>
              </select>
            </label>
          </div>
          <button class="submit-btn" :disabled="submitting" @click="create">
            {{ submitting ? '创建中...' : '创建问卷' }}
          </button>
        </section>
      </div>
    </div>

    <div v-if="showAnswers" class="modal-mask" @click.self="showAnswers = false">
      <div class="modal">
        <div class="modal-header">
          <span>「{{ viewTitle }}」答卷列表（{{ viewAnswers.length }} 份）</span>
          <button class="close-btn" @click="showAnswers = false">✕</button>
        </div>
        <div class="modal-body">
          <p v-if="viewAnswers.length === 0" class="tip">暂无答卷</p>
          <table v-else>
            <thead>
              <tr>
                <th>学生ID</th><th>起床</th><th>睡觉</th><th>熬夜</th><th>吸烟</th>
                <th>爱干净</th><th>家乡</th><th>专业</th><th>性格</th><th>爱好</th><th>提交时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="a in viewAnswers" :key="a.id">
                <td>{{ a.studentId }}</td>
                <td>{{ a.wakeUpTime || '-' }}</td>
                <td>{{ a.sleepTime || '-' }}</td>
                <td>{{ a.stayUpLate ? '是' : '否' }}</td>
                <td>{{ a.smoke ? '是' : '否' }}</td>
                <td>{{ a.keepClean ? '是' : '否' }}</td>
                <td>{{ a.hometown || '-' }}</td>
                <td>{{ a.major || '-' }}</td>
                <td>{{ a.personality || '-' }}</td>
                <td>{{ a.hobbies || '-' }}</td>
                <td>{{ a.submitTime?.slice(0, 16) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page { min-height: 100vh; padding: 32px; background: #f7f5ff; }
.panel { max-width: 1280px; margin: 0 auto; background: #fff; border-radius: 20px; padding: 32px; box-shadow: 0 16px 36px rgba(15,23,42,.08); }
.header-row { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 20px; }
h1 { margin: 0; color: #6d28d9; }
.desc { color: #475467; margin: 8px 0 0; font-size: 14px; line-height: 1.55; max-width: 720px; }
.back-btn { border: none; border-radius: 10px; padding: 10px 18px; background: #ede9fe; color: #5b21b6; cursor: pointer; font-weight: 700; white-space: nowrap; }
.layout { display: grid; grid-template-columns: 1.4fr 1fr; gap: 24px; margin-top: 16px; }
.card { background: #f8fafc; border: 1px solid #e4e7ec; border-radius: 16px; padding: 20px; }
.section-title { font-size: 18px; font-weight: 700; color: #344054; margin-bottom: 16px; }
.q-list { display: grid; gap: 12px; }
.q-item { background: #fff; border: 1px solid #e4e7ec; border-radius: 12px; padding: 14px 16px; }
.q-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.q-title { font-weight: 700; color: #1f2937; }
.q-status { font-size: 13px; font-weight: 600; }
.q-meta { font-size: 13px; color: #667085; margin-bottom: 10px; }
.warn-pending { color: #b54708; font-weight: 600; }
.q-actions { display: flex; flex-wrap: wrap; gap: 8px; }
.btn-green, .btn-orange, .btn-blue, .btn-red { border: none; border-radius: 8px; padding: 6px 14px; cursor: pointer; font-size: 13px; font-weight: 600; }
.btn-green { background: #d1fae5; color: #065f46; }
.btn-orange { background: #fef3c7; color: #92400e; }
.btn-blue { background: #dbeafe; color: #1e40af; }
.btn-red { background: #fee2e2; color: #b91c1c; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
label span { display: block; color: #667085; font-size: 14px; margin-bottom: 6px; }
input, select { width: 100%; height: 40px; border: 1px solid #d0d5dd; border-radius: 10px; padding: 0 12px; background: #fff; font-size: 14px; }
.submit-btn { margin-top: 16px; width: 100%; height: 42px; border: none; border-radius: 10px; background: #7c3aed; color: #fff; font-weight: 700; cursor: pointer; }
.submit-btn:disabled { opacity: .7; cursor: not-allowed; }
.tip { color: #667085; font-size: 14px; }
.tip.success { color: #027a48; }
.tip.error { color: #d92d20; }
.modal-mask { position: fixed; inset: 0; background: rgba(0,0,0,.4); display: flex; align-items: center; justify-content: center; z-index: 100; }
.modal { background: #fff; border-radius: 16px; width: 90vw; max-width: 1100px; max-height: 80vh; display: flex; flex-direction: column; }
.modal-header { display: flex; justify-content: space-between; align-items: center; padding: 16px 20px; border-bottom: 1px solid #e4e7ec; font-weight: 700; font-size: 16px; }
.close-btn { border: none; background: none; font-size: 18px; cursor: pointer; color: #667085; }
.modal-body { overflow: auto; padding: 16px 20px; }
table { width: 100%; border-collapse: collapse; font-size: 13px; }
th, td { padding: 10px 8px; border-bottom: 1px solid #e5e7eb; text-align: left; white-space: nowrap; }
th { color: #667085; }
</style>

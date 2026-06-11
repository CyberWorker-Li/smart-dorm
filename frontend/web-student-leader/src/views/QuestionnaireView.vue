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
  submittedCount: number
}

interface AnswerData {
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
}

const route = useRoute()
const router = useRouter()
const studentId = route.query.userId || ''

const questionnaires = ref<QuestionnaireVO[]>([])
const selectedId = ref<number | null>(null)
const loading = ref(false)
const submitting = ref(false)
const msg = ref('')
const msgType = ref<'success' | 'error'>('success')
const submitted = ref(false)

const form = ref<AnswerData>({
  wakeUpTime: '07:00',
  sleepTime: '23:00',
  stayUpLate: false,
  smoke: false,
  keepClean: true,
  hometown: '',
  major: '',
  personality: 'AMBIVERT',
  hobbies: '',
  selfDescription: '',
  dormExpectation: '',
})

const API = apiBase()

function showMsg(text: string, type: 'success' | 'error' = 'success') {
  msg.value = text
  msgType.value = type
  setTimeout(() => (msg.value = ''), 4000)
}

async function loadQuestionnaires() {
  loading.value = true
  try {
    const res = await fetch(`${API}/api/questionnaire`)
    const r = await res.json()
    if (r.code === 200) {
      questionnaires.value = r.data.filter((q: QuestionnaireVO) => q.status === 'PUBLISHED')
    }
  } finally {
    loading.value = false
  }
}

async function loadMyAnswer() {
  if (!selectedId.value || !studentId) return
  try {
    const res = await fetch(`${API}/api/questionnaire/answer?questionnaireId=${selectedId.value}&studentId=${studentId}`)
    const r = await res.json()
    if (r.code === 200 && r.data) {
      const a = r.data
      form.value = {
        wakeUpTime: a.wakeUpTime || '07:00',
        sleepTime: a.sleepTime || '23:00',
        stayUpLate: a.stayUpLate ?? false,
        smoke: a.smoke ?? false,
        keepClean: a.keepClean ?? true,
        hometown: a.hometown || '',
        major: a.major || '',
        personality: a.personality || 'AMBIVERT',
        hobbies: a.hobbies || '',
        selfDescription: a.selfDescription || '',
        dormExpectation: a.dormExpectation || '',
      }
      submitted.value = true
    } else {
      submitted.value = false
    }
  } catch {
    submitted.value = false
  }
}

async function submitAnswer() {
  if (!selectedId.value) {
    showMsg('请先选择问卷', 'error')
    return
  }
  submitting.value = true
  try {
    const res = await fetch(`${API}/api/questionnaire/answer`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ...form.value, questionnaireId: selectedId.value, studentId: Number(studentId) }),
    })
    const r = await res.json()
    if (r.code === 200) {
      showMsg('提交成功！')
      submitted.value = true
    } else {
      showMsg(r.message || '提交失败', 'error')
    }
  } finally {
    submitting.value = false
  }
}

async function onSelectChange() {
  await loadMyAnswer()
}

const personalityOptions = [
  { value: 'INTROVERT', label: '内向' },
  { value: 'EXTROVERT', label: '外向' },
  { value: 'AMBIVERT', label: '中间型' },
]

onMounted(loadQuestionnaires)
</script>

<template>
  <div class="page">
    <div class="panel">
      <div class="header-row">
        <div>
          <h1>填写分配问卷</h1>
          <p class="desc">如实填写，系统将根据您的信息为您匹配最合适的室友。</p>
        </div>
        <button class="back-btn" @click="router.push({ path: '/', query: route.query })">返回首页</button>
      </div>

      <p v-if="msg" :class="['tip-msg', msgType]">{{ msg }}</p>

      <div v-if="loading" class="tip">加载中...</div>
      <div v-else-if="questionnaires.length === 0" class="empty-box">
        当前没有开放中的问卷，请等待管理员发布。
      </div>
      <div v-else>
        <div class="select-row">
          <label>
            <span>选择问卷</span>
            <select v-model="selectedId" @change="onSelectChange">
              <option :value="null">请选择</option>
              <option v-for="q in questionnaires" :key="q.id" :value="q.id">
                {{ q.title }}（{{ q.academicYear }}）
              </option>
            </select>
          </label>
          <div v-if="submitted && selectedId" class="submitted-badge">✓ 已提交，可修改后重新提交</div>
        </div>

        <div v-if="selectedId" class="form-wrap">
          <div class="section-title">作息时间</div>
          <div class="form-grid">
            <label>
              <span>起床时间</span>
              <input v-model="form.wakeUpTime" type="time" />
            </label>
            <label>
              <span>睡觉时间</span>
              <input v-model="form.sleepTime" type="time" />
            </label>
          </div>

          <div class="section-title">生活习惯</div>
          <div class="checkbox-grid">
            <label class="check-item">
              <input v-model="form.stayUpLate" type="checkbox" />
              <span>经常熬夜（凌晨后睡）</span>
            </label>
            <label class="check-item">
              <input v-model="form.smoke" type="checkbox" />
              <span>有吸烟习惯</span>
            </label>
            <label class="check-item">
              <input v-model="form.keepClean" type="checkbox" />
              <span>注重宿舍卫生整洁</span>
            </label>
          </div>

          <div class="section-title">个人信息</div>
          <div class="form-grid">
            <label>
              <span>家乡（省/市）</span>
              <input v-model="form.hometown" placeholder="如：广东省广州市" />
            </label>
            <label>
              <span>专业</span>
              <input v-model="form.major" placeholder="如：计算机科学与技术" />
            </label>
            <label>
              <span>性格类型</span>
              <select v-model="form.personality">
                <option v-for="o in personalityOptions" :key="o.value" :value="o.value">{{ o.label }}</option>
              </select>
            </label>
            <label>
              <span>兴趣爱好（逗号分隔）</span>
              <input v-model="form.hobbies" placeholder="如：篮球,音乐,游戏" />
            </label>
          </div>

          <div class="section-title">主观填写</div>
          <div class="form-grid-full">
            <label>
              <span>自我优缺点分析</span>
              <textarea v-model="form.selfDescription" rows="3" placeholder="请简要描述自己的优缺点..." />
            </label>
            <label>
              <span>对宿舍的希望</span>
              <textarea v-model="form.dormExpectation" rows="3" placeholder="请描述您对宿舍环境和室友的期望..." />
            </label>
          </div>

          <button class="submit-btn" :disabled="submitting" @click="submitAnswer">
            {{ submitting ? '提交中...' : (submitted ? '更新答卷' : '提交答卷') }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page { min-height: 100vh; padding: 32px; background: #f5f7fb; }
.panel { max-width: 860px; margin: 0 auto; background: #fff; border-radius: 20px; padding: 32px; box-shadow: 0 16px 36px rgba(15,23,42,.08); }
.header-row { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 20px; }
h1 { margin: 0; color: #1d4ed8; }
.desc { color: #667085; margin: 8px 0 0; }
.back-btn { border: none; border-radius: 10px; padding: 10px 18px; background: #dbeafe; color: #1e40af; cursor: pointer; font-weight: 700; white-space: nowrap; }
.select-row { display: flex; align-items: center; gap: 16px; margin-bottom: 24px; }
.select-row label { display: flex; align-items: center; gap: 10px; }
.select-row label span { color: #667085; font-size: 14px; white-space: nowrap; }
.select-row select { height: 40px; border: 1px solid #d0d5dd; border-radius: 10px; padding: 0 12px; min-width: 280px; font-size: 14px; }
.submitted-badge { background: #d1fae5; color: #065f46; border-radius: 8px; padding: 6px 12px; font-size: 13px; font-weight: 600; }
.section-title { font-size: 16px; font-weight: 700; color: #1e40af; margin: 20px 0 12px; border-left: 3px solid #3b82f6; padding-left: 10px; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
.form-grid-full { display: grid; gap: 14px; }
label span { display: block; color: #667085; font-size: 14px; margin-bottom: 6px; }
input[type="text"], input[type="time"], select, textarea { width: 100%; border: 1px solid #d0d5dd; border-radius: 10px; padding: 8px 12px; background: #fff; font-size: 14px; font-family: inherit; }
input[type="time"] { height: 40px; }
textarea { resize: vertical; }
.checkbox-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin-bottom: 4px; }
.check-item { display: flex; align-items: center; gap: 8px; background: #f8fafc; border: 1px solid #e4e7ec; border-radius: 10px; padding: 12px; cursor: pointer; }
.check-item input { width: 16px; height: 16px; cursor: pointer; }
.check-item span { font-size: 14px; color: #374151; }
.submit-btn { margin-top: 24px; width: 100%; height: 46px; border: none; border-radius: 12px; background: #2563eb; color: #fff; font-size: 16px; font-weight: 700; cursor: pointer; }
.submit-btn:disabled { opacity: .7; cursor: not-allowed; }
.tip { color: #667085; font-size: 14px; }
.tip-msg { font-size: 14px; margin-bottom: 12px; }
.tip-msg.success { color: #027a48; }
.tip-msg.error { color: #d92d20; }
.empty-box { background: #f8fafc; border: 1px solid #e4e7ec; border-radius: 12px; padding: 32px; text-align: center; color: #667085; }
</style>

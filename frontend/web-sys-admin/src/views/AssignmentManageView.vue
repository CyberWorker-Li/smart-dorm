<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { apiBase } from '@/apiBase'

interface BatchVO {
  id: number
  questionnaireId: number
  questionnaireTitle: string
  academicYear: string
  status: string
  remark: string
  publishTime: string | null
  createTime: string
  totalAssigned: number
}

interface QuestionnaireVO {
  id: number
  title: string
  academicYear: string
  status: string
}

interface BuildingItem {
  id: number
  name: string
  gender: string
}

interface ConflictRow {
  id: number
  batchId: number
  roomId: number
  studentId1: number
  studentId2: number
  conflictScore: number
  description: string
  status: string
  createTime?: string
}

const route = useRoute()
const router = useRouter()
const adminId = route.query.userId || '1'

const batches = ref<BatchVO[]>([])
const questionnaires = ref<QuestionnaireVO[]>([])
const buildings = ref<BuildingItem[]>([])
const loading = ref(false)
const msg = ref('')
const msgType = ref<'success' | 'error'>('success')

const form = ref({ questionnaireId: '', academicYear: '', remark: '' })
const submitting = ref(false)
const assigningId = ref<number | null>(null)

const autoForm = ref({
  batchId: '' as string | number,
  reassignScope: 'FULL',
  targetBuildingId: '' as string | number,
})

const conflictBatchId = ref<number | null>(null)
const conflictLoading = ref(false)
const conflicts = ref<ConflictRow[]>([])

const manualForm = ref({
  batchId: '',
  studentId: '',
  roomId: '',
  bedNo: '',
})

const bulkForm = ref({
  batchId: '',
  academicYear: '',
  gender: 'MALE',
  sourceBuildingId: '',
  targetBuildingId: '',
})

const API = apiBase()

function showMsg(text: string, type: 'success' | 'error' = 'success') {
  msg.value = text
  msgType.value = type
  setTimeout(() => (msg.value = ''), 5000)
}

async function loadData() {
  loading.value = true
  try {
    const [bRes, qRes, bdRes] = await Promise.all([
      fetch(`${API}/api/assignment/batches`),
      fetch(`${API}/api/questionnaire`),
      fetch(`${API}/api/dorm/structure/buildings`),
    ])
    const bData = await bRes.json()
    const qData = await qRes.json()
    const bdData = await bdRes.json()
    if (bData.code === 200) batches.value = bData.data
    if (qData.code === 200) questionnaires.value = qData.data
    if (bdData.code === 200) buildings.value = bdData.data || []
  } finally {
    loading.value = false
  }
}

function academicYearHint(s: string): boolean {
  return /^\d{4}-\d{4}$/.test(s.trim())
}

async function createBatch() {
  if (!form.value.questionnaireId || !form.value.academicYear) {
    showMsg('请选择问卷并填写学年', 'error')
    return
  }
  if (!academicYearHint(form.value.academicYear)) {
    showMsg('学年须为连续两年四位数字，例如 2026-2027', 'error')
    return
  }
  submitting.value = true
  try {
    const res = await fetch(`${API}/api/assignment/batches?adminId=${adminId}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        questionnaireId: Number(form.value.questionnaireId),
        academicYear: form.value.academicYear.trim(),
        remark: form.value.remark,
      }),
    })
    const r = await res.json()
    if (r.code === 200) {
      showMsg('批次创建成功')
      form.value = { questionnaireId: '', academicYear: '', remark: '' }
      await loadData()
    } else {
      showMsg(r.message || '创建失败', 'error')
    }
  } finally {
    submitting.value = false
  }
}

async function runAutoAssign(batchId: number) {
  autoForm.value.batchId = batchId
  autoForm.value.reassignScope = 'FULL'
  autoForm.value.targetBuildingId = ''
}

async function confirmAutoAssign() {
  const batchId = Number(autoForm.value.batchId)
  if (!batchId) return
  assigningId.value = batchId
  try {
    const body: Record<string, unknown> = { reassignScope: autoForm.value.reassignScope }
    if (autoForm.value.targetBuildingId !== '' && autoForm.value.targetBuildingId != null) {
      body.targetBuildingId = Number(autoForm.value.targetBuildingId)
    }
    const res = await fetch(`${API}/api/assignment/batches/${batchId}/auto-assign`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
    const r = await res.json()
    if (r.code === 200) {
      showMsg('自动分配完成')
      autoForm.value.batchId = ''
    } else {
      showMsg(r.message || '分配失败', 'error')
    }
    await loadData()
  } finally {
    assigningId.value = null
  }
}

function cancelAutoDialog() {
  autoForm.value.batchId = ''
  assigningId.value = null
}

async function publishBatch(batchId: number) {
  const res = await fetch(`${API}/api/assignment/batches/${batchId}/publish`, { method: 'PUT' })
  const r = await res.json()
  r.code === 200 ? showMsg('已公示') : showMsg(r.message, 'error')
  await loadData()
}

async function openConflicts(batchId: number) {
  conflictBatchId.value = batchId
  conflicts.value = []
  conflictLoading.value = true
  try {
    const res = await fetch(`${API}/api/assignment/batches/${batchId}/conflicts`)
    const r = await res.json()
    if (r.code === 200) {
      conflicts.value = r.data || []
    } else {
      showMsg(r.message || '加载冲突预警失败', 'error')
    }
  } finally {
    conflictLoading.value = false
  }
}

function closeConflicts() {
  conflictBatchId.value = null
  conflicts.value = []
  conflictLoading.value = false
}

async function removeFromBatch() {
  const batchId = Number(manualForm.value.batchId)
  const studentId = Number(manualForm.value.studentId)
  if (!batchId || !studentId) {
    showMsg('请填写批次 ID 与学生 ID', 'error')
    return
  }
  const res = await fetch(`${API}/api/assignment/batches/${batchId}/students/${studentId}`, { method: 'DELETE' })
  const r = await res.json()
  r.code === 200 ? showMsg('已从本批次移出') : showMsg(r.message, 'error')
  await loadData()
}

async function manualAssignRoom() {
  const batchId = Number(manualForm.value.batchId)
  const studentId = Number(manualForm.value.studentId)
  const roomId = Number(manualForm.value.roomId)
  if (!batchId || !studentId || !roomId) {
    showMsg('请填写批次、学生、房间 ID', 'error')
    return
  }
  const payload: Record<string, unknown> = { batchId, studentId, roomId }
  if (manualForm.value.bedNo !== '' && manualForm.value.bedNo != null) {
    payload.bedNo = Number(manualForm.value.bedNo)
  }
  const res = await fetch(`${API}/api/assignment/manual`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
  const r = await res.json()
  r.code === 200 ? showMsg('手动编入成功') : showMsg(r.message, 'error')
  await loadData()
}

async function bulkBuildingMove() {
  const batchId = Number(bulkForm.value.batchId)
  if (!batchId || !bulkForm.value.academicYear.trim()) {
    showMsg('请填写批次与学年', 'error')
    return
  }
  if (!academicYearHint(bulkForm.value.academicYear)) {
    showMsg('学年格式须为 2026-2027', 'error')
    return
  }
  const sourceBuildingId = Number(bulkForm.value.sourceBuildingId)
  const targetBuildingId = Number(bulkForm.value.targetBuildingId)
  if (!sourceBuildingId || !targetBuildingId) {
    showMsg('请选择源楼与目标楼', 'error')
    return
  }
  const res = await fetch(`${API}/api/assignment/bulk-building-move`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      batchId,
      academicYear: bulkForm.value.academicYear.trim(),
      gender: bulkForm.value.gender,
      sourceBuildingId,
      targetBuildingId,
    }),
  })
  const r = await res.json()
  r.code === 200 ? showMsg('批量换楼分配完成') : showMsg(r.message, 'error')
  await loadData()
}

function buildingLabel(b: BuildingItem) {
  return `${b.name}（${b.gender === 'MALE' ? '男生楼' : '女生楼'}）`
}

const statusLabel: Record<string, string> = { DRAFT: '草稿', PUBLISHED: '已公示', ARCHIVED: '已归档' }
const statusColor: Record<string, string> = { DRAFT: '#667085', PUBLISHED: '#027a48', ARCHIVED: '#b54708' }

onMounted(loadData)
</script>

<template>
  <div class="page">
    <div class="panel">
      <div class="header-row">
        <div>
          <h1>宿舍分配管理</h1>
          <p class="desc">
            自动分配使用<strong>多维度加权相似度 + 贪心聚类</strong>算法；分配对象为学生档案学年与本批次学年一致的在档学生；仅<strong>有效</strong>的楼房/楼层/房间参与分配，并自动生成冲突预警（可在每个批次中查看）。
          </p>
        </div>
        <button class="back-btn" @click="router.push({ path: '/', query: route.query })">返回首页</button>
      </div>

      <p v-if="msg" :class="['tip', msgType]">{{ msg }}</p>

      <div v-if="autoForm.batchId !== ''" class="modal-mask" @click.self="cancelAutoDialog">
        <div class="modal" @click.stop>
          <div class="modal-title">自动分配选项（批次 #{{ autoForm.batchId }}）</div>
          <label>
            <span>对原有本批次结果的影响</span>
            <select v-model="autoForm.reassignScope">
              <option value="FULL">完全重分（清空本批次再全部分配）</option>
              <option value="SAME_ACADEMIC_YEAR">仅重分档案学年=批次学年的学生</option>
              <option value="INCREMENTAL">不影响已有记录（只给尚未分配的学生补位）</option>
            </select>
          </label>
          <label>
            <span>限定在某楼内分配（可选）</span>
            <select v-model="autoForm.targetBuildingId">
              <option value="">不限定（全校有效房间）</option>
              <option v-for="b in buildings" :key="b.id" :value="b.id">{{ buildingLabel(b) }}</option>
            </select>
          </label>
          <div class="modal-actions">
            <button type="button" class="btn-muted" @click="cancelAutoDialog">取消</button>
            <button type="button" class="btn-purple" :disabled="assigningId != null" @click="confirmAutoAssign">
              {{ assigningId != null ? '执行中...' : '开始分配' }}
            </button>
          </div>
        </div>
      </div>

      <div v-if="conflictBatchId != null" class="modal-mask" @click.self="closeConflicts">
        <div class="modal wide" @click.stop>
          <div class="modal-title">冲突预警（批次 #{{ conflictBatchId }}）</div>
          <div v-if="conflictLoading" class="tip">加载中...</div>
          <div v-else-if="conflicts.length === 0" class="tip">暂无冲突预警</div>
          <div v-else class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>ID</th>
                  <th>房间ID</th>
                  <th>学生A</th>
                  <th>学生B</th>
                  <th>冲突分</th>
                  <th>原因</th>
                  <th>状态</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="c in conflicts" :key="c.id">
                  <td>{{ c.id }}</td>
                  <td>{{ c.roomId }}</td>
                  <td>{{ c.studentId1 }}</td>
                  <td>{{ c.studentId2 }}</td>
                  <td>{{ Number(c.conflictScore).toFixed(3) }}</td>
                  <td class="desc-col">{{ c.description || '-' }}</td>
                  <td>{{ c.status }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <div class="modal-actions">
            <button type="button" class="btn-muted" @click="closeConflicts">关闭</button>
          </div>
        </div>
      </div>

      <div class="layout">
        <section class="card wide">
          <div class="section-title">分配批次列表</div>
          <p v-if="loading" class="tip">加载中...</p>
          <p v-else-if="batches.length === 0" class="tip">暂无批次</p>
          <div v-else class="b-list">
            <div v-for="b in batches" :key="b.id" class="b-item">
              <div class="b-top">
                <span class="b-title">{{ b.academicYear }} 学年分配</span>
                <span class="b-status" :style="{ color: statusColor[b.status] }">{{ statusLabel[b.status] }}</span>
              </div>
              <div class="b-meta">
                关联问卷：{{ b.questionnaireTitle || '-' }} &nbsp;|&nbsp; 已分配：{{ b.totalAssigned }} 人
                <span v-if="b.publishTime">&nbsp;|&nbsp; 公示时间：{{ b.publishTime?.slice(0, 16) }}</span>
              </div>
              <div v-if="b.remark" class="b-remark">备注：{{ b.remark }}</div>
              <div class="b-actions">
                <button
                  v-if="b.status === 'DRAFT'"
                  class="btn-purple"
                  :disabled="assigningId === b.id"
                  @click="runAutoAssign(b.id)"
                >
                  执行自动分配
                </button>
                <button
                  v-if="b.status === 'DRAFT' && b.totalAssigned > 0"
                  class="btn-green"
                  @click="publishBatch(b.id)"
                >
                  公示结果
                </button>
                <button
                  v-if="b.totalAssigned > 0"
                  class="btn-muted"
                  @click="openConflicts(b.id)"
                >
                  冲突预警
                </button>
              </div>
            </div>
          </div>
        </section>

        <div class="right-col">
          <section class="card">
            <div class="section-title">新建分配批次</div>
            <p class="mini-tip">批次学年须与所选问卷学年一致；分配对象为本学年全部在档学生（见顶部说明）。</p>
            <div class="form-grid">
              <label style="grid-column: span 2">
                <span>关联问卷</span>
                <select v-model="form.questionnaireId">
                  <option value="">请选择问卷</option>
                  <option v-for="q in questionnaires" :key="q.id" :value="q.id">
                    {{ q.title }}（{{ q.academicYear }}）
                  </option>
                </select>
              </label>
              <label style="grid-column: span 2">
                <span>学年（须与问卷一致，如 2026-2027）</span>
                <input v-model="form.academicYear" placeholder="2026-2027" />
              </label>
              <label style="grid-column: span 2">
                <span>备注（可选）</span>
                <input v-model="form.remark" placeholder="可填写备注说明" />
              </label>
            </div>
            <button class="submit-btn" :disabled="submitting" @click="createBatch">
              {{ submitting ? '创建中...' : '创建批次' }}
            </button>
          </section>

          <section class="card">
            <div class="section-title">手动移出 / 编入（草稿批次）</div>
            <div class="form-grid small">
              <label><span>批次 ID</span><input v-model="manualForm.batchId" placeholder="数字" /></label>
              <label><span>学生用户 ID</span><input v-model="manualForm.studentId" placeholder="sys_user.id" /></label>
              <label style="grid-column: span 2"><span>目标房间 ID</span><input v-model="manualForm.roomId" /></label>
              <label style="grid-column: span 2"><span>床位号（可选，不填则自动）</span><input v-model="manualForm.bedNo" /></label>
            </div>
            <div class="row-btns">
              <button type="button" class="btn-orange" @click="removeFromBatch">从本批次移出</button>
              <button type="button" class="btn-green" @click="manualAssignRoom">编入房间</button>
            </div>
          </section>

          <section class="card">
            <div class="section-title">按学年+性别批量换楼（草稿批次）</div>
            <p class="mini-tip">将当前批次中、指定学年与性别、且床位在<strong>源楼</strong>的学生迁出，并在<strong>目标楼</strong>内自动分配；性别须与两栋楼类型一致。</p>
            <div class="form-grid small">
              <label><span>批次 ID</span><input v-model="bulkForm.batchId" /></label>
              <label><span>学年</span><input v-model="bulkForm.academicYear" placeholder="2026-2027" /></label>
              <label>
                <span>性别</span>
                <select v-model="bulkForm.gender">
                  <option value="MALE">男</option>
                  <option value="FEMALE">女</option>
                </select>
              </label>
              <label>
                <span>源楼</span>
                <select v-model="bulkForm.sourceBuildingId">
                  <option value="">请选择</option>
                  <option v-for="b in buildings" :key="'s' + b.id" :value="b.id">{{ buildingLabel(b) }}</option>
                </select>
              </label>
              <label>
                <span>目标楼</span>
                <select v-model="bulkForm.targetBuildingId">
                  <option value="">请选择</option>
                  <option v-for="b in buildings" :key="'t' + b.id" :value="b.id">{{ buildingLabel(b) }}</option>
                </select>
              </label>
            </div>
            <button type="button" class="submit-btn secondary" @click="bulkBuildingMove">执行批量换楼分配</button>
          </section>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page { min-height: 100vh; padding: 32px; background: #f7f5ff; }
.panel { max-width: 1320px; margin: 0 auto; background: #fff; border-radius: 20px; padding: 32px; box-shadow: 0 16px 36px rgba(15,23,42,.08); }
.header-row { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 20px; }
h1 { margin: 0; color: #6d28d9; }
.desc { color: #475467; margin: 8px 0 0; font-size: 14px; line-height: 1.6; max-width: 900px; }
.back-btn { border: none; border-radius: 10px; padding: 10px 18px; background: #ede9fe; color: #5b21b6; cursor: pointer; font-weight: 700; white-space: nowrap; }
.layout { display: grid; grid-template-columns: 1.35fr 1fr; gap: 24px; margin-top: 16px; align-items: start; }
.card { background: #f8fafc; border: 1px solid #e4e7ec; border-radius: 16px; padding: 20px; }
.card.wide { grid-column: 1; }
.right-col { display: flex; flex-direction: column; gap: 16px; }
.section-title { font-size: 18px; font-weight: 700; color: #344054; margin-bottom: 12px; }
.mini-tip { font-size: 13px; color: #667085; margin: 0 0 12px; line-height: 1.5; }
.b-list { display: grid; gap: 12px; }
.b-item { background: #fff; border: 1px solid #e4e7ec; border-radius: 12px; padding: 14px 16px; }
.b-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.b-title { font-weight: 700; color: #1f2937; }
.b-status { font-size: 13px; font-weight: 600; }
.b-meta { font-size: 13px; color: #667085; margin-bottom: 6px; }
.b-remark { font-size: 13px; color: #92400e; margin-bottom: 8px; }
.b-actions { display: flex; gap: 8px; flex-wrap: wrap; }
.btn-purple, .btn-green, .btn-orange { border: none; border-radius: 8px; padding: 6px 14px; cursor: pointer; font-size: 13px; font-weight: 600; }
.btn-purple { background: #ede9fe; color: #5b21b6; }
.btn-purple:disabled { opacity: .6; cursor: not-allowed; }
.btn-green { background: #d1fae5; color: #065f46; }
.btn-orange { background: #ffedd5; color: #9a3412; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
.form-grid.small { grid-template-columns: 1fr 1fr; }
label span { display: block; color: #667085; font-size: 14px; margin-bottom: 6px; }
input, select { width: 100%; height: 40px; border: 1px solid #d0d5dd; border-radius: 10px; padding: 0 12px; background: #fff; font-size: 14px; }
.submit-btn { margin-top: 12px; width: 100%; height: 42px; border: none; border-radius: 10px; background: #7c3aed; color: #fff; font-weight: 700; cursor: pointer; }
.submit-btn.secondary { background: #4f46e5; }
.submit-btn:disabled { opacity: .7; cursor: not-allowed; }
.row-btns { display: flex; gap: 10px; margin-top: 12px; flex-wrap: wrap; }
.tip { font-size: 14px; }
.tip.success { color: #027a48; }
.tip.error { color: #d92d20; }
.modal-mask { position: fixed; inset: 0; background: rgba(0,0,0,.45); display: flex; align-items: center; justify-content: center; z-index: 200; }
.modal { background: #fff; border-radius: 16px; padding: 24px; width: 100%; max-width: 440px; box-shadow: 0 20px 50px rgba(0,0,0,.15); }
.modal.wide { max-width: 980px; }
.modal-title { font-weight: 700; font-size: 17px; margin-bottom: 16px; color: #1f2937; }
.modal .modal-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 20px; }
.btn-muted { border: 1px solid #d0d5dd; background: #fff; border-radius: 8px; padding: 8px 16px; cursor: pointer; font-weight: 600; color: #475467; }
.table-wrap { overflow: auto; border-radius: 12px; border: 1px solid #e4e7ec; background: #fff; }
table { width: 100%; border-collapse: collapse; min-width: 820px; }
th, td { padding: 10px 12px; border-bottom: 1px solid #e4e7ec; font-size: 14px; text-align: left; }
th { background: #f3f4f6; color: #344054; font-weight: 700; }
.desc-col { max-width: 360px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
</style>

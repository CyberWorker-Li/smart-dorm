<script setup lang="ts">
import { apiBase } from '@/apiBase'
import { computed, onMounted, ref, toRaw, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const API = apiBase()

interface UserItem {
  id: number
  username: string
  realName: string
  userNo: string
  userType: string
  gender: string
  leader: boolean
  academicYear: string | null
  enabled: boolean
}

const route = useRoute()
const router = useRouter()

const username = computed(() => String(route.query.username || ''))
const userType = computed(() => String(route.query.userType || ''))
const hasLoginInfo = computed(() => !!username.value && userType.value === 'SYS_ADMIN')

const users = ref<UserItem[]>([])
const listLoading = ref(false)
const submitLoading = ref(false)
const importLoading = ref(false)
const deleteLoading = ref(false)
const errorMessage = ref('')
const successMessage = ref('')
const importErrorMessage = ref('')
const importSuccessMessage = ref('')
const importFile = ref<File | null>(null)
const deleteErrorMessage = ref('')
const deleteSuccessMessage = ref('')
const deleteModalVisible = ref(false)
const deletingUser = ref<UserItem | null>(null)
const deleteForce = ref(false)

type UserTab = 'STUDENT' | 'DORM_MANAGER' | 'SYS_ADMIN'
const activeTab = ref<UserTab>('STUDENT')
const pageSize = ref(10)
const currentPage = ref(1)

interface MyAssignmentResultVO {
  batchId: number
  academicYear: string
  building: string
  roomNo: string
  roomGender: string
  bedNo: number | null
  roommates?: Array<unknown>
}

const expandedStudentId = ref<number | null>(null)
const studentResultMap = ref<Record<number, MyAssignmentResultVO | null | undefined>>({})
const studentResultLoading = ref<Record<number, boolean>>({})
const studentResultError = ref<Record<number, string>>({})

const tabLabel: Record<UserTab, string> = {
  STUDENT: '学生',
  DORM_MANAGER: '宿管',
  SYS_ADMIN: '管理员',
}

const tabCounts = computed(() => {
  const m: Record<UserTab, number> = { STUDENT: 0, DORM_MANAGER: 0, SYS_ADMIN: 0 }
  for (const u of users.value) {
    const t = (u.userType || '').toUpperCase() as UserTab
    if (t === 'STUDENT' || t === 'DORM_MANAGER' || t === 'SYS_ADMIN') {
      m[t]++
    }
  }
  return m
})

const filteredUsers = computed(() => {
  const t = activeTab.value
  return users.value.filter(u => String(u.userType || '').toUpperCase() === t)
})

const totalPages = computed(() => {
  const size = Math.max(1, Number(pageSize.value) || 10)
  return Math.max(1, Math.ceil(filteredUsers.value.length / size))
})

const pagedUsers = computed(() => {
  const size = Math.max(1, Number(pageSize.value) || 10)
  const page = Math.min(Math.max(1, Number(currentPage.value) || 1), totalPages.value)
  const start = (page - 1) * size
  return filteredUsers.value.slice(start, start + size)
})

const form = ref({
  username: '',
  password: '123456',
  realName: '',
  userNo: '',
  userType: 'STUDENT',
  gender: 'MALE',
  leader: false,
  academicYear: '2025-2026',
})

watch(() => form.value.userType, (value) => {
  if (value !== 'STUDENT') {
    form.value.leader = false
    form.value.academicYear = '2025-2026'
  }
})

watch([activeTab, pageSize], () => {
  currentPage.value = 1
})

watch([filteredUsers, pageSize, currentPage], () => {
  const p = Math.min(Math.max(1, Number(currentPage.value) || 1), totalPages.value)
  if (p !== currentPage.value) currentPage.value = p
})

function setTab(t: UserTab) {
  activeTab.value = t
}

function prevPage() {
  currentPage.value = Math.max(1, currentPage.value - 1)
}

function nextPage() {
  currentPage.value = Math.min(totalPages.value, currentPage.value + 1)
}

/** 与后端 MALE/FEMALE 对齐，避免大小写或空格导致误判 */
function normalizeGenderCode(g: string | null | undefined): 'MALE' | 'FEMALE' | null {
  if (g == null || typeof g !== 'string') return null
  const u = g.trim().toUpperCase()
  if (u === 'MALE' || u === 'FEMALE') return u
  return null
}

function genderDisplayText(g: string | null | undefined) {
  const n = normalizeGenderCode(g)
  if (n === 'FEMALE') return '女'
  if (n === 'MALE') return '男'
  return '未设置'
}

function enabledText(v: boolean) {
  return v ? '启用' : '禁用'
}

function safeStr(v: unknown) {
  if (v == null) return '-'
  const s = String(v)
  return s.trim() ? s : '-'
}

async function toggleStudentDetail(user: UserItem) {
  if (expandedStudentId.value === user.id) {
    expandedStudentId.value = null
    return
  }
  expandedStudentId.value = user.id

  if (studentResultMap.value[user.id] !== undefined) {
    return
  }

  studentResultLoading.value = { ...studentResultLoading.value, [user.id]: true }
  studentResultError.value = { ...studentResultError.value, [user.id]: '' }
  try {
    const res = await fetch(`${API}/api/assignment/my-result?studentId=${encodeURIComponent(String(user.id))}`)
    const r = await res.json().catch(() => null)
    if (!res.ok || !r || r.code !== 200) {
      const msg = r?.message || '加载宿舍信息失败'
      studentResultError.value = { ...studentResultError.value, [user.id]: msg }
      studentResultMap.value = { ...studentResultMap.value, [user.id]: null }
      return
    }
    const data = r.data as MyAssignmentResultVO | null
    studentResultMap.value = { ...studentResultMap.value, [user.id]: data }
  } catch (e) {
    const msg = e instanceof Error ? e.message : '加载宿舍信息失败'
    studentResultError.value = { ...studentResultError.value, [user.id]: msg }
    studentResultMap.value = { ...studentResultMap.value, [user.id]: null }
  } finally {
    studentResultLoading.value = { ...studentResultLoading.value, [user.id]: false }
  }
}

async function loadUsers() {
  listLoading.value = true
  errorMessage.value = ''
  try {
    const response = await fetch(`${API}/api/admin/users`)
    const result = await response.json()
    if (!response.ok || result.code !== 200) throw new Error(result.message || '获取用户列表失败')
    users.value = (result.data as unknown[]).map((row: unknown) => {
      const u = row as Record<string, unknown>
      return {
        id: Number(u.id),
        username: String(u.username ?? ''),
        realName: String(u.realName ?? ''),
        userNo: String(u.userNo ?? ''),
        userType: String(u.userType ?? ''),
        gender: String(u.gender ?? ''),
        leader: Boolean(u.leader),
        academicYear: u.academicYear != null ? String(u.academicYear) : null,
        enabled: Boolean(u.enabled),
      }
    })
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '获取用户列表失败'
  } finally {
    listLoading.value = false
  }
}

async function handleCreateUser() {
  if (!form.value.username || !form.value.password || !form.value.realName || !form.value.userType) {
    errorMessage.value = '请填写完整的账号信息'
    successMessage.value = ''
    return
  }

  if (form.value.userType === 'STUDENT' && (!form.value.academicYear || !String(form.value.academicYear).trim())) {
    errorMessage.value = '学生账号请填写学年'
    successMessage.value = ''
    return
  }

  const genderNorm = normalizeGenderCode(form.value.gender)
  if (!genderNorm) {
    errorMessage.value = '请选择性别'
    successMessage.value = ''
    return
  }

  submitLoading.value = true
  errorMessage.value = ''
  successMessage.value = ''

  try {
    const raw = toRaw(form.value)
    const payload: Record<string, unknown> = {
      username: String(raw.username).trim(),
      password: String(raw.password),
      realName: String(raw.realName).trim(),
      userNo: raw.userNo != null && String(raw.userNo).trim() !== '' ? String(raw.userNo).trim() : '',
      userType: String(raw.userType),
      gender: genderNorm,
      leader: Boolean(raw.leader),
    }
    if (String(raw.userType) === 'STUDENT') {
      payload.academicYear = String(raw.academicYear).trim()
    }
    const response = await fetch(`${API}/api/admin/users`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    })
    const result = await response.json()
    if (!response.ok || result.code !== 200) throw new Error(result.message || '创建失败')

    successMessage.value = `创建成功：${result.data.realName}（${result.data.username}）`
    form.value = {
      username: '',
      password: '123456',
      realName: '',
      userNo: '',
      userType: 'STUDENT',
      gender: 'MALE',
      leader: false,
      academicYear: '2025-2026',
    }
    await loadUsers()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '创建失败，请稍后重试'
  } finally {
    submitLoading.value = false
  }
}

function resetImportMsg() {
  importErrorMessage.value = ''
  importSuccessMessage.value = ''
}

function resetDeleteMsg() {
  deleteErrorMessage.value = ''
  deleteSuccessMessage.value = ''
}

function formatFileSize(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes <= 0) return '0 B'
  if (bytes < 1024) return `${bytes} B`
  const kb = bytes / 1024
  if (kb < 1024) return `${kb.toFixed(1)} KB`
  const mb = kb / 1024
  return `${mb.toFixed(1)} MB`
}

function setImportFile(file: File | null) {
  resetImportMsg()
  importFile.value = file
}

async function downloadTemplate() {
  resetImportMsg()
  if (!hasLoginInfo.value) {
    importErrorMessage.value = '请先从统一登录门户进入本后台'
    return
  }
  try {
    const res = await fetch(`${API}/api/admin/users/import/template`)
    if (!res.ok) throw new Error('模板下载失败')
    const blob = await res.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'user_import_template.csv'
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(url)
  } catch (e) {
    importErrorMessage.value = e instanceof Error ? e.message : '模板下载失败'
  }
}

async function uploadImport() {
  resetImportMsg()
  if (!hasLoginInfo.value) {
    importErrorMessage.value = '请先从统一登录门户进入本后台'
    return
  }
  if (!importFile.value) {
    importErrorMessage.value = '请先选择 CSV 文件'
    return
  }
  importLoading.value = true
  try {
    const formData = new FormData()
    formData.append('file', importFile.value)
    const res = await fetch(`${API}/api/admin/users/import`, {
      method: 'POST',
      body: formData,
    })
    const r = await res.json().catch(() => null)
    if (!res.ok || !r || r.code !== 200) {
      throw new Error(r?.message || '导入失败')
    }
    importSuccessMessage.value = String(r.data || '导入成功')
    setImportFile(null)
    await loadUsers()
  } catch (e) {
    importErrorMessage.value = e instanceof Error ? e.message : '导入失败'
  } finally {
    importLoading.value = false
  }
}

function onPickImportFile(e: Event) {
  if (!hasLoginInfo.value) return
  const input = e.target as HTMLInputElement
  const file = input.files?.[0] || null
  if (file) setImportFile(file)
  input.value = ''
}

function onDropImport(e: DragEvent) {
  e.preventDefault()
  if (!hasLoginInfo.value) return
  const file = e.dataTransfer?.files?.[0] || null
  if (file) setImportFile(file)
}

function canDeleteUser(u: UserItem): boolean {
  if (!hasLoginInfo.value) return false
  if (!u || !u.username) return false
  if (String(u.username).toLowerCase() === 'admin') return false
  return true
}

function openDeleteModal(u: UserItem) {
  resetDeleteMsg()
  if (!canDeleteUser(u)) return
  deletingUser.value = u
  deleteForce.value = false
  deleteModalVisible.value = true
}

function closeDeleteModal() {
  deleteModalVisible.value = false
  deletingUser.value = null
  deleteForce.value = false
}

async function confirmDelete() {
  resetDeleteMsg()
  if (!hasLoginInfo.value) {
    deleteErrorMessage.value = '请先从统一登录门户进入本后台'
    return
  }
  const u = deletingUser.value
  if (!u) return
  deleteLoading.value = true
  try {
    const url = new URL(`${API}/api/admin/users/${u.id}`, window.location.origin)
    if (deleteForce.value) url.searchParams.set('force', 'true')
    const res = await fetch(url.toString(), { method: 'DELETE' })
    const r = await res.json().catch(() => null)
    if (!res.ok || !r || r.code !== 200) {
      throw new Error(r?.message || '删除失败')
    }
    deleteSuccessMessage.value = `删除成功：${u.username}`
    closeDeleteModal()
    await loadUsers()
  } catch (e) {
    deleteErrorMessage.value = e instanceof Error ? e.message : '删除失败'
  } finally {
    deleteLoading.value = false
  }
}

function goBack() {
  router.push({ path: '/', query: route.query })
}

onMounted(loadUsers)
</script>

<template>
  <div class="page">
    <div class="panel">
      <div class="header-row">
        <div>
          <h1>账号管理</h1>
          <p class="desc">查看当前所有用户，并创建新的学生、宿舍长、宿管或管理员账号。</p>
        </div>
        <button class="back-btn" @click="goBack">返回首页</button>
      </div>

      <p v-if="!hasLoginInfo" class="warning">请先从统一登录门户进入本后台。</p>

      <div class="layout">
        <section class="card">
          <div class="section-title">当前用户列表</div>
          <div v-if="!listLoading && users.length > 0" class="list-head">
            <div class="tabs">
              <button
                v-for="t in (['STUDENT','DORM_MANAGER','SYS_ADMIN'] as const)"
                :key="t"
                class="tab"
                :class="{ active: activeTab === t }"
                @click="setTab(t)"
              >
                {{ tabLabel[t] }}（{{ tabCounts[t] }}）
              </button>
            </div>
            <div class="pager-ctrl">
              <span class="pager-text">每页</span>
              <select v-model.number="pageSize" class="pager-size">
                <option :value="10">10</option>
                <option :value="20">20</option>
                <option :value="50">50</option>
              </select>
              <span class="pager-text">条</span>
            </div>
          </div>
          <p v-if="listLoading" class="tip">加载中...</p>
          <p v-else-if="users.length === 0" class="tip">暂无用户数据</p>
          <p v-else-if="filteredUsers.length === 0" class="tip">该分类暂无用户</p>
          <div v-else-if="activeTab === 'STUDENT'" class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>ID</th>
                  <th>用户名</th>
                  <th>姓名</th>
                  <th>性别</th>
                  <th>学年</th>
                  <th>宿舍长</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <template v-for="user in pagedUsers" :key="user.id">
                  <tr>
                  <td>{{ user.id }}</td>
                  <td>{{ user.username }}</td>
                  <td>{{ user.realName }}</td>
                  <td>{{ genderDisplayText(user.gender) }}</td>
                  <td>{{ user.userType === 'STUDENT' ? (user.academicYear || '-') : '-' }}</td>
                  <td>{{ user.userType === 'STUDENT' ? (user.leader ? '是' : '否') : '-' }}</td>
                  <td>
                    <button class="ghost-btn mini" :disabled="deleteLoading" @click="toggleStudentDetail(user)">
                      {{ expandedStudentId === user.id ? '收起' : '详情' }}
                    </button>
                    <button
                      class="danger-btn"
                      :disabled="!canDeleteUser(user) || deleteLoading"
                      @click="openDeleteModal(user)"
                    >
                      删除
                    </button>
                  </td>
                  </tr>
                  <tr v-if="expandedStudentId === user.id" class="expand-row">
                    <td :colspan="7">
                      <div class="expand-box">
                        <div class="expand-grid">
                          <div class="kv"><span class="k">编号</span><span class="v">{{ safeStr(user.userNo) }}</span></div>
                          <div class="kv"><span class="k">状态</span><span class="v">{{ enabledText(user.enabled) }}</span></div>
                          <div class="kv"><span class="k">当前宿舍</span>
                            <span class="v">
                              <span v-if="studentResultLoading[user.id]">加载中...</span>
                              <span v-else-if="studentResultError[user.id]">{{ studentResultError[user.id] }}</span>
                              <span v-else-if="studentResultMap[user.id] == null">暂无已公示分配结果</span>
                              <span v-else>
                                {{ safeStr(studentResultMap[user.id]?.building) }} {{ safeStr(studentResultMap[user.id]?.roomNo) }}
                                <span v-if="studentResultMap[user.id]?.bedNo">（床位 {{ studentResultMap[user.id]?.bedNo }}）</span>
                              </span>
                            </span>
                          </div>
                          <div class="kv"><span class="k">批次</span>
                            <span class="v">
                              <span v-if="studentResultMap[user.id] && studentResultMap[user.id]?.batchId">{{ studentResultMap[user.id]?.batchId }}</span>
                              <span v-else>-</span>
                            </span>
                          </div>
                        </div>
                      </div>
                    </td>
                  </tr>
                </template>
              </tbody>
            </table>
          </div>
          <div v-else class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>ID</th>
                  <th>用户名</th>
                  <th>姓名</th>
                  <th>编号</th>
                  <th>角色</th>
                  <th>性别</th>
                  <th>学年</th>
                  <th>宿舍长</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="user in pagedUsers" :key="user.id">
                  <td>{{ user.id }}</td>
                  <td>{{ user.username }}</td>
                  <td>{{ user.realName }}</td>
                  <td>{{ user.userNo || '-' }}</td>
                  <td>{{ user.userType }}</td>
                  <td>{{ genderDisplayText(user.gender) }}</td>
                  <td>{{ user.userType === 'STUDENT' ? (user.academicYear || '-') : '-' }}</td>
                  <td>{{ user.userType === 'STUDENT' ? (user.leader ? '是' : '否') : '-' }}</td>
                  <td>
                    <button
                      class="danger-btn"
                      :disabled="!canDeleteUser(user) || deleteLoading"
                      @click="openDeleteModal(user)"
                    >
                      删除
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <div v-if="!listLoading && filteredUsers.length > 0" class="pager">
            <button class="ghost-btn" :disabled="currentPage <= 1" @click="prevPage">上一页</button>
            <div class="pager-info">
              <span>第 {{ currentPage }} / {{ totalPages }} 页</span>
              <span class="sep">·</span>
              <span>共 {{ filteredUsers.length }} 条</span>
            </div>
            <button class="ghost-btn" :disabled="currentPage >= totalPages" @click="nextPage">下一页</button>
          </div>

          <p v-if="deleteSuccessMessage" class="success">{{ deleteSuccessMessage }}</p>
          <p v-if="deleteErrorMessage" class="error">{{ deleteErrorMessage }}</p>
        </section>

        <div class="right-col">
          <section class="card">
            <div class="section-title">新增用户</div>
            <div class="form-grid">
              <label><span>用户名</span><input v-model="form.username" type="text" placeholder="如 stu002" /></label>
              <label><span>姓名</span><input v-model="form.realName" type="text" placeholder="请输入姓名" /></label>
              <label><span>初始密码</span><input v-model="form.password" type="text" placeholder="默认 123456" /></label>
              <label><span>编号</span><input v-model="form.userNo" type="text" placeholder="学号/工号/编号" /></label>
              <label>
                <span>角色类型</span>
                <select v-model="form.userType">
                  <option value="STUDENT">学生</option>
                  <option value="DORM_MANAGER">宿管</option>
                  <option value="SYS_ADMIN">系统管理员</option>
                </select>
              </label>
              <label>
                <span>性别</span>
                <select v-model="form.gender">
                  <option value="MALE">男</option>
                  <option value="FEMALE">女</option>
                </select>
              </label>
              <label v-if="form.userType === 'STUDENT'">
                <span>学年</span>
                <input v-model="form.academicYear" type="text" placeholder="如 2025-2026" />
              </label>
              <label class="checkbox-row">
                <span>宿舍长身份</span>
                <input v-model="form.leader" type="checkbox" :disabled="form.userType !== 'STUDENT'" />
              </label>
            </div>

            <p v-if="successMessage" class="success">{{ successMessage }}</p>
            <p v-if="errorMessage" class="error">{{ errorMessage }}</p>

            <button class="submit-btn" :disabled="submitLoading" @click="handleCreateUser">
              {{ submitLoading ? '创建中...' : '创建账号' }}
            </button>
          </section>

          <section class="card">
            <div class="section-title">批量导入用户</div>
            <div class="import-actions">
              <button class="ghost-btn" :disabled="!hasLoginInfo" @click="downloadTemplate">下载模板 CSV</button>
              <label class="pick-btn" :class="{ disabled: !hasLoginInfo }">
                选择 CSV 文件
                <input :disabled="!hasLoginInfo" type="file" accept=".csv,text/csv" @change="onPickImportFile" />
              </label>
            </div>

            <div class="drop-zone" :class="{ disabled: !hasLoginInfo }" @dragover.prevent @drop="onDropImport">
              <div v-if="importFile" class="file-meta">
                <div class="file-name">{{ importFile.name }}</div>
                <div class="file-sub">{{ formatFileSize(importFile.size) }}</div>
              </div>
              <div v-else class="drop-tip">
                将 CSV 文件拖到此处，或点击上方“选择 CSV 文件”
              </div>
            </div>

            <p class="import-hint">要求：UTF-8 CSV；表头字段与模板一致；学生必须填写学年（如 2025-2026）。模板里的示例行不会被导入（username 以 __EXAMPLE__ 或 EXAMPLE_ 开头）。</p>
            <p v-if="importSuccessMessage" class="success">{{ importSuccessMessage }}</p>
            <p v-if="importErrorMessage" class="error">{{ importErrorMessage }}</p>

            <button class="submit-btn" :disabled="!hasLoginInfo || importLoading" @click="uploadImport">
              {{ importLoading ? '导入中...' : '开始导入' }}
            </button>
          </section>
        </div>
      </div>
    </div>

    <div v-if="deleteModalVisible" class="modal-mask" @click.self="closeDeleteModal">
      <div class="modal">
        <div class="modal-title">确认删除账号</div>
        <div class="modal-body">
          <div class="modal-row">
            <span class="k">用户</span>
            <span class="v">{{ deletingUser?.username }}</span>
          </div>
          <div class="modal-row">
            <span class="k">姓名</span>
            <span class="v">{{ deletingUser?.realName }}</span>
          </div>
          <label class="modal-check">
            <input v-model="deleteForce" type="checkbox" />
            <span>强制删除（若已产生业务数据也删除，谨慎）</span>
          </label>
          <p class="modal-hint">提示：默认会阻止删除有业务数据的账号。强制删除可能导致历史数据缺失。</p>
        </div>
        <div class="modal-actions">
          <button class="ghost-btn" :disabled="deleteLoading" @click="closeDeleteModal">取消</button>
          <button class="danger-solid" :disabled="deleteLoading" @click="confirmDelete">
            {{ deleteLoading ? '删除中...' : '确认删除' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page { min-height: 100vh; padding: 32px; background: #f7f5ff; }
.panel {
  max-width: 1240px; margin: 0 auto; background: #fff; border-radius: 20px; padding: 32px;
  box-shadow: 0 16px 36px rgba(15, 23, 42, 0.08);
}
.header-row {
  display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 24px;
}
h1 { margin: 0; color: #6d28d9; }
.desc { color: #667085; margin: 12px 0 0; }
.back-btn, .submit-btn {
  border: none; border-radius: 10px; padding: 12px 20px; cursor: pointer; font-weight: 700;
}
.back-btn { background: #ede9fe; color: #5b21b6; }
.submit-btn { background: #7c3aed; color: #fff; margin-top: 16px; }
.submit-btn:disabled { opacity: .7; cursor: not-allowed; }
.layout { display: grid; grid-template-columns: 1.2fr 1fr; gap: 24px; }
.right-col { display: grid; gap: 24px; align-content: start; }
.card {
  background: #f8fafc; border: 1px solid #e4e7ec; border-radius: 16px; padding: 20px;
}
.section-title { font-size: 20px; font-weight: 700; color: #344054; margin-bottom: 16px; }
.list-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; margin-bottom: 10px; }
.tabs { display: flex; gap: 8px; flex-wrap: wrap; }
.tab {
  height: 36px;
  border-radius: 999px;
  border: 1px solid #d0d5dd;
  background: #fff;
  padding: 0 12px;
  cursor: pointer;
  font-weight: 800;
  color: #344054;
}
.tab.active { background: #ede9fe; border-color: #c4b5fd; color: #5b21b6; }
.pager-ctrl { display: inline-flex; align-items: center; gap: 8px; }
.pager-text { color: #667085; font-size: 13px; }
.pager-size { height: 36px; border-radius: 10px; border: 1px solid #d0d5dd; padding: 0 10px; background: #fff; }
.table-wrap { overflow: auto; }
table { width: 100%; border-collapse: collapse; }
th, td { padding: 12px 10px; border-bottom: 1px solid #e5e7eb; text-align: left; font-size: 14px; }
th { color: #667085; }
.pager { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-top: 12px; flex-wrap: wrap; }
.pager-info { display: inline-flex; align-items: center; gap: 8px; color: #667085; font-size: 13px; font-weight: 700; }
.pager-info .sep { opacity: .8; }
.form-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 16px; }
label span { display: block; color: #667085; font-size: 14px; margin-bottom: 8px; }
input, select {
  width: 100%; height: 42px; border: 1px solid #d0d5dd; border-radius: 10px; padding: 0 12px; background: #fff;
}
.checkbox-row input { width: 18px; height: 18px; margin-top: 10px; }
.warning, .tip, .success, .error { margin: 12px 0 0; }
.warning { color: #b54708; }
.success { color: #027a48; }
.error { color: #d92d20; }

.import-actions { display: flex; gap: 10px; flex-wrap: wrap; }
.ghost-btn {
  height: 42px;
  border-radius: 10px;
  border: 1px solid #d0d5dd;
  background: #fff;
  padding: 0 14px;
  cursor: pointer;
  font-weight: 700;
  color: #344054;
}
.ghost-btn:disabled { opacity: .7; cursor: not-allowed; }
.ghost-btn.mini { height: 34px; padding: 0 12px; }
.danger-btn {
  height: 34px;
  border-radius: 10px;
  border: 1px solid #fda29b;
  background: #fff;
  padding: 0 12px;
  cursor: pointer;
  font-weight: 800;
  color: #b42318;
}
.danger-btn:disabled { opacity: .6; cursor: not-allowed; }
.danger-solid {
  height: 42px;
  border-radius: 10px;
  border: none;
  background: #d92d20;
  color: #fff;
  padding: 0 14px;
  cursor: pointer;
  font-weight: 900;
}
.danger-solid:disabled { opacity: .7; cursor: not-allowed; }
.pick-btn {
  height: 42px;
  border-radius: 10px;
  border: 1px solid #d0d5dd;
  background: #fff;
  padding: 0 14px;
  cursor: pointer;
  font-weight: 700;
  color: #344054;
  display: inline-flex;
  align-items: center;
}
.pick-btn.disabled { opacity: .7; cursor: not-allowed; }
.pick-btn input { display: none; }
.drop-zone {
  margin-top: 12px;
  border: 2px dashed #d0d5dd;
  border-radius: 14px;
  background: #fff;
  padding: 14px;
  min-height: 84px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.drop-zone.disabled { opacity: .7; }
.drop-tip { color: #667085; font-size: 14px; text-align: center; }
.file-meta { display: grid; gap: 4px; text-align: center; }
.file-name { font-weight: 900; color: #344054; word-break: break-all; }
.file-sub { color: #667085; font-size: 13px; }
.import-hint { margin-top: 10px; color: #667085; font-size: 13px; line-height: 1.5; }

.modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 18px;
  z-index: 50;
}
.modal {
  width: 100%;
  max-width: 520px;
  background: #fff;
  border-radius: 16px;
  border: 1px solid #e4e7ec;
  padding: 18px;
  box-shadow: 0 20px 50px rgba(15, 23, 42, 0.2);
}
.modal-title { font-size: 18px; font-weight: 900; color: #101828; }
.modal-body { margin-top: 12px; display: grid; gap: 10px; }
.modal-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.modal-row .k { color: #667085; font-size: 13px; }
.modal-row .v { color: #344054; font-weight: 800; word-break: break-all; }
.modal-check { display: flex; gap: 10px; align-items: center; user-select: none; }
.modal-check input { width: 18px; height: 18px; }
.modal-hint { margin: 0; color: #667085; font-size: 13px; line-height: 1.5; }
.modal-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 14px; }

.expand-row td { padding: 0; border-bottom: 1px solid #e5e7eb; }
.expand-box { padding: 12px 10px; background: #ffffff; }
.expand-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px 16px; }
.kv { display: flex; gap: 10px; align-items: baseline; }
.kv .k { color: #667085; font-size: 13px; min-width: 72px; }
.kv .v { color: #344054; font-weight: 800; word-break: break-word; }
</style>

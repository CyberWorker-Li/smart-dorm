<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { apiBase } from '@/apiBase'

const API = apiBase()
const route = useRoute()
const router = useRouter()

const buildings = ref<any[]>([])
const msg = ref('')
const msgType = ref<'ok' | 'err'>('ok')

const genForm = ref({ name: '', gender: 'MALE', totalFloors: '' as string | number, roomsPerFloor: '' as string | number })
const floorForm = ref({ buildingId: '', floorNo: '', maxRooms: '' })
const roomForm = ref({ floorId: '', roomNo: '', capacity: 4, building: '' })

const loadingList = ref(false)
const generating = ref(false)

function tip(t: string, ok = true) {
  msg.value = t
  msgType.value = ok ? 'ok' : 'err'
  setTimeout(() => (msg.value = ''), 5000)
}

/** 仅当为正整数时写入请求体，避免 number 输入框产生 0、NaN、空字符串被 JSON 成异常值 */
function optionalPositiveInt(v: string | number | null | undefined): number | undefined {
  if (v === '' || v === null || v === undefined) return undefined
  const n = typeof v === 'number' ? v : Number(String(v).trim())
  if (!Number.isFinite(n) || n < 1) return undefined
  return Math.floor(n)
}

async function parseResult(res: Response): Promise<{ code?: number; message?: string; data?: unknown }> {
  const text = await res.text()
  if (!text) {
    return { code: res.ok ? 200 : res.status, message: res.statusText || '空响应' }
  }
  try {
    return JSON.parse(text) as { code?: number; message?: string; data?: unknown }
  } catch {
    return {
      code: res.status,
      message: `服务器返回非 JSON（HTTP ${res.status}），请确认后端已启动且地址为 ${API}`,
    }
  }
}

async function loadBuildings() {
  loadingList.value = true
  try {
    const r = await fetch(`${API}/api/dorm/structure/buildings`)
    const j = await parseResult(r)
    if (j.code === 200 && Array.isArray(j.data)) {
      buildings.value = j.data
    } else {
      tip(j.message || '加载楼房列表失败', false)
    }
  } catch (e) {
    tip(e instanceof Error ? e.message : '网络错误：无法连接后端', false)
  } finally {
    loadingList.value = false
  }
}

async function doGenerate() {
  const name = String(genForm.value.name ?? '').trim()
  if (!name) {
    tip('请填写楼房名称', false)
    return
  }

  const totalFloors = optionalPositiveInt(genForm.value.totalFloors)
  const roomsPerFloor = optionalPositiveInt(genForm.value.roomsPerFloor)

  if (roomsPerFloor != null && totalFloors == null) {
    tip('填写了每层房间数时，必须同时填写总层数', false)
    return
  }

  const body: Record<string, unknown> = {
    name,
    gender: String(genForm.value.gender || 'MALE').trim().toUpperCase(),
  }
  if (totalFloors != null) body.totalFloors = totalFloors
  if (roomsPerFloor != null) body.roomsPerFloor = roomsPerFloor

  generating.value = true
  try {
    const r = await fetch(`${API}/api/dorm/structure/buildings/generate`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
    const j = await parseResult(r)
    if (j.code === 200) {
      tip('楼房已生成')
      genForm.value = { name: '', gender: 'MALE', totalFloors: '', roomsPerFloor: '' }
      await loadBuildings()
    } else {
      tip(j.message || `操作失败（HTTP ${r.status}）`, false)
    }
  } catch (e) {
    tip(e instanceof Error ? e.message : '网络错误：无法连接后端', false)
  } finally {
    generating.value = false
  }
}

async function delBuilding(id: number) {
  if (!confirm('确定删除该楼房及其下所有楼层与房间？')) return
  try {
    const r = await fetch(`${API}/api/dorm/structure/buildings/${id}`, { method: 'DELETE' })
    const j = await parseResult(r)
    if (j.code === 200) {
      tip('已删除')
      await loadBuildings()
    } else tip(j.message || '失败', false)
  } catch (e) {
    tip(e instanceof Error ? e.message : '网络错误', false)
  }
}

async function addFloor() {
  try {
    const r = await fetch(`${API}/api/dorm/structure/floors`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        buildingId: Number(floorForm.value.buildingId),
        floorNo: Number(floorForm.value.floorNo),
        maxRooms: Number(floorForm.value.maxRooms),
        mainValid: true,
      }),
    })
    const j = await parseResult(r)
    j.code === 200 ? tip('楼层已添加') : tip(j.message || '失败', false)
  } catch (e) {
    tip(e instanceof Error ? e.message : '网络错误', false)
  }
}

async function addRoom() {
  try {
    const r = await fetch(`${API}/api/dorm/structure/rooms`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        floorId: Number(roomForm.value.floorId),
        roomNo: Number(roomForm.value.roomNo),
        capacity: Number(roomForm.value.capacity),
        building: roomForm.value.building || undefined,
        mainValid: true,
      }),
    })
    const j = await parseResult(r)
    j.code === 200 ? tip('房间已添加') : tip(j.message || '失败', false)
  } catch (e) {
    tip(e instanceof Error ? e.message : '网络错误', false)
  }
}

onMounted(loadBuildings)
</script>

<template>
  <div class="page">
    <div class="panel">
      <div class="header-row">
        <div>
          <h1>房间结构维护</h1>
          <p class="desc">批量生成楼房、单独增删楼层与房间。若生成失败，请关注页面上方红色提示（常见原因：后端未启动、数据库表与代码不一致、或网络无法访问后端）。</p>
        </div>
        <button type="button" class="back" @click="router.push({ path: '/', query: route.query })">返回</button>
      </div>
      <p v-if="msg" :class="msgType">{{ msg }}</p>

      <section class="card">
        <h2>批量生成楼房</h2>
        <p class="hint">可不填层数与每层房间数（仅建楼壳）；可只填层数；不可只填每层房间数。层数、房间数须为正整数。</p>
        <div class="grid">
          <label><span>名称</span><input v-model.trim="genForm.name" autocomplete="off" /></label>
          <label><span>性别</span>
            <select v-model="genForm.gender"><option value="MALE">男生楼</option><option value="FEMALE">女生楼</option></select>
          </label>
          <label><span>总层数</span><input v-model="genForm.totalFloors" type="number" min="1" step="1" placeholder="可空" /></label>
          <label><span>每层房间数</span><input v-model="genForm.roomsPerFloor" type="number" min="1" step="1" placeholder="可空" /></label>
        </div>
        <button type="button" class="btn" :disabled="generating" @click="doGenerate">
          {{ generating ? '提交中…' : '生成' }}
        </button>
      </section>

      <section class="card">
        <h2>楼房列表</h2>
        <p v-if="loadingList" class="muted">加载中…</p>
        <table v-else-if="buildings.length">
          <thead><tr><th>ID</th><th>名称</th><th>层数</th><th>性别</th><th>有效</th><th /></tr></thead>
          <tbody>
            <tr v-for="b in buildings" :key="b.id">
              <td>{{ b.id }}</td>
              <td>{{ b.name }}</td>
              <td>{{ b.floorCount }}</td>
              <td>{{ b.gender }}</td>
              <td>{{ b.valid ? '是' : '否' }}</td>
              <td><button type="button" class="link" @click="delBuilding(b.id)">删除</button></td>
            </tr>
          </tbody>
        </table>
        <p v-else class="muted">暂无楼房</p>
      </section>

      <section class="card">
        <h2>插入楼层（捎带更低层）</h2>
        <div class="grid">
          <label><span>楼房ID</span><input v-model="floorForm.buildingId" /></label>
          <label><span>层号</span><input v-model="floorForm.floorNo" /></label>
          <label><span>本层最多房间</span><input v-model="floorForm.maxRooms" /></label>
        </div>
        <button type="button" class="btn" @click="addFloor">添加楼层</button>
      </section>

      <section class="card">
        <h2>插入房间（捎带更小房号）</h2>
        <div class="grid">
          <label><span>楼层ID</span><input v-model="roomForm.floorId" /></label>
          <label><span>房间序号</span><input v-model="roomForm.roomNo" /></label>
          <label><span>床位数</span><input v-model.number="roomForm.capacity" type="number" /></label>
          <label><span>显示楼栋名(可选)</span><input v-model="roomForm.building" /></label>
        </div>
        <button type="button" class="btn" @click="addRoom">添加房间</button>
      </section>
    </div>
  </div>
</template>

<style scoped>
.page { min-height: 100vh; padding: 28px; background: #f5f3ff; }
.panel { max-width: 960px; margin: 0 auto; background: #fff; border-radius: 18px; padding: 24px; box-shadow: 0 12px 32px rgba(15,23,42,.08); }
.header-row { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 16px; }
h1 { margin: 0; color: #5b21b6; }
.desc { color: #64748b; margin: 8px 0 0; font-size: 13px; line-height: 1.5; }
.back { border: none; border-radius: 10px; padding: 10px 16px; background: #ede9fe; color: #5b21b6; font-weight: 700; cursor: pointer; }
.card { margin-top: 20px; padding: 16px; border: 1px solid #e5e7eb; border-radius: 12px; background: #fafafa; }
h2 { margin: 0 0 12px; font-size: 17px; color: #334155; }
.hint { font-size: 13px; color: #64748b; margin-bottom: 12px; }
.grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px; margin-bottom: 12px; }
label span { display: block; font-size: 13px; color: #64748b; margin-bottom: 4px; }
input, select { width: 100%; height: 38px; border: 1px solid #d1d5db; border-radius: 8px; padding: 0 10px; }
.btn { margin-top: 8px; padding: 10px 18px; border: none; border-radius: 10px; background: #7c3aed; color: #fff; font-weight: 700; cursor: pointer; }
.btn:disabled { opacity: 0.65; cursor: not-allowed; }
table { width: 100%; border-collapse: collapse; font-size: 14px; }
th, td { padding: 8px; border-bottom: 1px solid #e5e7eb; text-align: left; }
.link { background: none; border: none; color: #b91c1c; cursor: pointer; font-weight: 600; }
.muted { color: #94a3b8; }
.ok { color: #059669; margin: 8px 0; }
.err { color: #dc2626; margin: 8px 0; }
</style>

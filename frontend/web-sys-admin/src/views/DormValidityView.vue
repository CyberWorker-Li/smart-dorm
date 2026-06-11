<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { apiBase } from '@/apiBase'

interface DormRoomVO {
  id: number
  building: string
  buildingName: string
  floorNo: number
  roomNo: number
  gender: string
  capacity: number
  valid: boolean
  floorValid: boolean
  buildingValid: boolean
}

const API = apiBase()
const route = useRoute()
const router = useRouter()

const rooms = ref<DormRoomVO[]>([])
const loading = ref(false)
const msg = ref('')

async function load() {
  loading.value = true
  try {
    const r = await fetch(`${API}/api/dorm-room`)
    const j = await r.json()
    if (j.code === 200) rooms.value = j.data
  } finally {
    loading.value = false
  }
}

async function toggleRoom(id: number, v: boolean) {
  const r = await fetch(`${API}/api/dorm/settings/room/${id}/valid?valid=${v}`, { method: 'PATCH' })
  const j = await r.json()
  msg.value = j.code === 200 ? '已更新' : j.message || '失败'
  await load()
}

const bf = ref({ buildingId: '', from: '', to: '', valid: true })
async function batchFloor() {
  const r = await fetch(`${API}/api/dorm/settings/batch/floor-valid`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      buildingId: Number(bf.value.buildingId),
      fromFloor: Number(bf.value.from),
      toFloor: Number(bf.value.to),
      valid: bf.value.valid,
    }),
  })
  const j = await r.json()
  msg.value = j.code === 200 ? '楼层批量更新完成' : j.message || '失败'
}

const rf = ref({ floorId: '', from: '', to: '', valid: true })
async function batchRoom() {
  const r = await fetch(`${API}/api/dorm/settings/batch/room-valid`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      floorId: Number(rf.value.floorId),
      fromRoom: Number(rf.value.from),
      toRoom: Number(rf.value.to),
      valid: rf.value.valid,
    }),
  })
  const j = await r.json()
  msg.value = j.code === 200 ? '房间批量更新完成' : j.message || '失败'
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="panel">
      <div class="header-row">
        <div>
          <h1>宿舍启用设置</h1>
          <p class="desc">不再在此新增房间；仅调整楼房/楼层/房间是否有效及批量范围。</p>
        </div>
        <button class="back" @click="router.push({ path: '/', query: route.query })">返回</button>
      </div>
      <p v-if="msg" class="msg">{{ msg }}</p>

      <section class="card">
        <h2>批量：楼层有效区间</h2>
        <div class="row">
          <input v-model="bf.buildingId" placeholder="楼房ID" />
          <input v-model="bf.from" placeholder="起始层" />
          <input v-model="bf.to" placeholder="结束层" />
          <label><input v-model="bf.valid" type="checkbox" />设为有效</label>
          <button class="btn" @click="batchFloor">执行</button>
        </div>
      </section>

      <section class="card">
        <h2>批量：房间序号区间</h2>
        <div class="row">
          <input v-model="rf.floorId" placeholder="楼层ID" />
          <input v-model="rf.from" placeholder="起始房号" />
          <input v-model="rf.to" placeholder="结束房号" />
          <label><input v-model="rf.valid" type="checkbox" />设为有效</label>
          <button class="btn" @click="batchRoom">执行</button>
        </div>
      </section>

      <section class="card">
        <h2>全部房间（点行末切换本房间有效）</h2>
        <p v-if="loading">加载中...</p>
        <table v-else-if="rooms.length">
          <thead>
            <tr>
              <th>ID</th><th>楼栋显示</th><th>层</th><th>房号</th><th>性别(楼)</th><th>容量</th>
              <th>楼/层/房有效</th><th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="x in rooms" :key="x.id">
              <td>{{ x.id }}</td>
              <td>{{ x.building }}</td>
              <td>{{ x.floorNo }}</td>
              <td>{{ x.roomNo }}</td>
              <td>{{ x.gender }}</td>
              <td>{{ x.capacity }}</td>
              <td>{{ x.buildingValid ? '楼✓' : '楼✗' }} / {{ x.floorValid ? '层✓' : '层✗' }} / {{ x.valid ? '房✓' : '房✗' }}</td>
              <td>
                <button class="btn-sm" @click="toggleRoom(x.id, !x.valid)">{{ x.valid ? '设为无效' : '设为有效' }}</button>
              </td>
            </tr>
          </tbody>
        </table>
        <p v-else class="muted">暂无房间</p>
      </section>
    </div>
  </div>
</template>

<style scoped>
.page { min-height: 100vh; padding: 28px; background: #f0fdf4; }
.panel { max-width: 1100px; margin: 0 auto; background: #fff; border-radius: 18px; padding: 24px; box-shadow: 0 12px 32px rgba(15,23,42,.08); }
.header-row { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 16px; }
h1 { margin: 0; color: #166534; }
.desc { color: #64748b; margin: 8px 0 0; }
.back { border: none; border-radius: 10px; padding: 10px 16px; background: #bbf7d0; color: #166534; font-weight: 700; cursor: pointer; }
.card { margin-top: 16px; padding: 14px; border: 1px solid #bbf7d0; border-radius: 12px; background: #f8fafc; }
h2 { margin: 0 0 10px; font-size: 16px; color: #14532d; }
.row { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.row input { width: 120px; height: 36px; border: 1px solid #d1d5db; border-radius: 8px; padding: 0 8px; }
.btn { padding: 8px 14px; border: none; border-radius: 8px; background: #16a34a; color: #fff; font-weight: 700; cursor: pointer; }
.btn-sm { padding: 4px 10px; font-size: 12px; border-radius: 6px; border: 1px solid #16a34a; background: #fff; color: #166534; cursor: pointer; }
table { width: 100%; border-collapse: collapse; font-size: 13px; }
th, td { padding: 8px; border-bottom: 1px solid #e5e7eb; text-align: left; }
.msg { color: #166534; font-weight: 600; }
.muted { color: #94a3b8; }
</style>

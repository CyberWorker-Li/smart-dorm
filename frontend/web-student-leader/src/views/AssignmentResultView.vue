<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { apiBase } from '@/apiBase'

interface RoommateVO {
  studentId: number
  realName: string
  gender?: string
  wakeUpTime: string
  sleepTime: string
  personality: string
  hobbies: string
  hometown: string
  bedNo: number
}

interface MyResultVO {
  batchId: number
  academicYear: string
  building: string
  roomNo: string
  roomGender?: string
  bedNo: number
  roommates: RoommateVO[]
}

const route = useRoute()
const router = useRouter()
const studentId = route.query.userId || ''

const result = ref<MyResultVO | null>(null)
const loading = ref(false)
const noResult = ref(false)

const personalityLabel: Record<string, string> = {
  INTROVERT: '内向',
  EXTROVERT: '外向',
  AMBIVERT: '中间型',
}

const genderShort: Record<string, string> = { MALE: '男', FEMALE: '女' }

const API = apiBase()

async function loadResult() {
  if (!studentId) return
  loading.value = true
  noResult.value = false
  try {
    const res = await fetch(`${API}/api/assignment/my-result?studentId=${studentId}`)
    const r = await res.json()
    if (r.code === 200 && r.data) {
      result.value = r.data
    } else {
      noResult.value = true
    }
  } catch {
    noResult.value = true
  } finally {
    loading.value = false
  }
}

onMounted(loadResult)
</script>

<template>
  <div class="page">
    <div class="panel">
      <div class="header-row">
        <div>
          <h1>分配结果查询</h1>
          <p class="desc">查看您的宿舍分配结果及室友基础信息。</p>
        </div>
        <button class="back-btn" @click="router.push({ path: '/', query: route.query })">返回首页</button>
      </div>

      <div v-if="loading" class="tip">加载中...</div>

      <div v-else-if="noResult" class="empty-box">
        <div class="empty-icon">🏠</div>
        <div class="empty-title">暂无分配结果</div>
        <div class="empty-desc">管理员尚未公示分配结果，请耐心等待。</div>
      </div>

      <div v-else-if="result">
        <!-- 我的宿舍信息 -->
        <div class="my-room-card">
          <div class="room-badge">{{ result.academicYear }} 学年</div>
          <div class="room-info">
            <div class="room-main">
              <span class="room-label">宿舍楼</span>
              <span class="room-value">{{ result.building }}</span>
            </div>
            <div class="room-main">
              <span class="room-label">房间号</span>
              <span class="room-value">{{ result.roomNo }}</span>
            </div>
            <div class="room-main">
              <span class="room-label">床位号</span>
              <span class="room-value">{{ result.bedNo }} 号床</span>
            </div>
            <div class="room-main">
              <span class="room-label">房间类型</span>
              <span class="room-value">{{
                result.roomGender ? (genderShort[result.roomGender] ?? '—') + '生宿舍' : '—'
              }}</span>
            </div>
          </div>
        </div>

        <!-- 室友信息 -->
        <div class="section-title">室友信息（{{ result.roommates.length }} 人）</div>
        <div v-if="result.roommates.length === 0" class="tip">暂无室友信息</div>
        <div v-else class="roommate-grid">
          <div v-for="r in result.roommates" :key="r.studentId" class="roommate-card">
            <div class="rm-header">
              <span class="rm-name">{{ r.realName }}</span>
              <span class="rm-bed">{{ r.bedNo }} 号床</span>
            </div>
            <div class="rm-tags">
              <span v-if="r.gender" class="tag">{{ (genderShort[r.gender] ?? r.gender) }}生</span>
              <span v-if="r.wakeUpTime" class="tag">起床 {{ r.wakeUpTime }}</span>
              <span v-if="r.sleepTime" class="tag">睡觉 {{ r.sleepTime }}</span>
              <span v-if="r.personality" class="tag">{{ personalityLabel[r.personality] || r.personality }}</span>
              <span v-if="r.hometown" class="tag">{{ r.hometown }}</span>
            </div>
            <div v-if="r.hobbies" class="rm-hobbies">爱好：{{ r.hobbies }}</div>
          </div>
        </div>

        <div class="notice-box">
          <strong>温馨提示：</strong>室友信息已做脱敏处理，仅展示作息、性格、爱好等非隐私信息。
          如有异议，可前往「提交微调申请」页面提交申请。
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
.my-room-card { background: linear-gradient(135deg, #1d4ed8, #3b82f6); border-radius: 16px; padding: 24px; color: #fff; margin-bottom: 28px; }
.room-badge { display: inline-block; background: rgba(255,255,255,.2); border-radius: 8px; padding: 4px 12px; font-size: 13px; margin-bottom: 16px; }
.room-info { display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: 16px; }
.room-main { background: rgba(255,255,255,.15); border-radius: 12px; padding: 14px; }
.room-label { display: block; font-size: 13px; opacity: .8; margin-bottom: 6px; }
.room-value { font-size: 24px; font-weight: 700; }
.section-title { font-size: 16px; font-weight: 700; color: #1e40af; margin-bottom: 16px; border-left: 3px solid #3b82f6; padding-left: 10px; }
.roommate-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 16px; margin-bottom: 20px; }
.roommate-card { background: #f8fafc; border: 1px solid #e4e7ec; border-radius: 14px; padding: 16px; }
.rm-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.rm-name { font-weight: 700; color: #1f2937; font-size: 16px; }
.rm-bed { background: #dbeafe; color: #1e40af; border-radius: 6px; padding: 2px 8px; font-size: 12px; font-weight: 600; }
.rm-no { font-size: 13px; color: #667085; margin-bottom: 10px; }
.rm-tags { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 8px; }
.tag { background: #ede9fe; color: #5b21b6; border-radius: 6px; padding: 3px 8px; font-size: 12px; }
.rm-hobbies { font-size: 13px; color: #374151; }
.notice-box { background: #fffaeb; border: 1px solid #fedf89; border-radius: 12px; padding: 14px 16px; color: #92400e; font-size: 14px; }
.empty-box { text-align: center; padding: 60px 20px; }
.empty-icon { font-size: 48px; margin-bottom: 12px; }
.empty-title { font-size: 18px; font-weight: 700; color: #344054; margin-bottom: 8px; }
.empty-desc { color: #667085; }
.tip { color: #667085; font-size: 14px; }
</style>

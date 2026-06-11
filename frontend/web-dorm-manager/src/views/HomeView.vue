<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'

const params = new URLSearchParams(window.location.search)
const username = params.get('username') || ''
const realName = params.get('realName') || ''
const userType = params.get('userType') || ''
const hasLoginInfo = computed(() => !!username && userType === 'DORM_MANAGER')

const router = useRouter()
const query = Object.fromEntries(new URLSearchParams(window.location.search))

function go(path: string) {
  router.push({ path, query })
}
</script>

<template>
  <div class="page">
    <div class="panel">
      <h1>宿管后台</h1>
      <p class="desc">管理负责楼栋的宿舍分配结果，审核学生微调申请。</p>

      <div v-if="hasLoginInfo" class="info-list">
        <div class="item"><span>当前身份</span><strong>宿舍管理员</strong></div>
        <div class="item"><span>用户名</span><strong>{{ username }}</strong></div>
        <div class="item"><span>姓名</span><strong>{{ realName || '未传入' }}</strong></div>
        <div class="item"><span>管理范围</span><strong>负责楼栋宿舍</strong></div>
      </div>
      <p v-else class="warning">请先从统一登录门户进入本后台。</p>

      <div class="modules">
        <button class="module clickable" @click="go('/room-result')">查看分配结果</button>
        <button class="module clickable" @click="go('/adjust-handle')">审核微调申请</button>
        <button class="module clickable" @click="go('/hygiene')">卫生评分</button>
        <button class="module clickable" @click="go('/swap-handle')">审核互换申请</button>
        <button class="module clickable" @click="go('/notice')">公共通知</button>
        <button class="module clickable" @click="go('/noise-monitor')">噪音提醒</button>
        <button class="module clickable" @click="go('/peer-eval')">室友互评</button>
        <button class="module clickable" @click="go('/messages')">消息中心</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page { min-height: 100vh; padding: 40px; background: #f4f8fb; }
.panel { max-width: 960px; margin: 0 auto; background: #fff; border-radius: 20px; padding: 32px; box-shadow: 0 16px 36px rgba(15,23,42,.08); }
h1 { margin: 0; color: #0f766e; }
.desc { color: #667085; margin: 12px 0 24px; }
.info-list { display: grid; grid-template-columns: repeat(2, 1fr); gap: 16px; margin-bottom: 24px; }
.item, .module, .warning { background: #f8fafc; border: 1px solid #e4e7ec; border-radius: 14px; padding: 16px; }
.item span { display: block; color: #667085; font-size: 14px; margin-bottom: 8px; }
.modules { display: grid; grid-template-columns: repeat(2, 1fr); gap: 16px; }
.module { font-weight: 700; color: #344054; min-height: 64px; display: flex; align-items: center; }
.clickable { cursor: pointer; text-align: left; font-size: 16px; border: none; }
.warning { color: #b54708; background: #fffaeb; border-color: #fedf89; }
</style>

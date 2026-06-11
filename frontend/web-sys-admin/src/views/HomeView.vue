<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const username = computed(() => String(route.query.username || ''))
const realName = computed(() => String(route.query.realName || ''))
const userType = computed(() => String(route.query.userType || ''))
const hasLoginInfo = computed(() => !!username.value && userType.value === 'SYS_ADMIN')

function goToUsers() {
  router.push({
    path: '/users',
    query: route.query,
  })
}
</script>

<template>
  <div class="page">
    <div class="panel">
      <h1>系统管理员后台</h1>
      <p class="desc">问卷发布与回收、多维度宿舍分配、结果公示及宿舍结构维护。</p>

      <div v-if="hasLoginInfo" class="info-list">
        <div class="item"><span>当前身份</span><strong>系统管理员</strong></div>
        <div class="item"><span>用户名</span><strong>{{ username }}</strong></div>
        <div class="item"><span>姓名</span><strong>{{ realName || '未传入' }}</strong></div>
        <div class="item"><span>核心职责</span><strong>账号与系统配置管理</strong></div>
      </div>
      <p v-else class="warning">请先从统一登录门户进入本后台。</p>

      <div class="modules">
        <button class="module clickable" @click="goToUsers">账号管理</button>
        <button class="module clickable" @click="router.push({ path: '/questionnaire', query: route.query })">问卷管理</button>
        <button class="module clickable" @click="router.push({ path: '/assignment', query: route.query })">精准分配</button>
        <button class="module clickable" @click="router.push({ path: '/hygiene-admin', query: route.query })">卫生管理</button>
        <button class="module clickable" @click="router.push({ path: '/dorm-structure', query: route.query })">房间结构维护</button>
        <button class="module clickable" @click="router.push({ path: '/dorm-validity', query: route.query })">宿舍启用设置</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.page { min-height: 100vh; padding: 40px; background: #f7f5ff; }
.panel {
  max-width: 960px; margin: 0 auto; background: #fff; border-radius: 20px; padding: 32px;
  box-shadow: 0 16px 36px rgba(15, 23, 42, 0.08);
}
h1 { margin: 0; color: #6d28d9; }
.desc { color: #667085; margin: 12px 0 24px; }
.info-list { display: grid; grid-template-columns: repeat(2, 1fr); gap: 16px; margin-bottom: 24px; }
.item, .module, .warning {
  background: #f8fafc; border: 1px solid #e4e7ec; border-radius: 14px; padding: 16px;
}
.item span { display: block; color: #667085; font-size: 14px; margin-bottom: 8px; }
.modules { display: grid; grid-template-columns: repeat(2, 1fr); gap: 16px; }
.module {
  font-weight: 700; color: #344054; min-height: 64px; display: flex; align-items: center;
}
.clickable {
  cursor: pointer;
  text-align: left;
  font-size: 28px;
  color: #334155;
}
.warning { color: #b54708; background: #fffaeb; border-color: #fedf89; }
</style>

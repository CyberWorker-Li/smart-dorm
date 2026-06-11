<script setup lang="ts">
import { computed, ref } from 'vue'

interface LoginResponse {
  token: string
  userId: number
  username: string
  realName: string
  userType: string
  leader: boolean
  gender: string
  targetPortal: 'STUDENT_LEADER' | 'DORM_MANAGER' | 'SYS_ADMIN'
}

const form = ref({ username: '', password: '' })
const loading = ref(false)
const errorMessage = ref('')

const portalMap: Record<LoginResponse['targetPortal'], string> = {
  STUDENT_LEADER: 'http://127.0.0.1:5174',
  DORM_MANAGER: 'http://127.0.0.1:5175',
  SYS_ADMIN: 'http://127.0.0.1:5176',
}

const canSubmit = computed(() => form.value.username.trim() && form.value.password.trim())

async function handleLogin() {
  if (!canSubmit.value) {
    errorMessage.value = '请输入用户名和密码'
    return
  }

  loading.value = true
  errorMessage.value = ''

  try {
    const response = await fetch('http://127.0.0.1:8080/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(form.value),
    })
    const result = await response.json()

    if (!response.ok || result.code !== 200) {
      throw new Error(result.message || '登录失败')
    }

    const data = result.data as LoginResponse
    const targetUrl = new URL(portalMap[data.targetPortal])
    targetUrl.searchParams.set('token', data.token)
    targetUrl.searchParams.set('userId', String(data.userId))
    targetUrl.searchParams.set('username', data.username)
    targetUrl.searchParams.set('realName', data.realName)
    targetUrl.searchParams.set('userType', data.userType)
    targetUrl.searchParams.set('leader', String(data.leader))
    targetUrl.searchParams.set('gender', data.gender ?? '')
    window.location.href = targetUrl.toString()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '登录失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="page">
    <div class="login-card">
      <div class="header">
        <h1>高校宿舍楼智能管理系统</h1>
        <p>统一登录门户，按角色进入对应后台</p>
      </div>

      <div class="accounts">
        <span>测试账号（请先执行 backend/sql/init.sql）：</span>
        <span>管理员 admin / admin123456</span>
        <span>男宿管 dorm_mgr_m / 123456</span>
        <span>女宿管 dorm_mgr_f / 123456</span>
        <span>男学生（宿舍长）stu_m_leader / 123456</span>
        <span>男学生 stu_m / 123456</span>
        <span>女学生（宿舍长）stu_f_leader / 123456</span>
        <span>女学生 stu_f / 123456</span>
      </div>

      <form class="form" @submit.prevent="handleLogin">
        <label>
          <span>用户名</span>
          <input v-model="form.username" type="text" placeholder="请输入用户名" autocomplete="username" />
        </label>
        <label>
          <span>密码</span>
          <input v-model="form.password" type="password" placeholder="请输入密码" autocomplete="current-password" />
        </label>
        <p v-if="errorMessage" class="error">{{ errorMessage }}</p>
        <button type="submit" :disabled="loading">
          {{ loading ? '登录中...' : '登录' }}
        </button>
      </form>
    </div>
  </div>
</template>

<style scoped>
* { box-sizing: border-box; }
.page {
  min-height: 100vh; display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #e0ecff, #f4f8ff);
  padding: 24px;
}
.login-card {
  width: 100%; max-width: 520px; background: #fff; border-radius: 20px; padding: 32px;
  box-shadow: 0 20px 45px rgba(31, 78, 121, 0.15);
}
.header h1 { margin: 0; font-size: 28px; color: #1f3f75; }
.header p { margin: 12px 0 0; color: #5f6b7a; }
.accounts {
  margin: 24px 0; padding: 14px 16px; border-radius: 12px; background: #f5f8ff;
  color: #425466; display: grid; gap: 6px; font-size: 13px;
}
.form { display: grid; gap: 16px; }
label { display: grid; gap: 8px; color: #344054; font-weight: 600; }
input {
  height: 44px; border: 1px solid #d0d5dd; border-radius: 10px; padding: 0 14px;
  font-size: 14px; outline: none;
}
input:focus { border-color: #3b82f6; }
button {
  height: 46px; border: none; border-radius: 10px; background: #2f6bff; color: #fff;
  font-size: 15px; font-weight: 700; cursor: pointer;
}
button:disabled { opacity: 0.7; cursor: not-allowed; }
.error { margin: 0; color: #d92d20; font-size: 14px; }
</style>

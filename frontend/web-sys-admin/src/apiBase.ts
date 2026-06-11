/**
 * API 根地址（不含末尾斜杠）。
 * - 开发环境默认空字符串：请求使用相对路径 `/api/...`，由 Vite 代理转发到后端，与页面同源，避免浏览器 CORS 拦截。
 * - 生产环境默认 `http://localhost:8080`；部署分离时请设置环境变量 `VITE_API_ORIGIN`（如 `https://api.example.com`）。
 */
export function apiBase(): string {
  const fromEnv = import.meta.env.VITE_API_ORIGIN?.trim()
  if (fromEnv) {
    return fromEnv.replace(/\/$/, '')
  }
  if (import.meta.env.DEV) {
    return ''
  }
  return 'http://127.0.0.1:8080'
}

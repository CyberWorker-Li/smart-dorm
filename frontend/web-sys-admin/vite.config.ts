import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    vueDevTools(),
  ],
  server: {
    port: 5176,
    strictPort: true,
    // 同时监听 IPv4，避免门户用 127.0.0.1 跳转时连接被拒绝（仅 ::1 时常见）
    host: true,
    proxy: {
      // 开发时前端用相对路径 /api，经此转发到后端，浏览器不再做跨域校验
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
      '/uploads': { target: 'http://127.0.0.1:8080', changeOrigin: true },
    },
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    },
  },
})

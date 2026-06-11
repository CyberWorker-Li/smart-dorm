import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '@/views/HomeView.vue'
import RoomResultView from '@/views/RoomResultView.vue'
import AdjustHandleView from '@/views/AdjustHandleView.vue'
import HygieneInspectView from '@/views/HygieneInspectView.vue'
import SwapHandleView from '@/views/SwapHandleView.vue'
import NoticeManageView from '@/views/NoticeManageView.vue'
import NoiseMonitorView from '@/views/NoiseMonitorView.vue'
import PeerEvalManageView from '@/views/PeerEvalManageView.vue'
import MessageCenterView from '@/views/MessageCenterView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', name: 'home', component: HomeView },
    { path: '/room-result', name: 'roomResult', component: RoomResultView },
    { path: '/adjust-handle', name: 'adjustHandle', component: AdjustHandleView },
    { path: '/hygiene', name: 'hygiene', component: HygieneInspectView },
    { path: '/swap-handle', name: 'swapHandle', component: SwapHandleView },
    { path: '/notice', name: 'notice', component: NoticeManageView },
    { path: '/noise-monitor', name: 'noiseMonitor', component: NoiseMonitorView },
    { path: '/peer-eval', name: 'peerEval', component: PeerEvalManageView },
    { path: '/messages', name: 'messages', component: MessageCenterView },
  ],
})

export default router

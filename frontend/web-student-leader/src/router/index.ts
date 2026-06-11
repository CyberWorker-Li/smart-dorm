import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '@/views/HomeView.vue'
import QuestionnaireView from '@/views/QuestionnaireView.vue'
import AssignmentResultView from '@/views/AssignmentResultView.vue'
import AdjustRequestView from '@/views/AdjustRequestView.vue'
import HygieneView from '@/views/HygieneView.vue'
import SwapRequestView from '@/views/SwapRequestView.vue'
import DormPublicView from '@/views/DormPublicView.vue'
import NoiseRequestView from '@/views/NoiseRequestView.vue'
import MessageCenterView from '@/views/MessageCenterView.vue'
import InternalManageView from '@/views/InternalManageView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', name: 'home', component: HomeView },
    { path: '/questionnaire', name: 'questionnaire', component: QuestionnaireView },
    { path: '/result', name: 'result', component: AssignmentResultView },
    { path: '/adjust', name: 'adjust', component: AdjustRequestView },
    { path: '/hygiene', name: 'hygiene', component: HygieneView },
    { path: '/swap', name: 'swap', component: SwapRequestView },
    { path: '/dorm-public', name: 'dormPublic', component: DormPublicView },
    { path: '/noise', name: 'noise', component: NoiseRequestView },
    { path: '/messages', name: 'messages', component: MessageCenterView },
    { path: '/internal-manage', name: 'internalManage', component: InternalManageView },
  ],
})

export default router

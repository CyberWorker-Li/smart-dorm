import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '@/views/HomeView.vue'
import UserManagementView from '@/views/UserManagementView.vue'
import QuestionnaireManageView from '@/views/QuestionnaireManageView.vue'
import AssignmentManageView from '@/views/AssignmentManageView.vue'
import DormStructureView from '@/views/DormStructureView.vue'
import DormValidityView from '@/views/DormValidityView.vue'
import HygieneAdminView from '@/views/HygieneAdminView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', name: 'home', component: HomeView },
    { path: '/users', name: 'users', component: UserManagementView },
    { path: '/questionnaire', name: 'questionnaire', component: QuestionnaireManageView },
    { path: '/assignment', name: 'assignment', component: AssignmentManageView },
    { path: '/hygiene-admin', name: 'hygieneAdmin', component: HygieneAdminView },
    { path: '/dorm-room', name: 'dormRoom', component: DormValidityView },
    { path: '/dorm-structure', name: 'dormStructure', component: DormStructureView },
    { path: '/dorm-validity', name: 'dormValidity', component: DormValidityView },
  ],
})

export default router

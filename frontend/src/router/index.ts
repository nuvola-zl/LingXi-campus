import { createRouter, createWebHistory } from 'vue-router'
import HomePortal from '@/views/HomePortal.vue'
import { useUserStore } from '@/stores/user'
import { getToken } from '@/utils/auth'
import { ElMessage } from 'element-plus'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomePortal,
      meta: { title: '灵犀校园' },
    },
    // ===== 报修入口 =====
    {
      path: '/it',
      component: () => import('@/views/it/ItLayout.vue'),
      redirect: '/it/chat',
      children: [
        { path: 'chat', name: 'it-chat', component: () => import('@/views/it/ItChatView.vue'), meta: { title: '智能报修' } },
        { path: 'tickets', name: 'it-tickets', component: () => import('@/views/it/TicketListView.vue'), meta: { title: '我的报修' } },
        { path: 'tickets/:id', name: 'it-ticket-detail', component: () => import('@/views/it/TicketDetailView.vue'), meta: { title: '报修详情' } },
        { path: 'knowledge', name: 'it-knowledge', component: () => import('@/views/it/KnowledgeBaseView.vue'), meta: { title: '知识库' } },
        { path: 'knowledge/:id', name: 'it-knowledge-chat', component: () => import('@/views/it/KnowledgeBaseChatView.vue'), meta: { title: '知识库问答' } },
        { path: 'service-catalog', name: 'it-service-catalog', component: () => import('@/views/it/ServiceCatalogView.vue'), meta: { title: '服务目录管理', requireAdmin: true } },
        { path: 'engineer', name: 'it-engineer', component: () => import('@/views/it/EngineerTicketView.vue'), meta: { title: '报修处理', requireEngineer: true } },
      ],
    },
    // ===== 教务入口 =====
    {
      path: '/hr',
      component: () => import('@/views/hr/HrLayout.vue'),
      redirect: '/hr/chat',
      children: [
        { path: 'chat', name: 'hr-chat', component: () => import('@/views/hr/HrChatView.vue'), meta: { title: '教务助手' } },
        { path: 'leave', name: 'hr-leave', component: () => import('@/views/hr/LeaveRequestView.vue'), meta: { title: '请假申请' } },
        { path: 'punch', name: 'hr-punch', component: () => import('@/views/hr/PunchCorrectionView.vue'), meta: { title: '课堂补签' } },
      ],
    },
    // ===== 后勤入口 =====
    {
      path: '/admin',
      component: () => import('@/views/admin/AdminLayout.vue'),
      redirect: '/admin/chat',
      children: [
        { path: 'chat', name: 'admin-chat', component: () => import('@/views/admin/AdminChatView.vue'), meta: { title: '后勤助手' } },
        { path: 'device', name: 'admin-device', component: () => import('@/views/admin/DeviceRequestView.vue'), meta: { title: '器材借用' } },
        { path: 'meeting', name: 'admin-meeting', component: () => import('@/views/admin/MeetingRoomView.vue'), meta: { title: '场地预约' } },
        { path: 'manage', name: 'admin-manage', component: () => import('@/views/admin/ApprovalManageView.vue'), meta: { title: '审批管理', requireAdmin: true } },
        { path: 'devices', name: 'admin-devices-manage', component: () => import('@/views/admin/DeviceManageView.vue'), meta: { title: '器材管理', requireAdmin: true } },
        { path: 'models', name: 'admin-models', component: () => import('@/views/admin/ModelManageView.vue'), meta: { title: '模型管理', requireAdmin: true } },
      ],
    },
    // ===== 知识问答入口 =====
    {
      path: '/qa',
      component: () => import('@/views/qa/QaLayout.vue'),
      redirect: '/qa/chat',
      children: [
        { path: 'chat', name: 'qa-chat', component: () => import('@/views/qa/QaChatView.vue'), meta: { title: '知识问答' } },
      ],
    },
    // ===== 用户 =====
    {
      path: '/profile',
      name: 'profile',
      component: () => import('@/views/user/ProfileView.vue'),
      meta: { title: '个人中心' },
    },
  ],
})

router.beforeEach(async (to, _from, next) => {
  const userStore = useUserStore()
  const token = getToken()

  if (to.meta.title) {
    document.title = `${to.meta.title} - 灵犀校园`
  }

  if (!token && to.path !== '/') {
    ElMessage.warning('请先登录')
    window.dispatchEvent(new CustomEvent('open-login-dialog'))
    next({ path: '/', replace: true })
    return
  }

  if (token && !userStore.userInfo) {
    try {
      await userStore.getUserInfo()
    } catch {
      userStore.logout()
      ElMessage.error('登录状态验证失败，请重新登录')
      window.dispatchEvent(new CustomEvent('open-login-dialog'))
      next({ path: '/', replace: true })
      return
    }
  }

  if (to.meta.requireAdmin && !userStore.isAdmin) {
    ElMessage.error('需要管理员权限')
    next({ path: '/', replace: true })
    return
  }

  if (to.meta.requireEngineer && !userStore.isEngineerOrAdmin) {
    ElMessage.error('需要处理权限')
    next({ path: '/', replace: true })
    return
  }

  next()
})

export default router
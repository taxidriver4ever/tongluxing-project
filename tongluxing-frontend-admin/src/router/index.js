import { createRouter, createWebHistory } from 'vue-router'
import AdminLayout from '../layouts/AdminLayout.vue'
import { ensureAdminSession, hasAdminSession } from '../services/adminAuth.js'

const routes = [
  { path: '/login', name: 'login', component: () => import('../views/LoginPage.vue'), meta: { title: '管理员登录', public: true } },
  {
  path: '/',
  component: AdminLayout,
  children: [
    { path: '', name: 'dashboard', component: () => import('../views/DashboardPage.vue'), meta: { title: '运营总览' } },
    { path: 'certification-audit', name: 'audit', component: () => import('../views/CertificationAuditPage.vue'), meta: { title: '认证审核' } },
    { path: 'merchant-audit', name: 'merchant-audit', component: () => import('../views/MerchantAuditPage.vue'), meta: { title: '商家入驻审核' } },
    { path: 'merchant-coupon-audit', name: 'merchant-coupon-audit', component: () => import('../views/MerchantCouponAuditPage.vue'), meta: { title: '商家优惠审核' } },
    { path: 'trade-management', name: 'trade', component: () => import('../views/TradeManagementPage.vue'), meta: { title: '交易管理' } },
    { path: 'groupbuy-intervention', name: 'groupbuy', component: () => import('../views/GroupbuyInterventionPage.vue'), meta: { title: '拼团干预' } },
    { path: 'match-recommendations', name: 'matches', component: () => import('../views/MatchRecommendationsPage.vue'), meta: { title: '同行推荐监控' } },
    { path: 'customer-service', name: 'customer-service', component: () => import('../views/CustomerServicePage.vue'), meta: { title: '客服工单' } },
    { path: 'sos-events', name: 'sos-events', component: () => import('../views/SosEventsPage.vue'), meta: { title: 'SOS 事件' } },
    { path: 'chat-risk', name: 'chat-risk', component: () => import('../views/ChatRiskPage.vue'), meta: { title: '聊天风控' } },
    { path: 'operation-rules', name: 'rules', component: () => import('../views/OperationRulesPage.vue'), meta: { title: '运营规则' } },
    { path: 'audit-logs', name: 'logs', component: () => import('../views/AuditLogsPage.vue'), meta: { title: '审计日志' } },
    { path: 'internal-interfaces', name: 'internal', component: () => import('../views/InternalInterfacesPage.vue'), meta: { title: '内部接口' } },
  ],
  },
]

const router = createRouter({ history: createWebHistory(), routes, scrollBehavior: () => ({ top: 0 }) })

router.beforeEach(async to => {
  if (to.meta.public) {
    if (hasAdminSession() && await ensureAdminSession()) return { name: 'dashboard' }
    return true
  }
  if (await ensureAdminSession()) return true
  return { name: 'login', query: { redirect: to.fullPath } }
})

window.addEventListener('admin-auth-expired', () => {
  if (router.currentRoute.value.name !== 'login') {
    router.replace({ name: 'login', query: { reason: 'expired' } })
  }
})

export default router

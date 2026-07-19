import { createRouter, createWebHistory } from 'vue-router'
import MerchantLayout from '../layouts/MerchantLayout.vue'
import { ensureMerchantSession, hasSession } from '../services/auth.js'

const routes = [
  { path: '/login', name: 'login', component: () => import('../views/LoginPage.vue'), meta: { public: true, title: '商家登录' } },
  { path: '/', component: MerchantLayout, children: [
    { path: '', name: 'dashboard', component: () => import('../views/DashboardPage.vue'), meta: { title: '经营总览' } },
    { path: 'stores', name: 'stores', component: () => import('../views/StoresPage.vue'), meta: { title: '门店管理' } },
    { path: 'coupons', name: 'coupons', component: () => import('../views/CouponsPage.vue'), meta: { title: '优惠券管理' } },
    { path: 'groupbuys', name: 'groupbuys', component: () => import('../views/GroupbuyActivitiesPage.vue'), meta: { title: '拼单活动管理' } },
    { path: 'profile', name: 'profile', component: () => import('../views/ProfilePage.vue'), meta: { title: '商家资料' } },
    { path: 'settlement', name: 'settlement', component: () => import('../views/SettlementPage.vue'), meta: { title: '收款设置' } },
  ] },
]
const router = createRouter({ history: createWebHistory(), routes, scrollBehavior: () => ({ top: 0 }) })
router.beforeEach(async to => {
  if (to.meta.public) return hasSession() && await ensureMerchantSession() ? { name: 'dashboard' } : true
  return await ensureMerchantSession() ? true : { name: 'login', query: { redirect: to.fullPath } }
})
window.addEventListener('merchant-auth-expired', () => router.replace({ name: 'login', query: { reason: 'expired' } }))
export default router

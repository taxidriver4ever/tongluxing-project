<script setup>
import { computed } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import {
  Bell, Boxes, ChartNoAxesCombined, ChevronRight, FileClock, LayoutDashboard,
  Compass, Handshake, Headphones, LogOut, Network, Search, ShieldCheck, ShoppingBag, SlidersHorizontal, Store, TicketCheck, UsersRound, Siren, MessageSquareWarning, Route,
} from 'lucide-vue-next'
import { adminAuthState, logoutAdmin } from '../services/adminAuth.js'

const route = useRoute()
const router = useRouter()
const navGroups = [
  { label: '运营中心', items: [
    { to: '/', name: 'dashboard', label: '运营总览', icon: LayoutDashboard },
    { to: '/certification-audit', name: 'audit', label: '自动认证记录', icon: ShieldCheck },
    { to: '/merchant-audit', name: 'merchant-audit', label: '商家入驻审核', icon: Store },
    { to: '/partner-audit', name: 'partner-audit', label: '合作商审核', icon: Handshake },
    { to: '/coupon-center', name: 'coupon-center', label: '平台合作券池', icon: TicketCheck },
    { to: '/merchant-coupon-audit', name: 'merchant-coupon-audit', label: '商家优惠审核', icon: TicketCheck },
    { to: '/trade-management', name: 'trade', label: '交易管理', icon: ShoppingBag },
    { to: '/groupbuy-intervention', name: 'groupbuy', label: '拼团干预', icon: UsersRound },
    { to: '/match-recommendations', name: 'matches', label: '同行推荐', icon: Compass },
    { to: '/customer-service', name: 'customer-service', label: '客服工单', icon: Headphones },
    { to: '/sos-events', name: 'sos-events', label: 'SOS 事件', icon: Siren },
    { to: '/chat-risk', name: 'chat-risk', label: '聊天风控', icon: MessageSquareWarning },
    { to: '/trip-track-reviews', name: 'trip-track-reviews', label: '轨迹结算审核', icon: Route },
  ] },
  { label: '平台配置', items: [
    { to: '/operation-rules', name: 'rules', label: '运营规则', icon: SlidersHorizontal },
    { to: '/audit-logs', name: 'logs', label: '审计日志', icon: FileClock },
    { to: '/internal-interfaces', name: 'internal', label: '内部接口', icon: Network },
  ] },
]
const currentTitle = computed(() => route.meta.title || '运营总览')
const operator = computed(() => adminAuthState.operator || { displayName: '运营管理员', operatorId: '10001' })

async function logout() {
  await logoutAdmin()
  await router.replace({ name: 'login' })
}
</script>

<template>
  <aside class="sidebar">
    <div class="brand">
      <span class="brand-mark"><ChartNoAxesCombined /></span>
      <span><b>同路行 Admin</b><small>OPERATION CONSOLE</small></span>
    </div>
    <template v-for="group in navGroups" :key="group.label">
      <div class="nav-section">{{ group.label }}</div>
      <RouterLink v-for="item in group.items" :key="item.name" :to="item.to" class="nav-item" :class="{ active: route.name === item.name }">
        <component :is="item.icon" />{{ item.label }}
      </RouterLink>
    </template>
    <div class="sidebar-note"><b>原型接口说明</b><br>页面字段与现有 Admin Controller、内部接口及审计模型保持一致。</div>
  </aside>
  <main class="main">
    <header class="topbar">
      <div class="crumb"><span>同路行</span><ChevronRight :size="14" /><b>{{ currentTitle }}</b></div>
      <div class="top-actions">
        <button class="icon-btn" aria-label="搜索"><Search /></button>
        <button class="icon-btn has-notice" aria-label="通知"><Bell /></button>
        <div class="admin-user"><span class="user-avatar">OP</span><span>{{ operator.displayName }}<small>ID {{ operator.operatorId }}</small></span></div>
        <button class="icon-btn logout-btn" aria-label="退出登录" title="退出登录" @click="logout"><LogOut /></button>
      </div>
    </header>
    <RouterView />
  </main>
</template>

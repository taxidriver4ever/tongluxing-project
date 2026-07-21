<script setup>
import { computed } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { BadgePercent, Building2, ChevronRight, CircleDollarSign, LayoutDashboard, LogOut, Settings2, Store, UsersRound } from 'lucide-vue-next'
import { authState, logoutMerchant } from '../services/auth.js'
const route = useRoute(); const router = useRouter()
const nav = [
  { to: '/', name: 'dashboard', label: '经营总览', icon: LayoutDashboard },
  { to: '/stores', name: 'stores', label: '门店管理', icon: Store },
  { to: '/coupons', name: 'coupons', label: '优惠券管理', icon: BadgePercent },
  { to: '/groupbuys', name: 'groupbuys', label: '拼单活动管理', icon: UsersRound },
  { to: '/profile', name: 'profile', label: '商家资料', icon: Building2 },
  { to: '/settlement', name: 'settlement', label: '收款设置', icon: CircleDollarSign },
]
const title = computed(() => route.meta.title || '商家中心')
async function logout() { await logoutMerchant(); router.replace({ name: 'login' }) }
</script>
<template>
  <aside class="sidebar">
    <div class="brand"><span class="brand-mark"><Store /></span><span><b>同路行商家中心</b><small>MERCHANT CONSOLE</small></span></div>
    <div class="nav-label">经营中心</div>
    <RouterLink v-for="item in nav" :key="item.name" :to="item.to" class="nav-item" :class="{ active: route.name === item.name }"><component :is="item.icon" />{{ item.label }}</RouterLink>
    <div class="sidebar-stage"><Settings2 /><div><b>首期经营闭环</b><small>门店、优惠券及审核状态已连接真实后端。订单核销与推广数据属于后续阶段。</small></div></div>
  </aside>
  <main class="main">
    <header class="topbar"><div class="crumb"><span>商家中心</span><ChevronRight /><b>{{ title }}</b></div><div class="merchant-user"><span>{{ authState.merchant?.merchantName || '商家账号' }}<small>UID {{ authState.user?.userId }}</small></span><button class="icon-btn" title="退出登录" @click="logout"><LogOut /></button></div></header>
    <RouterView />
  </main>
</template>

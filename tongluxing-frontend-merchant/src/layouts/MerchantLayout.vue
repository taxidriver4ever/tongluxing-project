<script setup>
import { computed } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { BadgePercent, Boxes, Building2, ChevronRight, CircleDollarSign, Handshake, LayoutDashboard, LogOut, QrCode, Settings2, ShoppingBag, Store, UsersRound } from 'lucide-vue-next'
import { authState, logoutMerchant } from '../services/auth.js'
const route = useRoute(); const router = useRouter()
const nav = [
  { to: '/', name: 'dashboard', label: '经营总览', icon: LayoutDashboard },
  { to: '/stores', name: 'stores', label: '门店管理', icon: Store },
  { to: '/products', name: 'products', label: '商品管理', icon: Boxes },
  { to: '/orders', name: 'orders', label: '订单管理', icon: ShoppingBag },
  { to: '/verification', name: 'verification', label: '扫码核销', icon: QrCode },
  { to: '/coupons', name: 'coupons', label: '优惠券管理', icon: BadgePercent },
  { to: '/groupbuys', name: 'groupbuys', label: '拼单活动管理', icon: UsersRound },
  { to: '/profile', name: 'profile', label: '商家资料', icon: Building2 },
  { to: '/settlement', name: 'settlement', label: '收款设置', icon: CircleDollarSign },
  { to: '/partner-application', name: 'partner-application', label: '合作商申请', icon: Handshake },
  { to: '/partner-coupons', name: 'partner-coupons', label: '合作商券', icon: BadgePercent, partnerOnly: true },
]
const visibleNav = computed(() => nav.filter(item => !item.partnerOnly || authState.merchant?.partnerStatus === 'APPROVED'))
const title = computed(() => route.meta.title || '商家中心')
async function logout() { await logoutMerchant(); router.replace({ name: 'login' }) }
</script>
<template>
  <aside class="sidebar">
    <div class="brand"><span class="brand-mark"><Store /></span><span><b>同路行商家中心</b><small>MERCHANT CONSOLE</small></span></div>
    <div class="nav-label">经营中心</div>
    <RouterLink v-for="item in visibleNav" :key="item.name" :to="item.to" class="nav-item" :class="{ active: route.name === item.name }"><component :is="item.icon" />{{ item.label }}</RouterLink>
    <div class="sidebar-stage"><Settings2 /><div><b>{{authState.merchant?.partnerStatus==='APPROVED'?'平台合作商':'普通商户'}}</b><small>门店、商品、订单、核销与合作券均连接真实后端。</small></div></div>
  </aside>
  <main class="main">
    <header class="topbar"><div class="crumb"><span>商家中心</span><ChevronRight /><b>{{ title }}</b></div><div class="merchant-user"><span>{{ authState.merchant?.merchantName || '商家账号' }}<small>UID {{ authState.user?.userId }}</small></span><button class="icon-btn" title="退出登录" @click="logout"><LogOut /></button></div></header>
    <RouterView />
  </main>
</template>

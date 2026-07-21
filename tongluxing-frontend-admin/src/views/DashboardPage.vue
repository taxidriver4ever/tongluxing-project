<script setup>
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { CarFront, CircleDollarSign, Landmark, ReceiptText, ScanLine, Store, Users, UsersRound } from 'lucide-vue-next'
import MetricCard from '../components/MetricCard.vue'
import { getOverview } from '../services/trade.js'
import { showToast } from '../utils.js'

const data = ref({ trends: [] })
const loading = ref(false)
const number = value => Number(value || 0).toLocaleString('zh-CN')
const money = value => `¥${Number(value || 0).toLocaleString('zh-CN', { maximumFractionDigits: 2 })}`
const metrics = computed(() => [
  ['注册用户', number(data.value.registeredUserCount), `今日新增 ${number(data.value.newUserCount)}`, Users],
  ['已认证车辆', number(data.value.certifiedVehicleCount), `活跃用户 ${number(data.value.activeUserCount)}`, CarFront],
  ['累计订单', number(data.value.orderCount), `今日 ${number(data.value.todayOrderCount)} 单`, ReceiptText],
  ['支付金额', money(data.value.paidAmount), `今日 ${money(data.value.todayPaidAmount)}`, CircleDollarSign],
  ['入驻商家', number(data.value.merchantCount), `活跃 ${number(data.value.activeMerchantCount)}`, Store],
  ['拼团活动', number(data.value.groupbuyActivityCount), `发券 ${number(data.value.couponOfferCount)}`, UsersRound],
  ['核销次数', number(data.value.verificationCount), '真实核销记录', ScanLine],
  ['平台佣金', money(data.value.commissionAmount), '已结算佣金', Landmark],
])
const maxTrend = computed(() => Math.max(1, ...data.value.trends.flatMap(item => [item.orders, item.newUsers, item.verifications].map(Number))))
const height = value => `${Math.max(3, Number(value || 0) / maxTrend.value * 92)}%`

async function load() {
  loading.value = true
  try { data.value = await getOverview() }
  catch (error) { showToast(error.message, 'error') }
  finally { loading.value = false }
}
onMounted(load)
</script>

<template><div class="page">
  <div class="page-head"><div><h1>运营总览</h1><p>用户、商家、交易、优惠、拼团与核销的实时经营数据</p></div><div class="head-actions"><span class="endpoint">GET /v1/admin/operation-overview</span><button class="btn primary" :disabled="loading" @click="load">{{ loading ? '刷新中…' : '刷新数据' }}</button></div></div>
  <section class="grid cols-4"><MetricCard v-for="m in metrics.slice(0,4)" :key="m[0]" :label="m[0]" :value="m[1]" :trend="m[2]" :icon="m[3]" /></section>
  <section class="grid cols-4" style="margin-top:16px"><MetricCard v-for="m in metrics.slice(4)" :key="m[0]" :label="m[0]" :value="m[1]" :trend="m[2]" :icon="m[3]" /></section>
  <section class="grid" style="grid-template-columns:2fr 1fr;margin-top:16px">
    <div class="panel"><div class="panel-head"><div><h2>近 7 日业务趋势</h2><p>订单、新增用户与核销量均来自业务事实表</p></div><div class="legend"><span><i style="background:#285cff"></i>订单</span><span><i style="background:#8da7ff"></i>用户</span><span><i style="background:#f4b64c"></i>核销</span></div></div><div class="panel-body"><div class="chart"><div v-if="!data.trends.length" class="empty-state">暂无趋势数据</div><div v-else class="chart-bars"><div v-for="item in data.trends" :key="item.date" class="bar-group"><b class="bar" :style="{height:height(item.orders)}" :title="`订单 ${item.orders}`"></b><b class="bar alt" :style="{height:height(item.newUsers)}" :title="`新增用户 ${item.newUsers}`"></b><b class="bar warn" :style="{height:height(item.verifications)}" :title="`核销 ${item.verifications}`"></b><span class="bar-label">{{ String(item.date).slice(5) }}</span></div></div></div></div></div>
    <div class="panel"><div class="panel-head"><div><h2>经营健康度</h2><p>实时待办和活跃主体</p></div></div><div class="panel-body mini-list"><div class="mini-row"><span>活跃商家</span><b>{{ number(data.activeMerchantCount) }}</b></div><div class="mini-row"><span>待审商家</span><b>{{ number(data.pendingMerchantCount) }}</b></div><div class="mini-row"><span>待审退款</span><b>{{ number(data.pendingRefundCount) }}</b></div><div class="mini-row"><span>待处理结算</span><b>{{ number(data.pendingSettlementCount) }}</b></div></div></div>
  </section>
  <section class="split" style="margin-top:16px"><div class="panel"><div class="panel-head"><div><h2>待处理事项</h2><p>直接进入真实业务队列</p></div><RouterLink class="btn sm" to="/trade-management">交易处理台</RouterLink></div><div class="table-wrap"><table><thead><tr><th>事项</th><th>来源</th><th>数量</th><th>状态</th></tr></thead><tbody><tr><td>商家入驻审核</td><td>merchant_profile</td><td>{{ number(data.pendingMerchantCount) }}</td><td><span class="status warning">待处理</span></td></tr><tr><td>退款审核</td><td>payment_refund_record</td><td>{{ number(data.pendingRefundCount) }}</td><td><span class="status warning">待处理</span></td></tr><tr><td>交易结算</td><td>payment_profit_sharing_record</td><td>{{ number(data.pendingSettlementCount) }}</td><td><span class="status info">可触发</span></td></tr></tbody></table></div></div><div class="panel"><div class="panel-head"><h2>数据来源</h2></div><div class="side-card mini-list"><div class="mini-row"><span>用户与车辆</span><span class="status success">实时</span></div><div class="mini-row"><span>订单与支付</span><span class="status success">实时</span></div><div class="mini-row"><span>核销与结算</span><span class="status success">实时</span></div><div class="mini-row"><span>拼团与优惠</span><span class="status success">实时</span></div></div></div></section>
  <div class="footer-note">数据字段对齐 AdminOperationOverviewVO · 不再使用静态演示数字</div>
</div></template>

<script setup>
import { onMounted, ref } from 'vue'
import { RefreshCw, ShoppingBag } from 'lucide-vue-next'
import { getOrders } from '../services/merchant.js'
const rows=ref([]),status=ref(''),message=ref(''),loading=ref(false)
async function load(){loading.value=true;try{const data=await getOrders(status.value);rows.value=data.records||[]}catch(e){message.value=e.message}finally{loading.value=false}}
onMounted(load)
</script>
<template><div class="page"><div class="page-head"><div><small class="eyebrow">MERCHANT ORDERS</small><h1>订单管理</h1><p>仅展示当前登录商户的真实订单。</p></div><button class="btn" @click="load"><RefreshCw/>刷新</button></div><div class="toolbar"><select v-model="status" class="field" @change="load"><option value="">全部状态</option><option>WAIT_PAY</option><option>PAID</option><option>VERIFIED</option><option>COMPLETED</option><option>CANCELLED</option><option>REFUNDED</option></select></div><div v-if="message" class="notice danger">{{message}}</div><section class="panel"><div class="table-wrap"><table><thead><tr><th>订单</th><th>用户</th><th>金额</th><th>支付</th><th>核销</th><th>订单状态</th></tr></thead><tbody><tr v-for="row in rows" :key="row.orderId"><td><b>{{row.orderNo}}</b><small>{{row.orderId}}</small></td><td>{{row.userId}}</td><td>¥{{row.paidAmount||row.payableAmount}}</td><td>{{row.paymentStatus}}</td><td>{{row.verificationStatus}}</td><td><span class="tag">{{row.orderStatus}}</span></td></tr><tr v-if="!rows.length"><td colspan="6"><ShoppingBag/>暂无订单</td></tr></tbody></table></div></section></div></template>

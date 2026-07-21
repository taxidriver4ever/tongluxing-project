<script setup>
import { computed, onMounted, ref } from 'vue'
import { Landmark, ShoppingBag, Undo2, WalletCards } from 'lucide-vue-next'
import BaseModal from '../components/BaseModal.vue'
import MetricCard from '../components/MetricCard.vue'
import { auditRefund, getOrder, getOrders, getOverview, getRefund, getRefunds, getSettlements, getTransactions, getVerificationRecords, triggerSettlement } from '../services/trade.js'
import { showToast } from '../utils.js'

const active=ref('orders'), keyword=ref(''), status=ref(''), loading=ref(false), rows=ref([]), total=ref(0), overview=ref({})
const modal=ref(false), modalMode=ref('detail'), selected=ref(null), reason=ref(''), submitting=ref(false)
const money=value=>`¥${Number(value||0).toLocaleString('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:2})}`
const time=value=>value?String(value).replace('T',' ').slice(0,19):'—'
const statusText=value=>({WAIT_PAY:'待支付',PAID:'已支付',COMPLETED:'已完成',REFUNDED:'已退款',CLOSED:'已关闭',PENDING:'待审核',APPROVED:'已通过',REJECTED:'已拒绝',SUCCESS:'成功',FAILED:'失败',WAIT_SHARING:'待结算',VERIFIED:'已核销'}[value]||value||'—')
const statusClass=value=>['SUCCESS','APPROVED','PAID','COMPLETED','VERIFIED'].includes(value)?'success':['PENDING','WAIT_PAY','WAIT_SHARING'].includes(value)?'warning':['REJECTED','FAILED','REFUNDED'].includes(value)?'danger':'info'
const tabs=[['orders','订单'],['refunds','退款审核'],['settlements','结算'],['verifications','核销记录'],['transactions','交易流水']]
const metrics=computed(()=>[
  ['今日订单',String(overview.value.todayOrderCount||0),'真实订单事实表',ShoppingBag,false],
  ['今日实付',money(overview.value.todayPaidAmount),'支付成功金额',WalletCards,false],
  ['待审退款',String(overview.value.pendingRefundCount||0),'需运营审核',Undo2,true],
  ['待结算',String(overview.value.pendingSettlementCount||0),'可人工触发',Landmark,false],
])

async function load(){loading.value=true;try{
  const params={page:1,size:50,status:status.value}
  const loaders={orders:()=>getOrders({...params,keyword:keyword.value}),refunds:()=>getRefunds(params),settlements:()=>getSettlements(params),verifications:()=>getVerificationRecords(params),transactions:()=>getTransactions({...params,type:status.value})}
  const result=await loaders[active.value]();rows.value=result.records||[];total.value=result.total||0
}catch(error){showToast(error.message,'error')}finally{loading.value=false}}
async function loadOverview(){try{overview.value=await getOverview()}catch(error){showToast(error.message,'error')}}
function switchTab(id){active.value=id;keyword.value='';status.value='';load()}
async function openOrder(row){try{selected.value=await getOrder(row.orderId);modalMode.value='detail';modal.value=true}catch(error){showToast(error.message,'error')}}
async function openRefund(row){try{selected.value=await getRefund(row.refundId);reason.value='';modalMode.value='refund';modal.value=true}catch(error){showToast(error.message,'error')}}
async function submitRefund(result){if(result==='REJECTED'&&!reason.value.trim()){showToast('拒绝退款时必须填写原因','error');return}submitting.value=true;try{await auditRefund(selected.value.refundId,{auditResult:result,rejectReason:reason.value.trim(),requestId:`refund-${selected.value.refundId}-${Date.now()}`});modal.value=false;showToast(result==='APPROVED'?'退款已通过并同步订单':'退款已拒绝并恢复订单状态');await Promise.all([load(),loadOverview()])}catch(error){showToast(error.message,'error')}finally{submitting.value=false}}
async function settle(row){if(!window.confirm(`确认触发结算 ${row.settlementNo}？`))return;try{await triggerSettlement(row.settlementId,{auditResult:'APPROVED',rejectReason:'运营后台人工触发',requestId:`settlement-${row.settlementId}-${Date.now()}`});showToast('结算完成，订单状态已同步');await Promise.all([load(),loadOverview()])}catch(error){showToast(error.message,'error')}}
onMounted(()=>Promise.all([load(),loadOverview()]))
</script>

<template><div class="page"><div class="page-head"><div><h1>交易管理</h1><p>订单、退款、结算、核销与资金流水的一体化真实处理台</p></div><div class="head-actions"><button class="btn primary" :disabled="loading" @click="load">{{loading?'刷新中…':'刷新数据'}}</button></div></div>
  <div class="grid cols-4"><MetricCard v-for="m in metrics" :key="m[0]" :label="m[0]" :value="m[1]" :trend="m[2]" :icon="m[3]" :down="m[4]" /></div>
  <div class="tabs" style="margin-top:16px"><button v-for="t in tabs" :key="t[0]" class="tab" :class="{active:active===t[0]}" @click="switchTab(t[0])">{{t[1]}}</button></div>
  <div class="panel" style="margin-top:12px"><div class="toolbar"><input v-if="active==='orders'" v-model="keyword" class="field search" placeholder="订单号 / 用户 / 商家 / 商品" @keyup.enter="load"><select v-model="status" class="field" @change="load"><option value="">全部状态</option><template v-if="active==='orders'"><option>WAIT_PAY</option><option>PAID</option><option>COMPLETED</option><option>REFUNDED</option><option>CLOSED</option></template><template v-else-if="active==='refunds'"><option>PENDING</option><option>APPROVED</option><option>REJECTED</option></template><template v-else-if="active==='settlements'"><option>WAIT_SHARING</option><option>SUCCESS</option><option>FAILED</option></template><template v-else-if="active==='verifications'"><option>PENDING</option><option>SUCCESS</option><option>REVERSED</option></template><template v-else><option>PAYMENT</option><option>REFUND</option></template></select><button class="btn primary" @click="load">查询</button><span class="endpoint">共 {{total}} 条真实记录</span></div>
    <div class="table-wrap">
      <table v-if="active==='orders'"><thead><tr><th>订单号</th><th>用户</th><th>商家 / 商品</th><th>原价</th><th>优惠</th><th>实付</th><th>创建时间</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="row in rows" :key="row.orderId"><td>{{row.orderNo}}</td><td>{{row.userName}}</td><td>{{row.merchantName}}<br><small>{{row.productName}}</small></td><td>{{money(row.originalAmount)}}</td><td>{{money(row.discountAmount)}}</td><td><b>{{money(row.paidAmount)}}</b></td><td>{{time(row.createdAt)}}</td><td><span class="status" :class="statusClass(row.orderStatus)">{{statusText(row.orderStatus)}}</span></td><td><button class="btn sm" @click="openOrder(row)">详情</button></td></tr></tbody></table>
      <table v-else-if="active==='refunds'"><thead><tr><th>退款单</th><th>订单</th><th>用户 / 商家</th><th>金额</th><th>原因</th><th>申请时间</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="row in rows" :key="row.refundId"><td>{{row.refundNo}}</td><td>{{row.orderNo}}</td><td>{{row.userName}}<br><small>{{row.merchantName}}</small></td><td><b>{{money(row.refundAmount)}}</b></td><td>{{row.refundReason}}</td><td>{{time(row.requestedAt)}}</td><td><span class="status" :class="statusClass(row.auditStatus)">{{statusText(row.auditStatus)}}</span></td><td><button class="btn sm" @click="openRefund(row)">{{row.auditStatus==='PENDING'?'审核':'详情'}}</button></td></tr></tbody></table>
      <table v-else-if="active==='settlements'"><thead><tr><th>结算单</th><th>订单</th><th>商家</th><th>交易额</th><th>佣金</th><th>商家应收</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="row in rows" :key="row.settlementId"><td>{{row.settlementNo}}</td><td>{{row.orderNo}}</td><td>{{row.merchantName}}</td><td>{{money(row.totalAmount)}}</td><td>{{money(row.commissionAmount)}}</td><td><b>{{money(row.merchantAmount)}}</b></td><td><span class="status" :class="statusClass(row.settlementStatus)">{{statusText(row.settlementStatus)}}</span></td><td><button v-if="row.settlementStatus!=='SUCCESS'" class="btn sm primary" @click="settle(row)">触发结算</button><span v-else>{{time(row.settledAt)}}</span></td></tr></tbody></table>
      <table v-else-if="active==='verifications'"><thead><tr><th>核销码</th><th>业务</th><th>用户</th><th>商家</th><th>金额</th><th>地点</th><th>核销时间</th><th>状态</th></tr></thead><tbody><tr v-for="row in rows" :key="row.verificationId"><td>{{row.verificationCode}}</td><td>{{row.bizType}} #{{row.bizId}}</td><td>{{row.userName}}</td><td>{{row.merchantName}}</td><td>{{money(row.amount)}}</td><td>{{row.locationName||'—'}}</td><td>{{time(row.verifiedAt)}}</td><td><span class="status" :class="statusClass(row.verificationStatus)">{{statusText(row.verificationStatus)}}</span></td></tr></tbody></table>
      <table v-else><thead><tr><th>类型</th><th>流水号</th><th>订单号</th><th>金额</th><th>渠道</th><th>状态</th><th>发生时间</th></tr></thead><tbody><tr v-for="row in rows" :key="`${row.transactionType}-${row.transactionId}`"><td>{{row.transactionType==='PAYMENT'?'支付':'退款'}}</td><td>{{row.transactionNo}}</td><td>{{row.orderNo}}</td><td :style="{color:Number(row.amount)<0?'#d44':'inherit'}"><b>{{money(row.amount)}}</b></td><td>{{row.channel}}</td><td><span class="status" :class="statusClass(row.status)">{{statusText(row.status)}}</span></td><td>{{time(row.occurredAt)}}</td></tr></tbody></table>
      <div v-if="!loading&&!rows.length" class="footer-note">当前筛选条件下暂无记录</div>
    </div>
  </div>
  <BaseModal :open="modal" :title="modalMode==='refund'?'退款单详情':'订单详情'" @close="modal=false"><div v-if="selected" class="detail-grid"><div v-for="(value,key) in selected" :key="key" class="detail-item"><small>{{key}}</small><b>{{value??'—'}}</b></div></div><div v-if="modalMode==='refund'&&selected?.auditStatus==='PENDING'" class="form-group" style="margin-top:14px"><label>审核说明（拒绝时必填）</label><textarea v-model="reason" class="field" style="height:80px" placeholder="填写处理依据，操作将写入审计日志"></textarea></div><template #footer><button class="btn" @click="modal=false">关闭</button><template v-if="modalMode==='refund'&&selected?.auditStatus==='PENDING'"><button class="btn danger" :disabled="submitting" @click="submitRefund('REJECTED')">拒绝</button><button class="btn primary" :disabled="submitting" @click="submitRefund('APPROVED')">同意退款</button></template></template></BaseModal>
</div></template>

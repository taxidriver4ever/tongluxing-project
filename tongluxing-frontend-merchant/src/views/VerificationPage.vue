<script setup>
import { onMounted, ref } from 'vue'
import { QrCode, TicketCheck } from 'lucide-vue-next'
import { authState } from '../services/auth.js'
import { confirmVerification, getVerifications, parseVerification } from '../services/merchant.js'
const code=ref(''),preview=ref(null),rows=ref([]),message=ref('')
async function load(){try{const data=await getVerifications();rows.value=data.records||[]}catch(e){message.value=e.message}}
async function parse(){try{preview.value=await parseVerification(code.value.trim());message.value=preview.value.canVerify?'核销码有效，请确认信息':'不可核销：'+preview.value.blockReason}catch(e){message.value=e.message}}
async function confirm(){try{await confirmVerification({verificationCode:code.value.trim(),merchantId:authState.merchant.merchantId,operatorId:authState.user.userId,locationName:'商家Web后台',requestId:`VERIFY-${Date.now()}`});message.value='核销成功';preview.value=null;code.value='';await load()}catch(e){message.value=e.message}}
onMounted(load)
</script>
<template><div class="page"><div class="page-head"><div><small class="eyebrow">SCAN & VERIFY</small><h1>扫码核销</h1><p>可粘贴扫码结果或核销码，解析后由商户二次确认。</p></div></div><section class="panel"><div class="panel-body"><div class="form-grid"><label class="full"><span>核销码</span><input v-model="code" class="field" maxlength="128" placeholder="扫描或输入核销码"></label><button class="btn" :disabled="!code.trim()" @click="parse"><QrCode/>解析</button><button class="btn primary" :disabled="!preview?.canVerify" @click="confirm"><TicketCheck/>确认核销</button></div><div v-if="message" class="notice" style="margin-top:12px">{{message}}</div><div v-if="preview" class="notice-box">业务：{{preview.bizType}} · 金额：¥{{preview.amount}} · 用户：{{preview.userId}}</div></div></section><section class="panel" style="margin-top:18px"><div class="panel-head"><h2>核销记录</h2></div><div class="table-wrap"><table><thead><tr><th>核销码</th><th>业务</th><th>金额</th><th>状态</th><th>核销时间</th></tr></thead><tbody><tr v-for="row in rows" :key="row.verificationId"><td>{{row.verificationCode}}</td><td>{{row.bizType}}</td><td>¥{{row.amount}}</td><td>{{row.verificationStatus}}</td><td>{{row.verifiedAt}}</td></tr></tbody></table></div></section></div></template>

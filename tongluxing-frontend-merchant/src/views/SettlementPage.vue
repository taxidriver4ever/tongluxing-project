<script setup>
import { onMounted, reactive, ref } from 'vue'
import { CircleDollarSign, RefreshCw, ShieldCheck } from 'lucide-vue-next'
import { getSettlements, saveSettlement } from '../services/merchant.js'

const form = reactive({ accountType: 'CORPORATE', accountName: '', accountNo: '', bankName: '' })
const message = ref('')
const success = ref(false)
const loading = ref(false)
const records = ref([])
const recordsLoading = ref(false)

async function submit() {
  loading.value = true
  message.value = ''
  try {
    await saveSettlement(form)
    success.value = true
    message.value = '收款账户已安全保存'
  } catch (e) {
    success.value = false
    message.value = e.message
  } finally {
    loading.value = false
  }
}

async function loadRecords() {
  recordsLoading.value = true
  try {
    const data = await getSettlements()
    records.value = data?.records || []
  } catch (e) {
    message.value = e.message
    success.value = false
  } finally {
    recordsLoading.value = false
  }
}

const money = value => `¥${Number(value || 0).toFixed(2)}`
onMounted(loadRecords)
</script>

<template>
  <div class="page narrow-page">
    <div class="page-head">
      <div><small class="eyebrow">SETTLEMENT SECURITY</small><h1>结算中心</h1><p>安全维护收款账户，并核对本店真实结算记录。</p></div>
      <button class="btn" :disabled="recordsLoading" @click="loadRecords"><RefreshCw :size="16" />刷新记录</button>
    </div>
    <section class="panel settlement-card">
      <div class="settlement-icon"><CircleDollarSign /></div><h2>结算账户</h2>
      <p>收款账号不会在页面回显；当前联调只验证结算账务状态，不发起真实打款。</p>
      <div v-if="message" class="notice" :class="success ? 'success' : 'danger'">{{ message }}</div>
      <form class="form-grid" @submit.prevent="submit">
        <label><span>账户类型</span><select v-model="form.accountType"><option value="CORPORATE">对公账户</option><option value="LEGAL_PERSON">法人银行卡</option></select></label>
        <label><span>开户名称</span><input v-model="form.accountName" required maxlength="128"></label>
        <label class="full"><span>开户行</span><input v-model="form.bankName" required maxlength="128"></label>
        <label class="full"><span>收款账号</span><input v-model="form.accountNo" required maxlength="64" autocomplete="off"></label>
        <div class="security-note full"><ShieldCheck />账号仅用于平台结算，提交后按敏感数据处理。</div>
        <button class="btn primary full submit" :disabled="loading">{{ loading ? '正在保存…' : '保存收款账户' }}</button>
      </form>
    </section>
    <section class="panel" style="margin-top:18px">
      <div class="page-head"><div><h2>结算记录</h2><p>金额由交易额、平台佣金和商家应收逐笔对账。</p></div></div>
      <div class="table-wrap">
        <table><thead><tr><th>结算单</th><th>订单</th><th>交易额</th><th>佣金</th><th>商家应收</th><th>状态</th><th>结算时间</th></tr></thead>
          <tbody><tr v-for="item in records" :key="item.settlementId"><td>#{{ item.settlementId }}</td><td>{{ item.orderNo }}</td><td>{{ money(item.totalAmount) }}</td><td>{{ money(item.commissionAmount) }}</td><td>{{ money(item.merchantAmount) }}</td><td><span class="status" :class="item.settlementStatus === 'SUCCESS' ? 'ok' : 'warn'">{{ item.settlementStatus }}</span></td><td>{{ item.settledAt || '待运营触发' }}</td></tr><tr v-if="!recordsLoading && !records.length"><td colspan="7">暂无结算记录</td></tr></tbody>
        </table>
      </div>
    </section>
  </div>
</template>

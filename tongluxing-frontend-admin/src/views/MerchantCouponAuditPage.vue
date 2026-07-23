<script setup>
import { onMounted, reactive, ref } from 'vue'
import { BadgePercent, LoaderCircle, RefreshCw, Search } from 'lucide-vue-next'
import BaseModal from '../components/BaseModal.vue'
import { auditMerchantCoupon, getMerchantCoupon, getMerchantCoupons, updateMerchantCouponStatus } from '../services/merchantAudit.js'
import { showToast } from '../utils.js'

const rows = ref([]), total = ref(0), loading = ref(false), selected = ref(null), open = ref(false)
const reason = ref(''), processing = ref(false)
const filters = reactive({ status: 'PENDING', page: 1, size: 20 })
const meta = status => ({ PENDING: ['待办理', 'warning'], APPROVED: ['已同意', 'success'], REJECTED: ['已拒绝', 'danger'] }[status] || [status, ''])

async function load() {
  loading.value = true
  try {
    const data = await getMerchantCoupons(filters)
    rows.value = data.records || []
    total.value = Number(data.total || 0)
  } catch (error) { showToast(error.message) } finally { loading.value = false }
}

async function detail(row) {
  open.value = true
  selected.value = row
  reason.value = row.rejectReason || ''
  try { selected.value = await getMerchantCoupon(row.couponId) } catch (error) { showToast(error.message) }
}

async function audit(result) {
  if (result === 'REJECTED' && reason.value.trim().length < 2) return showToast('拒绝时请填写至少 2 个字的原因')
  processing.value = true
  try {
    await auditMerchantCoupon(selected.value.couponId, result, reason.value.trim())
    showToast(result === 'APPROVED' ? '优惠券审核通过并已上架' : '优惠券已拒绝，商家可修改重提')
    open.value = false
    await load()
  } catch (error) { showToast(error.message) } finally { processing.value = false }
}

async function changeStatus(status) {
  if (!window.confirm(status === 'ACTIVE' ? '确认重新上架该优惠券？' : '确认下架该优惠券？')) return
  processing.value = true
  try {
    await updateMerchantCouponStatus(selected.value.couponId, status, status === 'ACTIVE' ? 'Admin重新上架' : 'Admin下架')
    showToast(status === 'ACTIVE' ? '优惠券已重新上架' : '优惠券已下架，领取和自动发放均已停止')
    open.value = false
    await load()
  } catch (error) { showToast(error.message) } finally { processing.value = false }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head"><div><div class="eyebrow"><BadgePercent/> MERCHANT BENEFITS</div><h1>商家优惠审核</h1><p>审核、上架和管理合作商提供的优惠券。</p></div><button class="btn" @click="load"><LoaderCircle v-if="loading" class="spin"/><RefreshCw v-else/>刷新队列</button></div>
    <section class="panel">
      <div class="panel-head"><div><h2>优惠券管理</h2><p>共 {{ total }} 条</p></div><span class="endpoint">GET /v1/admin/merchant-coupons</span></div>
      <div class="toolbar"><select v-model="filters.status" class="field" @change="load"><option value="">全部状态</option><option value="PENDING">待办理</option><option value="APPROVED">已同意</option><option value="REJECTED">已拒绝</option></select><button class="btn primary" @click="load"><Search/>查询</button></div>
      <div v-if="!rows.length" class="empty-note"><BadgePercent/><p>当前状态下没有优惠券</p></div>
      <div v-else class="table-wrap"><table><thead><tr><th>优惠券</th><th>商家 / 门店</th><th>价格</th><th>库存</th><th>审核</th><th>上线</th><th>操作</th></tr></thead><tbody><tr v-for="row in rows" :key="row.couponId"><td><b>{{row.couponName}}</b><small>{{row.category}} · {{row.couponId}}</small></td><td><b>{{row.merchantName}}</b><small>{{row.storeName}}</small></td><td>¥{{row.salePrice}} / <small>¥{{row.originalPrice}}</small></td><td>{{row.stock}}</td><td><span class="status" :class="meta(row.auditStatus)[1]">{{meta(row.auditStatus)[0]}}</span></td><td><span class="status" :class="row.offerStatus==='ACTIVE'?'success':'danger'">{{row.offerStatus==='ACTIVE'?'已上架':'已下架'}}</span></td><td><button class="btn sm" @click="detail(row)">{{row.auditStatus==='PENDING'?'办理':'管理'}}</button></td></tr></tbody></table></div>
    </section>
    <BaseModal :open="open" title="优惠券审核与管理" @close="open=false">
      <template v-if="selected"><div class="detail-grid"><div class="detail-item"><small>优惠券</small><b>{{selected.couponName}}</b></div><div class="detail-item"><small>商家 / 门店</small><b>{{selected.merchantName}} / {{selected.storeName}}</b></div><div class="detail-item"><small>售价 / 原价</small><b>¥{{selected.salePrice}} / ¥{{selected.originalPrice}}</b></div><div class="detail-item"><small>库存 / 限购</small><b>{{selected.stock}} / {{selected.limitCount}}</b></div></div><p>{{selected.description}}</p><div class="notice-box">使用须知：{{selected.useInstructions}}</div><div v-if="selected.auditStatus==='PENDING'" class="form-group"><label>拒绝原因（拒绝时必填）</label><textarea v-model="reason" class="field" maxlength="200"></textarea></div></template>
      <template #footer><button class="btn" @click="open=false">关闭</button><template v-if="selected?.auditStatus==='PENDING'"><button class="btn danger" :disabled="processing" @click="audit('REJECTED')">拒绝</button><button class="btn success solid" :disabled="processing" @click="audit('APPROVED')">同意并上架</button></template><button v-else-if="selected?.auditStatus==='APPROVED'" class="btn" :class="selected.offerStatus==='ACTIVE'?'danger':'success solid'" :disabled="processing" @click="changeStatus(selected.offerStatus==='ACTIVE'?'INACTIVE':'ACTIVE')">{{selected.offerStatus==='ACTIVE'?'下架优惠券':'重新上架'}}</button></template>
    </BaseModal>
  </div>
</template>

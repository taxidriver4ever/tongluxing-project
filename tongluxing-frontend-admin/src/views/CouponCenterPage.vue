<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Plus, RefreshCw, TicketCheck } from 'lucide-vue-next'
import BaseModal from '../components/BaseModal.vue'
import {
  auditPartnerCoupon,
  createPlatformCoupon,
  getPartnerCoupons,
  getPlatformCoupons,
  updatePlatformCouponStatus,
} from '../services/couponCenter.js'
import { showToast } from '../utils.js'

const tab = ref('partners')
const partnerRows = ref([])
const platformRows = ref([])
const open = ref(false)
const processing = ref(false)
const form = reactive({
  couponName: '平台采购洗车券', couponType: 'CAR_WASH', issuerId: null,
  thresholdAmount: 0, discountAmount: 50, totalQuantity: 1000,
  validDays: 30, perUserLimit: 1, scopeJson: '{"orderTypes":["MERCHANT"]}',
})

async function load() {
  try {
    const [partners, templates] = await Promise.all([getPartnerCoupons(''), getPlatformCoupons('')])
    partnerRows.value = partners.records || []
    platformRows.value = templates || []
  } catch (error) {
    showToast(error.message)
  }
}

async function audit(id, result) {
  processing.value = true
  try {
    await auditPartnerCoupon(id, result)
    showToast(result === 'APPROVED' ? '合作券已审核并进入平台券池' : '合作券已拒绝')
    await load()
  } catch (error) {
    showToast(error.message)
  } finally {
    processing.value = false
  }
}

async function create() {
  processing.value = true
  try {
    await createPlatformCoupon(form)
    open.value = false
    showToast('平台券已创建并上架')
    tab.value = 'pool'
    await load()
  } catch (error) {
    showToast(error.message)
  } finally {
    processing.value = false
  }
}

async function changeStatus(row) {
  try {
    await updatePlatformCouponStatus(row.templateId, row.templateStatus === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE')
    await load()
  } catch (error) {
    showToast(error.message)
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div><div class="eyebrow"><TicketCheck /> COUPON CENTER</div><h1>平台优惠券中心</h1><p>审核合作券、录入平台采购券，并查看发放、领取、使用和核销数据。</p></div>
      <div class="head-actions"><button class="btn" @click="load"><RefreshCw />刷新</button><button class="btn primary" @click="open = true"><Plus />创建平台券</button></div>
    </div>
    <div class="toolbar"><button class="btn" :class="{ primary: tab === 'partners' }" @click="tab = 'partners'">合作商待审券</button><button class="btn" :class="{ primary: tab === 'pool' }" @click="tab = 'pool'">平台券池与数据</button></div>
    <section class="panel">
      <div v-if="tab === 'partners'" class="table-wrap">
        <table><thead><tr><th>券</th><th>合作商</th><th>库存</th><th>已发</th><th>有效期</th><th>审核状态</th><th>操作</th></tr></thead><tbody>
          <tr v-for="row in partnerRows" :key="row.couponPoolId"><td><b>{{ row.couponName }}</b><small>{{ row.couponType }}</small></td><td>{{ row.merchantName }}</td><td>{{ row.totalStock }}</td><td>{{ row.usedStock }}</td><td>{{ row.validDays }} 天</td><td><span class="status">{{ row.auditStatus }}</span></td><td><template v-if="row.auditStatus === 'PENDING'"><button class="btn sm success solid" :disabled="processing" @click="audit(row.couponPoolId, 'APPROVED')">通过并入池</button><button class="btn sm danger" :disabled="processing" @click="audit(row.couponPoolId, 'REJECTED')">拒绝</button></template><span v-else class="muted">已办理</span></td></tr>
        </tbody></table>
      </div>
      <div v-else class="table-wrap">
        <table><thead><tr><th>模板</th><th>类型</th><th>库存</th><th>已发放</th><th>主动领取</th><th>已使用</th><th>已核销</th><th>状态</th><th>操作</th></tr></thead><tbody>
          <tr v-for="row in platformRows" :key="row.templateId"><td><b>{{ row.couponName }}</b><small>{{ row.templateId }}</small></td><td>{{ row.couponType }}</td><td>{{ row.claimedQuantity }} / {{ row.totalQuantity }}</td><td>{{ row.issuedCount }}</td><td>{{ row.claimedCount }}</td><td>{{ row.usedCount }}</td><td>{{ row.verifiedCount }}</td><td><span class="status" :class="{ success: row.templateStatus === 'ACTIVE' }">{{ row.templateStatus }}</span></td><td><button class="btn sm" @click="changeStatus(row)">{{ row.templateStatus === 'ACTIVE' ? '停用' : '启用' }}</button></td></tr>
        </tbody></table>
      </div>
    </section>

    <BaseModal :open="open" title="创建或录入平台券" @close="open = false">
      <div class="form-grid">
        <div class="form-group full"><label>券名称</label><input v-model.trim="form.couponName" class="field" maxlength="64" placeholder="例如：平台采购洗车券" required></div>
        <div class="form-group"><label>券类型</label><select v-model="form.couponType" class="field"><option value="CAR_WASH">洗车券</option><option value="MAINTENANCE">保养抵扣券</option><option value="CASH">平台抵扣券</option></select></div>
        <div class="form-group"><label>券面价值</label><input v-model.number="form.discountAmount" class="field" type="number" min="0.01" step="0.01" required></div>
        <div class="form-group"><label>使用门槛</label><input v-model.number="form.thresholdAmount" class="field" type="number" min="0" step="0.01" required></div>
        <div class="form-group"><label>采购 / 发行数量</label><input v-model.number="form.totalQuantity" class="field" type="number" min="1" max="10000000" required></div>
        <div class="form-group"><label>领取后有效天数</label><input v-model.number="form.validDays" class="field" type="number" min="1" max="3650" required></div>
        <div class="form-group"><label>每人限领</label><input v-model.number="form.perUserLimit" class="field" type="number" min="1" max="100" required></div>
        <div class="form-group full"><label>适用范围 JSON</label><textarea v-model="form.scopeJson" class="field" maxlength="2000" placeholder='{"orderTypes":["MERCHANT"]}' required></textarea></div>
      </div>
      <template #footer><button class="btn" @click="open = false">取消</button><button class="btn primary" :disabled="processing" @click="create">{{ processing ? '创建中…' : '创建并上架' }}</button></template>
    </BaseModal>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { Handshake, RefreshCw, Send, Unlink } from 'lucide-vue-next'
import { applyPartner, cancelPartner, getPartnerApplication } from '../services/merchant.js'

const application = ref(null)
const loading = ref(false)
const submitting = ref(false)
const message = ref('')
const cancelOpen = ref(false)
const cancelReason = ref('')
const form = reactive({
  applicationReason: '',
  cooperationCategories: '洗车券、保养抵扣券',
  plannedMonthlyStock: 100,
})

const canApply = computed(() => !application.value || ['REJECTED', 'CANCELLED'].includes(application.value.applicationStatus))
const cancellationPending = computed(() => application.value?.cancellationStatus === 'PENDING')

async function load() {
  loading.value = true
  try {
    application.value = await getPartnerApplication()
  } catch (error) {
    if (error.code !== 404) message.value = error.message
  } finally {
    loading.value = false
  }
}

async function submit() {
  message.value = ''
  submitting.value = true
  try {
    application.value = await applyPartner(form)
    message.value = '合作商申请已提交，等待 Admin 审核'
  } catch (error) {
    message.value = error.message
  } finally {
    submitting.value = false
  }
}

async function submitCancellation() {
  if (cancelReason.value.trim().length < 2) {
    message.value = '请填写至少 2 个字的取消合作原因'
    return
  }
  submitting.value = true
  try {
    application.value = await cancelPartner(cancelReason.value.trim())
    cancelOpen.value = false
    cancelReason.value = ''
    message.value = '取消合作申请已提交，审核通过前合作权限保持不变'
  } catch (error) {
    message.value = error.message
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page narrow-page">
    <div class="page-head">
      <div>
        <small class="eyebrow">PARTNER PROGRAM</small>
        <h1>合作商计划</h1>
        <p>申请成为合作商，向平台提供免费合作券；合作中也可以申请退出。</p>
      </div>
      <button class="btn" @click="load"><RefreshCw :class="{ spin: loading }" />刷新</button>
    </div>

    <div v-if="message" class="notice">{{ message }}</div>

    <section v-if="application" class="panel partner-status-card">
      <div class="panel-head">
        <div><h2>当前合作状态</h2><p>{{ application.applicationReason }}</p></div>
        <span class="tag" :class="{ success: application.applicationStatus === 'APPROVED', warning: application.applicationStatus === 'PENDING' }">
          {{ cancellationPending ? '取消合作审核中' : application.applicationStatus }}
        </span>
      </div>
      <div class="panel-body partner-summary">
        <div><small>合作品类</small><b>{{ application.cooperationCategories }}</b></div>
        <div><small>计划月库存</small><b>{{ application.plannedMonthlyStock }}</b></div>
        <div v-if="application.rejectReason"><small>加入申请驳回原因</small><b>{{ application.rejectReason }}</b></div>
        <div v-if="application.cancellationReason"><small>取消合作原因</small><b>{{ application.cancellationReason }}</b></div>
        <div v-if="application.cancellationRejectReason"><small>取消申请驳回原因</small><b>{{ application.cancellationRejectReason }}</b></div>
      </div>
      <div v-if="application.applicationStatus === 'APPROVED'" class="partner-actions">
        <span>{{ cancellationPending ? 'Admin 审核通过前，合作券菜单和现有合作权益保持可用。' : '退出后合作券菜单关闭，未发放券池停止发放。' }}</span>
        <button v-if="!cancellationPending" class="btn danger" @click="cancelOpen = true"><Unlink />申请取消合作</button>
      </div>
    </section>

    <form v-if="canApply" class="panel partner-form" @submit.prevent="submit">
      <div class="panel-head"><div><h2>合作资料</h2><p>说明免费券供给能力和预计库存。</p></div><Handshake /></div>
      <div class="panel-body form-grid">
        <label class="full"><span>申请说明</span><textarea v-model.trim="form.applicationReason" required minlength="2" maxlength="500" placeholder="请说明合作优势、可提供的服务与覆盖地区"></textarea><small class="field-help">2-500 字</small></label>
        <label class="full"><span>合作品类</span><input v-model.trim="form.cooperationCategories" required maxlength="255" placeholder="例如：洗车券、保养抵扣券"></label>
        <label><span>计划每月提供库存</span><input v-model.number="form.plannedMonthlyStock" type="number" min="1" max="1000000" required></label>
        <button class="btn primary full submit" :disabled="submitting"><Send />{{ submitting ? '提交中…' : '提交合作申请' }}</button>
      </div>
    </form>

    <div v-if="cancelOpen" class="modal-mask" @click.self="cancelOpen = false">
      <form class="modal" @submit.prevent="submitCancellation">
        <div class="modal-head"><div><h2>申请取消合作</h2><p>申请将交由 Admin 审核，不会立即关闭权益。</p></div></div>
        <div class="form-grid">
          <label class="full"><span>取消合作原因</span><textarea v-model.trim="cancelReason" required minlength="2" maxlength="500" placeholder="请说明退出原因，便于平台审核和后续服务改进"></textarea><small class="field-help">2-500 字</small></label>
        </div>
        <div class="modal-foot"><button type="button" class="btn" @click="cancelOpen = false">暂不取消</button><button class="btn danger" :disabled="submitting">提交取消申请</button></div>
      </form>
    </div>
  </div>
</template>

<style scoped>
.partner-status-card{margin-bottom:18px}.partner-summary{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.partner-summary>div{padding:13px 15px;border:1px solid #e8edef;border-radius:12px;background:#fafcfc}.partner-summary small,.partner-summary b{display:block}.partner-summary small{color:var(--muted);font-size:10px;margin-bottom:6px}.partner-summary b{font-size:12px}.partner-actions{padding:14px 19px;border-top:1px solid var(--line);display:flex;align-items:center;justify-content:space-between;gap:20px;color:var(--muted);font-size:10px}.partner-form .panel-body{padding:20px}.field-help{display:block;margin-top:6px;color:var(--muted);font-size:9px}.modal>.form-grid{padding:20px}
</style>

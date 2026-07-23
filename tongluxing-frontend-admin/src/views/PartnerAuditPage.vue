<script setup>
import { computed, onMounted, ref } from 'vue'
import { Handshake, RefreshCw } from 'lucide-vue-next'
import BaseModal from '../components/BaseModal.vue'
import {
  auditPartnerApplication,
  auditPartnerCancellation,
  getPartnerApplications,
} from '../services/couponCenter.js'
import { showToast } from '../utils.js'

const rows = ref([])
const status = ref('PENDING')
const reason = ref('')
const selected = ref(null)
const mode = ref('APPLICATION')
const processing = ref(false)

const modalTitle = computed(() => mode.value === 'CANCELLATION'
  ? `审核取消合作 · ${selected.value?.merchantName || ''}`
  : `审核合作申请 · ${selected.value?.merchantName || ''}`)

async function load() {
  try {
    const data = await getPartnerApplications(status.value)
    rows.value = data.records || []
  } catch (error) {
    showToast(error.message)
  }
}

function openAudit(row, nextMode) {
  selected.value = row
  mode.value = nextMode
  reason.value = ''
}

async function audit(result) {
  if (result === 'REJECTED' && reason.value.trim().length < 2) {
    return showToast('拒绝时请填写至少 2 个字的原因')
  }
  processing.value = true
  try {
    if (mode.value === 'CANCELLATION') {
      await auditPartnerCancellation(selected.value.merchantId, result, reason.value)
    } else {
      await auditPartnerApplication(selected.value.merchantId, result, reason.value)
    }
    selected.value = null
    await load()
    showToast(mode.value === 'CANCELLATION' ? '取消合作审核已完成' : '合作商审核已完成')
  } catch (error) {
    showToast(error.message)
  } finally {
    processing.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <div class="eyebrow"><Handshake /> PARTNER AUDIT</div>
        <h1>合作商关系审核</h1>
        <p>统一办理加入合作与取消合作，审核结果实时同步商家菜单和合作券池。</p>
      </div>
      <button class="btn" @click="load"><RefreshCw />刷新</button>
    </div>

    <div class="toolbar">
      <select v-model="status" class="field" @change="load">
        <option value="">全部状态</option>
        <option value="PENDING">待审核</option>
        <option value="APPROVED">合作中</option>
        <option value="REJECTED">已拒绝</option>
        <option value="CANCELLED">已取消合作</option>
      </select>
      <span class="endpoint">待审核包含加入申请与取消申请</span>
    </div>

    <section class="panel">
      <div v-if="!rows.length" class="empty-note"><Handshake /><p>当前筛选下没有合作商申请</p></div>
      <div v-else class="table-wrap">
        <table>
          <thead><tr><th>商户</th><th>申请类型</th><th>合作品类 / 原因</th><th>计划月库存</th><th>状态</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="row in rows" :key="row.merchantId">
              <td><b>{{ row.merchantName }}</b><small>{{ row.merchantId }}</small></td>
              <td>{{ row.cancellationStatus === 'PENDING' ? '取消合作' : '加入合作' }}</td>
              <td>{{ row.cancellationStatus === 'PENDING' ? row.cancellationReason : row.cooperationCategories }}<small>{{ row.cancellationStatus === 'PENDING' ? '' : row.applicationReason }}</small></td>
              <td>{{ row.plannedMonthlyStock }}</td>
              <td><span class="status" :class="{ warning: row.cancellationStatus === 'PENDING', success: row.applicationStatus === 'APPROVED' && row.cancellationStatus !== 'PENDING' }">{{ row.cancellationStatus === 'PENDING' ? '取消待审' : row.applicationStatus }}</span></td>
              <td>
                <button v-if="row.cancellationStatus === 'PENDING'" class="btn sm" @click="openAudit(row, 'CANCELLATION')">审核取消</button>
                <button v-else-if="row.applicationStatus === 'PENDING'" class="btn sm" @click="openAudit(row, 'APPLICATION')">审核加入</button>
                <span v-else class="muted">已办理</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <BaseModal :open="Boolean(selected)" :title="modalTitle" @close="selected = null">
      <template v-if="selected">
        <div class="detail-grid">
          <div class="detail-item"><small>商户</small><b>{{ selected.merchantName }}</b></div>
          <div class="detail-item"><small>办理类型</small><b>{{ mode === 'CANCELLATION' ? '取消合作' : '加入合作' }}</b></div>
        </div>
        <div class="notice-box" style="margin-top: 14px">
          {{ mode === 'CANCELLATION' ? selected.cancellationReason : selected.applicationReason }}
        </div>
        <div class="form-group" style="margin-top: 14px">
          <label>拒绝原因（拒绝时必填）</label>
          <textarea v-model="reason" class="field" maxlength="255" placeholder="请说明审核依据，最多 255 字"></textarea>
        </div>
      </template>
      <template #footer>
        <button class="btn" @click="selected = null">取消</button>
        <button class="btn danger" :disabled="processing" @click="audit('REJECTED')">拒绝</button>
        <button class="btn success solid" :disabled="processing" @click="audit('APPROVED')">通过</button>
      </template>
    </BaseModal>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import {
  BadgeCheck, CarFront, CheckCircle2, CircleAlert, Clock3, Copy, ExternalLink,
  FileBadge2, FileImage, FilterX, ImageOff, LoaderCircle, RefreshCw, RotateCcw,
  Search, ShieldCheck, UserRound, XCircle,
} from 'lucide-vue-next'
import BaseModal from '../components/BaseModal.vue'
import {
  auditCertification, getCertificationStatusCounts, getDrivingLicenseApplications,
  getDrivingLicenseDetail, getVehicleMaterialApplications,
} from '../services/certificationAudit.js'
import { showToast } from '../utils.js'

const moduleDefinitions = [
  {
    key: 'driver', title: '驾驶证认证', icon: FileBadge2,
    description: '只审核驾驶证正反面，结果不影响车辆资料。',
    endpoint: '/v1/admin/users/certifications',
  },
  {
    key: 'vehicle', title: '车辆资料认证', icon: CarFront,
    description: '行驶证与车辆外观照片作为一个独立审核单元。',
    endpoint: '/v1/admin/vehicles/certifications',
  },
]

const activeModule = ref('driver')
const queues = reactive({
  driver: { rows: [], total: 0, page: 1, size: 20, loading: false, stats: { PENDING: 0, APPROVED: 0, REJECTED: 0 } },
  vehicle: { rows: [], total: 0, page: 1, size: 20, loading: false, stats: { PENDING: 0, APPROVED: 0, REJECTED: 0 } },
})
const filters = reactive({
  driver: { keyword: '', status: 'PENDING' },
  vehicle: { keyword: '', status: 'PENDING' },
})
const selected = ref(null)
const modalOpen = ref(false)
const detailLoading = ref(false)
const auditLoading = ref(false)
const auditAction = ref('')
const rejectReason = ref('')
const requestId = ref('')
const failedImages = reactive({})

const currentDefinition = computed(() => moduleDefinitions.find(item => item.key === activeModule.value))
const currentQueue = computed(() => queues[activeModule.value])
const currentFilters = computed(() => filters[activeModule.value])
const pageCount = computed(() => Math.max(1, Math.ceil(currentQueue.value.total / currentQueue.value.size)))
const selectedId = computed(() => selected.value?.certificationId)
const selectedImages = computed(() => {
  if (!selected.value) return []
  if (activeModule.value === 'driver') {
    return [{
      key: 'driver', name: '驾驶证', hint: '正面、背面', expected: '1–2 张',
      items: [
        selected.value.licenseFrontImageUrl || selected.value.licenseFrontImageKey,
        selected.value.licenseBackImageUrl || selected.value.licenseBackImageKey,
      ].filter(Boolean),
    }]
  }
  return [
    { key: 'registration', name: '行驶证', hint: '主页、副页', expected: '2 张', items: selected.value.registrationLicenseImages || [] },
    { key: 'vehicle', name: '车辆照片', hint: '车头必传，侧面/车牌可选', expected: '1–3 张', items: selected.value.vehicleImages || [] },
  ]
})
const selectedComplete = computed(() => {
  if (!selected.value) return false
  if (activeModule.value === 'driver') return selectedImages.value[0]?.items.length >= 1
  return (selected.value.registrationLicenseImages?.length || 0) === 2
    && (selected.value.vehicleImages?.length || 0) >= 1
})

const statusMeta = status => ({
  PENDING: { label: '待办理', className: 'warning', icon: Clock3 },
  APPROVED: { label: '已同意', className: 'success', icon: BadgeCheck },
  REJECTED: { label: '已拒绝', className: 'danger', icon: XCircle },
}[status] || { label: status || '未知', className: 'info', icon: CircleAlert })

function formatTime(value) {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
  }).format(date)
}

async function loadStats(module) {
  Object.assign(queues[module].stats, await getCertificationStatusCounts(module))
}

async function loadQueue(module = activeModule.value, { resetPage = false, notify = false } = {}) {
  const queue = queues[module]
  const filter = filters[module]
  if (resetPage) queue.page = 1
  queue.loading = true
  try {
    const loader = module === 'driver' ? getDrivingLicenseApplications : getVehicleMaterialApplications
    const data = await loader({ keyword: filter.keyword, status: filter.status, page: queue.page, size: queue.size })
    queue.rows = data?.records || []
    queue.total = Number(data?.total || 0)
    queue.page = Number(data?.page || queue.page)
    queue.size = Number(data?.size || queue.size)
    if (notify) showToast(`${module === 'driver' ? '驾驶证' : '车辆资料'}审核队列已刷新`)
  } catch (error) {
    queue.rows = []
    queue.total = 0
    showToast(error.message)
  } finally {
    queue.loading = false
  }
}

async function refreshModule(module = activeModule.value, notify = false) {
  await Promise.all([loadQueue(module, { notify }), loadStats(module).catch(error => showToast(error.message))])
}

function switchModule(module) {
  activeModule.value = module
  if (!queues[module].rows.length && !queues[module].loading) refreshModule(module)
}

function setStatus(status) {
  currentFilters.value.status = status
  loadQueue(activeModule.value, { resetPage: true })
}

function resetFilters() {
  currentFilters.value.keyword = ''
  currentFilters.value.status = 'PENDING'
  loadQueue(activeModule.value, { resetPage: true })
}

function changePage(next) {
  if (next < 1 || next > pageCount.value || next === currentQueue.value.page) return
  currentQueue.value.page = next
  loadQueue()
}

async function openApplication(row) {
  selected.value = row
  rejectReason.value = row.rejectReason || ''
  requestId.value = `ADMIN-${activeModule.value.toUpperCase()}-${row.certificationId}-${Date.now()}`
  Object.keys(failedImages).forEach(key => delete failedImages[key])
  modalOpen.value = true
  if (activeModule.value !== 'driver') return
  detailLoading.value = true
  try {
    selected.value = await getDrivingLicenseDetail(row.certificationId)
    rejectReason.value = selected.value.rejectReason || ''
  } catch (error) {
    showToast(`驾驶证详情加载失败：${error.message}`)
  } finally {
    detailLoading.value = false
  }
}

async function audit(auditResult) {
  if (!selected.value || selected.value.status !== 'PENDING') return
  const reason = rejectReason.value.trim()
  if (auditResult === 'REJECTED' && (reason.length < 2 || reason.length > 255)) {
    return showToast('驳回原因需填写 2–255 个字符')
  }
  if (auditResult === 'APPROVED' && !selectedComplete.value) return showToast('当前审核单元必传图片不完整')
  const action = auditResult === 'APPROVED' ? '通过' : '驳回'
  if (!window.confirm(`确认${action}${currentDefinition.value.title} ${selectedId.value}？`)) return
  auditAction.value = action
  auditLoading.value = true
  try {
    await auditCertification(activeModule.value, selectedId.value, { auditResult, rejectReason: reason, requestId: requestId.value })
    modalOpen.value = false
    showToast(`${currentDefinition.value.title}已${action}，另一认证状态不受影响`)
    await refreshModule(activeModule.value)
  } catch (error) {
    showToast(error.message)
  } finally {
    auditLoading.value = false
    auditAction.value = ''
  }
}

function requestReupload() {
  showToast('重新上传由用户端完成；本页仅展示驳回原因和重新上传要求')
}

async function copyText(text, label = '内容') {
  try { await navigator.clipboard.writeText(String(text)); showToast(`${label}已复制`) }
  catch { showToast('浏览器未允许复制，请手动选择') }
}

function openImage(url) { window.open(url, '_blank', 'noopener') }

onMounted(() => Promise.all(moduleDefinitions.map(item => refreshModule(item.key))))
</script>

<template>
  <div class="page certification-split-page">
    <div class="page-head">
      <div><div class="eyebrow"><ShieldCheck /> INDEPENDENT CERTIFICATION</div><h1>认证审核</h1><p>驾驶证与车辆资料分开审批，两个状态互不影响。</p></div>
      <button class="btn" :disabled="currentQueue.loading" @click="refreshModule(activeModule, true)"><LoaderCircle v-if="currentQueue.loading" class="spin" /><RefreshCw v-else />刷新当前队列</button>
    </div>

    <section class="certification-module-grid">
      <button v-for="definition in moduleDefinitions" :key="definition.key" type="button" class="certification-module-card" :class="{ active: activeModule === definition.key }" @click="switchModule(definition.key)">
        <span class="module-icon"><component :is="definition.icon" /></span>
        <div class="module-copy"><small>{{ definition.key === 'driver' ? '独立审核单元 ①' : '独立审核单元 ②' }}</small><h2>{{ definition.title }}</h2><p>{{ definition.description }}</p><code>{{ definition.endpoint }}</code></div>
        <div class="module-statuses"><span class="warning"><b>{{ queues[definition.key].stats.PENDING }}</b>待办理</span><span class="success"><b>{{ queues[definition.key].stats.APPROVED }}</b>已同意</span><span class="danger"><b>{{ queues[definition.key].stats.REJECTED }}</b>已拒绝</span></div>
      </button>
    </section>

    <section class="independent-note"><CircleAlert /><div><b>状态独立规则</b><p>{{ activeModule === 'driver' ? '通过或驳回驾驶证，不会改变行驶证和车辆照片的审核状态。' : '通过或驳回车辆资料，不会改变驾驶证的审核状态。' }}</p></div></section>

    <div class="grid cols-3 audit-metrics">
      <div class="metric compact-metric" @click="setStatus('PENDING')"><Clock3 /><span><small>待办理</small><b>{{ currentQueue.stats.PENDING }}</b></span></div>
      <div class="metric compact-metric" @click="setStatus('APPROVED')"><BadgeCheck /><span><small>已同意</small><b>{{ currentQueue.stats.APPROVED }}</b></span></div>
      <div class="metric compact-metric danger" @click="setStatus('REJECTED')"><XCircle /><span><small>已拒绝</small><b>{{ currentQueue.stats.REJECTED }}</b></span></div>
    </div>

    <section class="panel audit-queue">
      <div class="panel-head"><div><h2>{{ currentDefinition.title }}审核队列</h2><p>{{ currentDefinition.description }}</p></div><span class="endpoint">GET {{ currentDefinition.endpoint }}</span></div>
      <div class="toolbar">
        <label class="search-box"><Search /><input v-model="currentFilters.keyword" :placeholder="activeModule === 'driver' ? '用户 ID / 姓名 / 驾驶证号' : '用户 ID / 车辆 ID / 脱敏车牌'" @keyup.enter="loadQueue(activeModule, { resetPage: true })"></label>
        <select v-model="currentFilters.status" class="field" @change="loadQueue(activeModule, { resetPage: true })"><option value="">全部状态</option><option value="PENDING">待办理</option><option value="APPROVED">已同意</option><option value="REJECTED">已拒绝</option></select>
        <button class="btn primary" :disabled="currentQueue.loading" @click="loadQueue(activeModule, { resetPage: true })"><Search />查询</button>
        <button class="btn" :disabled="currentQueue.loading" @click="resetFilters"><FilterX />重置</button>
      </div>

      <div v-if="currentQueue.loading && !currentQueue.rows.length" class="audit-empty"><LoaderCircle class="spin" /><b>正在加载{{ currentDefinition.title }}队列</b></div>
      <div v-else-if="!currentQueue.rows.length" class="audit-empty"><CheckCircle2 /><b>当前条件下没有{{ currentDefinition.title }}申请</b><p>这只代表当前审核单元无数据，不代表另一认证已完成。</p></div>
      <div v-else class="table-wrap">
        <table class="audit-table">
          <thead v-if="activeModule === 'driver'"><tr><th>申请 / 用户</th><th>持证人</th><th>驾驶证号</th><th>准驾车型</th><th>有效期至</th><th>提交时间</th><th>审核结果</th><th>操作</th></tr></thead>
          <thead v-else><tr><th>申请 / 车辆</th><th>用户</th><th>车牌 / 类型</th><th>资料单元</th><th>提交时间</th><th>审核结果</th><th>驳回原因</th><th>操作</th></tr></thead>
          <tbody><tr v-for="row in currentQueue.rows" :key="row.certificationId">
            <template v-if="activeModule === 'driver'">
              <td><b class="mono">{{ row.certificationId }}</b><small>UID {{ row.userId }}</small></td><td>{{ row.holderName || '未填写' }}</td><td class="mono">{{ row.licenseNoMasked || '—' }}</td><td>{{ row.vehicleClass || '—' }}</td><td>{{ row.validTo || '—' }}</td><td>{{ formatTime(row.submittedAt) }}</td><td><span class="status" :class="statusMeta(row.status).className">{{ statusMeta(row.status).label }}</span></td><td><button class="btn sm" @click="openApplication(row)">{{ row.status === 'PENDING' ? '审核' : '查看结果' }}</button></td>
            </template>
            <template v-else>
              <td><b class="mono">{{ row.certificationId }}</b><small>VID {{ row.vehicleId }}</small></td><td><b class="mono">{{ row.userId }}</b></td><td><b>{{ row.plateNumber || row.plateNoMask }}</b><small>{{ row.vehicleBrand }} {{ row.vehicleModel }} {{ row.vehicleType }}</small></td><td><div class="material-counts"><span :class="{ invalid: row.registrationLicenseImages?.length !== 2 }">行驶证 {{ row.registrationLicenseImages?.length || 0 }}/2</span><span :class="{ invalid: !row.vehicleImages?.length }">车辆照片 {{ row.vehicleImages?.length || 0 }}/3</span></div></td><td>{{ formatTime(row.submittedAt) }}</td><td><span class="status" :class="statusMeta(row.status).className">{{ statusMeta(row.status).label }}</span></td><td><span class="reject-inline">{{ row.status === 'REJECTED' ? (row.rejectReason || '请查看详情') : '—' }}</span></td><td><button class="btn sm" @click="openApplication(row)">{{ row.status === 'PENDING' ? '审核' : '查看结果' }}</button></td>
            </template>
          </tr></tbody>
        </table>
      </div>
      <div class="pagination"><span>共 {{ currentQueue.total }} 条 · 第 {{ currentQueue.page }} / {{ pageCount }} 页</span><div class="page-actions"><button class="btn sm" :disabled="currentQueue.page <= 1" @click="changePage(currentQueue.page - 1)">上一页</button><span class="page-no active">{{ currentQueue.page }}</span><button class="btn sm" :disabled="currentQueue.page >= pageCount" @click="changePage(currentQueue.page + 1)">下一页</button></div></div>
    </section>

    <BaseModal :open="modalOpen" size="wide" :title="`${currentDefinition.title} · ${selectedId || ''}`" @close="modalOpen = false">
      <div v-if="detailLoading" class="audit-empty"><LoaderCircle class="spin" /><b>正在加载认证详情</b></div>
      <template v-else-if="selected">
        <div class="audit-detail-head"><div><span class="status" :class="statusMeta(selected.status).className">{{ statusMeta(selected.status).label }}</span><h2>{{ activeModule === 'driver' ? (selected.holderName || '驾驶证认证') : `${selected.vehicleBrand || ''} ${selected.vehicleModel || ''}` }}</h2><p>{{ activeModule === 'driver' ? `准驾车型 ${selected.vehicleClass || '—'}` : `${selected.plateNumber || selected.plateNoMask} · ${selected.vehicleColor || '颜色未填'}` }}</p></div><component :is="activeModule === 'driver' ? FileBadge2 : CarFront" /></div>
        <div class="detail-grid audit-detail-grid"><div class="detail-item"><small>申请 ID</small><b class="mono">{{ selectedId }}</b><button class="copy-mini" @click="copyText(selectedId, '申请 ID')"><Copy /></button></div><div class="detail-item"><small>用户 ID</small><b class="mono">{{ selected.userId }}</b></div><div v-if="activeModule === 'vehicle'" class="detail-item"><small>车辆 ID</small><b class="mono">{{ selected.vehicleId }}</b></div><div class="detail-item"><small>提交时间</small><b>{{ formatTime(selected.submittedAt) }}</b></div><div class="detail-item"><small>审核时间</small><b>{{ formatTime(selected.reviewedAt) }}</b></div><div class="detail-item"><small>当前审核单元</small><b>{{ currentDefinition.title }}</b></div></div>

        <section v-for="group in selectedImages" :key="group.key" class="audit-image-section"><div class="image-section-head"><div><b>{{ group.name }}</b><small>{{ group.hint }}</small></div><span :class="['image-count', { invalid: !group.items.length }]">{{ group.items.length }} / {{ group.expected }}</span></div><div class="audit-image-grid"><article v-for="(url, index) in group.items" :key="url" class="audit-photo-card"><div class="photo-preview"><img v-if="!failedImages[url]" :src="url" :alt="`${group.name}${index + 1}`" @error="failedImages[url] = true"><div v-else class="photo-fallback"><ImageOff /><span>图片无法预览</span></div></div><div class="photo-meta"><span>{{ group.name }} {{ index + 1 }}</span><div><button title="复制 URL" @click="copyText(url, '图片 URL')"><Copy /></button><button title="新窗口打开" @click="openImage(url)"><ExternalLink /></button></div></div><code>{{ url }}</code></article><div v-if="!group.items.length" class="missing-material"><FileImage /><span>未提交{{ group.name }}</span></div></div></section>

        <div v-if="selected.status === 'REJECTED'" class="existing-reason"><CircleAlert /><div><b>{{ currentDefinition.title }}驳回原因</b><p>{{ selected.rejectReason || '未记录驳回原因' }}</p></div><button class="btn sm" @click="requestReupload"><RotateCcw />要求重新上传</button></div>
        <div v-if="selected.status === 'PENDING'" class="audit-decision"><div v-if="auditLoading" class="processing-banner"><LoaderCircle class="spin" /><b>办理中：正在{{ auditAction }}{{ currentDefinition.title }}</b><span>请勿关闭页面或重复提交</span></div><div class="form-group"><label>拒绝原因 <span>拒绝时必填，2–255 字</span></label><textarea v-model="rejectReason" class="field" maxlength="255" :disabled="auditLoading" :placeholder="activeModule === 'driver' ? '例如：驾驶证正面反光，证件号无法辨认' : '例如：车辆照片无法确认车牌'"></textarea><small>{{ rejectReason.trim().length }} / 255</small></div><div class="request-id"><span>本次审核 requestId</span><code>{{ requestId }}</code><button @click="copyText(requestId, 'requestId')"><Copy /></button></div></div>
      </template>
      <template #footer><button class="btn" :disabled="auditLoading" @click="modalOpen = false">关闭</button><template v-if="selected?.status === 'PENDING'"><button class="btn danger" :disabled="auditLoading" @click="audit('REJECTED')"><LoaderCircle v-if="auditLoading && auditAction === '驳回'" class="spin" /><XCircle v-else />{{ auditLoading && auditAction === '驳回' ? '办理中…' : `拒绝${currentDefinition.title}` }}</button><button class="btn success solid" :disabled="auditLoading || !selectedComplete" @click="audit('APPROVED')"><LoaderCircle v-if="auditLoading && auditAction === '通过'" class="spin" /><BadgeCheck v-else />{{ auditLoading && auditAction === '通过' ? '办理中…' : `同意${currentDefinition.title}` }}</button></template></template>
    </BaseModal>
  </div>
</template>

<style>
.certification-split-page .eyebrow{display:flex;align-items:center;gap:7px;margin-bottom:7px;color:var(--primary);font-size:9px;font-weight:800;letter-spacing:.12em}.certification-module-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px;margin-bottom:15px}.certification-module-card{border:1px solid var(--line);border-radius:18px;background:#fff;padding:18px;display:grid;grid-template-columns:46px minmax(0,1fr);gap:14px;text-align:left;color:var(--ink);cursor:pointer;box-shadow:0 4px 16px rgba(26,39,66,.035);transition:.2s}.certification-module-card:hover,.certification-module-card.active{border-color:#9fb4ff;box-shadow:0 12px 32px rgba(40,92,255,.11);transform:translateY(-2px)}.certification-module-card.active{background:linear-gradient(145deg,#fff,#f4f7ff)}.module-icon{width:46px;height:46px;border-radius:14px;background:var(--primary-soft);color:var(--primary);display:grid;place-items:center}.module-icon .lucide{width:23px;height:23px}.module-copy small{color:var(--primary);font-size:9px;font-weight:800;letter-spacing:.1em}.module-copy h2{font-size:17px;margin:5px 0}.module-copy p{margin:0;color:var(--muted);font-size:10px;line-height:1.6}.module-copy code{display:block;margin-top:9px;color:#8a93a3;font-size:8px}.module-statuses{grid-column:1/-1;display:grid;grid-template-columns:repeat(3,1fr);gap:7px}.module-statuses span{padding:8px 10px;border-radius:10px;background:#f7f8fa;color:var(--muted);font-size:9px}.module-statuses b{display:block;font-size:17px;color:var(--ink);margin-bottom:2px}.module-statuses .warning b{color:var(--warning)}.module-statuses .success b{color:var(--success)}.module-statuses .danger b{color:var(--danger)}.independent-note{display:flex;gap:10px;align-items:flex-start;padding:12px 14px;margin-bottom:15px;border:1px solid #dce5ff;border-radius:13px;background:#f5f8ff;color:var(--primary)}.independent-note .lucide{flex:0 0 auto;width:17px}.independent-note b{font-size:11px}.independent-note p{margin:3px 0 0;color:#66728b;font-size:10px}.audit-metrics{margin-bottom:15px}.compact-metric{display:flex;align-items:center;gap:13px;cursor:pointer}.compact-metric>.lucide{width:25px;height:25px;color:var(--primary)}.compact-metric span small,.compact-metric span b{display:block}.compact-metric span small{color:var(--muted);font-size:10px}.compact-metric span b{font-size:22px;margin-top:3px}.compact-metric.danger>.lucide{color:var(--danger)}.audit-queue .panel-head{min-height:62px}.search-box{height:35px;min-width:330px;display:flex;align-items:center;gap:8px;padding:0 11px;border:1px solid #dfe3eb;border-radius:9px;background:#fff}.search-box input{width:100%;border:0;outline:0;background:transparent;font-size:11px}.audit-empty{min-height:270px;display:flex;flex-direction:column;align-items:center;justify-content:center;color:var(--muted);text-align:center}.audit-empty>.lucide{width:36px;height:36px;margin-bottom:12px}.audit-empty b{color:var(--ink)}.audit-empty p{font-size:10px}.audit-table td{height:68px}.audit-table td>b,.audit-table td>small{display:block}.audit-table td>small{margin-top:4px}.mono{font-family:ui-monospace,SFMono-Regular,Consolas,monospace;font-size:10px}.material-counts{display:flex;gap:4px}.material-counts span{padding:4px 6px;border-radius:6px;background:#edf9f3;color:var(--success);font-size:9px}.material-counts span.invalid{background:#fff0f0;color:var(--danger)}.reject-inline{display:block;max-width:160px;overflow:hidden;text-overflow:ellipsis;color:var(--danger)}.page-actions{display:flex;align-items:center;gap:6px}.spin{animation:audit-spin .8s linear infinite}@keyframes audit-spin{to{transform:rotate(360deg)}}.modal-wide{width:min(960px,94vw)}.audit-detail-head{display:flex;align-items:center;justify-content:space-between;padding:17px 18px;border-radius:15px;background:linear-gradient(135deg,#f7f9ff,#edf2ff)}.audit-detail-head h2{margin:9px 0 4px;font-size:20px}.audit-detail-head p{margin:0;color:var(--muted)}.audit-detail-head>.lucide{width:48px;height:48px;color:var(--primary)}.audit-detail-grid{grid-template-columns:repeat(3,1fr);margin-top:14px}.audit-detail-grid .detail-item{position:relative}.copy-mini{position:absolute;right:8px;top:8px;border:0;background:transparent;color:#8791a2;cursor:pointer}.copy-mini .lucide{width:13px}.audit-image-section{margin-top:18px}.image-section-head{display:flex;align-items:center;justify-content:space-between;margin-bottom:8px}.image-section-head b,.image-section-head small{display:block}.image-section-head small{color:var(--muted);font-size:9px}.image-count{padding:4px 8px;border-radius:99px;background:#edf9f3;color:var(--success);font-size:9px}.image-count.invalid{background:#fff0f0;color:var(--danger)}.audit-image-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px}.audit-photo-card{overflow:hidden;border:1px solid var(--line);border-radius:12px;background:#fff}.photo-preview{height:150px;background:#f2f4f7}.photo-preview img{width:100%;height:100%;object-fit:cover}.photo-fallback,.missing-material{height:100%;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:7px;color:#98a2b3;font-size:10px}.photo-meta{display:flex;align-items:center;justify-content:space-between;padding:8px 10px;font-size:10px;font-weight:700}.photo-meta button,.request-id button{border:0;background:transparent;color:#7d8798;cursor:pointer}.photo-meta button .lucide{width:13px}.audit-photo-card code{display:block;padding:0 10px 9px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:#929baa;font-size:8px}.missing-material{height:150px;border:1px dashed #d7dce5;border-radius:12px}.existing-reason{display:flex;align-items:center;gap:10px;margin-top:17px;padding:13px;border:1px solid #ffd6d6;border-radius:12px;background:#fff4f4;color:var(--danger)}.existing-reason>div{flex:1}.existing-reason p{margin:4px 0 0;color:#8f4242}.audit-decision{margin-top:18px;padding:15px;border-radius:14px;background:#fafbfc}.audit-decision textarea.field{height:84px;font-family:inherit}.audit-decision label{display:flex;justify-content:space-between}.audit-decision label span,.audit-decision .form-group>small{color:var(--muted)}.audit-decision .form-group>small{display:block;margin-top:5px;text-align:right;font-size:9px}.request-id{display:flex;align-items:center;gap:9px;margin-top:12px;padding:9px 11px;border-radius:9px;background:#f0f3f7;font-size:9px}.request-id code{flex:1;overflow:hidden;text-overflow:ellipsis}.btn.success.solid{background:var(--success);border-color:var(--success);color:#fff}@media(max-width:1250px){.certification-module-grid{grid-template-columns:1fr}.audit-image-grid{grid-template-columns:repeat(2,1fr)}.audit-detail-grid{grid-template-columns:repeat(2,1fr)}}
.processing-banner{display:grid;grid-template-columns:20px 1fr;gap:2px 9px;align-items:center;margin-bottom:13px;padding:11px 12px;border:1px solid #ffe0a3;border-radius:10px;background:#fff8e8;color:#a76500}.processing-banner .lucide{grid-row:1/3;width:18px}.processing-banner span{font-size:9px;color:#a77b32}
</style>

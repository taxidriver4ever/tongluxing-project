<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import {
  BadgeCheck, CarFront, CircleAlert, FileBadge2, ImageOff,
  LoaderCircle, RefreshCw, Search, ShieldCheck,
} from 'lucide-vue-next'
import BaseModal from '../components/BaseModal.vue'
import {
  getCertificationStatusCounts,
  getDrivingLicenseApplications,
  getDrivingLicenseDetail,
  getVehicleCertificationDetail,
  getVehicleMaterialApplications,
} from '../services/certificationAudit.js'
import { showToast } from '../utils.js'

/**
 * 认证后台展示驾驶证和行驶证的自动认证记录，并保留历史异常记录查询。
 * 新提交的驾驶证材料齐全时由后端直接通过，后台仍可查看材料和通过时间。
 */
const modules = [
  {
    key: 'vehicle',
    title: '车主自动认证',
    icon: CarFront,
    description: '上传行驶证后自动通过，并立即同步车主身份。',
  },
  {
    key: 'driver',
    title: '驾驶证自动认证',
    icon: FileBadge2,
    description: '正反面材料齐全后自动通过，并保留记录与材料详情。',
  },
]

const activeModule = ref('vehicle')
const queues = reactive({
  vehicle: { rows: [], total: 0, page: 1, size: 20, loading: false, stats: {} },
  driver: { rows: [], total: 0, page: 1, size: 20, loading: false, stats: {} },
})
const filters = reactive({
  vehicle: { keyword: '', status: '' },
  driver: { keyword: '', status: '' },
})
const selected = ref(null)
const detailOpen = ref(false)
const detailLoading = ref(false)

const currentQueue = computed(() => queues[activeModule.value])
const currentFilter = computed(() => filters[activeModule.value])
const currentModule = computed(() => modules.find(item => item.key === activeModule.value))
const pageCount = computed(() => Math.max(1, Math.ceil(currentQueue.value.total / currentQueue.value.size)))

function statusMeta(status) {
  return {
    APPROVED: { label: '自动通过', className: 'success' },
    REJECTED: { label: '异常/拒绝', className: 'danger' },
    PENDING: { label: '材料异常待处理', className: 'warning' },
  }[status] || { label: status || '未知', className: 'info' }
}

function formatTime(value) {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
  }).format(date)
}

function imageSource(image) {
  if (!image) return ''
  if (typeof image === 'string') return image
  return image.imageUrl || image.url || image.downloadUrl || image.imageKey || image.objectKey || ''
}

const detailImages = computed(() => {
  if (!selected.value) return []
  if (activeModule.value === 'driver') {
    return [selected.value.licenseFrontImageUrl || selected.value.licenseFrontImageKey,
      selected.value.licenseBackImageUrl || selected.value.licenseBackImageKey].filter(Boolean)
  }
  const direct = [selected.value.licenseFrontImageUrl || selected.value.licenseFrontImageKey,
    selected.value.licenseBackImageUrl || selected.value.licenseBackImageKey].filter(Boolean)
  return direct.length
    ? direct
    : (selected.value.registrationLicenseImages || []).map(imageSource).filter(Boolean)
})

async function loadStats(module) {
  queues[module].stats = await getCertificationStatusCounts(module)
}

async function loadQueue(module = activeModule.value, { resetPage = false, notify = false } = {}) {
  const queue = queues[module]
  const filter = filters[module]
  if (resetPage) queue.page = 1
  queue.loading = true
  try {
    const loader = module === 'vehicle' ? getVehicleMaterialApplications : getDrivingLicenseApplications
    const data = await loader({
      keyword: filter.keyword,
      status: filter.status || undefined,
      page: queue.page,
      size: queue.size,
    })
    queue.rows = data?.records || []
    queue.total = Number(data?.total || 0)
    queue.page = Number(data?.page || queue.page)
    if (notify) showToast('自动认证记录已刷新')
  } catch (error) {
    queue.rows = []
    queue.total = 0
    showToast(error.message)
  } finally {
    queue.loading = false
  }
}

async function refresh(module = activeModule.value, notify = false) {
  await Promise.all([
    loadQueue(module, { notify }),
    loadStats(module).catch(error => showToast(error.message)),
  ])
}

function switchModule(module) {
  activeModule.value = module
  if (!queues[module].rows.length) refresh(module)
}

async function openDetail(row) {
  selected.value = row
  detailOpen.value = true
  detailLoading.value = true
  try {
    selected.value = activeModule.value === 'vehicle'
      ? await getVehicleCertificationDetail(row.certificationId)
      : await getDrivingLicenseDetail(row.certificationId)
  } catch (error) {
    showToast(`详情加载失败：${error.message}`)
  } finally {
    detailLoading.value = false
  }
}

function changePage(page) {
  if (page < 1 || page > pageCount.value || page === currentQueue.value.page) return
  currentQueue.value.page = page
  loadQueue()
}

onMounted(() => Promise.all(modules.map(item => refresh(item.key))))
</script>

<template>
  <div class="page auto-cert-page">
    <div class="page-head">
      <div>
        <div class="eyebrow"><ShieldCheck /> P0 AUTO CERTIFICATION</div>
        <h1>自动认证记录</h1>
        <p>驾驶证和行驶证材料齐全后由系统自动通过，后台保留全部认证记录与材料详情。</p>
      </div>
      <button class="btn" :disabled="currentQueue.loading" @click="refresh(activeModule, true)">
        <LoaderCircle v-if="currentQueue.loading" class="spin" /><RefreshCw v-else />刷新
      </button>
    </div>

    <section class="auto-cert-note">
      <BadgeCheck />
      <div><b>当前执行规则</b><p>驾驶证正反面与基础信息齐全后自动通过；记录、材料图片和通过时间会继续显示在后台。</p></div>
    </section>

    <section class="auto-cert-modules">
      <button v-for="item in modules" :key="item.key" class="auto-cert-module"
        :class="{ active: activeModule === item.key }" @click="switchModule(item.key)">
        <component :is="item.icon" />
        <span><b>{{ item.title }}</b><small>{{ item.description }}</small></span>
        <em>{{ queues[item.key].total }}</em>
      </button>
    </section>

    <div class="grid cols-3 auto-cert-metrics">
      <div class="metric"><small>自动通过</small><b>{{ currentQueue.stats.APPROVED || 0 }}</b></div>
      <div class="metric"><small>异常/拒绝</small><b>{{ currentQueue.stats.REJECTED || 0 }}</b></div>
      <div class="metric"><small>材料异常待处理</small><b>{{ currentQueue.stats.PENDING || 0 }}</b></div>
    </div>

    <section class="panel">
      <div class="toolbar">
        <div class="search-box"><Search /><input v-model="currentFilter.keyword" placeholder="用户、车牌或认证单号" @keyup.enter="loadQueue(activeModule, { resetPage: true })"></div>
        <select v-model="currentFilter.status" class="field" @change="loadQueue(activeModule, { resetPage: true })">
          <option value="">全部状态</option><option value="APPROVED">自动通过</option>
          <option value="REJECTED">异常/拒绝</option><option value="PENDING">材料异常待处理</option>
        </select>
        <button class="btn sm" @click="loadQueue(activeModule, { resetPage: true })">查询</button>
      </div>
      <div class="table-wrap">
        <table>
          <thead><tr><th>认证单</th><th>用户</th><th>材料</th><th>状态</th><th>提交时间</th><th>说明</th><th></th></tr></thead>
          <tbody>
            <tr v-for="row in currentQueue.rows" :key="row.certificationId">
              <td><b>#{{ row.certificationId }}</b></td>
              <td>{{ row.nickname || row.userId || '—' }}<br><small>{{ row.userId || '' }}</small></td>
              <td>{{ activeModule === 'vehicle' ? '行驶证' : '驾驶证' }}</td>
              <td><span class="status" :class="statusMeta(row.status).className">{{ statusMeta(row.status).label }}</span></td>
              <td>{{ formatTime(row.submittedAt || row.createdAt) }}</td>
              <td><small>{{ row.rejectReason || (row.status === 'APPROVED' ? '系统自动认证' : '—') }}</small></td>
              <td><button class="btn sm" @click="openDetail(row)">查看</button></td>
            </tr>
            <tr v-if="!currentQueue.loading && !currentQueue.rows.length"><td colspan="7"><div class="empty-note"><CircleAlert /><p>暂无记录</p></div></td></tr>
          </tbody>
        </table>
      </div>
      <div class="pagination">
        <span>共 {{ currentQueue.total }} 条</span>
        <div class="pages"><button class="btn sm" :disabled="currentQueue.page <= 1" @click="changePage(currentQueue.page - 1)">上一页</button><span>{{ currentQueue.page }} / {{ pageCount }}</span><button class="btn sm" :disabled="currentQueue.page >= pageCount" @click="changePage(currentQueue.page + 1)">下一页</button></div>
      </div>
    </section>

    <BaseModal :open="detailOpen" title="认证记录详情" size="wide" @close="detailOpen = false">
      <div v-if="detailLoading" class="empty-note"><LoaderCircle class="spin" /><p>正在加载</p></div>
      <template v-else-if="selected">
        <div class="detail-grid">
          <div class="detail-item"><small>认证单号</small><b>{{ selected.certificationId }}</b></div>
          <div class="detail-item"><small>用户</small><b>{{ selected.nickname || selected.userId }}</b></div>
          <div class="detail-item"><small>状态</small><b>{{ statusMeta(selected.status).label }}</b></div>
          <div class="detail-item"><small>认证方式</small><b>{{ selected.status === 'APPROVED' ? '系统自动认证' : '历史/异常记录' }}</b></div>
          <div class="detail-item"><small>车牌</small><b>{{ selected.plateNumber || selected.plateNoMask || '—' }}</b></div>
          <div class="detail-item"><small>时间</small><b>{{ formatTime(selected.reviewedAt || selected.submittedAt || selected.createdAt) }}</b></div>
        </div>
        <h3 class="material-title">{{ activeModule === 'vehicle' ? '行驶证材料' : '驾驶证材料' }}</h3>
        <div class="auto-cert-images">
          <a v-for="url in detailImages" :key="url" :href="url" target="_blank" rel="noopener"><img :src="url" alt="认证材料"></a>
          <div v-if="!detailImages.length" class="missing-image"><ImageOff /><span>暂无可展示材料</span></div>
        </div>
        <div v-if="selected.rejectReason" class="notice-box">异常说明：{{ selected.rejectReason }}</div>
      </template>
      <template #footer><button class="btn" @click="detailOpen = false">关闭</button></template>
    </BaseModal>
  </div>
</template>

<style scoped>
.eyebrow{display:flex;align-items:center;gap:7px;color:var(--primary);font-size:9px;font-weight:800;letter-spacing:.12em}.auto-cert-note{display:flex;gap:12px;align-items:flex-start;padding:14px 16px;margin-bottom:16px;border:1px solid #cfe5d9;border-radius:14px;background:#f0faf5;color:var(--success)}.auto-cert-note p{margin:4px 0 0;color:#567266;font-size:11px}.auto-cert-modules{display:grid;grid-template-columns:repeat(2,1fr);gap:12px;margin-bottom:16px}.auto-cert-module{display:grid;grid-template-columns:40px 1fr auto;align-items:center;gap:12px;padding:15px;border:1px solid var(--line);border-radius:15px;background:#fff;text-align:left;cursor:pointer}.auto-cert-module.active{border-color:#9eb3f7;background:var(--primary-soft)}.auto-cert-module>svg{color:var(--primary)}.auto-cert-module b,.auto-cert-module small{display:block}.auto-cert-module small{margin-top:4px;color:var(--muted);font-size:10px}.auto-cert-module em{font-style:normal;font-size:22px;font-weight:800}.auto-cert-metrics{margin-bottom:16px}.auto-cert-metrics .metric small,.auto-cert-metrics .metric b{display:block}.auto-cert-metrics .metric b{margin-top:8px;font-size:25px}.search-box{height:35px;min-width:300px;display:flex;align-items:center;gap:8px;padding:0 11px;border:1px solid #dfe3eb;border-radius:9px;background:#fff}.search-box input{width:100%;border:0;outline:0}.material-title{margin-top:20px}.auto-cert-images{display:grid;grid-template-columns:repeat(2,1fr);gap:12px}.auto-cert-images a,.missing-image{height:210px;border:1px solid var(--line);border-radius:13px;overflow:hidden;background:#f7f8fa}.auto-cert-images img{width:100%;height:100%;object-fit:contain}.missing-image{display:grid;place-items:center;color:var(--muted)}.missing-image span{display:block}.pages{align-items:center}.spin{animation:spin .8s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}
</style>

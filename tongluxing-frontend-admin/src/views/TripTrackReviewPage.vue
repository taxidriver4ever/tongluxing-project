<script setup>
import { computed, onMounted, ref } from 'vue'
import {
  AlertTriangle, CheckCircle2, LoaderCircle, MapPinned, RefreshCw,
  Route, ShieldAlert, UserRoundCheck, XCircle,
} from 'lucide-vue-next'
import BaseModal from '../components/BaseModal.vue'
import {
  getTripTrackReview, listTripTrackReviews, reviewTripTrack,
} from '../services/tripTrackReview.js'
import { showToast } from '../utils.js'

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const detailLoading = ref(false)
const submitting = ref(false)
const open = ref(false)
const detail = ref(null)
const filters = ref({ riskLevel: '', settlementStatus: 'MANUAL_REVIEW' })
const page = ref(1)
const size = 20
const form = ref({ decision: 'APPROVE_SYSTEM_DISTANCE', approvedDistanceMeters: '', reason: '' })

const summary = computed(() => detail.value?.summary || null)
const members = computed(() => detail.value?.members || [])
const anomalies = computed(() => detail.value?.anomalies || [])
const recentPoints = computed(() => (detail.value?.points || []).slice(-200).reverse())
const memberCount = computed(() => members.value.length)
const systemGrowth = computed(() => growthPoints(summary.value?.filteredDistanceMeters || 0))
const previewDistance = computed(() => form.value.decision === 'APPROVE_MODIFIED_DISTANCE'
  ? Math.max(0, Number(form.value.approvedDistanceMeters) || 0)
  : (summary.value?.filteredDistanceMeters || 0))
const previewGrowth = computed(() => form.value.decision === 'REJECT_GROWTH'
  ? 0 : growthPoints(previewDistance.value))
const totalGrowthPreview = computed(() => previewGrowth.value * memberCount.value)

function growthPoints(meters) {
  return Math.floor(Math.max(0, Number(meters) || 0) / 5000) * 10
}
function km(meters) {
  return `${((Number(meters) || 0) / 1000).toFixed(1)} km`
}
function riskClass(level) {
  if (level === 'HIGH') return 'danger'
  if (level === 'MEDIUM') return 'warning'
  return 'success'
}
function statusClass(status) {
  if (status === 'MANUAL_REVIEW' || status === 'PENDING') return 'warning'
  if (status === 'SETTLED') return 'success'
  if (status === 'REJECTED') return 'danger'
  return 'info'
}
function statusLabel(status) {
  return ({
    MANUAL_REVIEW: '待人工审核', PENDING: '待结算', SETTLED: '已结算', REJECTED: '已拒绝',
  })[status] || status || '—'
}
function roleLabel(row) {
  return row.userId === summary.value?.primaryUserId || row.joinStatus === 'OWNER' ? '队长' : '成员'
}
function locationText(row) {
  if (row.latestLongitude == null || row.latestLatitude == null) return '暂无位置'
  return `${Number(row.latestLongitude).toFixed(5)}, ${Number(row.latestLatitude).toFixed(5)}`
}
function requestId() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID()
  return `track-review-${Date.now()}-${Math.random().toString(16).slice(2, 10)}`
}

async function load() {
  loading.value = true
  try {
    const data = await listTripTrackReviews({
      ...filters.value, page: page.value, size,
    }) || {}
    rows.value = data.records || []
    total.value = Number(data.total) || 0
  } catch (error) {
    showToast(error.message, 'error')
  } finally {
    loading.value = false
  }
}
async function applyFilters() {
  page.value = 1
  await load()
}
async function showDetail(row) {
  open.value = true
  detail.value = null
  detailLoading.value = true
  form.value = { decision: 'APPROVE_SYSTEM_DISTANCE', approvedDistanceMeters: '', reason: '' }
  try {
    detail.value = await getTripTrackReview(row.tripId)
  } catch (error) {
    showToast(error.message, 'error')
    open.value = false
  } finally {
    detailLoading.value = false
  }
}
async function submitReview() {
  if (!summary.value) return
  if (!form.value.reason.trim()) {
    showToast('请填写审核原因', 'error')
    return
  }
  if (form.value.decision === 'APPROVE_MODIFIED_DISTANCE'
      && (form.value.approvedDistanceMeters === '' || Number(form.value.approvedDistanceMeters) < 0)) {
    showToast('请输入有效的认可里程', 'error')
    return
  }
  submitting.value = true
  try {
    await reviewTripTrack(summary.value.tripId, {
      decision: form.value.decision,
      approvedDistanceMeters: form.value.decision === 'APPROVE_MODIFIED_DISTANCE'
        ? Math.floor(Number(form.value.approvedDistanceMeters)) : null,
      reason: form.value.reason.trim(),
      requestId: requestId(),
    })
    showToast(form.value.decision === 'REJECT_GROWTH' ? '已拒绝本次成长值结算' : '轨迹审核完成，成长值已结算')
    open.value = false
    await load()
  } catch (error) {
    showToast(error.message, 'error')
  } finally {
    submitting.value = false
  }
}
async function previousPage() {
  if (page.value <= 1) return
  page.value -= 1
  await load()
}
async function nextPage() {
  if (page.value * size >= total.value) return
  page.value += 1
  await load()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <div class="eyebrow"><Route /> TRIP TRACK REVIEW</div>
        <h1>轨迹结算审核</h1>
        <p>只有队长记录完整轨迹；全队成长值均以队长认可里程为基准，每 5km 发放 10 点/人。</p>
      </div>
      <button class="btn" @click="load">
        <LoaderCircle v-if="loading" class="spin"/><RefreshCw v-else/>刷新
      </button>
    </div>

    <div class="notice-box">
      队长轨迹中断、没有到达终点 1km 范围、无轨迹或出现疑似瞬移/模拟定位/极端速度时进入人工审核。
      普通成员不保存轨迹历史，只展示最新位置快照用于脱队与失联核对。
    </div>

    <section class="panel" style="margin-top:16px">
      <div class="panel-head">
        <div><h2>行程审核队列</h2><p>优先展示待人工审核记录</p></div>
        <span class="endpoint">GET /v1/admin/trip-track-reviews</span>
      </div>
      <div class="toolbar">
        <select v-model="filters.settlementStatus" class="field" @change="applyFilters">
          <option value="MANUAL_REVIEW">待人工审核</option>
          <option value="PENDING">待结算</option>
          <option value="SETTLED">已结算</option>
          <option value="REJECTED">已拒绝</option>
          <option value="">全部状态</option>
        </select>
        <select v-model="filters.riskLevel" class="field" @change="applyFilters">
          <option value="">全部风险</option>
          <option value="LOW">低风险</option>
          <option value="MEDIUM">中风险</option>
          <option value="HIGH">高风险</option>
        </select>
        <span style="margin-left:auto;color:var(--muted);font-size:11px">共 {{ total }} 条</span>
      </div>
      <div v-if="!rows.length && !loading" class="empty-note"><CheckCircle2/><p>当前没有对应轨迹审核记录</p></div>
      <div v-else class="table-wrap">
        <table>
          <thead><tr><th>行程 / 队长</th><th>系统认可里程</th><th>轨迹点</th><th>中断 / 严重异常</th><th>风险</th><th>状态</th><th>审核原因</th><th>操作</th></tr></thead>
          <tbody>
            <tr v-for="row in rows" :key="row.tripId">
              <td><b>{{ row.tripTitle || `行程 ${row.tripId}` }}</b><small>{{ row.captainNickname || row.primaryUserId }} · {{ row.tripId }}</small></td>
              <td><b>{{ km(row.filteredDistanceMeters) }}</b><small>预计 {{ growthPoints(row.filteredDistanceMeters) }} 点/人</small></td>
              <td>{{ row.validPointCount || 0 }} / {{ row.totalPointCount || 0 }}</td>
              <td>{{ row.locationGapCount || 0 }} / {{ row.hardAnomalyCount || 0 }}</td>
              <td><span class="status" :class="riskClass(row.riskLevel)">{{ row.riskLevel || 'LOW' }} · {{ row.riskScore || 0 }}</span></td>
              <td><span class="status" :class="statusClass(row.settlementStatus)">{{ statusLabel(row.settlementStatus) }}</span></td>
              <td class="reason-cell">{{ row.reviewReason || '—' }}</td>
              <td><button class="btn sm" @click="showDetail(row)">{{ row.settlementStatus === 'MANUAL_REVIEW' ? '审核' : '查看' }}</button></td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="pagination">
        <span>第 {{ page }} 页 · 每页 {{ size }} 条</span>
        <div class="head-actions">
          <button class="btn sm" :disabled="page<=1" @click="previousPage">上一页</button>
          <button class="btn sm" :disabled="page*size>=total" @click="nextPage">下一页</button>
        </div>
      </div>
    </section>

    <BaseModal :open="open" :title="`轨迹审核 · ${summary?.tripTitle || summary?.tripId || ''}`" size="wide" @close="open=false">
      <div v-if="detailLoading" class="empty-note"><LoaderCircle class="spin"/><p>加载轨迹详情…</p></div>
      <template v-else-if="summary">
        <div class="grid cols-4 review-metrics">
          <div class="metric compact-review"><MapPinned/><span><small>系统认可里程</small><b>{{ km(summary.filteredDistanceMeters) }}</b></span></div>
          <div class="metric compact-review"><UserRoundCheck/><span><small>成员人数</small><b>{{ memberCount }}</b></span></div>
          <div class="metric compact-review"><ShieldAlert/><span><small>风险等级</small><b>{{ summary.riskLevel }} / {{ summary.riskScore || 0 }}</b></span></div>
          <div class="metric compact-review"><AlertTriangle/><span><small>中断 / 严重异常</small><b>{{ summary.locationGapCount || 0 }} / {{ summary.hardAnomalyCount || 0 }}</b></span></div>
        </div>

        <div class="notice-box" style="margin-top:14px">
          <b>进入人工审核原因：</b>{{ summary.reviewReason || '未记录原因' }}<br>
          当前系统里程可发 {{ systemGrowth }} 点/人；若按此通过，全队共 {{ systemGrowth * memberCount }} 点。
        </div>

        <section class="review-section">
          <h3>成员与最新位置</h3>
          <div class="table-wrap review-table">
            <table><thead><tr><th>成员</th><th>身份</th><th>队长轨迹里程</th><th>轨迹点</th><th>最新位置</th><th>最后定位</th><th>模拟定位</th></tr></thead>
              <tbody><tr v-for="member in members" :key="member.userId">
                <td><b>{{ member.nickname || member.userId }}</b><small>{{ member.userId }}</small></td>
                <td>{{ roleLabel(member) }}</td>
                <td>{{ roleLabel(member)==='队长' ? km(member.distanceMeters) : '不记录成员轨迹' }}</td>
                <td>{{ roleLabel(member)==='队长' ? `${member.validPointCount || 0}/${member.totalPointCount || 0}` : '—' }}</td>
                <td>{{ locationText(member) }}<small v-if="member.latestAccuracyMeters!=null">精度约 {{ member.latestAccuracyMeters }}m</small></td>
                <td>{{ member.latestLocationTime || '暂无' }}</td>
                <td><span v-if="member.mockLocation" class="status danger">疑似</span><span v-else class="status success">否</span></td>
              </tr></tbody>
            </table>
          </div>
        </section>

        <section class="review-section">
          <h3>异常事件</h3>
          <div v-if="!anomalies.length" class="review-empty">没有轨迹异常事件</div>
          <div v-else class="table-wrap review-table">
            <table><thead><tr><th>时间</th><th>类型</th><th>风险分</th><th>用户</th><th>详情</th></tr></thead>
              <tbody><tr v-for="row in anomalies" :key="row.id"><td>{{ row.occurredAt }}</td><td><span class="status danger">{{ row.anomalyType }}</span></td><td>{{ row.riskScore }}</td><td>{{ row.userId }}</td><td class="json-cell">{{ row.detailJson }}</td></tr></tbody>
            </table>
          </div>
        </section>

        <section class="review-section">
          <h3>队长最近轨迹点 <small>仅展示最新 200 个</small></h3>
          <div v-if="!recentPoints.length" class="review-empty">没有队长轨迹数据</div>
          <div v-else class="table-wrap review-table points-table">
            <table><thead><tr><th>时间</th><th>经纬度</th><th>速度</th><th>计入里程</th><th>状态</th><th>风险标记</th></tr></thead>
              <tbody><tr v-for="point in recentPoints" :key="point.id"><td>{{ point.locationTime }}</td><td>{{ point.longitude }}, {{ point.latitude }}</td><td>{{ point.calculatedSpeedKmh == null ? '—' : `${Number(point.calculatedSpeedKmh).toFixed(1)} km/h` }}</td><td>{{ point.acceptedDistanceMeters || 0 }}m</td><td>{{ point.pointStatus }}</td><td>{{ point.riskFlags || point.rejectReason || '—' }}</td></tr></tbody>
            </table>
          </div>
        </section>

        <section v-if="summary.settlementStatus==='MANUAL_REVIEW'" class="review-action">
          <h3>人工结算</h3>
          <div class="form-grid">
            <div class="form-group">
              <label>审核决定</label>
              <select v-model="form.decision" class="field">
                <option value="APPROVE_SYSTEM_DISTANCE">按系统认可里程通过</option>
                <option value="APPROVE_MODIFIED_DISTANCE">修改认可里程后通过</option>
                <option value="MARK_FALSE_POSITIVE">标记误报并按系统里程通过</option>
                <option value="REJECT_GROWTH">拒绝成长值</option>
              </select>
            </div>
            <div v-if="form.decision==='APPROVE_MODIFIED_DISTANCE'" class="form-group">
              <label>管理员认可里程（米）</label>
              <input v-model="form.approvedDistanceMeters" type="number" min="0" class="field" placeholder="例如 125000">
            </div>
            <div class="form-group full">
              <label>审核原因</label>
              <textarea v-model="form.reason" class="field review-reason" maxlength="255" placeholder="例如：队长中途杀后台，轨迹中断 18 分钟；结合前后位置及终点到达情况，认可实际记录里程。"></textarea>
            </div>
          </div>
          <div class="growth-preview">
            <CheckCircle2 v-if="form.decision!=='REJECT_GROWTH'"/><XCircle v-else/>
            <span><small>本次审核结果预览</small><b>{{ form.decision==='REJECT_GROWTH' ? '不发放成长值' : `${km(previewDistance)} → ${previewGrowth} 点/人 × ${memberCount} 人 = ${totalGrowthPreview} 点` }}</b></span>
          </div>
        </section>
      </template>
      <template #footer>
        <button class="btn" @click="open=false">关闭</button>
        <button v-if="summary?.settlementStatus==='MANUAL_REVIEW'" class="btn primary" :disabled="submitting" @click="submitReview">
          {{ submitting ? '提交中…' : '确认审核并结算' }}
        </button>
      </template>
    </BaseModal>
  </div>
</template>

<style scoped>
.reason-cell{max-width:260px;overflow:hidden;text-overflow:ellipsis}.review-metrics{gap:10px}.compact-review{padding:13px;display:flex;align-items:center;gap:10px}.compact-review>svg{color:var(--primary)}.compact-review small,.compact-review b{display:block}.compact-review small{font-size:9px;color:var(--muted);margin-bottom:4px}.compact-review b{font-size:13px}.review-section{margin-top:18px}.review-section h3,.review-action h3{font-size:13px;margin:0 0 9px}.review-section h3 small{font-size:9px;color:var(--muted);font-weight:400}.review-table{border:1px solid var(--line);border-radius:12px;max-height:280px}.review-table td{height:44px}.json-cell{max-width:380px;overflow:hidden;text-overflow:ellipsis}.points-table{max-height:300px}.review-empty{padding:22px;border:1px dashed var(--line);border-radius:12px;color:var(--muted);font-size:11px;text-align:center}.review-action{margin-top:18px;padding:16px;border:1px solid #dce4fb;background:#f8faff;border-radius:14px}.review-reason{height:80px!important}.growth-preview{margin-top:12px;display:flex;align-items:center;gap:10px;padding:11px 13px;border-radius:11px;background:#fff;border:1px solid var(--line)}.growth-preview>svg{color:var(--primary)}.growth-preview small,.growth-preview b{display:block}.growth-preview small{color:var(--muted);font-size:9px;margin-bottom:3px}.growth-preview b{font-size:12px}:deep(.modal-wide){width:min(1180px,96vw)}
</style>

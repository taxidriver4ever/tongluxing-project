<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { History, LoaderCircle, Play, Save, Ticket, TrendingUp, UserPlus } from 'lucide-vue-next'
import { getPlatformCoupons } from '../services/couponCenter.js'
import { getOperationRule, runMonthlyCouponGrant, updateOperationRule } from '../services/operationRules.js'
import { showToast } from '../utils.js'

const active = ref('growth')
const loading = ref(false)
const saving = ref(false)
const approvedCoupons = ref([])
const configs = reactive({
  growth: { label: '成长规则', icon: TrendingUp, domain: 'GROWTH', key: 'growth.rules', endpoint: '/v1/admin/growth-rules', version: 0, reason: '更新成长规则', json: '{\n  "tripCompleted": 100,\n  "mileagePointsPer100Km": 10\n}' },
  invite: { label: '邀请规则', icon: UserPlus, domain: 'INVITE', key: 'invite.rules', endpoint: '/v1/admin/invite-rules', version: 0, reason: '更新邀请规则', json: '{\n  "registerPoints": 100,\n  "firstTeamPoints": 100,\n  "qrRotationDays": 7\n}' },
  coupon: { label: '等级发券规则', icon: Ticket, domain: 'COUPON', key: 'coupon.monthly-level-grants', endpoint: '/v1/admin/coupon-issuance-rules', version: 0, reason: '更新月度等级优惠券权益', json: '', enabled: false, washTemplateId: '', maintenanceTemplateId: '', cityCodes: '', startDate: '', endDate: '', maxUsersPerRun: 100000, lv4Wash: 1, lv4Maintenance: 0, lv5Wash: 2, lv5Maintenance: 1, lv6Wash: 3, lv6Maintenance: 2 },
  history: { label: '版本记录', icon: History, domain: 'CONFIG', key: 'version.history', endpoint: '/v1/admin/audit-logs?targetModule=CONFIG', version: '-', reason: '', json: '' },
})
const config = computed(() => configs[active.value])

function couponRules() {
  return {
    enabled: Boolean(configs.coupon.enabled),
    washTemplateId: configs.coupon.washTemplateId || null,
    maintenanceTemplateId: configs.coupon.maintenanceTemplateId || null,
    cityCodes: configs.coupon.cityCodes.split(',').map(x => x.trim()).filter(Boolean),
    startDate: configs.coupon.startDate || null,
    endDate: configs.coupon.endDate || null,
    maxUsersPerRun: Number(configs.coupon.maxUsersPerRun || 100000),
    levels: {
      LV4: { wash: Number(configs.coupon.lv4Wash), maintenance: Number(configs.coupon.lv4Maintenance) },
      LV5: { wash: Number(configs.coupon.lv5Wash), maintenance: Number(configs.coupon.lv5Maintenance) },
      LV6: { wash: Number(configs.coupon.lv6Wash), maintenance: Number(configs.coupon.lv6Maintenance) },
    },
  }
}

function applyCouponJson(value) {
  const parsed = JSON.parse(value)
  configs.coupon.enabled = Boolean(parsed.enabled)
  configs.coupon.washTemplateId = parsed.washTemplateId == null ? '' : String(parsed.washTemplateId)
  configs.coupon.maintenanceTemplateId = parsed.maintenanceTemplateId == null ? '' : String(parsed.maintenanceTemplateId)
  configs.coupon.cityCodes = (parsed.cityCodes || []).join(',')
  configs.coupon.startDate = parsed.startDate || ''
  configs.coupon.endDate = parsed.endDate || ''
  configs.coupon.maxUsersPerRun = Number(parsed.maxUsersPerRun || 100000)
  for (const level of ['LV4','LV5','LV6']) {
    configs.coupon[`${level.toLowerCase()}Wash`] = Number(parsed.levels?.[level]?.wash || 0)
    configs.coupon[`${level.toLowerCase()}Maintenance`] = Number(parsed.levels?.[level]?.maintenance || 0)
  }
  configs.coupon.json = JSON.stringify(parsed, null, 2)
}

async function load() {
  if (active.value === 'history') return
  loading.value = true
  try {
    const result = await getOperationRule(config.value.endpoint)
    config.value.version = result.versionNo || 0
    config.value.json = result.configValue || config.value.json
    if (active.value === 'coupon') applyCouponJson(config.value.json)
  } catch (error) {
    if (error.code !== 404) showToast(error.message)
  } finally {
    loading.value = false
  }
}

async function loadCoupons() {
  try {
    approvedCoupons.value = await getPlatformCoupons('ACTIVE')
  } catch (error) {
    showToast(error.message)
  }
}

function validateJson() {
  try {
    JSON.parse(active.value === 'coupon' ? JSON.stringify(couponRules()) : config.value.json)
    showToast('JSON 格式校验通过')
    return true
  } catch {
    showToast('JSON 格式不正确，请检查后再发布')
    return false
  }
}

async function publish() {
  if (!validateJson()) return
  if (active.value === 'coupon' && configs.coupon.enabled
      && (!configs.coupon.washTemplateId || !configs.coupon.maintenanceTemplateId)) {
    showToast('启用规则前请选择洗车券和保养抵扣券')
    return
  }
  saving.value = true
  try {
    const value = active.value === 'coupon' ? JSON.stringify(couponRules()) : config.value.json
    const result = await updateOperationRule(config.value.endpoint, config.value.key, value, config.value.reason)
    config.value.version = result.versionNo
    config.value.json = result.configValue
    showToast(`${config.value.label} V${result.versionNo} 已发布`)
  } catch (error) {
    showToast(error.message)
  } finally {
    saving.value = false
  }
}

async function runNow() {
  try {
    const result = await runMonthlyCouponGrant('')
    showToast(`发券完成：新增 ${result.issued}，幂等跳过 ${result.duplicates}，失败 ${result.failed}`)
  } catch (error) {
    showToast(error.message)
  }
}

watch(active, load)
onMounted(() => { load(); loadCoupons() })
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div><h1>运营规则配置</h1><p>配置成长、邀请和合作商优惠券的月度自动发放规则</p></div>
      <div class="head-actions">
        <button v-if="active==='coupon'" class="btn" @click="runNow"><Play/>立即执行本月发券</button>
        <button v-if="active!=='history'" class="btn primary" :disabled="saving" @click="publish"><LoaderCircle v-if="saving" class="spin"/><Save v-else/>发布新版本</button>
      </div>
    </div>
    <div class="config-layout">
      <div class="panel config-nav">
        <div v-for="(item,id) in configs" :key="id" class="config-item" :class="{active:active===id}" @click="active=id"><component :is="item.icon"/>{{item.label}}</div>
      </div>
      <div class="panel">
        <div class="panel-head"><div><h2>{{config.label}} · {{config.key}}</h2><p>当前版本 V{{config.version}} · ACTIVE</p></div><span class="endpoint">GET {{config.endpoint}}</span></div>
        <div v-if="loading" class="empty-note"><LoaderCircle class="spin"/><p>正在读取当前配置</p></div>
        <div v-else-if="active==='history'" class="panel-body"><div class="notice-box">版本发布历史请在审计日志中按 CONFIG_UPDATE 查询。</div></div>
        <div v-else class="panel-body">
          <div class="notice-box">配置发布后生成新版本并写入审计日志。定时任务每月 1 日 03:15 执行，重复执行不会重复发券。</div>
          <div v-if="active==='coupon'" class="form-grid" style="margin-top:16px">
            <div class="form-group"><label>自动发券</label><select v-model="configs.coupon.enabled" class="field"><option :value="false">停用</option><option :value="true">启用</option></select></div>
            <div class="form-group"><label>洗车券模板</label><select v-model="configs.coupon.washTemplateId" class="field"><option value="">请选择券池中已启用的券</option><option v-for="item in approvedCoupons" :key="item.templateId" :value="String(item.templateId)">{{item.couponName}}（剩余 {{item.totalQuantity-item.claimedQuantity}}）</option></select></div>
            <div class="form-group"><label>保养抵扣券模板</label><select v-model="configs.coupon.maintenanceTemplateId" class="field"><option value="">请选择券池中已启用的券</option><option v-for="item in approvedCoupons" :key="item.templateId" :value="String(item.templateId)">{{item.couponName}}（剩余 {{item.totalQuantity-item.claimedQuantity}}）</option></select></div>
            <div class="form-group"><label>LV4 洗车券 / 保养券</label><div style="display:flex;gap:8px"><input v-model.number="configs.coupon.lv4Wash" class="field" type="number" min="0" max="20"><input v-model.number="configs.coupon.lv4Maintenance" class="field" type="number" min="0" max="20"></div></div>
            <div class="form-group"><label>LV5 洗车券 / 保养券</label><div style="display:flex;gap:8px"><input v-model.number="configs.coupon.lv5Wash" class="field" type="number" min="0" max="20"><input v-model.number="configs.coupon.lv5Maintenance" class="field" type="number" min="0" max="20"></div></div>
            <div class="form-group"><label>LV6 洗车券 / 保养券</label><div style="display:flex;gap:8px"><input v-model.number="configs.coupon.lv6Wash" class="field" type="number" min="0" max="20"><input v-model.number="configs.coupon.lv6Maintenance" class="field" type="number" min="0" max="20"></div></div>
            <div class="form-group"><label>地区城市编码（逗号分隔，留空为全国）</label><input v-model="configs.coupon.cityCodes" class="field" maxlength="500" placeholder="例如 440100,440300"></div>
            <div class="form-group"><label>规则开始日期</label><input v-model="configs.coupon.startDate" class="field" type="date"></div>
            <div class="form-group"><label>规则结束日期</label><input v-model="configs.coupon.endDate" class="field" type="date"></div>
            <div class="form-group"><label>单次最多覆盖用户数</label><input v-model.number="configs.coupon.maxUsersPerRun" class="field" type="number" min="1" max="1000000"></div>
            <div class="form-group full"><label>规则预览</label><textarea class="field" :value="JSON.stringify(couponRules(), null, 2)" readonly></textarea></div>
          </div>
          <div v-else class="form-grid" style="margin-top:16px"><div class="form-group full"><label>配置值（JSON）</label><textarea v-model="config.json" class="field" maxlength="8000"></textarea></div></div>
          <div class="form-group" style="margin-top:16px"><label>变更原因</label><input v-model="config.reason" class="field" maxlength="200"></div>
          <div style="display:flex;justify-content:flex-end;gap:8px;margin-top:16px"><button class="btn" @click="validateJson">JSON 校验</button><button class="btn primary" :disabled="saving" @click="publish">发布新版本</button></div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { CheckCircle2, CircleAlert, Clock3, HeartPulse, Network, Play, RadioTower, RefreshCw } from 'lucide-vue-next'
import BaseModal from '../components/BaseModal.vue'
import MetricCard from '../components/MetricCard.vue'
import { closeExpiredOrders, getBackendHealth, processAdminCompensation, processOrderCompensation, processVerificationCompensation } from '../services/internalInterfaces.js'
import { showToast } from '../utils.js'

const checking=ref(false), health=ref({status:'未检测',latency:0,checkedAt:''}), lastResult=ref('尚未执行')
const modal=ref(false), selected=ref(null), limit=ref(20), executing=ref(false)
const apis=[
  {name:'Admin 补偿任务',method:'POST',path:'/v1/admin/compensation-tasks/process?limit={limit}',module:'admin-module',description:'消费到期后台补偿任务，返回实际处理数量。',action:'admin'},
  {name:'订单过期关闭',method:'POST',path:'/v1/orders/internal/expired/close?limit={limit}',module:'order-module',description:'关闭超时 WAIT_PAY 订单。',action:'expired'},
  {name:'订单补偿任务',method:'POST',path:'/v1/orders/internal/compensation-tasks/process?limit={limit}',module:'order-module',description:'重试优惠券与订单状态同步任务。',action:'order'},
  {name:'核销补偿任务',method:'POST',path:'/v1/verifications/internal/compensation-tasks/process?limit={limit}',module:'verification-module',description:'重试核销后的分账与券状态同步。',action:'verification'},
  {name:'内部发券',method:'POST',path:'/internal/v1/coupons/issues',module:'coupon-module',description:'按 sourceType + sourceBizId 幂等发券，需要业务请求体。'},
  {name:'优惠券锁定',method:'POST',path:'/internal/v1/coupons/{id}/lock',module:'coupon-module',description:'订单创建时锁定用户优惠券，需要券 ID。'},
  {name:'优惠券订单结果',method:'POST',path:'/internal/v1/coupons/order-result',module:'coupon-module',description:'支付成功确认使用，失败或取消时释放。'},
  {name:'成长值发放',method:'POST',path:'/internal/v1/growth/grants',module:'growth-module',description:'以 bizType + bizId + userId 保证成长值幂等。'},
  {name:'首次组队奖励',method:'POST',path:'/internal/v1/invites/first-team-completed',module:'invite-module',description:'将邀请关系转为有效并触发奖励。'},
  {name:'通知事件',method:'POST',path:'/internal/v1/notifications/events',module:'notify-module',description:'业务事件统一创建站内通知。'},
  {name:'里程结算',method:'POST',path:'/internal/v1/mileage/settlements',module:'driver-track-module',description:'结束行程后结算有效里程与成长值。'},
  {name:'商家考核重算',method:'POST',path:'/internal/v1/assessments/merchants/{merchantId}/recalculate',module:'assessment-module',description:'按商家重新计算指定周期考核。'},
  {name:'月度商家考核',method:'POST',path:'/internal/v1/assessments/monthly/run',module:'assessment-module',description:'批量运行月度商家考核。'},
  {name:'商家等级快照',method:'GET',path:'/internal/v1/assessments/merchants/{merchantId}/snapshot',module:'assessment-module',description:'查询商家最新等级、健康值及权益快照。'},
]
const metrics=computed(()=>[
  ['已登记接口',String(apis.length),'覆盖 9 个业务模块',Network,false],
  ['后端健康',health.value.status,health.value.checkedAt?`${health.value.latency} ms · ${health.value.checkedAt}`:'点击开始检测',HeartPulse,health.value.status!=='UP'],
  ['可直接执行','4','均为无业务副作用的补偿入口',RefreshCw,false],
  ['最近执行',lastResult.value,'返回后端实际处理数量',RadioTower,false],
])

async function checkHealth(){checking.value=true;try{const result=await getBackendHealth();health.value={...result,checkedAt:new Date().toLocaleTimeString('zh-CN',{hour12:false})};showToast(`后端健康状态：${result.status}，响应 ${result.latency} ms`)}catch(error){health.value={status:'DOWN',latency:0,checkedAt:new Date().toLocaleTimeString('zh-CN',{hour12:false})};showToast(error.message,'error')}finally{checking.value=false}}
function open(api){selected.value=api;limit.value=20;modal.value=true}
async function execute(){executing.value=true;try{const handlers={admin:processAdminCompensation,expired:closeExpiredOrders,order:processOrderCompensation,verification:processVerificationCompensation};const count=await handlers[selected.value.action](Math.min(100,Math.max(1,Number(limit.value)||20)));lastResult.value=`${selected.value.name}：${count} 条`;modal.value=false;showToast(`${selected.value.name}执行完成，处理 ${count} 条`);await checkHealth()}catch(error){showToast(error.message,'error')}finally{executing.value=false}}
onMounted(checkHealth)
</script>

<template><div class="page"><div class="page-head"><div><h1>内部接口与健康中心</h1><p>完整登记跨模块接口，检查服务健康状态并安全触发补偿任务</p></div><div class="head-actions"><span class="status" :class="health.status==='UP'?'success':'danger'">{{health.status}}</span><button class="btn primary" :disabled="checking" @click="checkHealth"><RefreshCw :class="{spin:checking}"/>{{checking?'检测中…':'测试健康状态'}}</button></div></div>
  <div class="grid cols-4"><MetricCard v-for="m in metrics" :key="m[0]" :label="m[0]" :value="m[1]" :trend="m[2]" :icon="m[3]" :down="m[4]" /></div>
  <div class="panel" style="margin-top:16px"><div class="panel-head"><div><h2>内部接口登记表</h2><p>绿色接口可直接执行；其余接口需要具体业务 ID 或请求体，避免空参数误操作</p></div><span class="endpoint">GET /actuator/health</span></div><div class="grid cols-3" style="padding:16px"><div v-for="api in apis" :key="api.path" class="api-card"><div class="api-card-top"><div><span class="method" :class="api.method.toLowerCase()">{{api.method}}</span> <b>{{api.name}}</b></div><span class="health" :class="{warning:health.status!=='UP'}"></span></div><div class="api-path">{{api.path}}</div><p>{{api.description}}</p><div class="internal-card-foot"><span>{{api.module}}</span><button v-if="api.action" class="btn sm success" @click="open(api)"><Play/>执行</button><span v-else class="status info">需业务参数</span></div></div></div></div>
  <div class="panel" style="margin-top:16px"><div class="panel-head"><div><h2>健康检查结果</h2><p>检测 Spring Boot Actuator、接口连通性和响应延迟</p></div></div><div class="panel-body health-detail"><component :is="health.status==='UP'?CheckCircle2:CircleAlert" :class="health.status==='UP'?'health-ok':'health-error'"/><div><b>{{health.status==='UP'?'服务运行正常':'服务暂不可用或尚未检测'}}</b><p>检测地址：<code>/api/actuator/health</code></p></div><div><Clock3/><span>响应时间</span><b>{{health.latency||'—'}} ms</b></div><div><RefreshCw/><span>检测时间</span><b>{{health.checkedAt||'—'}}</b></div></div></div>
  <BaseModal :open="modal" :title="`执行 · ${selected?.name||''}`" @close="modal=false"><div class="notice-box">该操作会调用真实后端接口，但仅处理已经到期或符合条件的任务；返回值是实际处理数量。</div><div v-if="selected" class="code-note" style="margin-top:12px">{{selected.method}} {{selected.path.replace('{limit}',limit)}}</div><div class="form-group" style="margin-top:14px"><label>单次处理上限 limit（1～100）</label><input v-model="limit" class="field" type="number" min="1" max="100"></div><template #footer><button class="btn" @click="modal=false">取消</button><button class="btn primary" :disabled="executing" @click="execute">{{executing?'执行中…':'确认执行'}}</button></template></BaseModal>
</div></template>

<style scoped>
.internal-card-foot{display:flex;align-items:center;justify-content:space-between;margin-top:13px;padding-top:11px;border-top:1px dashed var(--line);color:var(--muted);font-size:9px}.internal-card-foot .lucide{width:12px}.health-detail{display:grid;grid-template-columns:auto 1fr auto auto;align-items:center;gap:18px}.health-detail>svg{width:38px;height:38px}.health-detail .health-ok{color:var(--success)}.health-detail .health-error{color:var(--danger)}.health-detail p{margin:5px 0 0;color:var(--muted);font-size:10px}.health-detail>div:nth-last-child(-n+2){display:grid;grid-template-columns:auto auto;gap:3px 7px;align-items:center;padding:10px 14px;border-radius:11px;background:#f7f8fa}.health-detail>div:nth-last-child(-n+2) svg{grid-row:1/3;width:17px;color:var(--primary)}.health-detail>div:nth-last-child(-n+2) span{font-size:9px;color:var(--muted)}.health-detail>div:nth-last-child(-n+2) b{font-size:11px}
</style>

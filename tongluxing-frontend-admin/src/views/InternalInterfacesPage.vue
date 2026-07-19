<script setup>
import { ref } from 'vue'
import { HeartPulse, Network, RadioTower, RefreshCw } from 'lucide-vue-next'
import BaseModal from '../components/BaseModal.vue'
import MetricCard from '../components/MetricCard.vue'
import { showToast } from '../utils.js'

const modal=ref(false), limit=ref(20)
const metrics=[['内部接口','12','全部已登记',Network,false],['健康模块','9 / 10','groupbuy 待接入',HeartPulse,true],['待补偿','2','下一次 15:00',RefreshCw,false],['今日事件','1,842','成功率 99.92%',RadioTower,false]]
const apis=[
  ['Admin 补偿任务','POST /v1/admin/compensation-tasks/process?limit=20','消费到期后台补偿任务，返回实际处理数量。'],
  ['订单过期关闭','POST /v1/orders/internal/expired/close?limit=100','关闭超时 WAIT_PAY 订单。'],
  ['订单补偿','POST /v1/orders/internal/compensation-tasks/process','处理订单优惠券、支付状态等补偿任务。'],
  ['核销补偿','POST /v1/verifications/internal/compensation-tasks/process','同步核销后的分账与券状态。'],
  ['内部发券','POST /internal/v1/coupons/issues','以 sourceType + sourceBizId 保证发券幂等。'],
  ['优惠券锁定','POST /internal/v1/coupons/{id}/lock','订单创建或支付前锁定优惠券。'],
  ['优惠券订单结果','POST /internal/v1/coupons/order-result','支付成功确认使用，失败则释放。'],
  ['成长值发放','POST /internal/v1/growth/grants','bizType + bizId + userId 作为幂等依据。'],
  ['邀请首次组队','POST /internal/v1/invites/first-team-completed','转有效邀请关系并触发奖励。'],
  ['通知事件','POST /internal/v1/notifications/events','业务事件统一创建站内通知。'],
  ['里程结算','POST /internal/v1/mileage/settlements','结束行程后结算有效里程。'],
  ['商家考核','POST /internal/v1/assessments/monthly/run','批量运行月度商家考核，可按商家重算。'],
]
</script>

<template><div class="page"><div class="page-head"><div><h1>内部接口与补偿中心</h1><p>跨模块事件、补偿任务与运营手动触发入口</p></div><div class="head-actions"><button class="btn" @click="showToast('健康状态已刷新')">刷新健康状态</button><button class="btn primary" @click="modal=true">执行补偿任务</button></div></div>
  <div class="grid cols-4"><MetricCard v-for="m in metrics" :key="m[0]" :label="m[0]" :value="m[1]" :trend="m[2]" :icon="m[3]" :down="m[4]" /></div>
  <div class="grid cols-3" style="margin-top:16px"><div v-for="api in apis" :key="api[0]" class="api-card"><div class="api-card-top"><b>{{api[0]}}</b><span class="health"></span></div><div class="api-path">{{api[1]}}</div><p>{{api[2]}}</p></div></div>
  <div class="panel" style="margin-top:16px"><div class="panel-head"><div><h2>最近补偿执行</h2><p>跨模块异步处理状态</p></div></div><div class="table-wrap"><table><thead><tr><th>任务 ID</th><th>目标模块</th><th>业务类型</th><th>动作</th><th>重试</th><th>下次执行</th><th>状态</th></tr></thead><tbody><tr><td>CT-88231</td><td>groupbuy-module</td><td>ACTIVITY / 108</td><td>FORCE_SUCCESS</td><td>0 / 5</td><td>14:45</td><td><span class="status info">等待处理</span></td></tr><tr><td>CT-88218</td><td>payment-module</td><td>REFUND / 031</td><td>APPROVED</td><td>1 / 5</td><td>14:48</td><td><span class="status warning">重试中</span></td></tr><tr><td>CT-88196</td><td>vehicle-module</td><td>CERTIFICATION / 819</td><td>APPROVED</td><td>0 / 5</td><td>-</td><td><span class="status success">已完成</span></td></tr></tbody></table></div></div>
  <BaseModal :open="modal" title="处理 Admin 补偿任务" @close="modal=false"><div class="notice-box">该入口对应已存在的 AdminCompensationTaskController。请限制单次处理数量，避免人工操作造成下游压力。</div><div class="form-group" style="margin-top:14px"><label>本次处理上限 limit</label><input v-model="limit" class="field" type="number" min="1" max="100"></div><div class="code-note" style="margin-top:12px">POST /v1/admin/compensation-tasks/process?limit={{limit}}</div><template #footer><button class="btn" @click="modal=false">取消</button><button class="btn primary" @click="modal=false;showToast('补偿任务处理已触发（原型）')">确认执行</button></template></BaseModal>
</div></template>

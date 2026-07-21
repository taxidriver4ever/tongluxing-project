<script setup>
import { computed, onMounted, ref } from 'vue'
import { Compass, Eye, MousePointerClick, Route, UsersRound } from 'lucide-vue-next'
import MetricCard from '../components/MetricCard.vue'
import { getMatchDashboard } from '../services/matchRecommendations.js'
import { showToast } from '../utils.js'

const loading=ref(false), data=ref({overview:{},funnel:[],records:[],popularRoutes:[],anomalies:[],rule:{}})
const overview=computed(()=>data.value.overview||{})
const ratio=(a,b)=>Number(b||0)?`${(Number(a||0)*100/Number(b)).toFixed(1)}%`:'0.0%'
const time=value=>value?String(value).replace('T',' ').slice(0,19):'—'
const actionName=value=>({IMPRESSION:'曝光',CLICK:'点击详情',APPLY:'提交申请',ACCEPT:'审核通过',START:'开始行程',FINISH:'完成行程',REJECT:'审核拒绝'}[value]||value)
const funnel=computed(()=>{const map=Object.fromEntries((data.value.funnel||[]).map(v=>[v.actionType,Number(v.eventCount||0)]));return ['IMPRESSION','CLICK','APPLY','ACCEPT','START','FINISH'].map((key,index)=>({key,label:actionName(key),value:map[key]||0,rate:index===0?'100%':ratio(map[key],map.IMPRESSION)}))})
const metrics=computed(()=>[
  ['有效推荐',String(overview.value.recommendationCount||0),`平均分 ${overview.value.averageScore||0}`,Compass,false],
  ['推荐曝光',String(overview.value.impressions||0),'真实 IMPRESSION 事件',Eye,false],
  ['点击率',ratio(overview.value.clicks,overview.value.impressions),`${overview.value.clicks||0} 次详情点击`,MousePointerClick,false],
  ['申请转化',ratio(overview.value.applications,overview.value.clicks),`${overview.value.applications||0} 次入队申请`,UsersRound,false],
])
async function load(){loading.value=true;try{data.value=await getMatchDashboard(80)}catch(error){showToast(error.message,'error')}finally{loading.value=false}}
onMounted(load)
</script>

<template><div class="page">
  <div class="page-head"><div><h1>同行推荐监控</h1><p>监控“发布行程 → 推荐曝光 → 点击 → 入队 → 开始/完成”的真实转化链路</p></div><div class="head-actions"><button class="btn primary" :disabled="loading" @click="load">{{loading?'刷新中…':'刷新数据'}}</button></div></div>
  <div class="grid cols-4"><MetricCard v-for="m in metrics" :key="m[0]" :label="m[0]" :value="m[1]" :trend="m[2]" :icon="m[3]" :down="m[4]" /></div>
  <div class="grid cols-2" style="margin-top:16px">
    <section class="panel"><div class="panel-head"><div><h3>推荐转化漏斗</h3><p>同一事件事实表按行为类型汇总</p></div></div><div class="match-funnel"><div v-for="(step,index) in funnel" :key="step.key" class="funnel-row"><span class="funnel-index">{{index+1}}</span><b>{{step.label}}</b><div class="funnel-track"><i :style="{width:`${Math.max(4,Number(step.rate.replace('%','')))}%`}"></i></div><strong>{{step.value}}</strong><small>{{step.rate}}</small></div></div></section>
    <section class="panel"><div class="panel-head"><div><h3>当前匹配规则</h3><p>规则匹配 MVP，不使用黑盒 AI</p></div><span class="status success">运行中</span></div><div class="rule-grid"><div><b>{{data.rule.routeWeight||40}}%</b><span>路线相似</span></div><div><b>{{data.rule.destinationWeight||30}}%</b><span>目的地</span></div><div><b>{{data.rule.timeWeight||20}}%</b><span>出发时间</span></div><div><b>{{data.rule.interestWeight||10}}%</b><span>同行偏好</span></div></div><div class="footer-note">时间窗口 ≤ {{data.rule.maxTimeGapHours||24}} 小时；{{data.rule.idealScore||80}} 分以上标记为理想匹配。</div></section>
  </div>
  <div class="grid cols-2" style="margin-top:16px">
    <section class="panel"><div class="panel-head"><div><h3>热门同行路线</h3><p>按推荐覆盖量排序</p></div><Route :size="20" /></div><div class="table-wrap"><table><thead><tr><th>路线</th><th>推荐</th><th>均分</th><th>点击</th><th>申请</th></tr></thead><tbody><tr v-for="row in data.popularRoutes" :key="row.routeName"><td><b>{{row.routeName}}</b></td><td>{{row.recommendationCount}}</td><td>{{row.averageScore}}</td><td>{{row.clicks||0}}</td><td>{{row.applications||0}}</td></tr></tbody></table><div v-if="!data.popularRoutes?.length" class="footer-note">发布两条不同用户的公开行程后自动形成排行</div></div></section>
    <section class="panel"><div class="panel-head"><div><h3>异常提示</h3><p>发现高曝光低点击推荐</p></div><span class="status" :class="data.anomalies?.length?'danger':'success'">{{data.anomalies?.length||0}} 条</span></div><div v-if="!data.anomalies?.length" class="healthy-box">当前未发现推荐质量异常</div><div v-for="row in data.anomalies" :key="row.targetTripId" class="anomaly-row"><b>行程 {{row.targetTripId}}</b><span>{{row.impressions}} 次曝光 / 0 点击</span></div></section>
  </div>
  <section class="panel" style="margin-top:16px"><div class="panel-head"><div><h3>推荐结果明细</h3><p>结果在行程发布时生成并持久化，不在浏览时临时计算</p></div><span class="endpoint">{{data.records?.length||0}} 条</span></div><div class="table-wrap"><table><thead><tr><th>推荐 ID</th><th>来源行程</th><th>推荐行程</th><th>匹配分</th><th>时间差</th><th>生成时间</th><th>状态</th></tr></thead><tbody><tr v-for="row in data.records" :key="row.matchId"><td>{{row.matchId}}</td><td>{{row.sourceRoute}}</td><td>{{row.targetRoute}}</td><td><b class="score-text">{{row.matchScore}}%</b></td><td>{{Math.round(Number(row.departureGapMinutes||0)/60*10)/10}} 小时</td><td>{{time(row.calculatedAt)}}</td><td><span class="status success">{{row.status}}</span></td></tr></tbody></table><div v-if="!data.records?.length" class="footer-note">暂无推荐结果，新的公开行程发布后会自动生成</div></div></section>
</div></template>

<style scoped>
.match-funnel{display:grid;gap:14px;padding:8px 0}.funnel-row{display:grid;grid-template-columns:28px 72px 1fr 46px 48px;align-items:center;gap:10px}.funnel-index{width:24px;height:24px;border-radius:50%;display:grid;place-items:center;background:#edf2ff;color:#285cff;font-size:12px;font-weight:700}.funnel-track{height:8px;background:#edf0f6;border-radius:10px;overflow:hidden}.funnel-track i{display:block;height:100%;background:linear-gradient(90deg,#285cff,#6d8cff);border-radius:10px}.funnel-row small{color:#768096;text-align:right}.rule-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:10px}.rule-grid div{padding:18px 10px;background:#f6f8fc;border-radius:14px;text-align:center}.rule-grid b{display:block;color:#285cff;font-size:22px}.rule-grid span{display:block;margin-top:5px;color:#768096;font-size:12px}.healthy-box{margin-top:8px;padding:34px;text-align:center;border-radius:16px;background:#effaf4;color:#16855b}.anomaly-row{display:flex;justify-content:space-between;padding:14px;background:#fff4f3;border-radius:12px;margin-top:8px;color:#b54136}.score-text{color:#285cff}
</style>

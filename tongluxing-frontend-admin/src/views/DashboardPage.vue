<script setup>
import { RouterLink } from 'vue-router'
import { CarFront, CircleDollarSign, Landmark, ReceiptText, ScanLine, Store, Users, UsersRound } from 'lucide-vue-next'
import MetricCard from '../components/MetricCard.vue'
import { showToast } from '../utils.js'

const metrics = [
  ['注册用户','18,620','↑ 12.6% 较上月',Users],['已认证车辆','6,842','↑ 8.4% 较上月',CarFront],
  ['累计订单','32,418','↑ 18.2% 较上月',ReceiptText],['支付金额','¥1.26M','↑ 9.7% 较上月',CircleDollarSign],
  ['入驻商家','286','↑ 21 家',Store],['拼团活动','1,204','↑ 15.1%',UsersRound],
  ['核销次数','20,936','核销率 64.6%',ScanLine],['平台佣金','¥82.4K','↑ 6.3%',Landmark],
]
const bars = [[28,19,16],[42,31,24],[38,26,30],[55,39,35],[48,35,39],[66,44,51],[61,47,43],[79,54,64],[88,63,71]]
const dates = ['07/05','07/06','07/07','07/08','07/09','07/10','07/11','07/12','07/13']
</script>

<template><div class="page">
  <div class="page-head"><div><h1>运营总览</h1><p>核心业务数据、增长趋势与待处理事项</p></div><div class="head-actions"><span class="endpoint">GET /v1/admin/operation-overview</span><button class="btn" @click="showToast('数据已刷新')">刷新数据</button><button class="btn primary" @click="showToast('报表导出任务已创建（原型）')">导出报表</button></div></div>
  <section class="grid cols-4"><MetricCard v-for="m in metrics.slice(0,4)" :key="m[0]" :label="m[0]" :value="m[1]" :trend="m[2]" :icon="m[3]" /></section>
  <section class="grid cols-4" style="margin-top:16px"><MetricCard v-for="m in metrics.slice(4)" :key="m[0]" :label="m[0]" :value="m[1]" :trend="m[2]" :icon="m[3]" /></section>
  <section class="grid" style="grid-template-columns:2fr 1fr;margin-top:16px">
    <div class="panel"><div class="panel-head"><div><h2>近 9 日业务趋势</h2><p>注册用户、订单与核销量</p></div><div class="legend"><span><i style="background:#285cff"></i>订单</span><span><i style="background:#8da7ff"></i>用户</span><span><i style="background:#f4b64c"></i>核销</span></div></div><div class="panel-body"><div class="chart"><div class="chart-bars"><div v-for="(values,i) in bars" :key="dates[i]" class="bar-group"><b class="bar" :style="{height:values[0]+'%'}"></b><b class="bar alt" :style="{height:values[1]+'%'}"></b><b class="bar warn" :style="{height:values[2]+'%'}"></b><span class="bar-label">{{ dates[i] }}</span></div></div></div></div></div>
    <div class="panel"><div class="panel-head"><div><h2>运营构成</h2><p>核心实体占比</p></div></div><div class="panel-body"><div class="donut"></div><div class="rank-list"><div class="rank-row"><span class="rank-no">1</span><span>注册用户<div class="progress"><span style="width:88%"></span></div></span><b>18.6K</b></div><div class="rank-row"><span class="rank-no">2</span><span>已认证车辆<div class="progress"><span style="width:58%"></span></div></span><b>6.8K</b></div><div class="rank-row"><span class="rank-no">3</span><span>发券数量<div class="progress"><span style="width:46%"></span></div></span><b>5.2K</b></div></div></div></div>
  </section>
  <section class="split" style="margin-top:16px"><div class="panel"><div class="panel-head"><div><h2>待处理事项</h2><p>审核与异常业务队列</p></div><RouterLink class="btn sm" to="/certification-audit">查看全部</RouterLink></div><div class="table-wrap"><table><thead><tr><th>事项</th><th>来源模块</th><th>数量</th><th>最长等待</th><th>状态</th></tr></thead><tbody><tr><td>驾驶证认证审核</td><td>user-module</td><td>7</td><td>42 分钟</td><td><span class="status warning">待处理</span></td></tr><tr><td>车辆认证审核</td><td>vehicle-module</td><td>5</td><td>1.3 小时</td><td><span class="status warning">待处理</span></td></tr><tr><td>退款审核</td><td>payment-module</td><td>3</td><td>18 分钟</td><td><span class="status info">处理中</span></td></tr><tr><td>补偿任务重试</td><td>admin-module</td><td>2</td><td>8 分钟</td><td><span class="status danger">异常</span></td></tr></tbody></table></div></div><div class="panel"><div class="panel-head"><h2>接口状态</h2><RouterLink class="btn sm" to="/internal-interfaces">接口中心</RouterLink></div><div class="side-card mini-list"><div class="mini-row"><span>Admin 查询</span><span class="status success">正常</span></div><div class="mini-row"><span>订单补偿</span><span class="status success">正常</span></div><div class="mini-row"><span>优惠券内部事件</span><span class="status success">正常</span></div><div class="mini-row"><span>拼团查询端口</span><span class="status warning">待接入</span></div></div></div></section>
  <div class="footer-note">数据字段对齐 AdminOperationOverviewVO · 当前页面为 Vue 静态交互原型</div>
</div></template>

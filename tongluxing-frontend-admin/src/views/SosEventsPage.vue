<script setup>
import { onMounted, ref } from 'vue'
import { CheckCircle2, ExternalLink, LoaderCircle, MapPin, RefreshCw, Siren } from 'lucide-vue-next'
import { acceptSosEvent, listSosEvents, resolveSosEvent } from '../services/sosEvents.js'
import { showToast } from '../utils.js'
const rows=ref([]), status=ref(''), loading=ref(false), processing=ref('')
const labels={PENDING:['待受理','danger'],PROCESSING:['处理中','warn'],RESOLVED:['已结案','success']}
async function load(){loading.value=true;try{rows.value=await listSosEvents(status.value)||[]}catch(e){showToast(e.message,'error')}finally{loading.value=false}}
async function accept(row){processing.value=row.id;try{await acceptSosEvent(row.id);showToast('SOS 已受理');await load()}catch(e){showToast(e.message,'error')}finally{processing.value=''}}
async function resolve(row){const note=window.prompt('请输入结案说明（请勿填写无法核实的信息）','已电话确认用户安全，平台处置完成');if(!note?.trim())return;processing.value=row.id;try{await resolveSosEvent(row.id,note.trim());showToast('SOS 已结案');await load()}catch(e){showToast(e.message,'error')}finally{processing.value=''}}
const amap=row=>`https://uri.amap.com/marker?position=${row.longitude},${row.latitude}&name=${encodeURIComponent(row.address)}`
onMounted(load)
</script>
<template><div class="page">
  <div class="page-head"><div><div class="eyebrow"><Siren/> EMERGENCY OPERATIONS</div><h1>SOS 事件</h1><p>平台内位置上报、运营受理与结案。外部报警仍为 Mock，页面不得展示为已联系警方。</p></div><button class="btn" @click="load"><LoaderCircle v-if="loading" class="spin"/><RefreshCw v-else/>刷新</button></div>
  <section class="panel"><div class="panel-head"><div><h2>事件队列</h2><p>PENDING → PROCESSING → RESOLVED</p></div><span class="endpoint">GET /v1/admin/sos/events</span></div>
    <div class="toolbar"><select v-model="status" class="field" @change="load"><option value="">全部状态</option><option value="PENDING">待受理</option><option value="PROCESSING">处理中</option><option value="RESOLVED">已结案</option></select></div>
    <div v-if="!rows.length" class="empty-note"><CheckCircle2/><p>当前筛选下没有 SOS 事件</p></div>
    <div v-else class="table-wrap"><table><thead><tr><th>事件 / 用户</th><th>发生位置</th><th>求助说明</th><th>状态</th><th>时间</th><th>操作</th></tr></thead><tbody>
      <tr v-for="row in rows" :key="row.id"><td><b>#{{row.id}}</b><small>用户 {{row.userId}} · {{row.alarmMode}}</small></td>
        <td><b><MapPin :size="15"/> {{row.address}}</b><small>{{row.latitude}}, {{row.longitude}}</small><a :href="amap(row)" target="_blank" rel="noopener"><ExternalLink :size="13"/> 高德查看位置</a></td>
        <td>{{row.message||'未填写'}}</td><td><span class="status" :class="labels[row.status]?.[1]">{{labels[row.status]?.[0]||row.status}}</span></td>
        <td>{{row.occurredAt}}<small v-if="row.acceptedAt">受理 {{row.acceptedAt}}</small></td>
        <td><button v-if="row.status==='PENDING'" class="btn sm primary" :disabled="processing===row.id" @click="accept(row)">受理</button>
          <button v-if="row.status!=='RESOLVED'" class="btn sm success solid" :disabled="processing===row.id" @click="resolve(row)">结案</button><small v-else>{{row.resolutionNote}}</small></td>
      </tr></tbody></table></div>
  </section>
</div></template>

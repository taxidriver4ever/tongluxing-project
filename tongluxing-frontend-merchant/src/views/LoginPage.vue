<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowRight, Eye, EyeOff, LockKeyhole, Route, ShieldCheck, Smartphone, Store } from 'lucide-vue-next'
import { loginMerchant } from '../services/auth.js'
const route = useRoute(); const router = useRouter(); const loading = ref(false); const error = ref(''); const showPassword = ref(false)
const form = reactive({ phone: '', password: '', remember: true })
async function submit() {
  error.value = ''
  if (!/^1[3-9]\d{9}$/.test(form.phone)) return error.value = '请输入正确的 11 位手机号'
  if (!form.password) return error.value = '请输入登录密码'
  loading.value = true
  try { await loginMerchant(form); await router.replace(String(route.query.redirect || '/')) }
  catch (e) { error.value = e.message }
  finally { loading.value = false }
}
</script>
<template>
  <div class="login-shell">
    <section class="login-story"><div class="login-brand"><span><Route /></span><div><b>同路行商家中心</b><small>TONG LUXING PARTNER</small></div></div><div class="story-copy"><small>连接自驾出行与本地服务</small><h1>让好服务，出现在用户的每一段旅程里。</h1><p>统一管理门店和出行优惠，审核通过后即可进入同路行商业合作伙伴生态。</p><div class="story-points"><span><Store />门店经营</span><span><ShieldCheck />平台审核</span><span><ArrowRight />用户转化</span></div></div><div class="story-foot">账号体系与同路行 App 统一 · 不单独创建商家密码</div></section>
    <section class="login-panel"><div class="login-card"><span class="card-icon"><LockKeyhole /></span><small class="kicker">MERCHANT SIGN IN</small><h2>登录商家后台</h2><p>请使用已通过商家审核的同路行手机号与密码。</p><form @submit.prevent="submit"><label><span>手机号</span><div class="input-wrap"><Smartphone /><input v-model.trim="form.phone" inputmode="numeric" maxlength="11" autocomplete="username" placeholder="请输入同路行账号手机号"></div></label><label><span>密码</span><div class="input-wrap"><LockKeyhole /><input v-model="form.password" :type="showPassword ? 'text' : 'password'" autocomplete="current-password" placeholder="请输入密码"><button type="button" class="plain-icon" @click="showPassword = !showPassword"><EyeOff v-if="showPassword" /><Eye v-else /></button></div></label><div class="login-meta"><label><input v-model="form.remember" type="checkbox"> 记住登录状态</label><span>未入驻？请在 App「我的-成为商家」提交资料</span></div><div v-if="error" class="form-error">{{ error }}</div><button class="login-submit" :disabled="loading">{{ loading ? '正在校验商家权限…' : '进入商家中心' }}<ArrowRight /></button></form></div></section>
  </div>
</template>

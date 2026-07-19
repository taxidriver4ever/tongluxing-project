<script setup>
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowRight, Eye, EyeOff, KeyRound, LoaderCircle, LockKeyhole, Route,
  ShieldAlert, ShieldCheck, UserRound,
} from 'lucide-vue-next'
import { loginAdmin } from '../services/adminAuth.js'

const route = useRoute()
const router = useRouter()
const form = reactive({ username: 'admin', password: 'Admin@123456', remember: true })
const showPassword = ref(false)
const loading = ref(false)
const errorMessage = ref('')
const locked = computed(() => errorMessage.value.includes('锁定') || errorMessage.value.includes('5 次'))

async function submit() {
  errorMessage.value = ''
  if (!form.username.trim() || !form.password) {
    errorMessage.value = '请输入管理员账号和密码'
    return
  }
  loading.value = true
  try {
    await loginAdmin(form)
    const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/')
      ? route.query.redirect : '/'
    await router.replace(redirect)
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="admin-login-shell">
    <section class="admin-login-story">
      <div class="admin-login-brand">
        <span><Route /></span>
        <b>同路行 Admin<small>OPERATION CONSOLE</small></b>
      </div>
      <div class="admin-login-story-copy">
        <span class="admin-login-kicker">PLATFORM OPERATIONS</span>
        <h1>让每一次出发<br>都有可靠的平台保障</h1>
        <p>统一处理认证审核、运营规则、业务异常与平台审计。</p>
      </div>
      <div class="admin-login-story-status">
        <span><ShieldCheck /></span>
        <div><b>独立后台会话</b><small>与微信 / App 用户登录态完全隔离</small></div>
      </div>
    </section>

    <section class="admin-login-panel">
      <form class="admin-login-card" @submit.prevent="submit">
        <div class="admin-login-card-icon"><LockKeyhole /></div>
        <span class="admin-login-card-kicker">管理员身份验证</span>
        <h2>欢迎回来</h2>
        <p>请使用平台运营账号登录。</p>

        <div v-if="errorMessage" class="admin-login-error" :class="{ locked }" role="alert">
          <ShieldAlert /><span>{{ errorMessage }}</span>
        </div>

        <div class="admin-login-form">
          <label>
            <span>账号</span>
            <div><UserRound /><input v-model="form.username" autocomplete="username" maxlength="64" placeholder="请输入管理员账号"></div>
          </label>
          <label>
            <span>密码</span>
            <div>
              <KeyRound />
              <input v-model="form.password" :type="showPassword ? 'text' : 'password'" autocomplete="current-password" maxlength="128" placeholder="请输入密码">
              <button type="button" class="admin-password-toggle" :aria-label="showPassword ? '隐藏密码' : '显示密码'" @click="showPassword = !showPassword">
                <EyeOff v-if="showPassword" /><Eye v-else />
              </button>
            </div>
          </label>
          <div class="admin-login-form-meta">
            <label><input v-model="form.remember" type="checkbox"> 保持本次会话</label>
            <span>1 小时内错误 5 次将锁定</span>
          </div>
          <button class="admin-login-submit" type="submit" :disabled="loading">
            <LoaderCircle v-if="loading" class="spin" />
            <template v-else>进入运营后台 <ArrowRight /></template>
          </button>
        </div>

        <div class="admin-login-security">
          <ShieldAlert /><span>当前为联调固定账号。正式环境将接入多管理员和 RBAC 权限。</span>
        </div>
        <code>POST /v1/admin/auth/login</code>
      </form>
    </section>
  </main>
</template>

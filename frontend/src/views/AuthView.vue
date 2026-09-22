<template>
  <main class="auth-page">
    <section class="auth-card">
      <button class="brand" @click="router.push('/')"><span>F</span> FitPlan</button>
      <div class="copy">
        <p class="eyebrow">SECURE WORKSPACE</p>
        <h1>{{ mode === 'login' ? '登录你的训练空间' : '创建个人训练空间' }}</h1>
        <p>账号用于隔离个人画像、训练日志、计划和对话记忆，不同用户不会共享上下文。</p>
      </div>

      <form @submit.prevent="submit">
        <label v-if="mode === 'register'">
          <span>称呼</span>
          <input v-model.trim="form.displayName" maxlength="80" autocomplete="name" placeholder="例如：小林" />
        </label>
        <label>
          <span>邮箱</span>
          <input v-model.trim="form.email" type="email" required maxlength="320" autocomplete="email" placeholder="name@example.com" />
        </label>
        <label>
          <span>密码</span>
          <input v-model="form.password" type="password" required :minlength="mode === 'register' ? 10 : 1" maxlength="128" :autocomplete="mode === 'login' ? 'current-password' : 'new-password'" placeholder="至少 10 位密码" />
        </label>
        <p v-if="errorMessage" class="error" role="alert">{{ errorMessage }}</p>
        <button class="submit" type="submit" :disabled="loading">
          {{ loading ? '正在验证…' : mode === 'login' ? '登录并继续' : '注册并继续' }}
        </button>
      </form>

      <button class="switch" type="button" @click="switchMode">
        {{ mode === 'login' ? '还没有账号？立即注册' : '已有账号？返回登录' }}
      </button>
      <p class="privacy">登录凭证仅保存在当前浏览器会话中；退出或关闭会话后需重新登录。</p>
    </section>
  </main>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useHead } from '@vueuse/head'
import { login, register } from '../api'
import { saveSession } from '../auth'

useHead({ title: '登录｜FitPlan' })
const route = useRoute()
const router = useRouter()
const mode = ref(route.query.mode === 'register' ? 'register' : 'login')
const loading = ref(false)
const errorMessage = ref('')
const form = reactive({ displayName: '', email: '', password: '' })

const switchMode = () => {
  mode.value = mode.value === 'login' ? 'register' : 'login'
  errorMessage.value = ''
}

const submit = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    const session = mode.value === 'login'
      ? await login({ email: form.email, password: form.password })
      : await register({ email: form.email, password: form.password, displayName: form.displayName })
    saveSession(session)
    const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/') && !route.query.redirect.startsWith('//')
      ? route.query.redirect
      : '/workspace'
    await router.replace(redirect)
  } catch (error) {
    errorMessage.value = error.message || '认证失败，请稍后重试。'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-page{min-height:100vh;display:grid;place-items:center;padding:28px;background:#eeede8;color:#171916}.auth-card{width:min(440px,100%);padding:34px;border:1px solid #d0cfc8;border-radius:18px;background:#f9f8f4;box-shadow:0 24px 70px #1c211a12}.brand{display:flex;align-items:center;gap:10px;border:0;background:none;color:#171916;font-weight:800;font-size:17px}.brand span{display:grid;place-items:center;width:32px;height:32px;border-radius:9px;background:#b6f83b;font:italic bold 17px Georgia}.copy{margin:34px 0 28px}.eyebrow{font-size:10px;letter-spacing:1.8px;color:#6f9e11;font-weight:800}.copy h1{margin:10px 0 12px;font-size:28px;letter-spacing:-1px}.copy p:last-child{font-size:13px;line-height:1.75;color:#6d706a}form{display:grid;gap:16px}label{display:grid;gap:8px;font-size:12px;font-weight:700;color:#4d504b}input{width:100%;height:46px;padding:0 13px;border:1px solid #cbc9c1;border-radius:9px;background:#fff;color:#171916;font:14px inherit;outline:none}input:focus{border-color:#84b92b;box-shadow:0 0 0 3px #b6f83b24}.error{padding:11px 12px;border-radius:8px;background:#fff0ed;color:#a43827;font-size:12px;line-height:1.5}.submit{height:48px;border:0;border-radius:9px;background:#171916;color:#fff;font-weight:750}.submit:disabled{opacity:.55;cursor:wait}.switch{margin-top:20px;border:0;background:none;color:#567f0e;font-size:13px}.privacy{margin-top:18px;font-size:11px;line-height:1.6;color:#858780}
</style>

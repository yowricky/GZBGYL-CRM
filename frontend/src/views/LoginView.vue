<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import logoUrl from '../assets/gez-logo.png'
import { getApiMessage } from '../api/http'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const loading = ref(false)
const error = ref('')
const form = reactive({
  username: '',
})

async function submit() {
  error.value = ''
  loading.value = true
  try {
    await auth.login(form.username)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/workspace'
    await router.push(redirect)
  } catch (err) {
    error.value = getApiMessage(err)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-brand">
      <img :src="logoUrl" alt="葛洲坝集团供应链管理有限公司" />
      <h1>CRM 管理平台</h1>
      <p>面向项目型销售、供应链协同与经营管理，统一客户、商机、合同回款和目标完成。</p>
    </section>
    <section class="login-card">
      <div>
        <p class="section-kicker">账号登录</p>
        <h1>登录</h1>
      </div>
      <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
      <el-form class="login-form" label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名">
          <el-input v-model="form.username" autocomplete="username" autofocus placeholder="请输入用户名：admin" />
        </el-form-item>
        <p class="login-test-note">测试阶段仅需输入用户名，暂不校验密码。</p>
        <el-button class="login-button" type="primary" :loading="loading" native-type="submit"> 登录 </el-button>
      </el-form>
    </section>
  </main>
</template>

<template>
  <div class="login-container">
    <n-card class="login-card" title="智维Wiki">
      <n-form ref="formRef" :model="formData" :rules="rules">
        <n-form-item path="username" label="用户名">
          <n-input v-model:value="formData.username" placeholder="请输入用户名" />
        </n-form-item>
        <n-form-item path="password" label="密码">
          <n-input v-model:value="formData.password" type="password" placeholder="请输入密码" @keyup.enter="handleLogin" />
        </n-form-item>

        <!-- 2FA验证码 -->
        <n-form-item v-if="show2FA" path="totpCode" label="双因素验证码">
          <n-input v-model:value="formData.totpCode" placeholder="请输入6位验证码" maxlength="6" @keyup.enter="handleLogin" />
        </n-form-item>

        <n-button type="primary" block :loading="loading" @click="handleLogin">登 录</n-button>
      </n-form>

      <!-- OAuth2/SSO登录 -->
      <n-divider style="margin: 16px 0 12px">其他登录方式</n-divider>
      <n-space justify="center">
        <n-button secondary @click="handleOAuth2Login('wecom')" :disabled="!oauth2Enabled.wecom">
          企业微信
        </n-button>
        <n-button secondary @click="handleOAuth2Login('dingtalk')" :disabled="!oauth2Enabled.dingtalk">
          钉钉
        </n-button>
        <n-button secondary @click="handleOAuth2Login('generic')" :disabled="!oauth2Enabled.generic">
          SSO登录
        </n-button>
      </n-space>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import { useUserStore } from '@/stores/user'
import { authApi } from '@/api'

const router = useRouter()
const message = useMessage()
const userStore = useUserStore()
const loading = ref(false)
const show2FA = ref(false)

const formData = reactive({ username: '', password: '', totpCode: '' })
const rules = {
  username: { required: true, message: '请输入用户名', trigger: 'blur' },
  password: { required: true, message: '请输入密码', trigger: 'blur' },
}
const oauth2Enabled = reactive({ wecom: true, dingtalk: true, generic: true })

async function handleLogin() {
  loading.value = true
  try {
    const params: any = { username: formData.username, password: formData.password }
    if (show2FA.value && formData.totpCode) {
      params.totpCode = formData.totpCode
      params.loginType = 'TWO_FACTOR'
    }
    await userStore.login(formData.username, formData.password)
    message.success('登录成功')
    router.push('/')
  } catch (e: any) {
    // 如果返回需要2FA验证
    if (e.message?.includes('2FA') || e.message?.includes('双因素')) {
      show2FA.value = true
      message.info('请输入双因素验证码')
    } else {
      message.error(e.message || '登录失败')
    }
  } finally {
    loading.value = false
  }
}

async function handleOAuth2Login(provider: string) {
  try {
    const res: any = await authApi.getOAuth2Authorize(provider)
    const authUrl = res.data
    if (authUrl) {
      window.location.href = authUrl
    }
  } catch (e: any) {
    message.error(e.message || 'OAuth2登录失败')
  }
}
</script>

<style scoped>
.login-container {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.login-card {
  width: 400px;
}
</style>

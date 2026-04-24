import { defineStore } from 'pinia'
import { ref } from 'vue'
import { authApi } from '@/api'
import type { CurrentUserVO, LoginVO } from '@/types/api'

type UserInfo = Partial<CurrentUserVO & LoginVO>

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const userInfo = ref<UserInfo | null>(null)

  async function login(username: string, password: string, totpCode?: string) {
    const loginData: { username: string; password: string; totpCode?: string; loginType?: string } = { username, password }
    if (totpCode) {
      loginData.totpCode = totpCode
      loginData.loginType = 'TWO_FACTOR'
    }
    const res = await authApi.login(loginData)
    const loginResult = res.data
    token.value = loginResult.token || ''
    userInfo.value = loginResult
    if (loginResult.token) {
      localStorage.setItem('token', loginResult.token)
    }
    return loginResult
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('token')
    // 异步通知后端，失败不影响本地清理
    authApi.logout().catch((error) => {
      console.warn('[User] 登出请求失败', error)
    })
  }

  async function getUserInfo() {
    const res = await authApi.getCurrentUser()
    userInfo.value = res.data
    return res.data
  }

  return { token, userInfo, login, logout, getUserInfo }
})

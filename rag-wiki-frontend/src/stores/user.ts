import { defineStore } from 'pinia'
import { ref } from 'vue'
import { authApi } from '@/api'

interface UserInfo {
  userId: string
  username: string
  realName: string
  deptName: string
  securityLevel: number
  token?: string
}

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const userInfo = ref<UserInfo | null>(null)

  async function login(username: string, password: string) {
    const res: any = await authApi.login({ username, password })
    token.value = res.data.token
    userInfo.value = res.data
    localStorage.setItem('token', res.data.token)
    return res.data
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
    const res: any = await authApi.getCurrentUser()
    userInfo.value = res.data
    return res.data
  }

  return { token, userInfo, login, logout, getUserInfo }
})

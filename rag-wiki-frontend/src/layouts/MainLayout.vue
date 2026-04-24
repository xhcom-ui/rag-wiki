<template>
  <n-layout has-sider style="height: 100vh">
    <n-layout-sider bordered collapse-mode="width" :collapsed-width="64" :width="220" show-trigger>
      <div class="logo">智维Wiki</div>
      <n-menu :options="menuOptions" :value="activeKey" @update:value="handleMenuClick" />
    </n-layout-sider>
    <n-layout>
      <n-layout-header bordered style="height: 56px; display: flex; align-items: center; padding: 0 20px; justify-content: space-between;">
        <span style="font-size: 16px; font-weight: 500;">{{ currentTitle }}</span>
        <n-space>
          <n-dropdown :options="userOptions" @select="handleUserAction">
            <n-button quaternary>{{ userStore.userInfo?.realName || '用户' }}</n-button>
          </n-dropdown>
        </n-space>
      </n-layout-header>
      <n-layout-content style="padding: 20px; overflow: auto;">
        <router-view />
      </n-layout-content>
    </n-layout>
  </n-layout>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const activeKey = computed(() => route.name as string)
const currentTitle = computed(() => (route.meta.title as string) || '智维Wiki')

const menuOptions = [
  { label: '仪表盘', key: 'Dashboard' },
  { label: '知识库', key: 'Knowledge' },
  { label: '文档编辑器', key: 'DocumentEditor' },
  { type: 'divider', key: 'd1' },
  { label: '智能问答', key: 'AIChat' },
  { label: '深度研究', key: 'AIAgent' },
  { label: '代码沙箱', key: 'AISandbox' },
  { type: 'divider', key: 'd2' },
  { label: '用户管理', key: 'UserManagement' },
  { label: '角色管理', key: 'RoleManagement' },
  { label: '部门管理', key: 'DeptManagement' },
  { label: '租户管理', key: 'TenantManagement' },
  { label: '审计日志', key: 'AuditLog' },
  { label: '安全告警', key: 'SecurityAlert' },
  { label: 'Badcase管理', key: 'BadcaseManagement' },
  { label: '统计报表', key: 'Statistics' },
  { label: '模型管理', key: 'ModelManagement' },
  { label: '向量库管理', key: 'VectorManagement' },
  { type: 'divider', key: 'd3' },
  { label: '系统配置', key: 'SystemConfig' },
  { label: '菜单管理', key: 'MenuManagement' },
  { label: '低代码平台', key: 'LowCodePlatform' },
]

const userOptions = [
  { label: '个人中心', key: 'profile' },
  { label: '退出登录', key: 'logout' },
]

function handleMenuClick(key: string) {
  router.push({ name: key })
}

function handleUserAction(key: string) {
  if (key === 'logout') {
    userStore.logout()
    router.push('/login')
  } else if (key === 'profile') {
    router.push('/profile')
  }
}
</script>

<style scoped>
.logo {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  font-weight: 700;
  color: #18a058;
  border-bottom: 1px solid #efeff5;
}
</style>

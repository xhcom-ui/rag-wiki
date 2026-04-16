import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { authApi } from '@/api'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', requiresAuth: false },
  },
  {
    path: '/',
    name: 'Layout',
    component: () => import('@/layouts/MainLayout.vue'),
    redirect: '/dashboard',
    meta: { requiresAuth: true },
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '仪表盘', icon: 'HomeOutline' },
      },
      // 知识库
      {
        path: 'knowledge',
        name: 'Knowledge',
        component: () => import('@/views/knowledge/index.vue'),
        meta: { title: '知识库', icon: 'BookOutline' },
      },
      {
        path: 'knowledge/:spaceId',
        name: 'KnowledgeSpace',
        component: () => import('@/views/knowledge/space.vue'),
        meta: { title: '知识库空间' },
      },
      {
        path: 'document/:id',
        name: 'DocumentDetail',
        component: () => import('@/views/knowledge/document.vue'),
        meta: { title: '文档详情' },
      },
      {
        path: 'knowledge/editor',
        name: 'DocumentEditor',
        component: () => import('@/views/knowledge/editor.vue'),
        meta: { title: '文档编辑器' },
      },
      {
        path: 'knowledge/editor/:id',
        name: 'DocumentEditorEdit',
        component: () => import('@/views/knowledge/editor.vue'),
        meta: { title: '编辑文档' },
      },
      // AI交互中心
      {
        path: 'ai/chat',
        name: 'AIChat',
        component: () => import('@/views/ai/chat.vue'),
        meta: { title: '智能问答', icon: 'ChatbubblesOutline' },
      },
      {
        path: 'ai/agent',
        name: 'AIAgent',
        component: () => import('@/views/ai/agent.vue'),
        meta: { title: '深度研究', icon: 'SearchOutline' },
      },
      {
        path: 'ai/sandbox',
        name: 'AISandbox',
        component: () => import('@/views/ai/sandbox.vue'),
        meta: { title: '代码沙箱', icon: 'CodeSlashOutline' },
      },
      // 后台管理
      {
        path: 'admin/user',
        name: 'UserManagement',
        component: () => import('@/views/admin/user.vue'),
        meta: { title: '用户管理', icon: 'PeopleOutline' },
      },
      {
        path: 'admin/role',
        name: 'RoleManagement',
        component: () => import('@/views/admin/role.vue'),
        meta: { title: '角色管理', icon: 'ShieldOutline' },
      },
      {
        path: 'admin/dept',
        name: 'DeptManagement',
        component: () => import('@/views/admin/dept.vue'),
        meta: { title: '部门管理', icon: 'GitBranchOutline' },
      },
      {
        path: 'admin/audit',
        name: 'AuditLog',
        component: () => import('@/views/admin/audit.vue'),
        meta: { title: '审计日志', icon: 'DocumentTextOutline' },
      },
      {
        path: 'admin/badcase',
        name: 'BadcaseManagement',
        component: () => import('@/views/admin/badcase.vue'),
        meta: { title: 'Badcase管理', icon: 'BugOutline' },
      },
      {
        path: 'admin/model',
        name: 'ModelManagement',
        component: () => import('@/views/admin/model.vue'),
        meta: { title: '模型管理', icon: 'HardwareChipOutline' },
      },
      {
        path: 'admin/vector',
        name: 'VectorManagement',
        component: () => import('@/views/admin/vector.vue'),
        meta: { title: '向量库管理', icon: 'ServerOutline' },
      },
      {
        path: 'admin/security-alert',
        name: 'SecurityAlert',
        component: () => import('@/views/admin/security-alert.vue'),
        meta: { title: '安全告警', icon: 'WarningOutline' },
      },
      {
        path: 'admin/tenant',
        name: 'TenantManagement',
        component: () => import('@/views/admin/tenant.vue'),
        meta: { title: '租户管理', icon: 'BusinessOutline' },
      },
      {
        path: 'admin/statistics',
        name: 'Statistics',
        component: () => import('@/views/admin/statistics.vue'),
        meta: { title: '统计报表', icon: 'StatsChartOutline' },
      },
      {
        path: 'admin/config',
        name: 'SystemConfig',
        component: () => import('@/views/admin/config.vue'),
        meta: { title: '系统配置', icon: 'SettingsOutline' },
      },
      {
        path: 'admin/menu',
        name: 'MenuManagement',
        component: () => import('@/views/admin/menu.vue'),
        meta: { title: '菜单管理', icon: 'MenuOutline' },
      },
      {
        path: 'admin/lowcode',
        name: 'LowCodePlatform',
        component: () => import('@/views/admin/lowcode.vue'),
        meta: { title: '低代码平台', icon: 'BuildOutline' },
      },
      // 个人中心
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/views/profile/index.vue'),
        meta: { title: '个人中心', icon: 'PersonOutline' },
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 路由守卫
router.beforeEach(async (to, _from, next) => {
  const token = localStorage.getItem('token')
  
  // 不需要认证的路由直接放行
  if (to.meta.requiresAuth === false) {
    next()
    return
  }
  
  // 没有token，跳转登录
  if (!token) {
    next({ name: 'Login' })
    return
  }
  
  // 有token但访问登录页，跳转首页
  if (to.name === 'Login') {
    next({ name: 'Dashboard' })
    return
  }
  
  // 验证token有效性（仅在首次加载或token刷新时）
  if (!router.currentRoute.value.meta._tokenVerified) {
    try {
      await authApi.getCurrentUser()
      // 标记token已验证
      to.meta._tokenVerified = true
      next()
    } catch (error) {
      // token无效或过期
      console.warn('[Router] Token验证失败，清除登录状态', error)
      localStorage.removeItem('token')
      next({ name: 'Login' })
    }
  } else {
    next()
  }
})

export default router

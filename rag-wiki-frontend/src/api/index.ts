import axios from 'axios'
import type { AxiosInstance, AxiosRequestConfig, AxiosResponse } from 'axios'
import type { ApiResponse, LoginDTO, RAGQueryDTO } from '@/types/api'

const service: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

// ==================== 全局Loading计数器 ====================
let loadingCount = 0
const loadingCallbacks: Set<(loading: boolean) => void> = new Set()

export function onGlobalLoading(cb: (loading: boolean) => void) {
  loadingCallbacks.add(cb)
  return () => loadingCallbacks.delete(cb)
}

function setLoading(loading: boolean) {
  loadingCallbacks.forEach((cb) => cb(loading))
}

// ==================== 全局错误提示 ====================
type MessageFn = (content: string, options?: { duration?: number }) => void
let _errorMessage: MessageFn | null = null
let _warningMessage: MessageFn | null = null

export function setGlobalMessage(error: MessageFn, warning: MessageFn) {
  _errorMessage = error
  _warningMessage = warning
}

function showError(msg: string) {
  if (_errorMessage) {
    _errorMessage(msg, { duration: 3000 })
  } else {
    console.error('[API Error]', msg)
  }
}

function showWarning(msg: string) {
  if (_warningMessage) {
    _warningMessage(msg, { duration: 3000 })
  }
}

// ==================== 请求重试配置 ====================
interface RetryConfig {
  retries: number
  retryDelay: number
  retryCondition: (error: any) => boolean
}

const defaultRetryConfig: RetryConfig = {
  retries: 2,
  retryDelay: 1000,
  retryCondition: (error: any) => {
    // 仅对网络错误和5xx重试
    if (!error.response) return true // 网络异常
    const status = error.response.status
    return status >= 500 && status < 600
  },
}

async function retryRequest(config: AxiosRequestConfig, retryConfig: RetryConfig): Promise<any> {
  let lastError: any
  for (let i = 0; i <= retryConfig.retries; i++) {
    try {
      return await axios(config)
    } catch (error) {
      lastError = error
      if (i < retryConfig.retries && retryConfig.retryCondition(error)) {
        const delay = retryConfig.retryDelay * Math.pow(2, i) // 指数退避
        await new Promise((resolve) => setTimeout(resolve, delay))
        continue
      }
      throw error
    }
  }
  throw lastError
}

// 请求拦截器
service.interceptors.request.use(
  (config) => {
    loadingCount++
    if (loadingCount === 1) setLoading(true)

    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = token
    }
    return config
  },
  (error) => {
    loadingCount = Math.max(0, loadingCount - 1)
    if (loadingCount === 0) setLoading(false)
    return Promise.reject(error)
  }
)

// 响应拦截器
service.interceptors.response.use(
  (response: AxiosResponse) => {
    loadingCount = Math.max(0, loadingCount - 1)
    if (loadingCount === 0) setLoading(false)

    const { data } = response
    if (data.code !== 200) {
      if (data.code === 401) {
        localStorage.removeItem('token')
        showWarning('登录已过期，请重新登录')
        window.location.href = '/login'
        return Promise.reject(new Error('未授权'))
      }
      if (data.code === 403) {
        showError('无权限执行此操作')
      } else if (data.code === 429) {
        showWarning('请求过于频繁，请稍后重试')
      } else {
        showError(data.message || '请求失败')
      }
      return Promise.reject(new Error(data.message || '请求失败'))
    }
    return data
  },
  async (error) => {
    loadingCount = Math.max(0, loadingCount - 1)
    if (loadingCount === 0) setLoading(false)

    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      showWarning('登录已过期，请重新登录')
      window.location.href = '/login'
      return Promise.reject(error)
    }

    // 网络错误/超时自动重试
    const config = error.config
    if (config && !config._retried && defaultRetryConfig.retryCondition(error)) {
      config._retried = true
      try {
        showWarning('网络异常，正在重试...')
        return await retryRequest(config, defaultRetryConfig)
      } catch (retryError) {
        // 重试也失败了
      }
    }

    // 友好的错误提示
    if (!error.response) {
      showError('网络连接异常，请检查网络后重试')
    } else if (error.response.status >= 500) {
      showError('服务器异常，请稍后重试')
    } else if (error.code === 'ECONNABORTED') {
      showError('请求超时，请稍后重试')
    }

    return Promise.reject(error)
  }
)

export default service

// ==================== 认证模块 ====================
export const authApi = {
  login: (data: LoginDTO) =>
    service.post('/auth/login', data),
  logout: () => service.post('/auth/logout'),
  getCurrentUser: () => service.get('/auth/current'),
  changePassword: (data: { oldPassword: string; newPassword: string; confirmPassword: string }) =>
    service.put('/auth/password', data),
  getPermissions: () => service.get('/auth/permissions'),
  // OAuth2/SSO
  getOAuth2Authorize: (provider: string) => service.get('/auth/oauth2/authorize', { params: { provider } }),
  oauth2Callback: (provider: string, code: string, state: string) =>
    service.get('/auth/oauth2/callback', { params: { provider, code, state } }),
  bindOAuth2: (userId: string, provider: string, code: string) =>
    service.post('/auth/oauth2/bind', null, { params: { userId, provider, code } }),
  // 2FA
  enable2FA: () => service.post('/auth/2fa/enable'),
  verify2FA: (code: string) => service.post('/auth/2fa/verify', null, { params: { code } }),
  disable2FA: (code: string) => service.post('/auth/2fa/disable', null, { params: { code } }),
  get2FAQRCode: () => service.get('/auth/2fa/qrcode'),
}

// ==================== 用户管理 ====================
export const userApi = {
  create: (data: any) => service.post('/user', data),
  getByUserId: (userId: string) => service.get(`/user/${userId}`),
  list: (params: any) => service.get('/user/list', { params }),
  update: (data: any) => service.put('/user', data),
  delete: (id: number) => service.delete(`/user/${id}`),
}

// ==================== 角色管理 ====================
export const roleApi = {
  create: (data: any) => service.post('/role', data),
  getByRoleId: (roleId: string) => service.get(`/role/${roleId}`),
  list: () => service.get('/role/list'),
  update: (data: any) => service.put('/role', data),
  delete: (id: number) => service.delete(`/role/${id}`),
}

// ==================== 部门管理 ====================
export const deptApi = {
  create: (data: any) => service.post('/dept', data),
  getByDeptId: (deptId: string) => service.get(`/dept/${deptId}`),
  tree: () => service.get('/dept/tree'),
  update: (data: any) => service.put('/dept', data),
  delete: (id: number) => service.delete(`/dept/${id}`),
}

// ==================== 知识库空间 ====================
export const spaceApi = {
  create: (data: any) => service.post('/space', data),
  get: (spaceId: string) => service.get(`/space/${spaceId}`),
  list: (params: any) => service.get('/space/list', { params }),
  update: (data: any) => service.put('/space', data),
  delete: (id: number) => service.delete(`/space/${id}`),
}

// ==================== 文档管理 ====================
export const documentApi = {
  create: (data: any) => service.post('/document', data),
  get: (docId: string) => service.get(`/document/${docId}`),
  list: (params: any) => service.get('/document/list', { params }),
  update: (data: any) => service.put('/document', data),
  delete: (id: number) => service.delete(`/document/${id}`),
  upload: (spaceId: string, file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('spaceId', spaceId)
    return service.post('/document/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
  // 版本历史
  getVersions: (documentId: string) => service.get(`/document/${documentId}/versions`),
  // 权限管理
  getPermissions: (documentId: string) => service.get(`/document/${documentId}/permissions`),
  addPermission: (data: any) => service.post('/document/permission', data),
  removePermission: (id: number) => service.delete(`/document/permission/${id}`),
}

// ==================== AI - 文档解析 ====================
export const parseApi = {
  submit: (data: any) => service.post('/ai/document/parse', data),
  getStatus: (taskId: string) => service.post('/ai/document/parse/status', null, { params: { taskId } }),
  upload: (file: File, spaceId: string, securityLevel: number = 1) => {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('space_id', spaceId)
    formData.append('security_level', String(securityLevel))
    return service.post('/ai/document/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
}

// ==================== AI - 向量检索 ====================
export const vectorApi = {
  embed: (data: any) => service.post('/ai/vector/embed', data),
  search: (data: any) => service.post('/ai/vector/search', data),
  hybridSearch: (data: any) => service.post('/ai/vector/hybrid-search', data),
}

// ==================== AI - RAG问答 ====================
export const ragApi = {
  query: (data: RAGQueryDTO) => service.post('/ai/rag/query', data),
  queryStream: (data: RAGQueryDTO) => fetch('/api/ai/rag/query/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': localStorage.getItem('token') || '',
    },
    body: JSON.stringify(data),
  }),
  getSession: (sessionId: string) => service.get(`/ai/rag/sessions/${sessionId}`),
  clearSession: (sessionId: string) => service.delete(`/ai/rag/sessions/${sessionId}`),
  feedback: (data: any) => service.post('/ai/rag/feedback', data),
  getSessionHistory: (params: any) => service.get('/ai/rag/sessions', { params }),
  getDailyStats: () => service.get('/ai/rag/stats/daily'),
}

// ==================== AI - 记忆管理 ====================
export const memoryApi = {
  create: (data: any) => service.post('/ai/memory/create', data),
  search: (data: any) => service.post('/ai/memory/search', data),
  extract: (sessionId: string, userId: string) =>
    service.post('/ai/memory/extract', null, { params: { session_id: sessionId, user_id: userId } }),
  list: (params: any) => service.get('/ai/memory/list', { params }),
  delete: (memoryId: string) => service.delete(`/ai/memory/${memoryId}`),
}

// ==================== AI - Agent ====================
export const agentApi = {
  submit: (data: any) => service.post('/ai/agent/submit', data),
  getTask: (taskId: string) => service.get(`/ai/agent/task/${taskId}`),
  cancel: (taskId: string) => service.post(`/ai/agent/task/${taskId}/cancel`),
  getTools: () => service.get('/ai/agent/tools'),
}

// ==================== AI - 代码沙箱 ====================
export const sandboxApi = {
  execute: (data: any) => service.post('/ai/sandbox/execute', data),
  getResult: (taskId: string) => service.get(`/ai/sandbox/result/${taskId}`),
  securityCheck: (code: string) => service.post('/ai/sandbox/security-check', null, { params: { code } }),
}

// ==================== 审批管理 ====================
export const approvalApi = {
  submit: (data: any) => service.post('/approval/submit', null, { params: data }),
  process: (data: any) => service.post('/approval/process', data),
  getByDocId: (documentId: string) => service.get('/approval/document', { params: { documentId } }),
}

// ==================== 审计管理 ====================
export const auditApi = {
  listLogs: (params: any) => service.get('/audit/logs', { params }),
  listBadcases: (params: any) => service.get('/audit/badcases', { params }),
}

// ==================== Badcase管理 ====================
export const badcaseApi = {
  page: (params: any) => service.get('/audit/badcase/page', { params }),
  getStats: () => service.get('/audit/badcase/stats'),
  getDetail: (id: number) => service.get(`/audit/badcase/${id}`),
  process: (id: number, data: any) => service.post(`/audit/badcase/${id}/process`, data),
}

// ==================== 向量库管理 ====================
export const vectorAdminApi = {
  getStats: () => service.get('/vector/admin/stats'),
  page: (params: any) => service.get('/vector/admin/page', { params }),
  delete: (id: string) => service.delete(`/vector/admin/${id}`),
  rebuildIndex: (collection?: string) => service.post('/vector/admin/rebuild-index', null, { params: { collection } }),
  getCollections: () => service.get('/vector/admin/collections'),
}

// ==================== 角色权限管理 ====================
export const rolePermissionApi = {
  getRolePermissions: (roleId: string) => service.get(`/role/${roleId}/permissions`),
  saveRolePermissions: (roleId: string, permissionIds: string[]) =>
    service.put(`/role/${roleId}/permissions`, { permissionIds }),
  getUserRoles: (userId: string) => service.get(`/role/user/${userId}`),
  saveUserRoles: (userId: string, roleIds: string[]) =>
    service.put(`/role/user/${userId}`, { roleIds }),
}

// ==================== LLM配置管理 ====================
export const llmConfigApi = {
  // 分页查询配置列表
  page: (params: { pageNum: number; pageSize: number; provider?: string; isEnabled?: number }) =>
    service.get('/ai/llm-config/page', { params }),
  // 获取所有启用的配置
  listEnabled: () => service.get('/ai/llm-config/list'),
  // 获取配置详情
  getById: (configId: string) => service.get(`/ai/llm-config/${configId}`),
  // 创建配置
  create: (data: any) => service.post('/ai/llm-config', data),
  // 更新配置
  update: (configId: string, data: any) => service.put(`/ai/llm-config/${configId}`, data),
  // 删除配置
  delete: (configId: string) => service.delete(`/ai/llm-config/${configId}`),
  // 设置默认配置
  setDefault: (configId: string) => service.post(`/ai/llm-config/${configId}/set-default`),
  // 获取支持的提供商列表
  getProviders: () => service.get('/ai/llm-config/providers'),
}

// ==================== 安全告警 ====================
export const securityAlertApi = {
  // 分页查询告警
  page: (params: { pageNum: number; pageSize: number; severity?: string; status?: string; alertType?: string }) =>
    service.get('/security/alert/page', { params }),
  // 获取告警统计
  getStatistics: () => service.get('/security/alert/statistics'),
  // 获取未处理告警
  getUnhandled: (limit: number = 10) => service.get('/security/alert/unhandled', { params: { limit } }),
  // 处理告警
  handle: (alertId: string, data: { handlerId: string; handleResult: string; status?: string }) =>
    service.post(`/security/alert/${alertId}/handle`, data),
  // 忽略告警
  ignore: (alertId: string, data: { handlerId: string; reason: string }) =>
    service.post(`/security/alert/${alertId}/ignore`, data),
}

// ==================== 租户管理 ====================
export const tenantApi = {
  // 创建租户
  create: (data: any) => service.post('/tenant', data),
  // 获取租户详情
  get: (tenantId: string) => service.get(`/tenant/${tenantId}`),
  // 更新租户
  update: (data: any) => service.put('/tenant', data),
  // 切换租户隔离级别
  switchIsolation: (tenantId: string, isolationLevel: string) =>
    service.put(`/tenant/${tenantId}/isolation`, null, { params: { isolationLevel } }),
  // 启用/禁用租户
  toggleStatus: (tenantId: string, status: number) =>
    service.put(`/tenant/${tenantId}/status/${status}`),
  // 分页查询租户
  page: (params: { pageNum: number; pageSize: number; tenantName?: string; status?: number }) =>
    service.get('/tenant/page', { params }),
}

// ==================== 统计报表 ====================
export const statisticsApi = {
  // 获取仪表盘数据
  getDashboardData: () => service.get('/statistics/dashboard'),
  // 获取系统概览
  getOverview: () => service.get('/statistics/overview'),
  // 获取AI统计
  getAIStatistics: (startDate: string, endDate: string) => 
    service.get('/statistics/ai', { params: { startDate, endDate } }),
  // 获取文档统计
  getDocumentStatistics: () => service.get('/statistics/documents'),
  // 获取用户活跃度
  getUserActivity: (startDate: string, endDate: string) => 
    service.get('/statistics/user-activity', { params: { startDate, endDate } }),
  // 获取Badcase统计
  getBadcaseStatistics: () => service.get('/statistics/badcases'),
  // 获取热门查询
  getHotQueries: (limit: number = 10) => 
    service.get('/statistics/hot-queries', { params: { limit } }),
  // 获取热门文档
  getHotDocuments: (limit: number = 10) => 
    service.get('/statistics/hot-documents', { params: { limit } }),
}

// ==================== 文档互动(收藏/点赞/评论/浏览) ====================
export const interactionApi = {
  toggleFavorite: (documentId: string) => service.post(`/document/interaction/favorite/${documentId}`),
  checkFavorite: (documentId: string) => service.get(`/document/interaction/favorite/check/${documentId}`),
  getMyFavorites: () => service.get('/document/interaction/favorite/list'),
  toggleLike: (documentId: string) => service.post(`/document/interaction/like/${documentId}`),
  getLikeCount: (documentId: string) => service.get(`/document/interaction/like/count/${documentId}`),
  addComment: (documentId: string, content: string, parentId?: number) =>
    service.post('/document/interaction/comment', null, { params: { documentId, content, parentId } }),
  getComments: (documentId: string) => service.get(`/document/interaction/comment/${documentId}`),
  deleteComment: (commentId: string) => service.delete(`/document/interaction/comment/${commentId}`),
  recordBrowse: (documentId: string) => service.post(`/document/interaction/browse/${documentId}`),
  getBrowseHistory: (limit: number = 20) => service.get('/document/interaction/browse/history', { params: { limit } }),
  clearBrowseHistory: () => service.delete('/document/interaction/browse/history'),
}

// ==================== 系统配置管理 ====================
export const configApi = {
  getAllGroups: () => service.get('/config/groups'),
  getByGroup: (group: string) => service.get(`/config/group/${group}`),
  getValue: (key: string) => service.get(`/config/value/${key}`),
  updateValue: (key: string, value: string) => service.put('/config/value', { key, value }),
  add: (data: any) => service.post('/config', data),
  delete: (key: string) => service.delete(`/config/${key}`),
}

// ==================== 批量用户导入 ====================
export const userImportApi = {
  batchImport: (file: File, defaultPassword?: string, defaultRoleId?: number) => {
    const formData = new FormData()
    formData.append('file', file)
    if (defaultPassword) formData.append('defaultPassword', defaultPassword)
    if (defaultRoleId) formData.append('defaultRoleId', String(defaultRoleId))
    return service.post('/user/import', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
  getTemplate: () => service.get('/user/import/template'),
}

// ==================== 内容创作辅助 ====================
export const contentAssistApi = {
  generateOutline: (data: { topic: string; spaceId?: string; style?: string }) =>
    service.post('/ai/content/outline', data),
  polishContent: (data: { content: string; style?: string }) =>
    service.post('/ai/content/polish', data),
  expandContent: (data: { content: string; direction?: string }) =>
    service.post('/ai/content/expand', data),
  summarizeContent: (data: { content: string; maxLength?: number }) =>
    service.post('/ai/content/summarize', data),
  generateFromKnowledge: (data: { topic: string; spaceIds: string[]; template?: string }) =>
    service.post('/ai/content/generate', data),
}

// ==================== 用户数据管理(GDPR) ====================
export const userDataApi = {
  exportMyData: () => service.get('/user/data/export'),
  destroyMyData: () => service.delete('/user/data/destroy'),
}

// ==================== 用户API密钥管理 ====================
export const apiKeyApi = {
  create: (data: { keyName: string; rateLimit?: number; allowedScopes?: string }) =>
    service.post('/user/api-key', data),
  list: () => service.get('/user/api-key/list'),
  revoke: (keyId: string) => service.delete(`/user/api-key/${keyId}`),
  toggle: (keyId: string, enabled: boolean) =>
    service.put(`/user/api-key/${keyId}/toggle`, { enabled }),
}

// ==================== 多Agent协作 ====================
export const enterpriseAgentApi = {
  getScenarios: () => service.get('/ai/enterprise-agent/scenarios'),
  executeScenario: (data: { scenarioId: string; task: string; context?: any; maxRounds?: number }) =>
    service.post('/ai/enterprise-agent/execute', data),
  getSession: (sessionId: string) => service.get(`/ai/enterprise-agent/session/${sessionId}`),
}

// ==================== Badcase优化闭环 ====================
export const badcaseOptimizerApi = {
  createExperiment: (data: any) => service.post('/ai/badcase-optimizer/experiment', data),
  runExperiment: (experimentId: string) => service.post(`/ai/badcase-optimizer/experiment/${experimentId}/run`),
  getExperiment: (experimentId: string) => service.get(`/ai/badcase-optimizer/experiment/${experimentId}`),
  attributeBadcase: (data: any) => service.post('/ai/badcase-optimizer/attribute', data),
  optimizationLoop: (data: any) => service.post('/ai/badcase-optimizer/optimization-loop', data),
}

// ==================== 领域模型微调 ====================
export const domainTuningApi = {
  buildDataset: (data: { name: string; spaceIds: string[]; instructionTemplate?: string; maxSamples?: number }) =>
    service.post('/ai/domain-tuning/dataset/build', data),
  createJob: (data: any) => service.post('/ai/domain-tuning/job', data),
  startJob: (jobId: string) => service.post(`/ai/domain-tuning/job/${jobId}/start`),
  getJobStatus: (jobId: string) => service.get(`/ai/domain-tuning/job/${jobId}`),
  listJobs: () => service.get('/ai/domain-tuning/jobs'),
}

// ==================== 智能工作流引擎 ====================
export const workflowApi = {
  getTemplates: () => service.get('/ai/workflow/templates'),
  create: (data: any) => service.post('/ai/workflow/create', data),
  get: (workflowId: string) => service.get(`/ai/workflow/${workflowId}`),
  execute: (workflowId: string, inputData: any) => service.post(`/ai/workflow/${workflowId}/execute`, { inputData }),
  getExecution: (executionId: string) => service.get(`/ai/workflow/execution/${executionId}`),
}

// ==================== 第三方系统集成 ====================
export const thirdPartyApi = {
  sendWecomMessage: (data: any) => service.post('/ai/third-party/wecom/message', data),
  sendDingtalkMessage: (data: any) => service.post('/ai/third-party/dingtalk/message', data),
  sendWebhook: (data: any) => service.post('/ai/third-party/webhook', data),
  createOAProcess: (data: any) => service.post('/ai/third-party/oa/process', data),
  getWebhookLogs: (limit: number = 50) => service.get('/ai/third-party/webhook/logs', { params: { limit } }),
}

// ==================== 质量工具(忠实度/冲突) ====================
export const qualityToolApi = {
  validateFaithfulness: (data: { answer: string; context: string }) =>
    service.post('/ai/quality/faithfulness/validate', data),
  quickValidate: (data: { answer: string; context: string }) =>
    service.post('/ai/quality/faithfulness/quick', data),
  detectConflicts: (data?: any) => service.post('/ai/quality/memory/conflict-detect', data || {}),
  semanticDedup: (data?: any) => service.post('/ai/quality/memory/semantic-dedup', data || {}),
}

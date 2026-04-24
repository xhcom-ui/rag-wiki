import axios from 'axios'
import type { AxiosError, AxiosInstance, AxiosRequestConfig, AxiosResponse } from 'axios'
import type {
  AgentSubmitDTO,
  AgentTaskVO,
  ApiKeyVO,
  ApiResponse,
  CurrentUserVO,
  DeptDTO,
  DeptVO,
  DocumentCommentVO,
  DocumentPermissionVO,
  DocumentVersionVO,
  DocumentVO,
  FavoriteStateVO,
  LikeStateVO,
  LoginDTO,
  LoginVO,
  PageResult,
  RAGAnswerVO,
  RAGQueryDTO,
  RoleDTO,
  RoleVO,
  SandboxExecuteDTO,
  SandboxExecuteVO,
  SessionVO,
  SpaceVO,
  UserDTO,
  UserVO,
} from '@/types/api'

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

function normalizePageParams(params?: Record<string, unknown>) {
  if (!params) return params
  const normalized = { ...params }
  if (normalized.page !== undefined && normalized.pageNum === undefined) {
    normalized.pageNum = normalized.page
    delete normalized.page
  }
  return normalized
}

function apiGet<T>(url: string, config?: AxiosRequestConfig) {
  return service.get<unknown, ApiResponse<T>>(url, config)
}

function apiPost<T>(url: string, data?: unknown, config?: AxiosRequestConfig) {
  return service.post<unknown, ApiResponse<T>>(url, data, config)
}

function apiPut<T>(url: string, data?: unknown, config?: AxiosRequestConfig) {
  return service.put<unknown, ApiResponse<T>>(url, data, config)
}

function apiDelete<T>(url: string, config?: AxiosRequestConfig) {
  return service.delete<unknown, ApiResponse<T>>(url, config)
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
  retryCondition: (error: AxiosError | Error) => boolean
}

const defaultRetryConfig: RetryConfig = {
  retries: 2,
  retryDelay: 1000,
  retryCondition: (error: AxiosError | Error) => {
    // 仅对网络错误和5xx重试
    const axiosError = error as AxiosError
    if (!axiosError.response) return true // 网络异常
    const status = axiosError.response.status
    return status >= 500 && status < 600
  },
}

type RetryableRequestConfig = AxiosRequestConfig & { _retried?: boolean }

async function retryRequest<T>(config: RetryableRequestConfig, retryConfig: RetryConfig): Promise<AxiosResponse<T>> {
  let lastError: AxiosError | Error | null = null
  for (let i = 0; i <= retryConfig.retries; i++) {
    try {
      return await axios<T>(config)
    } catch (error) {
      const requestError = error instanceof Error ? error : new Error('请求失败')
      lastError = requestError
      if (i < retryConfig.retries && retryConfig.retryCondition(requestError)) {
        const delay = retryConfig.retryDelay * Math.pow(2, i) // 指数退避
        await new Promise((resolve) => setTimeout(resolve, delay))
        continue
      }
      throw requestError
    }
  }
  throw lastError ?? new Error('请求失败')
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
  async (error: AxiosError) => {
    loadingCount = Math.max(0, loadingCount - 1)
    if (loadingCount === 0) setLoading(false)

    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      showWarning('登录已过期，请重新登录')
      window.location.href = '/login'
      return Promise.reject(error)
    }

    // 网络错误/超时自动重试
    const config = error.config as RetryableRequestConfig | undefined
    if (config && !config._retried && defaultRetryConfig.retryCondition(error)) {
      config._retried = true
      try {
        showWarning('网络异常，正在重试...')
        return await retryRequest(config, defaultRetryConfig)
      } catch {
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
  login: (data: LoginDTO) => apiPost<LoginVO>('/auth/login', data),
  logout: () => apiPost<void>('/auth/logout'),
  getCurrentUser: () => apiGet<CurrentUserVO>('/auth/current'),
  changePassword: (data: { oldPassword: string; newPassword: string; confirmPassword: string }) =>
    apiPut<void>('/auth/password', data),
  getPermissions: () => apiGet<unknown>('/auth/permissions'),
  // OAuth2/SSO
  getOAuth2Authorize: (provider: string) => apiGet<string>('/auth/oauth2/authorize', { params: { provider } }),
  oauth2Callback: (provider: string, code: string, state: string) =>
    apiGet<LoginVO>('/auth/oauth2/callback', { params: { provider, code, state } }),
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
  create: (data: UserDTO | Record<string, unknown>) => apiPost<UserVO>('/user', data),
  getByUserId: (userId: string) => apiGet<UserVO>(`/user/${userId}`),
  list: (params: Record<string, unknown>) => apiGet<PageResult<UserVO>>('/user/list', { params: normalizePageParams(params) }),
  update: (data: Partial<UserVO> | Record<string, unknown>) => apiPut<UserVO>('/user', data),
  delete: (id: number) => apiDelete<void>(`/user/${id}`),
}

// ==================== 角色管理 ====================
export const roleApi = {
  create: (data: RoleDTO | Record<string, unknown>) => apiPost<RoleVO>('/role', data),
  getByRoleId: (roleId: string) => apiGet<RoleVO>(`/role/${roleId}`),
  list: () => apiGet<RoleVO[]>('/role/list'),
  update: (data: Partial<RoleVO> | Record<string, unknown>) => apiPut<RoleVO>('/role', data),
  delete: (id: number) => apiDelete<void>(`/role/${id}`),
}

// ==================== 部门管理 ====================
export const deptApi = {
  create: (data: DeptDTO | Record<string, unknown>) => apiPost<DeptVO>('/dept', data),
  getByDeptId: (deptId: string) => apiGet<DeptVO>(`/dept/${deptId}`),
  tree: () => apiGet<DeptVO[]>('/dept/tree'),
  update: (data: Partial<DeptVO> | Record<string, unknown>) => apiPut<DeptVO>('/dept', data),
  delete: (id: number) => apiDelete<void>(`/dept/${id}`),
}

// ==================== 知识库空间 ====================
export const spaceApi = {
  create: (data: Partial<SpaceVO>) => apiPost<SpaceVO>('/space', data),
  get: (spaceId: string) => apiGet<SpaceVO>(`/space/${spaceId}`),
  list: (params: Record<string, unknown>) => apiGet<PageResult<SpaceVO>>('/space/list', { params: normalizePageParams(params) }),
  update: (data: Partial<SpaceVO>) => apiPut<SpaceVO>('/space', data),
  delete: (id: number) => apiDelete<void>(`/space/${id}`),
}

// ==================== 文档管理 ====================
export const documentApi = {
  create: (data: Partial<DocumentVO>) => apiPost<DocumentVO>('/document', data),
  get: (docId: string) => apiGet<DocumentVO>(`/document/${docId}`),
  list: (params: Record<string, unknown>) => apiGet<PageResult<DocumentVO>>('/document/page', { params: normalizePageParams(params) }),
  update: (data: Partial<DocumentVO>) => apiPut<DocumentVO>('/document', data),
  delete: (id: number) => apiDelete<void>(`/document/${id}`),
  upload: (spaceId: string, file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('spaceId', spaceId)
    return service.post('/document/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
  // 版本历史
  getVersions: (documentId: string) => apiGet<DocumentVersionVO[]>(`/document/${documentId}/versions`),
  // 权限管理
  getPermissions: (documentId: string) => apiGet<DocumentPermissionVO[]>(`/document/${documentId}/permissions`),
  addPermission: (data: Partial<DocumentPermissionVO>) => apiPost<void>('/document/permission', data),
  removePermission: (id: number) => apiDelete<void>(`/document/permission/${id}`),
}

// ==================== AI - 文档解析 ====================
export const parseApi = {
  submit: (data: { documentId: string; spaceId?: string }) => apiPost<void>('/ai/document/parse', data),
  getStatus: (taskId: string) => apiPost<unknown>('/ai/document/parse/status', undefined, { params: { taskId } }),
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
  embed: (data: { document_id: string }) => apiPost<void>('/ai/vector/embed', data),
  search: (data: Record<string, unknown>) => apiPost<unknown>('/ai/vector/search', data),
  hybridSearch: (data: Record<string, unknown>) => apiPost<unknown>('/ai/vector/hybrid-search', data),
}

// ==================== AI - RAG问答 ====================
export const ragApi = {
  query: (data: RAGQueryDTO) => apiPost<RAGAnswerVO>('/ai/rag/query', data),
  queryStream: (data: RAGQueryDTO) => fetch('/api/ai/rag/query/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': localStorage.getItem('token') || '',
    },
    body: JSON.stringify(data),
  }),
  getSession: (sessionId: string) => apiGet<{ session_id: string; messages: SessionVO['messages'] }>(`/ai/rag/sessions/${sessionId}`),
  clearSession: (sessionId: string) => apiDelete<void>(`/ai/rag/sessions/${sessionId}`),
  feedback: (data: { session_id: string; message_id: string; feedback: string }) => apiPost<void>('/ai/rag/feedback', data),
  getSessionHistory: (params: Record<string, unknown>) => apiGet<PageResult<SessionVO>>('/ai/rag/sessions', { params }),
  getDailyStats: () => service.get('/ai/rag/stats/daily'),
}

// ==================== AI - 记忆管理 ====================
export const memoryApi = {
  create: (data: Record<string, unknown>) => service.post('/ai/memory/create', data),
  search: (data: Record<string, unknown>) => service.post('/ai/memory/search', data),
  extract: (sessionId: string, userId: string) =>
    service.post('/ai/memory/extract', null, { params: { session_id: sessionId, user_id: userId } }),
  list: (params: Record<string, unknown>) => service.get('/ai/memory/list', { params }),
  delete: (memoryId: string) => service.delete(`/ai/memory/${memoryId}`),
}

// ==================== AI - Agent ====================
export const agentApi = {
  submit: (data: AgentSubmitDTO) => apiPost<AgentTaskVO>('/ai/agent/submit', data),
  getTask: (taskId: string) => apiGet<AgentTaskVO>(`/ai/agent/task/${taskId}`),
  cancel: (taskId: string) => apiPost<void>(`/ai/agent/task/${taskId}/cancel`),
  getTools: () => apiGet<string[]>('/ai/agent/tools'),
}

// ==================== AI - 代码沙箱 ====================
export const sandboxApi = {
  execute: (data: SandboxExecuteDTO) => apiPost<SandboxExecuteVO>('/ai/sandbox/execute', data),
  getResult: (taskId: string) => apiGet<SandboxExecuteVO>(`/ai/sandbox/result/${taskId}`),
  securityCheck: (code: string) => apiPost<{ safe?: boolean; issues?: string[] }>('/ai/sandbox/security-check', undefined, { params: { code } }),
}

// ==================== 审批管理 ====================
export const approvalApi = {
  submit: (data: Record<string, unknown>) => service.post('/approval/submit', null, { params: data }),
  process: (data: Record<string, unknown>) => service.post('/approval/process', data),
  getByDocId: (documentId: string) => service.get('/approval/document', { params: { documentId } }),
}

// ==================== 审计管理 ====================
export const auditApi = {
  listLogs: (params: Record<string, unknown>) => apiGet<unknown>('/audit/logs', { params }),
  listBadcases: (params: Record<string, unknown>) => apiGet<unknown>('/audit/badcases', { params }),
}

// ==================== Badcase管理 ====================
export const badcaseApi = {
  page: (params: Record<string, unknown>) => apiGet<unknown>('/audit/badcase/page', { params }),
  getStats: () => apiGet<unknown>('/audit/badcase/stats'),
  getDetail: (id: number) => apiGet<unknown>(`/audit/badcase/${id}`),
  process: (id: number, data: Record<string, unknown>) => apiPost<void>(`/audit/badcase/${id}/process`, data),
}

// ==================== 向量库管理 ====================
export const vectorAdminApi = {
  getStats: () => apiGet<unknown>('/vector/admin/stats'),
  page: (params: Record<string, unknown>) => apiGet<unknown>('/vector/admin/page', { params }),
  delete: (id: string) => apiDelete<void>(`/vector/admin/${id}`),
  rebuildIndex: (collection?: string) => apiPost<{ taskId?: string }>('/vector/admin/rebuild-index', undefined, { params: { collection } }),
  getCollections: () => apiGet<string[]>('/vector/admin/collections'),
}

// ==================== 角色权限管理 ====================
export const rolePermissionApi = {
  getRolePermissions: (roleId: string) => apiGet<string[]>(`/role/${roleId}/permissions`),
  saveRolePermissions: (roleId: string, permissionIds: string[]) =>
    apiPut<void>(`/role/${roleId}/permissions`, { permissionIds }),
  getUserRoles: (userId: string) => apiGet<string[]>(`/role/user/${userId}`),
  saveUserRoles: (userId: string, roleIds: string[]) =>
    apiPut<void>(`/role/user/${userId}`, { roleIds }),
}

// ==================== LLM配置管理 ====================
export const llmConfigApi = {
  // 分页查询配置列表
  page: (params: { pageNum: number; pageSize: number; provider?: string; isEnabled?: number }) =>
    apiGet<unknown>('/ai/llm-config/page', { params }),
  // 获取所有启用的配置
  listEnabled: () => apiGet<unknown>('/ai/llm-config/list'),
  // 获取配置详情
  getById: (configId: string) => apiGet<unknown>(`/ai/llm-config/${configId}`),
  // 创建配置
  create: (data: Record<string, unknown>) => apiPost<void>('/ai/llm-config', data),
  // 更新配置
  update: (configId: string, data: Record<string, unknown>) => apiPut<void>(`/ai/llm-config/${configId}`, data),
  // 删除配置
  delete: (configId: string) => apiDelete<void>(`/ai/llm-config/${configId}`),
  // 设置默认配置
  setDefault: (configId: string) => apiPost<void>(`/ai/llm-config/${configId}/set-default`),
  // 获取支持的提供商列表
  getProviders: () => apiGet<Array<{ label: string; value: string }>>('/ai/llm-config/providers'),
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
  create: (data: Record<string, unknown>) => service.post('/tenant', data),
  // 获取租户详情
  get: (tenantId: string) => service.get(`/tenant/${tenantId}`),
  // 更新租户
  update: (data: Record<string, unknown>) => service.put('/tenant', data),
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
  getDashboardData: () => apiGet<unknown>('/statistics/dashboard'),
  // 获取系统概览
  getOverview: () => apiGet<unknown>('/statistics/overview'),
  // 获取AI统计
  getAIStatistics: (startDate: string, endDate: string) => 
    apiGet<unknown>('/statistics/ai', { params: { startDate, endDate } }),
  // 获取文档统计
  getDocumentStatistics: () => apiGet<unknown>('/statistics/documents'),
  // 获取用户活跃度
  getUserActivity: (startDate: string, endDate: string) => 
    apiGet<unknown>('/statistics/user-activity', { params: { startDate, endDate } }),
  // 获取Badcase统计
  getBadcaseStatistics: () => apiGet<unknown>('/statistics/badcases'),
  // 获取热门查询
  getHotQueries: (limit: number = 10) => 
    apiGet<unknown>('/statistics/hot-queries', { params: { limit } }),
  // 获取热门文档
  getHotDocuments: (limit: number = 10) => 
    apiGet<unknown>('/statistics/hot-documents', { params: { limit } }),
}

// ==================== 文档互动(收藏/点赞/评论/浏览) ====================
export const interactionApi = {
  toggleFavorite: (documentId: string) => apiPost<FavoriteStateVO>(`/document/interaction/favorite/${documentId}`),
  checkFavorite: (documentId: string) => apiGet<FavoriteStateVO>(`/document/interaction/favorite/check/${documentId}`),
  getMyFavorites: () => apiGet<DocumentVO[]>('/document/interaction/favorite/list'),
  toggleLike: (documentId: string) => apiPost<LikeStateVO>(`/document/interaction/like/${documentId}`),
  getLikeCount: (documentId: string) => apiGet<LikeStateVO>(`/document/interaction/like/count/${documentId}`),
  addComment: (documentId: string, content: string, parentId?: number) =>
    apiPost<void>('/document/interaction/comment', undefined, { params: { documentId, content, parentId } }),
  getComments: (documentId: string) => apiGet<DocumentCommentVO[]>(`/document/interaction/comment/${documentId}`),
  deleteComment: (commentId: string) => apiDelete<void>(`/document/interaction/comment/${commentId}`),
  recordBrowse: (documentId: string) => apiPost<void>(`/document/interaction/browse/${documentId}`),
  getBrowseHistory: (limit: number = 20) => apiGet<DocumentVO[]>('/document/interaction/browse/history', { params: { limit } }),
  clearBrowseHistory: () => apiDelete<void>('/document/interaction/browse/history'),
}

// ==================== 系统配置管理 ====================
export const configApi = {
  getAllGroups: () => service.get('/config/groups'),
  getByGroup: (group: string) => service.get(`/config/group/${group}`),
  getValue: (key: string) => service.get(`/config/value/${key}`),
  updateValue: (key: string, value: string) => service.put('/config/value', { key, value }),
  add: (data: Record<string, unknown>) => service.post('/config', data),
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
    apiPost<{ apiKey: string; message: string }>('/user/api-key', data),
  list: () => apiGet<ApiKeyVO[]>('/user/api-key/list'),
  revoke: (keyId: string) => service.delete(`/user/api-key/${keyId}`),
  toggle: (keyId: string, enabled: boolean) =>
    service.put(`/user/api-key/${keyId}/toggle`, { enabled }),
}

// ==================== 多Agent协作 ====================
export const enterpriseAgentApi = {
  getScenarios: () => service.get('/ai/enterprise-agent/scenarios'),
  executeScenario: (data: { scenarioId: string; task: string; context?: Record<string, unknown>; maxRounds?: number }) =>
    service.post('/ai/enterprise-agent/execute', data),
  getSession: (sessionId: string) => service.get(`/ai/enterprise-agent/session/${sessionId}`),
}

// ==================== Badcase优化闭环 ====================
export const badcaseOptimizerApi = {
  createExperiment: (data: Record<string, unknown>) => service.post('/ai/badcase-optimizer/experiment', data),
  runExperiment: (experimentId: string) => service.post(`/ai/badcase-optimizer/experiment/${experimentId}/run`),
  getExperiment: (experimentId: string) => service.get(`/ai/badcase-optimizer/experiment/${experimentId}`),
  attributeBadcase: (data: Record<string, unknown>) => service.post('/ai/badcase-optimizer/attribute', data),
  optimizationLoop: (data: Record<string, unknown>) => service.post('/ai/badcase-optimizer/optimization-loop', data),
}

// ==================== 领域模型微调 ====================
export const domainTuningApi = {
  buildDataset: (data: { name: string; spaceIds: string[]; instructionTemplate?: string; maxSamples?: number }) =>
    service.post('/ai/domain-tuning/dataset/build', data),
  createJob: (data: Record<string, unknown>) => service.post('/ai/domain-tuning/job', data),
  startJob: (jobId: string) => service.post(`/ai/domain-tuning/job/${jobId}/start`),
  getJobStatus: (jobId: string) => service.get(`/ai/domain-tuning/job/${jobId}`),
  listJobs: () => service.get('/ai/domain-tuning/jobs'),
}

// ==================== 智能工作流引擎 ====================
export const workflowApi = {
  getTemplates: () => service.get('/ai/workflow/templates'),
  create: (data: Record<string, unknown>) => service.post('/ai/workflow/create', data),
  get: (workflowId: string) => service.get(`/ai/workflow/${workflowId}`),
  execute: (workflowId: string, inputData: Record<string, unknown>) => service.post(`/ai/workflow/${workflowId}/execute`, { inputData }),
  getExecution: (executionId: string) => service.get(`/ai/workflow/execution/${executionId}`),
}

// ==================== 第三方系统集成 ====================
export const thirdPartyApi = {
  sendWecomMessage: (data: Record<string, unknown>) => service.post('/ai/third-party/wecom/message', data),
  sendDingtalkMessage: (data: Record<string, unknown>) => service.post('/ai/third-party/dingtalk/message', data),
  sendWebhook: (data: Record<string, unknown>) => service.post('/ai/third-party/webhook', data),
  createOAProcess: (data: Record<string, unknown>) => service.post('/ai/third-party/oa/process', data),
  getWebhookLogs: (limit: number = 50) => service.get('/ai/third-party/webhook/logs', { params: { limit } }),
}

// ==================== 质量工具(忠实度/冲突) ====================
export const qualityToolApi = {
  validateFaithfulness: (data: { answer: string; context: string }) =>
    service.post('/ai/quality/faithfulness/validate', data),
  quickValidate: (data: { answer: string; context: string }) =>
    service.post('/ai/quality/faithfulness/quick', data),
  detectConflicts: (data?: Record<string, unknown>) => service.post('/ai/quality/memory/conflict-detect', data || {}),
  semanticDedup: (data?: Record<string, unknown>) => service.post('/ai/quality/memory/semantic-dedup', data || {}),
}

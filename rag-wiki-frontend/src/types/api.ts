/**
 * 通用API响应类型
 */
export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
  traceId?: string
}

export interface RoleInfo {
  roleId: string
  roleName: string
  roleCode: string
  roleLevel?: number
}

/**
 * 分页请求参数
 */
export interface PageParams {
  pageNum: number
  pageSize: number
}

/**
 * 分页响应数据
 */
export interface PageResult<T> {
  records: T[]
  total: number
  pageNum: number
  pageSize: number
  pages: number
}

/**
 * 用户相关类型
 */
export interface UserDTO {
  username: string
  password?: string
  realName: string
  email: string
  phone: string
  deptId: number
  status: number
  securityLevel: number
  tenantId: string
}

export interface UserVO {
  userId: string
  username: string
  realName: string
  email: string
  phone: string
  deptId: number
  deptName: string
  status: number
  securityLevel: number
  tenantId: string
  avatar: string
  extInfo: string
  createdAt: string
  updatedAt: string
  roleIds?: string[]
  roles?: RoleInfo[]
}

export interface CurrentUserVO extends UserVO {}

/**
 * 角色相关类型
 */
export interface RoleDTO {
  roleName: string
  roleCode: string
  description: string
  status: number
}

export interface RoleVO {
  roleId: string
  roleName: string
  roleCode: string
  description: string
  status: number
  permissions?: string[]
  createdAt: string
  updatedAt: string
}

/**
 * 部门相关类型
 */
export interface DeptDTO {
  deptName: string
  parentId: number
  sortOrder: number
  status: number
}

export interface DeptVO {
  deptId: number
  deptName: string
  deptCode?: string
  parentId: number
  parentName: string
  sort?: number
  sortOrder: number
  status: number
  children?: DeptVO[]
  createdAt: string
  updatedAt: string
}

/**
 * 知识库空间相关类型
 */
export interface SpaceDTO {
  spaceName: string
  description: string
  securityLevel: number
  tenantId: string
}

export interface SpaceVO {
  spaceId: string
  spaceName: string
  description: string
  securityLevel: number
  tenantId: string
  status?: number
  visibility?: string
  spaceCode?: string
  ownerDeptName?: string
  creatorName?: string
  documentCount?: number
  storageSize?: number
  members?: Array<{ avatar?: string }>
  createdAt: string
  updatedAt: string
}

/**
 * 文档相关类型
 */
export interface DocumentDTO {
  documentName: string
  spaceId: string
  parentId: string
  documentType: string
  content: string
  securityLevel: number
}

export interface DocumentVO {
  id?: number
  documentId: string
  documentName: string
  spaceId: string
  spaceName?: string
  documentType?: string
  filePath?: string
  fileSize?: number
  version?: string
  status?: number | string
  isFolder?: number
  securityLevel?: number
  creatorId?: string
  creatorName?: string
  content?: string
  documentContent?: string
  parsedAt?: string
  createdAt?: string
  updatedAt?: string
}

/**
 * 登录相关类型
 */
export interface LoginDTO {
  username: string
  password: string
}

export interface LoginVO {
  token: string
  userId: string
  username: string
  realName: string
  deptName?: string
  securityLevel?: number
}

/**
 * AI问答相关类型
 */
export interface RAGQueryDTO {
  question: string
  session_id?: string
  space_id?: string
  stream?: boolean
}

export interface RAGAnswerVO {
  answer: string
  sources: Array<{
    document_name: string
    page_num?: number
    chunk_id?: string
    score?: number
  }>
  session_id: string
  traceId?: string
}

export type AgentTaskStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'
export type AgentTaskStepStatus = 'pending' | 'running' | 'completed' | 'failed'

export interface AgentTaskStepVO {
  name: string
  description: string
  status: AgentTaskStepStatus
  timestamp: string
}

export interface AgentTaskSourceVO {
  type: string
  title: string
  content?: string
}

export interface AgentTaskVO {
  taskId: string
  title?: string
  query: string
  status: AgentTaskStatus
  progress?: number
  createdAt: string
  completedAt?: string
  result?: string
  steps?: AgentTaskStepVO[]
  sources?: AgentTaskSourceVO[]
}

export interface AgentSubmitDTO {
  query: string
  depth: 'quick' | 'standard' | 'deep'
  spaces?: string[]
  tools?: string[]
}

export interface SandboxExecuteDTO {
  code: string
  language: 'python' | 'javascript' | 'bash'
}

export interface SandboxExecuteVO {
  output?: string
  error?: string
  taskId?: string
}

export interface SessionMessageVO {
  id: string
  role?: string
  content?: string
}

export interface SessionVO {
  sessionId: string
  title?: string
  type?: string
  messageCount?: number
  createdAt?: string
  updatedAt?: string
  messages?: SessionMessageVO[]
}

export interface FavoriteStateVO {
  favorited: boolean
}

export interface LikeStateVO {
  liked: boolean
  likeCount: number
}

export interface DocumentCommentVO {
  id?: number
  commentId?: string
  documentId?: string
  userId?: string
  username?: string
  content: string
  createdAt?: string
}

export interface DocumentVersionVO {
  id?: number
  versionId?: string
  documentId?: string
  version: string
  changeLog?: string
  createdAt?: string
}

export interface DocumentPermissionVO {
  id?: number
  documentId?: string
  type?: string
  name?: string
  permission?: string
  allowedRoleIds?: string
  allowedUserIds?: string
  owningDeptId?: string
  createdAt?: string
}

export interface ApiKeyVO {
  keyId: string
  keyName: string
  keyPrefix?: string
  isEnabled?: boolean
  rateLimit?: number
  createdAt?: string
  lastUsedAt?: string
}

/**
 * LLM配置相关类型
 */
export interface LLMConfigDTO {
  provider: string
  modelName: string
  apiKey: string
  baseUrl: string
  maxTokens: number
  temperature: number
  isEnabled: number
}

export interface LLMConfigVO {
  configId: string
  provider: string
  modelName: string
  baseUrl: string
  maxTokens: number
  temperature: number
  isEnabled: number
  isDefault: number
  createdAt: string
  updatedAt: string
}

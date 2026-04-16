/**
 * 通用API响应类型
 */
export interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
  traceId?: string
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
}

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
  parentId: number
  parentName: string
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
  status: number
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
  documentId: string
  documentName: string
  spaceId: string
  spaceName: string
  documentType: string
  fileSize: number
  version: string
  status: number
  isFolder: number
  securityLevel: number
  creatorId: string
  creatorName: string
  createdAt: string
  updatedAt: string
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

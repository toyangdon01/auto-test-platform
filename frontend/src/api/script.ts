import request, { type PageParams, type PageResult } from '@/utils/request'

// 参数定义
export interface ParamDefinition {
  name: string
  label: string
  type: 'string' | 'integer' | 'float' | 'boolean' | 'select'
  required: boolean
  default?: string | number | boolean
  unit?: string
  description?: string
  options?: Array<{ label: string; value: string }>
}

export interface ScriptParameters {
  shared?: ParamDefinition[]
  deploy?: ParamDefinition[]
  run?: ParamDefinition[]
}

// 脚本类型
export interface Script {
  id: number
  name: string
  description?: string
  testCategory: string
  scriptType: 'shell' | 'python'
  fileList?: Array<{ path: string; size: number }>
  parameters?: ScriptParameters
  parseRules?: Record<string, unknown>
  currentVersion: string
  defaultTimeout?: number
  status: string
  createdAt: string
  updatedAt: string
}

export interface ScriptQueryParams extends PageParams {
  name?: string
  testCategory?: string
  status?: string
}

// 在线导入要逐文件调用 Gitee API，耗时随仓库文件数增长。
// 全局默认 30s 短于后端单次 API 的 60s 超时，会让浏览器先断开、后端却仍在导入。
// 必须大于后端 DOWNLOAD_BUDGET_MS（OnlineImportServiceImpl），由后端先给出明确错误。
const ONLINE_IMPORT_TIMEOUT = 300000

// 脚本 API
export const scriptApi = {
  list(params: ScriptQueryParams) {
    return request.get<PageResult<Script>>('/scripts', params)
  },

  get(id: number) {
    return request.get<Script>(`/scripts/${id}`)
  },

  create(data: Partial<Script>) {
    return request.post<Script>('/scripts', data)
  },

  update(id: number, data: Partial<Script>) {
    return request.put(`/scripts/${id}`, data)
  },

  delete(id: number) {
    return request.delete(`/scripts/${id}`)
  },

  // 获取脚本文件列表
  listFiles(scriptId: number) {
    return request.get<string[]>(`/scripts/${scriptId}/file-list`)
  },

  // 在线导入 - 预览
  previewOnline(data: {
    url: string
    branch?: string
    subDir?: string
    accessToken?: string
  }) {
    return request.post('/scripts/import/online/preview', data, { timeout: ONLINE_IMPORT_TIMEOUT })
  },

  // 在线导入 - 执行
  importOnline(data: {
    tempPath: string
    selectedScripts?: string[]
    conflictStrategy?: 'SKIP' | 'OVERWRITE' | 'RENAME'
  }) {
    return request.post('/scripts/import/online/execute', data, { timeout: ONLINE_IMPORT_TIMEOUT })
  },
}

// 任务类型
export interface Task {
  id: number
  name: string
  description?: string
  scriptId: number
  scriptVersion: string
  status: string
  progress?: number
  executionMode: string
  parallelMode: string
  maxParallel: number
  failureStrategy: string
  collectEnabled: boolean
  skipDeploy: boolean
  skipCleanup: boolean
  createdAt: string
  startedAt?: string
  finishedAt?: string
}

export interface TaskCreateParams {
  name: string
  description?: string
  scriptId: number
  scriptVersion: string
  serverIds: number[]
  sharedParams?: Record<string, unknown>
  deployParams?: Record<string, unknown>
  runParams?: Record<string, unknown>
  lifecycleConfig?: {
    skipDeploy?: boolean
    skipCleanup?: boolean
    deployTimeout?: number
    cleanupTimeout?: number
  }
  collectEnabled?: boolean
  collectConfig?: Record<string, unknown>
  executionMode: string
  scheduledTime?: string
  parallelMode?: string
  maxParallel?: number
  failureStrategy?: string
}

export interface TaskQueryParams extends PageParams {
  name?: string
  status?: string
  scriptId?: number
}

// 任务 API
export const taskApi = {
  list(params: TaskQueryParams) {
    return request.get<PageResult<Task>>('/tasks', params)
  },

  get(id: number) {
    return request.get<Task>(`/tasks/${id}`)
  },

  create(data: TaskCreateParams) {
    return request.post<Task>('/tasks', data)
  },

  update(id: number, data: Partial<TaskCreateParams>) {
    return request.put<Task>(`/tasks/${id}`, data)
  },

  delete(id: number) {
    return request.delete(`/tasks/${id}`)
  },

  execute(id: number) {
    return request.post(`/tasks/${id}/execute`)
  },

  cancel(id: number) {
    return request.post(`/tasks/${id}/cancel`)
  },

  retry(id: number) {
    return request.post(`/tasks/${id}/retry`)
  },

  getProgress(id: number) {
    return request.get(`/tasks/${id}/progress`)
  },

  getLogs(id: number, serverId?: number, stage?: string) {
    return request.get(`/tasks/${id}/logs`, { serverId, stage })
  },
}

import request, { type PageParams, type PageResult } from '@/utils/request'

// 脚本类型
export interface Script {
  id: number
  name: string
  description?: string
  testCategory: string
  lifecycleMode: 'simple' | 'full'
  hasDeploy: boolean
  hasCleanup: boolean
  deployEntry?: string
  cleanupEntry?: string
  currentVersion: string
  status: string
  createdAt: string
  updatedAt: string
}

export interface ScriptQueryParams extends PageParams {
  name?: string
  testCategory?: string
  status?: string
}

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

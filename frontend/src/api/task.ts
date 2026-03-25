import request, { type PageParams, type PageResult } from '@/utils/request'

// 收集的文件类型
export interface CollectedFile {
  name: string
  path: string
  size?: number
  contentType?: string
  content?: string  // 文本文件内容
}

// 任务步骤类型
export interface TaskStep {
  id: number
  taskId: number
  serverId: number
  serverName?: string
  serverHost?: string
  stepName: string
  displayName: string
  script: string
  dependsOn?: string
  params?: Record<string, any>
  status: 'pending' | 'waiting' | 'running' | 'success' | 'failed' | 'skipped'
  waitReason?: string
  startedAt?: string
  finishedAt?: string
  exitCode?: number
  output?: string
  errorMessage?: string
  resultCollector?: boolean
  parsedResult?: Record<string, any>
  outputFiles?: CollectedFile[]  // 收集的文件列表
  createdAt: string
  updatedAt: string
}

// 任务 API
export const taskApi = {
  // 获取任务步骤
  getSteps(taskId: number) {
    return request.get<TaskStep[]>(`/tasks/${taskId}/steps`)
  },
}
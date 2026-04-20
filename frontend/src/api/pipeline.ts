import request from '@/utils/request'

export interface Pipeline {
  id: number
  name: string
  description: string
  executionMode: string
  enabled: boolean
  createdBy: string
  createdAt: string
  updatedAt: string
}

export interface PipelineTask {
  id: number
  pipelineId: number
  name: string
  scriptId: number
  orderNum: number
  serverIds: number[]
  enabled: boolean
  dependsOn: string
  createdAt: string
  updatedAt: string
}

export interface PipelineRun {
  id: number
  pipelineId: number
  pipelineName: string
  status: string
  startedAt: string
  finishedAt: string
  triggeredBy: string
  createdAt: string
}

export interface PipelineRunTask {
  id: number
  taskId: number
  taskName: string
  status: string
}

// 获取编排列表
export function listPipelines(params: { page?: number; size?: number; keyword?: string }) {
  return request.get('/pipelines', params)
}

// 获取编排详情
export function getPipeline(id: number) {
  return request.get(`/pipelines/${id}`)
}

// 创建编排
export function createPipeline(data: {
  name: string
  description?: string
  executionMode?: string
  tasks?: Array<{
    name: string
    scriptId: number
    serverIds?: number[]
    dependsOn?: string
  }>
}) {
  return request.post('/pipelines', data)
}

// 更新编排
export function updatePipeline(id: number, data: {
  name: string
  description?: string
  executionMode?: string
  enabled?: boolean
  tasks?: Array<{
    name: string
    scriptId: number
    serverIds?: number[]
    dependsOn?: string
  }>
}) {
  return request.put(`/pipelines/${id}`, data)
}

// 删除编排
export function deletePipeline(id: number) {
  return request.delete(`/pipelines/${id}`)
}

// 获取编排任务列表
export function getPipelineTasks(pipelineId: number) {
  return request.get(`/pipelines/${pipelineId}/tasks`)
}

// 执行编排
export function executePipeline(pipelineId: number, serverMapping?: Record<number, number[]>) {
  return request.post(`/pipelines/${pipelineId}/execute`, serverMapping || {})
}

// 获取执行记录列表
export function listPipelineRuns(params: { pipelineId?: number; page?: number; size?: number }) {
  return request.get('/pipelines/runs', params)
}

// 获取执行详情
export function getPipelineRun(runId: number) {
  return request.get(`/pipelines/runs/${runId}`)
}

// 获取执行任务列表
export function getPipelineRunTasks(runId: number) {
  return request.get(`/pipelines/runs/${runId}/tasks`)
}

// 取消执行
export function cancelPipelineRun(runId: number) {
  return request.post(`/pipelines/runs/${runId}/cancel`)
}

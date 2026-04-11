import request, { type PageParams, type PageResult } from '@/utils/request'

// 类型定义
export interface Server {
  id: number
  name: string
  host: string
  port: number
  username: string
  authType: string
  osType?: string
  osVersion?: string
  cpuCores?: number
  cpuModel?: string
  cpuArch?: string
  memorySize?: string
  memoryTotalMb?: number
  diskInfo?: object
  groupId?: number
  tags?: string[]
  remark?: string
  status: string
  lastCheckAt?: string
  createdAt: string
  updatedAt: string
}

export interface ServerCreateParams {
  name: string
  host: string
  port?: number
  username: string
  authType: string
  authSecret: string
  groupId?: number
  tags?: string[]
  remark?: string
}

export interface ServerQueryParams extends PageParams {
  name?: string
  status?: string
  groupId?: number
}

// API 方法
export const serverApi = {
  // 获取服务器列表
  list(params: ServerQueryParams) {
    return request.get<PageResult<Server>>('/servers', params)
  },

  // 获取服务器详情
  get(id: number) {
    return request.get<Server>(`/servers/${id}`)
  },

  // 创建服务器
  create(data: ServerCreateParams) {
    return request.post<Server>('/servers', data)
  },

  // 更新服务器
  update(id: number, data: ServerCreateParams) {
    return request.put<Server>(`/servers/${id}`, data)
  },

  // 删除服务器
  delete(id: number) {
    return request.delete(`/servers/${id}`)
  },

  // 测试连接
  testConnection(id: number) {
    return request.post<{ connected: boolean }>(`/servers/${id}/test`)
  },

  // 获取状态
  getStatus(id: number) {
    return request.get<Server>(`/servers/${id}/status`)
  },

  // 刷新信息
  refresh(id: number) {
    return request.post<Server>(`/servers/${id}/refresh`)
  },

  // 批量导入
  batchCreate(data: ServerCreateParams[]) {
    return request.post<{ total: number; success: number; failed: number; errors: string }>('/servers/batch', data)
  },
}

// 服务器分组 API
export interface ServerGroup {
  id: number
  name: string
  description?: string
  serverCount?: number
  createdAt: string
}

export const serverGroupApi = {
  list() {
    return request.get<ServerGroup[]>('/server-groups')
  },

  create(data: { name: string; description?: string }) {
    return request.post<ServerGroup>('/server-groups', data)
  },

  update(id: number, data: { name: string; description?: string }) {
    return request.put(`/server-groups/${id}`, data)
  },

  delete(id: number) {
    return request.delete(`/server-groups/${id}`)
  },
}

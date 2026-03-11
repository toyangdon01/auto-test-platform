import request from '@/utils/request'

export interface ResourceFile {
  id: number
  name: string
  storagePath: string
  fileSize: number
  fileType: string
  category: string
  checksum: string
  description: string
  createdBy: string
  createdAt: string
  updatedAt: string
}

export interface ScriptResource {
  id: number
  scriptId: number
  resourceId: number
  targetPath: string
  permissions: string
  uploadOrder: number
  resource?: ResourceFile
}

export interface AddResourceRequest {
  resourceId: number
  targetPath?: string
  permissions?: string
  uploadOrder?: number
}

export interface UpdateResourceRequest {
  targetPath?: string
  permissions?: string
  uploadOrder?: number
}

export const resourceApi = {
  // 上传资源文件
  upload(file: File, fileType?: string, category?: string, description?: string) {
    const formData = new FormData()
    formData.append('file', file)
    if (fileType) formData.append('fileType', fileType)
    if (category) formData.append('category', category)
    if (description) formData.append('description', description)

    return request.post<ResourceFile>('/resources/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 300000 // 5分钟超时（支持大文件）
    })
  },

  // 分页查询
  getPage(params: {
    pageNum?: number
    pageSize?: number
    name?: string
    fileType?: string
    category?: string
  }) {
    return request.get<{ records: ResourceFile[], total: number }>('/resources', { params })
  },

  // 获取详情
  getById(id: number) {
    return request.get<ResourceFile>(`/resources/${id}`)
  },

  // 删除
  delete(id: number) {
    return request.delete(`/resources/${id}`)
  },

  // 下载
  getDownloadUrl(id: number) {
    return `/api/v1/resources/${id}/download`
  },

  // 检查文件是否已存在
  checkExists(checksum: string) {
    return request.get<{ exists: boolean, file?: ResourceFile }>('/resources/check', { params: { checksum } })
  }
}

export const scriptResourceApi = {
  // 获取脚本的资源列表
  getByScriptId(scriptId: number) {
    return request.get<ScriptResource[]>(`/scripts/${scriptId}/resources`)
  },

  // 添加资源关联
  add(scriptId: number, data: AddResourceRequest) {
    return request.post<ScriptResource>(`/scripts/${scriptId}/resources`, data)
  },

  // 更新资源关联
  update(scriptId: number, resourceId: number, data: UpdateResourceRequest) {
    return request.put<ScriptResource>(`/scripts/${scriptId}/resources/${resourceId}`, data)
  },

  // 删除资源关联
  remove(scriptId: number, resourceId: number) {
    return request.delete(`/scripts/${scriptId}/resources/${resourceId}`)
  }
}

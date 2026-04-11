// 导出所有 API 模块
export * from './server'
export * from './script'

// 通用 API
import request from '@/utils/request'

export const commonApi = {
  // 获取系统配置
  getConfig(key: string) {
    return request.get<string>(`/config/${key}`)
  },

  // 上传文件
  uploadFile(file: File) {
    const formData = new FormData()
    formData.append('file', file)
    return request.post<{ url: string; filename: string }>('/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
}

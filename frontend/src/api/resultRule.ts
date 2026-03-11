import request from '@/utils/request'

export interface ResultRule {
  id: number
  scriptId: number | null
  name: string
  description: string
  parserType: 'builtin' | 'script'
  builtinFormat: 'key_value' | 'json' | null
  scriptSource: 'package' | 'inline' | null
  scriptPath: string | null
  scriptContent: string | null
  scriptLanguage: 'python' | 'shell' | null
  inputSource: 'stdout' | 'file'
  filePattern: string | null
  outputFormat: 'json' | 'csv'
  enabled: boolean
  createdAt: string
  updatedAt: string
}

export interface TestParseParams {
  parserType: 'builtin' | 'script'
  builtinFormat?: 'key_value' | 'json'
  scriptSource?: 'package' | 'inline'
  scriptContent?: string
  scriptLanguage?: 'python' | 'shell'
  sampleInput: string
}

export const resultRuleApi = {
  /**
   * 获取脚本的解析规则列表
   */
  listByScript(scriptId: number) {
    return request.get<ResultRule[]>(`/result-rules/script/${scriptId}`)
  },

  /**
   * 获取全局解析规则列表
   */
  listGlobal() {
    return request.get<ResultRule[]>('/result-rules/global')
  },

  /**
   * 获取解析规则详情
   */
  getById(id: number) {
    return request.get<ResultRule>(`/result-rules/${id}`)
  },

  /**
   * 创建解析规则
   */
  create(data: Partial<ResultRule>) {
    return request.post<ResultRule>('/result-rules', data)
  },

  /**
   * 更新解析规则
   */
  update(id: number, data: Partial<ResultRule>) {
    return request.put<ResultRule>(`/result-rules/${id}`, data)
  },

  /**
   * 删除解析规则
   */
  delete(id: number) {
    return request.delete(`/result-rules/${id}`)
  },

  /**
   * 启用/禁用规则
   */
  setEnabled(id: number, enabled: boolean) {
    return request.put(`/result-rules/${id}/enabled`, { enabled })
  },

  /**
   * 测试解析规则
   */
  testParse(params: TestParseParams) {
    return request.post<Record<string, any>>('/result-rules/test', params)
  },
}

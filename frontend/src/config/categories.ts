/**
 * 测试分类配置
 * 用于脚本分类和资源文件分类
 */

export interface CategoryOption {
  value: string
  label: string
}

export const TEST_CATEGORIES: CategoryOption[] = [
  { value: 'cpu', label: 'CPU测试' },
  { value: 'memory', label: '内存测试' },
  { value: 'disk', label: '磁盘测试' },
  { value: 'network', label: '网络测试' },
  { value: 'mixed', label: '综合测试' },
  { value: 'database', label: '数据库' },
  { value: 'middleware', label: '中间件' },
  { value: 'java', label: 'JAVA' },
  { value: 'storage', label: '存储' },
  { value: 'bigdata', label: '大数据' },
]

export function getCategoryLabel(value: string): string {
  const category = TEST_CATEGORIES.find(c => c.value === value)
  return category?.label || value
}

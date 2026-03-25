<template>
  <div class="page-card" v-loading="loading">
    <div class="page-header">
      <el-page-header @back="$router.back()">
        <template #content>
          <span class="title">结果详情</span>
        </template>
      </el-page-header>
    </div>

    <template v-if="detail">
      <!-- 基本信息 -->
      <el-descriptions :column="3" border class="mb-4">
        <el-descriptions-item label="任务名称">
          {{ detail.taskName || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="服务器">
          {{ detail.serverName }} ({{ detail.serverIp }})
        </el-descriptions-item>
        <el-descriptions-item label="执行结果">
          <el-tag :type="resultTagType" size="large">
            {{ resultText }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="综合评分">
          <span :class="scoreClass" class="score-text">{{ detail.overallScore ?? '-' }}</span>
          <span v-if="detail.overallScore != null">分</span>
        </el-descriptions-item>
        <el-descriptions-item label="执行时长">
          {{ formatDuration(detail.durationMs) }}
        </el-descriptions-item>
        <el-descriptions-item label="退出码">
          <el-tag :type="detail.exitCode === 0 ? 'success' : 'danger'" size="small">
            {{ detail.exitCode ?? '-' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="开始时间">
          {{ formatTime(detail.startedAt) }}
        </el-descriptions-item>
        <el-descriptions-item label="结束时间">
          {{ formatTime(detail.finishedAt) }}
        </el-descriptions-item>
        <el-descriptions-item label="结果原因" :span="3">
          {{ detail.resultReason || '-' }}
        </el-descriptions-item>
      </el-descriptions>

      <!-- 解析结果 -->
      <template v-if="hasParsedData">
        <el-divider content-position="left">
          <div class="divider-header">
            <span><el-icon><DataLine /></el-icon> 解析结果</span>
            <el-button type="primary" size="small" @click="exportToExcel">
              <el-icon><Download /></el-icon> 导出 Excel
            </el-button>
          </div>
        </el-divider>

        <div class="parsed-data">
          <el-descriptions :column="1" border>
            <el-descriptions-item 
              v-for="(item, index) in parsedFormItems" 
              :key="index"
              :label="item.label"
              label-align="left"
              label-class-name="parsed-label"
            >
              {{ item.displayValue }}
            </el-descriptions-item>
          </el-descriptions>
        </div>
      </template>

      <!-- 指标数据 -->
      <el-divider content-position="left">
        <el-icon><DataAnalysis /></el-icon>
        指标数据
      </el-divider>

      <el-table :data="detail.metricList || []" stripe v-if="detail.metricList?.length">
        <el-table-column prop="name" label="指标名称" min-width="150">
          <template #default="{ row }">
            <span>{{ row.name }}</span>
            <el-text type="info" size="small" class="ml-2">({{ row.key }})</el-text>
          </template>
        </el-table-column>
        <el-table-column prop="value" label="数值" width="150">
          <template #default="{ row }">
            <span class="metric-value">{{ formatValue(row.value) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" width="80">
          <template #default="{ row }">
            {{ row.unit || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="baseline" label="基准线" width="100">
          <template #default="{ row }">
            {{ row.baseline ?? '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'normal' ? 'success' : 'warning'" size="small">
              {{ row.status === 'normal' ? '正常' : '异常' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-else description="暂无指标数据" />

      <!-- 原始输出 -->
      <el-divider content-position="left">
        <el-icon><Document /></el-icon>
        原始输出
      </el-divider>

      <el-tabs v-model="activeTab">
        <el-tab-pane label="标准输出" name="stdout">
          <div class="output-box">
            <pre>{{ detail.rawOutput || '无输出' }}</pre>
          </div>
        </el-tab-pane>
        <el-tab-pane label="错误输出" name="stderr">
          <div class="output-box error">
            <pre>{{ detail.rawError || '无错误' }}</pre>
          </div>
        </el-tab-pane>
      </el-tabs>

      <!-- 输出文件 -->
      <template v-if="detail.outputFiles && Object.keys(detail.outputFiles).length">
        <el-divider content-position="left">
          <el-icon><Folder /></el-icon>
          输出文件
        </el-divider>

        <el-table :data="outputFileList" stripe>
          <el-table-column prop="name" label="文件名" min-width="200" />
          <el-table-column prop="size" label="大小" width="120">
            <template #default="{ row }">
              {{ formatFileSize(row.size) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150">
            <template #default="{ row }">
              <el-button 
                v-if="row.storagePath && row.status === 'success'" 
                type="primary" 
                link 
                size="small"
                @click="downloadFile(row)"
              >
                下载
              </el-button>
              <span v-else-if="row.status === 'not_found'" class="text-warning">文件不存在</span>
              <span v-else-if="row.status === 'error'" class="text-danger">{{ row.error || '下载失败' }}</span>
              <span v-else class="text-muted">不可用</span>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </template>

    <el-empty v-else-if="!loading" description="结果不存在" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { DataAnalysis, Document, Folder, DataLine, Download } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import axios from 'axios'
import request from '@/utils/request'

interface MetricItem {
  key: string
  name: string
  value: any
  unit?: string
  baseline?: any
  status: string
}

interface TestResultDetail {
  id: number
  taskId: number
  taskName: string
  serverId: number
  serverName: string
  serverIp: string
  result: string
  resultReason: string
  overallScore: number
  metrics: Record<string, any>
  metricList: MetricItem[]
  parsedData: Record<string, any>
  rawOutput: string
  rawError: string
  outputFiles: Record<string, any>
  exitCode: number
  durationMs: number
  startedAt: string
  finishedAt: string
  createdAt: string
}

const route = useRoute()
const loading = ref(true)
const detail = ref<TestResultDetail | null>(null)
const activeTab = ref('stdout')

const resultTagType = computed(() => {
  const map: Record<string, string> = {
    pass: 'success',
    fail: 'danger',
    warning: 'warning',
    error: 'danger'
  }
  return map[detail.value?.result || ''] || 'info'
})

const resultText = computed(() => {
  const map: Record<string, string> = {
    pass: '通过',
    fail: '失败',
    warning: '警告',
    error: '错误'
  }
  return map[detail.value?.result || ''] || detail.value?.result || '-'
})

const scoreClass = computed(() => {
  const score = detail.value?.overallScore
  if (score == null) return ''
  if (score >= 90) return 'score-excellent'
  if (score >= 70) return 'score-good'
  if (score >= 50) return 'score-warning'
  return 'score-danger'
})

const outputFileList = computed(() => {
  if (!detail.value?.outputFiles?.files) return []
  return detail.value.outputFiles.files.map((file: any) => ({
    name: file.name || '未知文件',
    originalPath: file.originalPath || '',
    storagePath: file.storagePath || '',
    size: file.size || 0,
    status: file.status || 'unknown',
    error: file.error || ''
  }))
})

// 是否有解析数据
const hasParsedData = computed(() => {
  return detail.value?.parsedData && Object.keys(detail.value.parsedData).length > 0
})

// 解析 parsedData 为表单项列表
const parsedFormItems = computed(() => {
  if (!detail.value?.parsedData) return []
  
  const items: Array<{ label: string; value: any; displayValue: string; rawValue: string }> = []
  
  for (const [key, rawValue] of Object.entries(detail.value.parsedData)) {
    // 跳过 parser 字段
    const keyLower = key.toLowerCase()
    if (keyLower === 'parser' || keyLower.includes('parser')) {
      continue
    }
    
    // 尝试解析字符串值（可能是 JSON 或 key-value 格式）
    const parsed = parseValue(rawValue)
    
    if (parsed.isObject) {
      // 如果是对象/JSON，展开每个字段
      for (const [subKey, subValue] of Object.entries(parsed.data)) {
        // 跳过子字段中的 parser
        if (subKey.toLowerCase() === 'parser' || subKey.toLowerCase().includes('parser')) {
          continue
        }
        items.push({
          label: formatParsedKey(subKey),
          value: subValue,
          displayValue: formatDisplayValue(subValue),
          rawValue: String(subValue)
        })
      }
    } else if (parsed.isKeyValue) {
      // 如果是 key-value 格式，展开每个字段
      for (const [subKey, subValue] of Object.entries(parsed.data)) {
        // 跳过子字段中的 parser
        if (subKey.toLowerCase() === 'parser' || subKey.toLowerCase().includes('parser')) {
          continue
        }
        items.push({
          label: formatParsedKey(subKey),
          value: subValue,
          displayValue: String(subValue),
          rawValue: String(subValue)
        })
      }
    } else {
      // 普通值，直接显示
      items.push({
        label: formatParsedKey(key),
        value: parsed.data,
        displayValue: formatDisplayValue(parsed.data),
        rawValue: String(parsed.data)
      })
    }
  }
  
  return items
})

// 解析值：支持 JSON、key-value、普通值
const parseValue = (value: any): { data: any; isObject: boolean; isKeyValue: boolean } => {
  // 如果已经是对象
  if (typeof value === 'object' && value !== null) {
    return { data: value, isObject: !Array.isArray(value), isKeyValue: false }
  }
  
  // 如果不是字符串，直接返回
  if (typeof value !== 'string') {
    return { data: value, isObject: false, isKeyValue: false }
  }
  
  const trimmed = value.trim()
  
  // 尝试解析为 JSON 对象
  if (trimmed.startsWith('{')) {
    try {
      const parsed = JSON.parse(trimmed)
      if (typeof parsed === 'object' && parsed !== null && !Array.isArray(parsed)) {
        return { data: parsed, isObject: true, isKeyValue: false }
      }
    } catch (e) {
      // JSON 解析失败，继续尝试其他格式
    }
  }
  
  // 尝试解析为 key-value 格式（每行一个 key=value 或 key: value）
  const kvPattern = /^([^=:]+)[=:]\s*(.+)$/
  const lines = trimmed.split(/\r?\n/).filter(line => line.trim())
  const kvData: Record<string, string> = {}
  let hasKvFormat = false
  
  for (const line of lines) {
    const match = line.match(kvPattern)
    if (match) {
      hasKvFormat = true
      kvData[match[1].trim()] = match[2].trim()
    }
  }
  
  if (hasKvFormat && Object.keys(kvData).length > 0) {
    return { data: kvData, isObject: false, isKeyValue: true }
  }
  
  // 无法解析，返回原值
  return { data: value, isObject: false, isKeyValue: false }
}

// 下载收集的文件
const downloadFile = async (file: any) => {
  if (!file.storagePath) {
    ElMessage.warning('文件路径不存在')
    return
  }
  
  try {
    const response = await axios.get(`/api/v1/results/download`, {
      params: { path: file.storagePath },
      responseType: 'blob'
    })
    
    const url = window.URL.createObjectURL(new Blob([response.data]))
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', file.name.endsWith('.tar.gz') ? file.name : file.name + '.tar.gz')
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.URL.revokeObjectURL(url)
  } catch (error) {
    console.error('下载失败:', error)
    ElMessage.error('下载失败')
  }
}

const formatDuration = (ms: number | null) => {
  if (ms == null) return '-'
  if (ms < 1000) return `${ms}ms`
  if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`
  const minutes = Math.floor(ms / 60000)
  const seconds = Math.round((ms % 60000) / 1000)
  return `${minutes}m ${seconds}s`
}

const formatTime = (time: string | null) => {
  if (!time) return '-'
  return time.replace('T', ' ').substring(0, 19)
}

const formatValue = (value: any) => {
  if (value == null) return '-'
  if (typeof value === 'number') {
    return value % 1 === 0 ? value : value.toFixed(2)
  }
  return value
}

const formatFileSize = (bytes: number) => {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

const formatParsedKey = (key: string) => {
  if (!key) return ''
  // 处理特殊键名
  return key
    .replace(/"/g, '')
    .replace(/_/g, ' ')
    .replace(/([A-Z])/g, ' $1')
    .trim()
    .split(' ')
    .map(word => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
    .join(' ')
}

// 格式化显示值
const formatDisplayValue = (value: any): string => {
  if (value == null) return '-'
  if (typeof value === 'boolean') return value ? '是' : '否'
  if (Array.isArray(value)) return value.join(', ')
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

// 复制到剪贴板
const copyToClipboard = async (text: string) => {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制到剪贴板')
  } catch (err) {
    console.error('复制失败:', err)
    ElMessage.error('复制失败')
  }
}

// 导出为 Excel
const exportToExcel = () => {
  if (!parsedFormItems.value.length) {
    ElMessage.warning('没有可导出的数据')
    return
  }
  
  // 创建 CSV 内容
  const headers = ['指标名称', '指标值']
  const rows = parsedFormItems.value.map(item => [item.label, item.displayValue])
  
  // 使用 BOM 确保 Excel 正确识别 UTF-8 编码
  let csvContent = '\uFEFF'
  csvContent += headers.join(',') + '\n'
  csvContent += rows.map(row => row.map(cell => `"${String(cell).replace(/"/g, '""')}"`).join(',')).join('\n')
  
  // 创建 Blob 并下载
  const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.setAttribute('download', `解析结果_${detail.value?.taskName || 'export'}_${new Date().toISOString().slice(0, 10)}.csv`)
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
  
  ElMessage.success('导出成功')
}

const fetchDetail = async () => {
  const id = route.params.id as string
  if (!id) return

  loading.value = true
  try {
    const res = await request.get<TestResultDetail>(`/results/${id}/detail`)
    if (res.code === 0) {
      detail.value = res.data
    }
  } catch (e) {
    console.error('获取详情失败', e)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchDetail()
})
</script>

<style scoped>
.score-text {
  font-size: 20px;
  font-weight: bold;
}

.score-excellent {
  color: #67c23a;
}

.score-good {
  color: #409eff;
}

.score-warning {
  color: #e6a23c;
}

.score-danger {
  color: #f56c6c;
}

.metric-value {
  font-family: 'Monaco', 'Menlo', monospace;
  font-size: 14px;
}

.output-box {
  background: #1e1e1e;
  border-radius: 4px;
  padding: 16px;
  max-height: 400px;
  overflow: auto;
}

.output-box pre {
  margin: 0;
  color: #d4d4d4;
  font-family: 'Monaco', 'Menlo', monospace;
  font-size: 13px;
  white-space: pre-wrap;
  word-break: break-all;
}

.output-box.error {
  background: #2d1f1f;
}

.output-box.error pre {
  color: #f56c6c;
}

.mb-4 {
  margin-bottom: 16px;
}

.ml-2 {
  margin-left: 8px;
}

.parsed-data {
  margin-bottom: 20px;
}

:deep(.parsed-label) {
  width: 160px;
  font-weight: 500;
}

.divider-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.divider-header span {
  display: flex;
  align-items: center;
  gap: 6px;
}
</style>

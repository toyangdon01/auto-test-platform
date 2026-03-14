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
      <template v-if="detail.parsedData && Object.keys(detail.parsedData).length">
        <el-divider content-position="left">
          <el-icon><DataLine /></el-icon>
          解析结果
        </el-divider>

        <div class="parsed-data">
          <el-descriptions :column="3" border>
            <el-descriptions-item 
              v-for="(value, key) in detail.parsedData" 
              :key="key"
              :label="formatParsedKey(key as string)"
            >
              <span class="parsed-value">{{ formatParsedValue(value) }}</span>
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
import { DataAnalysis, Document, Folder, DataLine } from '@element-plus/icons-vue'
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

const formatParsedValue = (value: any) => {
  if (value == null) return '-'
  if (typeof value === 'object') {
    return JSON.stringify(value, null, 2)
  }
  return String(value)
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

.parsed-value {
  font-family: 'Monaco', 'Menlo', monospace;
  font-size: 13px;
  color: #409eff;
}
</style>

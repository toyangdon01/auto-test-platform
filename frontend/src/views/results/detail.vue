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
          <el-table-column label="操作" width="100">
            <template #default="{ row }">
              <el-button type="primary" link size="small">下载</el-button>
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
import { DataAnalysis, Document, Folder } from '@element-plus/icons-vue'
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
  if (!detail.value?.outputFiles) return []
  return Object.entries(detail.value.outputFiles).map(([name, info]: [string, any]) => ({
    name,
    size: info?.size || 0,
    path: info?.path || ''
  }))
})

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
</style>

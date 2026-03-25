<template>
  <div class="page-card">
    <div class="page-header">
      <h3 class="page-title">测试结果列表</h3>
    </div>

    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="关键词">
          <el-input
            v-model="searchForm.keyword"
            placeholder="搜索任务名称/服务器"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item label="结果状态">
          <el-select v-model="searchForm.result" placeholder="全部" clearable style="width: 120px">
            <el-option label="通过" value="pass" />
            <el-option label="警告" value="warning" />
            <el-option label="失败" value="fail" />
            <el-option label="错误" value="error" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="success" @click="handleExport">导出</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-row" v-if="statistics">
      <div class="stat-card">
        <div class="stat-value">{{ statistics.total }}</div>
        <div class="stat-label">总结果</div>
      </div>
      <div class="stat-card success">
        <div class="stat-value">{{ statistics.pass }}</div>
        <div class="stat-label">通过</div>
      </div>
      <div class="stat-card warning">
        <div class="stat-value">{{ statistics.warning }}</div>
        <div class="stat-label">警告</div>
      </div>
      <div class="stat-card danger">
        <div class="stat-value">{{ statistics.fail }}</div>
        <div class="stat-label">失败</div>
      </div>
      <div class="stat-card info">
        <div class="stat-value">{{ statistics.passRate }}%</div>
        <div class="stat-label">通过率</div>
      </div>
    </div>

    <!-- 表格 -->
    <el-table :data="results" stripe v-loading="loading">
      <el-table-column prop="id" label="结果编号" width="100" />
      <el-table-column prop="taskName" label="所属任务" min-width="150">
        <template #default="{ row }">
          <span>{{ row.taskName || `任务 #${row.taskId}` }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="scriptName" label="所属脚本" min-width="150">
        <template #default="{ row }">
          <span>{{ row.scriptName || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="serverName" label="执行服务器" min-width="150">
        <template #default="{ row }">
          <span>{{ row.serverName || `服务器 #${row.serverId}` }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="result" label="结果" width="100">
        <template #default="{ row }">
          <el-tag :type="getResultType(row.result)">{{ getResultLabel(row.result) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="overallScore" label="得分" width="80">
        <template #default="{ row }">
          <span :class="getScoreClass(row.overallScore)">{{ row.overallScore ?? '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="durationMs" label="耗时" width="100">
        <template #default="{ row }">
          {{ formatDuration(row.durationMs) }}
        </template>
      </el-table-column>
      <el-table-column prop="resultReason" label="结果原因" min-width="200" show-overflow-tooltip />
      <el-table-column prop="createdAt" label="创建时间" width="160">
        <template #default="{ row }">
          {{ formatTime(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click="handleDetail(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pagination-wrapper">
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="pagination.total"
        layout="total, sizes, prev, pager, next"
        @size-change="fetchResults"
        @current-change="fetchResults"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import request from '@/utils/request'
import { formatTime, formatDuration } from '@/utils/format'
import axios from 'axios'

const router = useRouter()

interface TestResult {
  id: number
  taskId: number
  taskName?: string
  serverId: number
  serverName?: string
  taskServerId: number
  result: string
  resultReason: string
  overallScore: number
  metrics: Record<string, any>
  rawOutput: string
  rawError: string
  exitCode: number
  durationMs: number
  startedAt: string
  finishedAt: string
  createdAt: string
}

interface Statistics {
  total: number
  pass: number
  fail: number
  warning: number
  error: number
  passRate: number
  avgScore: number
}

const loading = ref(false)
const results = ref<TestResult[]>([])
const statistics = ref<Statistics | null>(null)

const searchForm = reactive({
  keyword: '',
  result: '',
})

const pagination = reactive({
  page: 1,
  pageSize: 10,
  total: 0,
})

onMounted(() => {
  fetchResults()
  fetchStatistics()
})

async function fetchResults() {
  loading.value = true
  try {
    const params: any = {
      page: pagination.page,
      pageSize: pagination.pageSize,
    }
    if (searchForm.result) {
      params.result = searchForm.result
    }
    if (searchForm.keyword) {
      params.keyword = searchForm.keyword
    }

    const res = await request.get('/results', { params })
    if (res.code === 0) {
      results.value = res.data.items
      pagination.total = res.data.total
    }
  } catch (error) {
    console.error('获取结果列表失败:', error)
  } finally {
    loading.value = false
  }
}

async function fetchStatistics() {
  try {
    const res = await request.get('/results/statistics')
    if (res.code === 0) {
      statistics.value = res.data
    }
  } catch (error) {
    console.error('获取统计数据失败:', error)
  }
}

function handleSearch() {
  pagination.page = 1
  fetchResults()
}

function handleReset() {
  searchForm.keyword = ''
  searchForm.result = ''
  pagination.page = 1
  fetchResults()
}

function handleDetail(row: TestResult) {
  router.push(`/results/detail/${row.id}`)
}

async function handleExport() {
  try {
    ElMessage.info('正在导出测试结果...')
    
    const params: Record<string, string> = { format: 'csv' }
    if (searchForm.result) {
      params.result = searchForm.result
    }
    
    const response = await axios.get('/api/v1/results/export', {
      params,
      responseType: 'blob'
    })
    
    const blob = new Blob([response.data], { type: 'text/csv' })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `test_results_${Date.now()}.csv`
    document.body.appendChild(link)
    link.click()
    
    setTimeout(() => {
      document.body.removeChild(link)
      window.URL.revokeObjectURL(url)
    }, 100)
    
    ElMessage.success('导出成功')
  } catch (e: any) {
    console.error('导出失败:', e)
    ElMessage.error(e.message || '导出失败')
  }
}

function getResultType(result: string) {
  const types: Record<string, string> = {
    pass: 'success',
    warning: 'warning',
    fail: 'danger',
    error: 'info',
  }
  return types[result] || 'info'
}

function getResultLabel(result: string) {
  const labels: Record<string, string> = {
    pass: '通过',
    warning: '警告',
    fail: '失败',
    error: '错误',
  }
  return labels[result] || result
}

function getScoreClass(score: number) {
  if (score >= 80) return 'score-high'
  if (score >= 60) return 'score-medium'
  return 'score-low'
}
</script>

<style scoped>
.stats-row {
  display: flex;
  gap: 16px;
  margin-bottom: 20px;
}

.stat-card {
  background: #f5f7fa;
  padding: 16px 24px;
  border-radius: 4px;
  text-align: center;
  min-width: 100px;
}

.stat-card.success {
  background: #f0f9eb;
}

.stat-card.warning {
  background: #fdf6ec;
}

.stat-card.danger {
  background: #fef0f0;
}

.stat-card.info {
  background: #f4f4f5;
}

.stat-value {
  font-size: 24px;
  font-weight: bold;
  color: #303133;
}

.stat-label {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.score-high {
  color: #67c23a;
  font-weight: bold;
}

.score-medium {
  color: #e6a23c;
  font-weight: bold;
}

.score-low {
  color: #f56c6c;
  font-weight: bold;
}
</style>

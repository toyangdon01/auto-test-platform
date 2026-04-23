<template>
  <div class="page-card">
    <div class="page-header">
      <h3 class="page-title">结果对比</h3>
      <el-button type="success" @click="handleExport">导出CSV</el-button>
    </div>

    <div v-loading="loading">
      <!-- 基本信息 -->
      <div class="info-section" v-if="compareData">
        <el-descriptions :column="3" border>
          <el-descriptions-item label="脚本名称">{{ compareData.scriptName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="对比结果数">{{ compareData.results?.length || 0 }}</el-descriptions-item>
          <el-descriptions-item label="对比指标数">{{ compareData.metrics?.length || 0 }}</el-descriptions-item>
        </el-descriptions>
      </div>

      <!-- 对比表格 -->
      <div class="compare-table" v-if="compareData && compareData.metrics?.length > 0">
        <el-table :data="compareData.metrics" stripe border style="width: 100%">
          <el-table-column prop="metricName" label="指标名称" width="200" fixed />
          <el-table-column 
            v-for="(result, index) in compareData.results" 
            :key="result.id"
            :label="result.taskName || `结果#${result.id}`"
            min-width="120"
          >
            <template #default="{ row }">
              <span :class="getValueClass(row.values?.[index]?.value)">
                {{ row.values?.[index]?.displayValue || '-' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="平均值" width="100">
            <template #default="{ row }">
              <span class="stat-value">{{ formatStat(row.values) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="变化率" width="100">
            <template #default="{ row }">
              <span :class="getTrendClass(row.changeRate)">
                {{ row.changeRate != null ? `${row.changeRate.toFixed(1)}%` : '-' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="趋势" width="80">
            <template #default="{ row }">
              <el-tag v-if="row.trend === 'up'" type="success" size="small">上升</el-tag>
              <el-tag v-else-if="row.trend === 'down'" type="danger" size="small">下降</el-tag>
              <el-tag v-else type="info" size="small">稳定</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 无数据提示 -->
      <el-empty v-if="!loading && !compareData" description="无对比数据" />

      <!-- 结果详情列表 -->
      <div class="result-list" v-if="compareData && compareData.results?.length > 0">
        <h4>结果详情</h4>
        <el-collapse>
          <el-collapse-item 
            v-for="result in compareData.results" 
            :key="result.id"
            :name="result.id"
          >
            <template #title>
              <span class="result-title">
                <el-tag :type="getResultType(result.result)" size="small">{{ getResultLabel(result.result) }}</el-tag>
                <span style="margin-left: 8px">{{ result.taskName || `任务#${result.taskId}` }}</span>
                <span style="margin-left: 8px; color: #909399">{{ result.serverName || '-' }}</span>
              </span>
            </template>
            <el-descriptions :column="2" border size="small">
              <el-descriptions-item label="结果ID">{{ result.id }}</el-descriptions-item>
              <el-descriptions-item label="任务ID">{{ result.taskId }}</el-descriptions-item>
              <el-descriptions-item label="服务器">{{ result.serverName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="得分">{{ result.overallScore ?? '-' }}</el-descriptions-item>
              <el-descriptions-item label="耗时">{{ result.durationMs ? `${result.durationMs}ms` : '-' }}</el-descriptions-item>
              <el-descriptions-item label="执行时间">{{ result.executedAt || '-' }}</el-descriptions-item>
            </el-descriptions>
          </el-collapse-item>
        </el-collapse>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '@/utils/request'
import axios from 'axios'

const route = useRoute()
const router = useRouter()

interface CompareData {
  scriptId: number
  scriptName: string
  results: ResultItem[]
  metrics: MetricCompare[]
  statistics: Statistics
}

interface ResultItem {
  id: number
  taskId: number
  taskName: string
  serverName: string
  result: string
  overallScore: number
  durationMs: number
  executedAt: string
}

interface MetricCompare {
  metricName: string
  values: MetricValue[]
  changeRate: number | null
  trend: string
}

interface MetricValue {
  id: number
  value: any
  displayValue: string
}

interface Statistics {
  totalResults: number
  passCount: number
  failCount: number
  avgScore: number
  avgDuration: number
}

const loading = ref(false)
const compareData = ref<CompareData | null>(null)
const resultIds = ref<number[]>([])

onMounted(() => {
  const ids = route.query.ids as string
  if (ids) {
    resultIds.value = ids.split(',').map(Number)
    fetchCompareData()
  }
})

async function fetchCompareData() {
  if (resultIds.value.length < 2) {
    ElMessage.error('至少需要选择2个结果进行对比')
    router.back()
    return
  }

  loading.value = true
  try {
    const res = await request.post('/results/compare', {
      resultIds: resultIds.value
    })
    if (res.code === 0) {
      compareData.value = res.data
    } else {
      ElMessage.error(res.message || '获取对比数据失败')
    }
  } catch (error: any) {
    console.error('获取对比数据失败:', error)
    ElMessage.error(error.message || '获取对比数据失败')
  } finally {
    loading.value = false
  }
}

async function handleExport() {
  try {
    const response = await axios.post('/api/v1/results/compare/export', {
      resultIds: resultIds.value
    }, {
      responseType: 'blob'
    })
    
    const blob = new Blob([response.data], { type: 'text/csv' })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `result_compare_${Date.now()}.csv`
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

function formatStat(values: MetricValue[] | undefined): string {
  if (!values || values.length === 0) return '-'
  
  const numericValues = values
    .map(v => typeof v.value === 'number' ? v.value : null)
    .filter((v): v is number => v !== null)
  
  if (numericValues.length === 0) return '-'
  
  const avg = numericValues.reduce((a, b) => a + b, 0) / numericValues.length
  return avg.toFixed(2)
}

function getValueClass(value: any): string {
  if (value == null) return ''
  if (typeof value !== 'number') return ''
  if (value > 0) return 'value-positive'
  if (value < 0) return 'value-negative'
  return ''
}

function getTrendClass(changeRate: number | null | undefined): string {
  if (changeRate == null) return ''
  if (changeRate > 5) return 'trend-up'
  if (changeRate < -5) return 'trend-down'
  return 'trend-stable'
}

function getResultType(result: string): string {
  const types: Record<string, string> = {
    pass: 'success',
    warning: 'warning',
    fail: 'danger',
    error: 'info',
  }
  return types[result] || 'info'
}

function getResultLabel(result: string): string {
  const labels: Record<string, string> = {
    pass: '通过',
    warning: '警告',
    fail: '失败',
    error: '错误',
  }
  return labels[result] || result
}
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-title {
  margin: 0;
}

.info-section {
  margin-bottom: 20px;
}

.compare-table {
  margin-bottom: 20px;
}

.result-list {
  margin-top: 20px;
}

.result-list h4 {
  margin-bottom: 12px;
  color: #303133;
}

.result-title {
  display: flex;
  align-items: center;
}

.stat-value {
  font-weight: 500;
  color: #409eff;
}

.value-positive {
  color: #67c23a;
}

.value-negative {
  color: #f56c6c;
}

.trend-up {
  color: #67c23a;
  font-weight: 500;
}

.trend-down {
  color: #f56c6c;
  font-weight: 500;
}

.trend-stable {
  color: #909399;
}
</style>

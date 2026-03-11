<template>
  <div class="page-card" v-loading="loading">
    <div class="page-header">
      <el-page-header @back="$router.back()">
        <template #content>
          <span class="title">{{ report?.title || '报告详情' }}</span>
        </template>
        <template #extra>
          <el-button type="primary" @click="handleExport">
            <el-icon><Download /></el-icon>
            导出
          </el-button>
        </template>
      </el-page-header>
    </div>

    <template v-if="report">
      <!-- 结论卡片 -->
      <div class="conclusion-card">
        <div class="conclusion-content">
          <div class="conclusion-icon" :class="conclusionClass">
            <el-icon v-if="report.conclusion === 'pass'"><SuccessFilled /></el-icon>
            <el-icon v-else-if="report.conclusion === 'warning'"><WarningFilled /></el-icon>
            <el-icon v-else><CircleCloseFilled /></el-icon>
          </div>
          <div class="conclusion-text">
            <h2>{{ conclusionText }} - {{ report.overview?.avgScore || 0 }}分</h2>
            <p>{{ report.summary }}</p>
          </div>
        </div>
      </div>

      <!-- 执行概览 -->
      <el-divider content-position="left">
        <el-icon><DataLine /></el-icon>
        执行概览
      </el-divider>

      <el-row :gutter="16" class="overview-cards">
        <el-col :span="6">
          <div class="stat-card">
            <div class="stat-value">{{ report.overview?.totalServers || 0 }}</div>
            <div class="stat-label">服务器总数</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card success">
            <div class="stat-value">{{ report.overview?.successCount || 0 }}</div>
            <div class="stat-label">通过</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card danger">
            <div class="stat-value">{{ report.overview?.failCount || 0 }}</div>
            <div class="stat-label">失败</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card">
            <div class="stat-value">{{ formatDuration(report.overview?.totalTimeMs) }}</div>
            <div class="stat-label">总耗时</div>
          </div>
        </el-col>
      </el-row>

      <!-- 测试结果 -->
      <el-divider content-position="left">
        <el-icon><List /></el-icon>
        测试结果
      </el-divider>

      <el-table :data="report.results || []" stripe>
        <el-table-column prop="serverName" label="服务器" min-width="180">
          <template #default="{ row }">
            {{ row.serverName }}
            <el-text type="info" size="small">({{ row.serverIp }})</el-text>
          </template>
        </el-table-column>
        <el-table-column prop="result" label="结果" width="100">
          <template #default="{ row }">
            <el-tag :type="row.result === 'pass' ? 'success' : 'danger'" size="small">
              {{ row.result === 'pass' ? '通过' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="score" label="得分" width="80">
          <template #default="{ row }">
            <span :class="getScoreClass(row.score)">{{ row.score }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="durationMs" label="耗时" width="100">
          <template #default="{ row }">
            {{ formatDuration(row.durationMs) }}
          </template>
        </el-table-column>
        <el-table-column label="关键指标" min-width="200">
          <template #default="{ row }">
            <div class="metric-tags">
              <el-tag
                v-for="(value, key) in row.keyMetrics"
                :key="key"
                size="small"
                class="metric-tag"
              >
                {{ key }}: {{ formatMetricValue(value) }}
              </el-tag>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <!-- 指标统计 -->
      <template v-if="report.metricStats && Object.keys(report.metricStats).length">
        <el-divider content-position="left">
          <el-icon><TrendCharts /></el-icon>
          指标统计
        </el-divider>

        <el-row :gutter="16">
          <el-col :span="8" v-for="(stats, key) in report.metricStats" :key="key">
            <el-card class="metric-card" shadow="hover">
              <template #header>
                <span>{{ stats.name }}</span>
              </template>
              <el-descriptions :column="1" size="small">
                <el-descriptions-item label="最小值">
                  <span class="metric-value">{{ formatNumber(stats.min) }}</span>
                </el-descriptions-item>
                <el-descriptions-item label="最大值">
                  <span class="metric-value">{{ formatNumber(stats.max) }}</span>
                </el-descriptions-item>
                <el-descriptions-item label="平均值">
                  <span class="metric-value highlight">{{ formatNumber(stats.avg) }}</span>
                </el-descriptions-item>
              </el-descriptions>
            </el-card>
          </el-col>
        </el-row>
      </template>
    </template>

    <el-empty v-else-if="!loading" description="报告不存在" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Download,
  SuccessFilled,
  WarningFilled,
  CircleCloseFilled,
  DataLine,
  List,
  TrendCharts
} from '@element-plus/icons-vue'
import request from '@/utils/request'

interface ReportDetail {
  id: number
  taskId: number
  taskName: string
  title: string
  summary: string
  conclusion: string
  overview: {
    totalServers: number
    successCount: number
    failCount: number
    totalTimeMs: number
    avgScore: string
  }
  results: Array<{
    id: number
    serverName: string
    serverIp: string
    result: string
    score: number
    durationMs: number
    keyMetrics: Record<string, any>
  }>
  metricStats: Record<string, {
    name: string
    min: number
    max: number
    avg: number
  }>
  fileFormat: string
  createdAt: string
}

const route = useRoute()
const loading = ref(true)
const report = ref<ReportDetail | null>(null)

const conclusionClass = computed(() => {
  const map: Record<string, string> = {
    pass: 'success',
    warning: 'warning',
    fail: 'danger'
  }
  return map[report.value?.conclusion || ''] || ''
})

const conclusionText = computed(() => {
  const map: Record<string, string> = {
    pass: '测试通过',
    warning: '部分异常',
    fail: '测试失败'
  }
  return map[report.value?.conclusion || ''] || '未知'
})

const formatDuration = (ms: number | null | undefined) => {
  if (ms == null) return '-'
  if (ms < 1000) return `${ms}ms`
  if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`
  const minutes = Math.floor(ms / 60000)
  const seconds = Math.round((ms % 60000) / 1000)
  return `${minutes}m ${seconds}s`
}

const formatMetricValue = (value: any) => {
  if (value == null) return '-'
  if (typeof value === 'number') {
    return value % 1 === 0 ? value : value.toFixed(2)
  }
  return value
}

const formatNumber = (value: number | null | undefined) => {
  if (value == null) return '-'
  if (typeof value === 'number') {
    return value % 1 === 0 ? value : value.toFixed(2)
  }
  return value
}

const getScoreClass = (score: number | null) => {
  if (score == null) return ''
  if (score >= 90) return 'score-excellent'
  if (score >= 70) return 'score-good'
  if (score >= 50) return 'score-warning'
  return 'score-danger'
}

const fetchReport = async () => {
  const id = route.params.id as string
  if (!id) return

  loading.value = true
  try {
    const res = await request.get<ReportDetail>(`/reports/${id}/detail`)
    if (res.code === 0) {
      report.value = res.data
    }
  } catch (e) {
    console.error('获取报告详情失败', e)
  } finally {
    loading.value = false
  }
}

const handleExport = () => {
  ElMessage.info('导出功能开发中...')
}

onMounted(() => {
  fetchReport()
})
</script>

<style scoped>
.conclusion-card {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 12px;
  padding: 24px;
  margin-bottom: 24px;
  color: white;
}

.conclusion-content {
  display: flex;
  align-items: center;
  gap: 20px;
}

.conclusion-icon {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.2);
}

.conclusion-icon.success {
  background: rgba(103, 194, 58, 0.3);
}

.conclusion-icon.warning {
  background: rgba(230, 162, 60, 0.3);
}

.conclusion-icon.danger {
  background: rgba(245, 108, 108, 0.3);
}

.conclusion-icon .el-icon {
  font-size: 32px;
}

.conclusion-text h2 {
  margin: 0 0 8px 0;
  font-size: 24px;
}

.conclusion-text p {
  margin: 0;
  opacity: 0.9;
}

.overview-cards {
  margin-bottom: 16px;
}

.stat-card {
  background: #f5f7fa;
  border-radius: 8px;
  padding: 20px;
  text-align: center;
}

.stat-card.success {
  background: #f0f9eb;
}

.stat-card.danger {
  background: #fef0f0;
}

.stat-value {
  font-size: 28px;
  font-weight: bold;
  color: #303133;
}

.stat-card.success .stat-value {
  color: #67c23a;
}

.stat-card.danger .stat-value {
  color: #f56c6c;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-top: 8px;
}

.metric-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.metric-tag {
  margin: 2px;
}

.metric-card {
  margin-bottom: 16px;
}

.metric-value {
  font-family: 'Monaco', 'Menlo', monospace;
  font-size: 14px;
}

.metric-value.highlight {
  color: #409eff;
  font-weight: bold;
}

.score-excellent {
  color: #67c23a;
  font-weight: bold;
}

.score-good {
  color: #409eff;
  font-weight: bold;
}

.score-warning {
  color: #e6a23c;
  font-weight: bold;
}

.score-danger {
  color: #f56c6c;
  font-weight: bold;
}
</style>

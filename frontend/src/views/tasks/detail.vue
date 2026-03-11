<template>
  <div class="task-detail">
    <!-- 任务概览 -->
    <div class="page-card">
      <div class="page-header">
        <el-page-header @back="$router.back()">
          <template #content>
            <span class="title">{{ task?.name || '任务详情' }}</span>
          </template>
        </el-page-header>
        
        <div class="header-actions">
          <el-button v-if="task?.status === 'pending'" type="primary" @click="handleExecute">
            <el-icon><VideoPlay /></el-icon>执行任务
          </el-button>
          <el-button v-if="task?.status === 'running'" type="warning" @click="handleCancel">
            <el-icon><VideoPause /></el-icon>取消任务
          </el-button>
          <el-button v-if="task?.status === 'failed' || task?.status === 'completed_with_errors'" type="primary" @click="handleRetry">
            <el-icon><RefreshRight /></el-icon>重试
          </el-button>
          <el-button v-if="task?.status === 'completed' || task?.status === 'completed_with_errors' || task?.status === 'failed'" type="success" @click="handleExportMetrics">
            <el-icon><Download /></el-icon>导出指标
          </el-button>
          <el-button @click="fetchDetail">
            <el-icon><Refresh /></el-icon>刷新
          </el-button>
        </div>
      </div>

      <!-- 任务基本信息 -->
      <el-descriptions :column="4" border size="small">
        <el-descriptions-item label="任务ID">{{ task?.id }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="getStatusType(task?.status)" effect="light">
            <el-icon v-if="task?.status === 'running'" class="is-loading"><Loading /></el-icon>
            {{ getStatusText(task?.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="执行模式">
          <el-tag size="small" effect="plain">
            {{ task?.executionMode === 'immediate' ? '立即执行' : '定时执行' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="并发模式">
          <el-tag size="small" effect="plain">
            {{ task?.parallelMode === 'parallel' ? '并行' : '顺序' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatTime(task?.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="开始时间">{{ formatTime(task?.startedAt) }}</el-descriptions-item>
        <el-descriptions-item label="结束时间">{{ formatTime(task?.finishedAt) }}</el-descriptions-item>
        <el-descriptions-item label="总耗时">{{ calculateDuration(task?.startedAt, task?.finishedAt) }}</el-descriptions-item>
      </el-descriptions>
    </div>

    <!-- 执行进度概览 -->
    <div class="page-card mt-20" v-if="task">
      <h4 class="section-title">执行进度</h4>
      
      <div class="progress-overview">
        <div class="progress-stats">
          <div class="stat-item">
            <span class="stat-label">总服务器</span>
            <span class="stat-value">{{ serverList.length }}</span>
          </div>
          <div class="stat-item success">
            <span class="stat-label">成功</span>
            <span class="stat-value">{{ successCount }}</span>
          </div>
          <div class="stat-item warning">
            <span class="stat-label">执行中</span>
            <span class="stat-value">{{ runningCount }}</span>
          </div>
          <div class="stat-item danger">
            <span class="stat-label">失败</span>
            <span class="stat-value">{{ failCount }}</span>
          </div>
          <div class="stat-item info">
            <span class="stat-label">待执行</span>
            <span class="stat-value">{{ pendingCount }}</span>
          </div>
        </div>
        
        <div class="progress-bar-container">
          <el-progress
            :percentage="overallProgress"
            :stroke-width="20"
            :show-text="false"
          />
          <div class="progress-label">{{ overallProgress }}% 完成</div>
        </div>
      </div>
    </div>

    <!-- 正在执行的命令 -->
    <div class="page-card mt-20" v-if="task?.status === 'running'">
      <h4 class="section-title">正在执行的命令</h4>
      <div class="execution-status">
        <div v-for="server in runningServers" :key="server.serverId" class="server-execution">
          <div class="server-header">
            <el-icon><Monitor /></el-icon>
            <span class="server-name">{{ server.serverName || `Server-${server.serverId}` }}</span>
            <el-tag v-if="server.currentPhase" type="warning" size="small" effect="light">
              <el-icon class="is-loading"><Loading /></el-icon>
              {{ getPhaseText(server.currentPhase) }}阶段
            </el-tag>
          </div>
          
          <div v-if="server.currentCommand" class="command-box">
            <div class="command-header">
              <span class="command-label">$ 执行命令</span>
              <span class="command-time" v-if="server.commandStartedAt">
                开始于 {{ formatTime(server.commandStartedAt) }}
                (已运行 {{ calculateDuration(server.commandStartedAt, null) }})
              </span>
            </div>
            <pre class="command-content">{{ server.currentCommand }}</pre>
          </div>
          <div v-else class="no-command">
            <span>等待执行...</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 服务器执行详情 -->
    <div class="page-card mt-20">
      <h4 class="section-title">服务器执行详情</h4>
      <el-table :data="serverList" stripe>
        <el-table-column prop="serverName" label="服务器" min-width="180">
          <template #default="{ row }">
            <div class="server-info">
              <el-icon><Monitor /></el-icon>
              <span>{{ row.serverName || `Server-${row.serverId}` }}</span>
            </div>
          </template>
        </el-table-column>
        
        <el-table-column label="角色" width="120">
          <template #default="{ row }">
            <el-tag v-if="row.role && row.role !== 'default'" size="small" type="info">
              {{ row.role }}
            </el-tag>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        
        <el-table-column label="执行状态" width="120">
          <template #default="{ row }">
            <el-tag :type="getRunStatusType(row.overallStatus)" size="small" effect="light">
              <el-icon v-if="row.overallStatus === 'running'" class="is-loading"><Loading /></el-icon>
              {{ getRunStatusText(row.overallStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        
        <el-table-column label="失败原因" min-width="200">
          <template #default="{ row }">
            <template v-if="row.overallStatus === 'failed'">
              <div class="error-message">
                <template v-if="row.deploy?.status === 'failed'">
                  <div class="error-phase">部署阶段失败:</div>
                  <div class="error-detail">{{ getErrorMessage(row.deploy?.output) }}</div>
                </template>
                <template v-else-if="row.run?.status === 'failed'">
                  <div class="error-phase">执行阶段失败:</div>
                  <div class="error-detail">{{ getErrorMessage(row.run?.output) }}</div>
                </template>
                <template v-else-if="row.cleanup?.status === 'failed'">
                  <div class="error-phase">清理阶段失败:</div>
                  <div class="error-detail">{{ getErrorMessage(row.cleanup?.output) }}</div>
                </template>
                <template v-else>
                  <span class="text-muted">未知错误</span>
                </template>
              </div>
            </template>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        
        <el-table-column label="退出码" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.run?.exitCode !== null && row.run?.exitCode !== undefined" 
                    :type="row.run.exitCode === 0 ? 'success' : 'danger'" 
                    size="small">
              {{ row.run.exitCode }}
            </el-tag>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        
        <el-table-column label="开始时间" width="160">
          <template #default="{ row }">
            {{ formatTime(row.run?.startedAt) }}
          </template>
        </el-table-column>
        
        <el-table-column label="结束时间" width="160">
          <template #default="{ row }">
            {{ formatTime(row.run?.finishedAt) }}
          </template>
        </el-table-column>
        
        <el-table-column label="耗时" width="100">
          <template #default="{ row }">
            {{ calculateDuration(row.run?.startedAt, row.run?.finishedAt) }}
          </template>
        </el-table-column>
        
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openTerminal(row)">
              <el-icon><Monitor /></el-icon>终端
            </el-button>
            <el-button type="primary" link @click="showLogs(row)">
              <el-icon><Document /></el-icon>日志
            </el-button>
            <el-button type="primary" link @click="showResult(row)" v-if="row.run?.status === 'completed'">
              <el-icon><DataAnalysis /></el-icon>结果
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 执行日志弹窗 -->
    <el-dialog v-model="logDialogVisible" :title="`执行日志 - ${currentServer?.serverName}`" width="900px" top="5vh">
      <div class="log-toolbar">
        <el-radio-group v-model="currentLogPhase" size="small" @change="updateLogContent">
          <el-radio-button value="deploy">部署阶段</el-radio-button>
          <el-radio-button value="run">执行阶段</el-radio-button>
          <el-radio-button value="cleanup">清理阶段</el-radio-button>
          <el-radio-button value="all">全部日志</el-radio-button>
        </el-radio-group>
        
        <div class="log-actions">
          <el-button size="small" @click="copyLogs">
            <el-icon><CopyDocument /></el-icon>复制
          </el-button>
          <el-button size="small" @click="refreshLogs">
            <el-icon><Refresh /></el-icon>刷新
          </el-button>
        </div>
      </div>
      <pre class="log-content">{{ currentLogs || '暂无日志' }}</pre>
    </el-dialog>

    <!-- 执行结果弹窗 -->
    <el-dialog v-model="resultDialogVisible" :title="`执行结果 - ${currentServer?.serverName}`" width="800px">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="结果">
          <el-tag :type="currentResult?.result === 'pass' ? 'success' : 'danger'">
            {{ currentResult?.result === 'pass' ? '通过' : '失败' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="得分">
          <span :class="getScoreClass(currentResult?.overallScore)">
            {{ currentResult?.overallScore ?? '-' }}
          </span>
        </el-descriptions-item>
        <el-descriptions-item label="执行时长">
          {{ currentResult?.durationMs ? `${currentResult.durationMs}ms` : '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="退出码">
          {{ currentResult?.exitCode ?? '-' }}
        </el-descriptions-item>
      </el-descriptions>
      
      <div class="mt-20" v-if="currentResult?.metrics">
        <h4 class="section-title">采集指标</h4>
        <el-table :data="metricsList" size="small" border>
          <el-table-column prop="key" label="指标名" />
          <el-table-column prop="value" label="值" />
        </el-table>
      </div>
      
      <div class="mt-20" v-if="currentResult?.parsedData">
        <h4 class="section-title">解析结果</h4>
        <el-table :data="parsedDataList" size="small" border>
          <el-table-column prop="key" label="字段" />
          <el-table-column prop="value" label="值" />
        </el-table>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { 
  VideoPlay, VideoPause, RefreshRight, Refresh, Loading,
  Monitor, Document, DataAnalysis, CopyDocument
} from '@element-plus/icons-vue'
import request from '@/utils/request'
import axios from 'axios'

const route = useRoute()
const router = useRouter()
const taskId = Number(route.params.id)

const task = ref<any>(null)
const serverList = ref<any[]>([])
const logDialogVisible = ref(false)
const resultDialogVisible = ref(false)
const currentServer = ref<any>(null)
const currentLogs = ref('')
const currentResult = ref<any>(null)
const currentLogPhase = ref<'deploy' | 'run' | 'cleanup' | 'all'>('all')

let pollTimer: ReturnType<typeof setInterval> | null = null

// 计算属性
const successCount = computed(() => serverList.value.filter(s => s.run?.status === 'completed').length)
const runningCount = computed(() => serverList.value.filter(s => s.run?.status === 'running').length)
const failCount = computed(() => serverList.value.filter(s => s.run?.status === 'failed' || s.run?.status === 'error').length)
const pendingCount = computed(() => serverList.value.filter(s => !s.run?.status || s.run?.status === 'pending').length)

const overallProgress = computed(() => {
  if (serverList.value.length === 0) return 0
  const completed = successCount.value + failCount.value
  return Math.round((completed / serverList.value.length) * 100)
})

const metricsList = computed(() => {
  if (!currentResult.value?.metrics) return []
  return Object.entries(currentResult.value.metrics).map(([key, value]) => ({ key, value }))
})

const parsedDataList = computed(() => {
  if (!currentResult.value?.parsedData) return []
  return Object.entries(currentResult.value.parsedData).map(([key, value]) => ({ key, value }))
})

async function fetchDetail() {
  try {
    const res = await request.get(`/tasks/${taskId}`)
    if (res.code === 0) {
      task.value = res.data
      serverList.value = res.data.servers || []
    }
  } catch (e: any) {
    ElMessage.error('获取任务详情失败')
  }
}

function getStatusType(status?: string) {
  const types: Record<string, string> = {
    pending: 'info',
    running: 'warning',
    completed: 'success',
    completed_with_errors: 'warning',
    failed: 'danger',
    cancelled: 'info',
  }
  return types[status || ''] || 'info'
}

function getStatusText(status?: string) {
  const texts: Record<string, string> = {
    pending: '待执行',
    running: '执行中',
    completed: '已完成',
    completed_with_errors: '部分失败',
    failed: '失败',
    cancelled: '已取消',
  }
  return texts[status || ''] || status || '-'
}

function getRunStatusType(status?: string) {
  const types: Record<string, string> = {
    pending: 'info',
    running: 'warning',
    completed: 'success',
    failed: 'danger',
    error: 'danger',
  }
  return types[status || ''] || 'info'
}

function getRunStatusText(status?: string) {
  const texts: Record<string, string> = {
    pending: '待执行',
    running: '执行中',
    completed: '已完成',
    failed: '失败',
    error: '错误',
  }
  return texts[status || ''] || status || '-'
}

function getPhaseText(phase?: string) {
  const texts: Record<string, string> = {
    deploy: '部署',
    run: '执行',
    cleanup: '清理',
  }
  return texts[phase || ''] || phase || '-'
}

const runningServers = computed(() => {
  if (!task.value?.servers) return []
  return task.value.servers.filter(s => s.overallStatus === 'running')
})

function getScoreClass(score?: number) {
  if (score === undefined || score === null) return ''
  if (score >= 80) return 'score-high'
  if (score >= 60) return 'score-medium'
  return 'score-low'
}

function formatTime(time?: string) {
  if (!time) return '-'
  return time.replace('T', ' ').substring(0, 19)
}

function calculateDuration(startedAt?: string, finishedAt?: string | null) {
  if (!startedAt) return '-'
  
  const start = new Date(startedAt).getTime()
  const end = finishedAt ? new Date(finishedAt).getTime() : Date.now()
  const diff = end - start
  
  if (diff < 0) return '-'
  if (diff < 1000) return `${diff}ms`
  if (diff < 60000) return `${Math.round(diff / 1000)}s`
  if (diff < 3600000) return `${Math.round(diff / 60000)}min`
  return `${(diff / 3600000).toFixed(1)}h`
}

async function handleExecute() {
  try {
    await ElMessageBox.confirm('确定要执行该任务吗？', '提示', { type: 'info' })
    const res = await request.post(`/tasks/${taskId}/execute`)
    if (res.code === 0) {
      ElMessage.success('任务已开始执行')
      fetchDetail()
    }
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '执行失败')
    }
  }
}

async function handleCancel() {
  try {
    await ElMessageBox.confirm('确定要取消该任务吗？', '提示', { type: 'warning' })
    const res = await request.post(`/tasks/${taskId}/cancel`)
    if (res.code === 0) {
      ElMessage.success('任务已取消')
      fetchDetail()
    }
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '取消失败')
    }
  }
}

async function handleRetry() {
  try {
    const res = await request.post(`/tasks/${taskId}/retry`)
    if (res.code === 0) {
      ElMessage.success('任务已重新排队')
      fetchDetail()
    }
  } catch (e: any) {
    ElMessage.error(e.message || '重试失败')
  }
}

async function handleExportMetrics() {
  if (!task.value?.id) return
  
  try {
    ElMessage.info('正在导出指标数据...')
    
    const response = await axios.get(`/api/v1/tasks/${task.value.id}/metrics/export`, {
      params: { format: 'csv' },
      responseType: 'blob'
    })
    
    const blob = new Blob([response.data], { type: 'text/csv' })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `task_${task.value.id}_metrics.csv`
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

async function showLogs(row: any) {
  currentServer.value = row
  logDialogVisible.value = true
  currentLogs.value = '加载中...'
  
  // 默认显示全部日志
  updateLogContent()
}

function updateLogContent() {
  if (!currentServer.value) return
  
  const server = currentServer.value
  let logs = ''
  
  if (currentLogPhase.value === 'deploy' || currentLogPhase.value === 'all') {
    if (server.deploy?.output) {
      logs += '========== 部署阶段 ==========\n'
      logs += `状态: ${server.deploy.status || '-'}\n`
      logs += `退出码: ${server.deploy.exitCode ?? '-'}\n`
      logs += `开始时间: ${formatTime(server.deploy.startedAt)}\n`
      logs += `结束时间: ${formatTime(server.deploy.finishedAt)}\n`
      logs += '--- 输出 ---\n'
      logs += server.deploy.output
      logs += '\n\n'
    } else if (currentLogPhase.value === 'deploy') {
      logs = '部署阶段暂无日志'
    }
  }
  
  if (currentLogPhase.value === 'run' || currentLogPhase.value === 'all') {
    if (server.run?.output) {
      if (currentLogPhase.value === 'all') logs += '\n'
      logs += '========== 执行阶段 ==========\n'
      logs += `状态: ${server.run.status || '-'}\n`
      logs += `退出码: ${server.run.exitCode ?? '-'}\n`
      logs += `开始时间: ${formatTime(server.run.startedAt)}\n`
      logs += `结束时间: ${formatTime(server.run.finishedAt)}\n`
      logs += '--- 输出 ---\n'
      logs += server.run.output
      logs += '\n\n'
    } else if (currentLogPhase.value === 'run') {
      logs = '执行阶段暂无日志'
    }
  }
  
  if (currentLogPhase.value === 'cleanup' || currentLogPhase.value === 'all') {
    if (server.cleanup?.output) {
      if (currentLogPhase.value === 'all') logs += '\n'
      logs += '========== 清理阶段 ==========\n'
      logs += `状态: ${server.cleanup.status || '-'}\n`
      logs += `退出码: ${server.cleanup.exitCode ?? '-'}\n`
      logs += `开始时间: ${formatTime(server.cleanup.startedAt)}\n`
      logs += `结束时间: ${formatTime(server.cleanup.finishedAt)}\n`
      logs += '--- 输出 ---\n'
      logs += server.cleanup.output
      logs += '\n'
    } else if (currentLogPhase.value === 'cleanup') {
      logs = '清理阶段暂无日志'
    }
  }
  
  currentLogs.value = logs || '暂无日志'
}

function refreshLogs() {
  updateLogContent()
}

function copyLogs() {
  if (currentLogs.value) {
    navigator.clipboard.writeText(currentLogs.value)
    ElMessage.success('已复制到剪贴板')
  }
}

async function showResult(row: any) {
  currentServer.value = row
  
  try {
    const res = await request.get('/results', {
      params: { taskId, serverId: row.serverId }
    })
    if (res.code === 0 && res.data.items.length > 0) {
      currentResult.value = res.data.items[0]
      resultDialogVisible.value = true
    } else {
      ElMessage.warning('暂无测试结果')
    }
  } catch (e) {
    ElMessage.error('获取测试结果失败')
  }
}

// 打开服务器终端
function openTerminal(row: any) {
  router.push(`/servers/terminal/${row.serverId}`)
}

function startPolling() {
  pollTimer = setInterval(() => {
    if (task.value?.status === 'running') {
      fetchDetail()
    }
  }, 3000)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

onMounted(() => {
  fetchDetail()
  startPolling()
})

onUnmounted(() => {
  stopPolling()
})

// 提取错误信息
function getErrorMessage(output: string | null): string {
  if (!output) return '无错误信息'
  
  // 提取 [ERROR] 标记的行
  const errorLines = output.split('\n')
    .filter((line: string) => line.includes('[ERROR]') || line.includes('error') || line.includes('failed'))
    .map((line: string) => line.replace(/^\[ERROR\]\s*/, '').trim())
    .filter((line: string) => line.length > 0)
  
  if (errorLines.length > 0) {
    return errorLines.slice(0, 3).join('; ') // 最多显示3条
  }
  
  // 如果没有明确的错误标记，返回前100个字符
  const trimmed = output.trim()
  if (trimmed.length > 100) {
    return trimmed.substring(0, 100) + '...'
  }
  return trimmed || '无错误信息'
}
</script>

<style lang="scss" scoped>
.title {
  font-size: 16px;
  font-weight: 600;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 12px;
  color: #303133;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.progress-overview {
  .progress-stats {
    display: flex;
    gap: 24px;
    margin-bottom: 16px;
    
    .stat-item {
      display: flex;
      flex-direction: column;
      align-items: center;
      padding: 12px 20px;
      background: #f5f7fa;
      border-radius: 4px;
      min-width: 80px;
      
      .stat-label {
        font-size: 12px;
        color: #909399;
        margin-bottom: 4px;
      }
      
      .stat-value {
        font-size: 24px;
        font-weight: bold;
        color: #303133;
      }
      
      &.success .stat-value { color: #67c23a; }
      &.warning .stat-value { color: #e6a23c; }
      &.danger .stat-value { color: #f56c6c; }
      &.info .stat-value { color: #909399; }
    }
  }
  
  .progress-bar-container {
    position: relative;
    
    .progress-label {
      position: absolute;
      top: 50%;
      left: 50%;
      transform: translate(-50%, -50%);
      font-size: 12px;
      color: #fff;
      font-weight: bold;
      text-shadow: 0 0 2px rgba(0,0,0,0.5);
    }
  }
}

.server-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.text-muted {
  color: #c0c4cc;
}

.score-high { color: #67c23a; font-weight: bold; }
.score-medium { color: #e6a23c; font-weight: bold; }
.score-low { color: #f56c6c; font-weight: bold; }

.error-message {
  .error-phase {
    font-size: 12px;
    color: #909399;
    margin-bottom: 2px;
  }
  
  .error-detail {
    font-size: 12px;
    color: #f56c6c;
    word-break: break-all;
  }
}

.log-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  
  .log-actions {
    display: flex;
    gap: 8px;
  }
}

.log-content {
  max-height: 500px;
  padding: 16px;
  background: #1e1e1e;
  color: #d4d4d4;
  font-family: 'Consolas', 'Fira Code', monospace;
  font-size: 13px;
  line-height: 1.5;
  overflow: auto;
  border-radius: 4px;
  white-space: pre-wrap;
  word-break: break-all;
}

/* 执行状态区域 */
.execution-status {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.server-execution {
  background: #f5f7fa;
  border-radius: 8px;
  padding: 16px;
  border: 1px solid #e4e7ed;
}

.server-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  
  .server-name {
    font-weight: 600;
    color: #303133;
  }
}

.command-box {
  background: #1e1e1e;
  border-radius: 6px;
  overflow: hidden;
}

.command-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: #2d2d2d;
  border-bottom: 1px solid #3d3d3d;
  
  .command-label {
    color: #4ec9b0;
    font-family: 'Consolas', 'Fira Code', monospace;
    font-size: 13px;
  }
  
  .command-time {
    color: #808080;
    font-size: 12px;
  }
}

.command-content {
  padding: 12px;
  margin: 0;
  color: #d4d4d4;
  font-family: 'Consolas', 'Fira Code', monospace;
  font-size: 13px;
  line-height: 1.6;
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-all;
}

.no-command {
  color: #909399;
  text-align: center;
  padding: 12px;
  font-size: 13px;
}
</style>

<template>
  <div class="page-card">
    <div class="page-header">
      <h3 class="page-title">任务列表</h3>
      <div class="header-actions">
        <el-switch
          v-model="autoRefresh"
          active-text="自动刷新"
          @change="toggleAutoRefresh"
        />
        <el-button type="primary" @click="$router.push('/tasks/create')">
          <el-icon><Plus /></el-icon>
          创建任务
        </el-button>
      </div>
    </div>

    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-input
        v-model="queryParams.name"
        placeholder="搜索任务名称"
        clearable
        style="width: 240px"
        @keyup.enter="fetchData"
      />
      
      <el-select v-model="queryParams.status" placeholder="状态" clearable style="width: 140px">
        <el-option label="待执行" value="pending" />
        <el-option label="执行中" value="running" />
        <el-option label="已完成" value="completed" />
        <el-option label="部分失败" value="completed_with_errors" />
        <el-option label="失败" value="failed" />
        <el-option label="已取消" value="cancelled" />
      </el-select>

      <el-button type="primary" @click="fetchData">查询</el-button>
      <el-button @click="resetQuery">重置</el-button>
    </div>

    <!-- 数据表格 -->
    <el-table v-loading="loading" :data="tableData" stripe>
      <el-table-column prop="name" label="任务名称" min-width="180">
        <template #default="{ row }">
          <el-link type="primary" @click="$router.push(`/tasks/detail/${row.id}`)">
            {{ row.name }}
          </el-link>
        </template>
      </el-table-column>
      
      <el-table-column prop="scriptName" label="所属脚本" min-width="150">
        <template #default="{ row }">
          <el-link type="info" @click="$router.push(`/scripts/edit/${row.scriptId}`)">
            {{ row.scriptName || `脚本 ${row.scriptId}` }}
          </el-link>
        </template>
      </el-table-column>
      
      <el-table-column prop="status" label="状态" width="180">
        <template #default="{ row }">
          <div class="status-cell">
            <el-tag :type="getStatusType(row.status)" effect="light">
              <el-icon v-if="row.status === 'running'" class="is-loading"><Loading /></el-icon>
              {{ getStatusText(row.status) }}
            </el-tag>
            <el-tooltip v-if="row.status === 'failed' || row.status === 'completed_with_errors'" 
                        :content="getTaskErrorMessage(row)" 
                        placement="top"
                        :disabled="!getTaskErrorMessage(row)">
              <el-icon class="error-icon" @click.stop><Warning /></el-icon>
            </el-tooltip>
          </div>
        </template>
      </el-table-column>

      <el-table-column label="执行进度" width="200">
        <template #default="{ row }">
          <div class="progress-cell">
            <template v-if="row.status === 'running'">
              <el-progress
                :percentage="calculateProgress(row)"
                :stroke-width="10"
                :format="progressFormat"
                :striped="false"
                :striped-flow="false"
              />
              <div class="progress-detail">
                <span class="success">{{ row.successCount || 0 }} 成功</span>
                <span class="separator">/</span>
                <span class="fail">{{ row.failCount || 0 }} 失败</span>
                <span class="separator">/</span>
                <span class="running">{{ row.runningCount || 0 }} 执行中</span>
                <span class="separator">/</span>
                <span>{{ row.totalServers || 0 }} 台</span>
              </div>
            </template>
            <template v-else-if="row.status === 'completed' || row.status === 'completed_with_errors'">
              <div class="result-summary">
                <el-tag type="success" size="small">{{ row.successCount || 0 }} 成功</el-tag>
                <el-tag v-if="row.failCount > 0" type="danger" size="small">{{ row.failCount }} 失败</el-tag>
                <el-tag type="info" size="small">{{ row.totalServers }} 台</el-tag>
              </div>
            </template>
            <template v-else>
              <span class="text-muted">-</span>
            </template>
          </div>
        </template>
      </el-table-column>

      <el-table-column prop="executionMode" label="执行模式" width="100">
        <template #default="{ row }">
          <el-tag size="small" effect="plain">
            {{ row.executionMode === 'immediate' ? '立即执行' : '定时执行' }}
          </el-tag>
        </template>
      </el-table-column>
      
      <el-table-column prop="startedAt" label="开始时间" width="160">
        <template #default="{ row }">
          {{ formatTime(row.startedAt) }}
        </template>
      </el-table-column>
      
      <el-table-column label="耗时" width="100">
        <template #default="{ row }">
          <span v-if="row.startedAt">
            {{ calculateDuration(row.startedAt, row.finishedAt) }}
          </span>
          <span v-else>-</span>
        </template>
      </el-table-column>
      
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <template v-if="row.status === 'pending'">
            <el-button type="primary" link @click="handleExecute(row)">
              <el-icon><VideoPlay /></el-icon>执行
            </el-button>
            <el-button type="danger" link @click="handleDelete(row)">
              <el-icon><Delete /></el-icon>删除
            </el-button>
          </template>
          <template v-else-if="row.status === 'running'">
            <el-button type="warning" link @click="handleCancel(row)">
              <el-icon><VideoPause /></el-icon>取消
            </el-button>
            <el-button type="primary" link @click="handleDetail(row)">详情</el-button>
          </template>
          <template v-else>
            <el-button type="primary" link @click="handleExecute(row)">
              <el-icon><VideoPlay /></el-icon>再次执行
            </el-button>
            <el-button type="primary" link @click="handleDetail(row)">详情</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pagination-wrap">
      <el-pagination
        v-model:current-page="queryParams.page"
        v-model:page-size="queryParams.size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @size-change="fetchData"
        @current-change="fetchData"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Loading, VideoPlay, VideoPause, Delete, RefreshRight, Warning } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { formatTime, formatDuration } from '@/utils/format'

const router = useRouter()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const autoRefresh = ref(true)
let refreshTimer: ReturnType<typeof setInterval> | null = null

const queryParams = reactive({
  page: 1,
  size: 20,
  name: '',
  status: '',
})

async function fetchData() {
  loading.value = true
  try {
    const res = await request.get('/tasks', { params: queryParams })
    if (res.code === 0) {
      tableData.value = res.data.items.map((task: any) => ({
        ...task,
        totalServers: task.serverCount || 0,
        successCount: task.successCount || 0,
        failCount: task.failCount || 0,
        runningCount: task.runningCount || 0,
      }))
      total.value = res.data.total
    }
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  queryParams.name = ''
  queryParams.status = ''
  queryParams.page = 1
  fetchData()
}

function getStatusType(status: string) {
  const types: Record<string, string> = {
    pending: 'info',
    running: 'warning',
    completed: 'success',
    completed_with_errors: 'warning',
    failed: 'danger',
    cancelled: 'info',
  }
  return types[status] || 'info'
}

function getStatusText(status: string) {
  const texts: Record<string, string> = {
    pending: '待执行',
    running: '执行中',
    completed: '已完成',
    completed_with_errors: '部分失败',
    failed: '失败',
    cancelled: '已取消',
  }
  return texts[status] || status
}

function calculateProgress(row: any) {
  if (!row.totalServers) return 0
  // 已完成 = 成功 + 失败，进度 = 已完成 / 总数
  const completed = (row.successCount || 0) + (row.failCount || 0)
  const running = row.runningCount || 0
  // 如果有正在执行的，显示 (已完成 + 执行中*0.5) / 总数，模拟进度感
  if (running > 0) {
    const estimated = completed + running * 0.5
    return Math.min(Math.round((estimated / row.totalServers) * 100), 95)
  }
  return Math.round((completed / row.totalServers) * 100)
}

function progressFormat(percentage: number) {
  return `${percentage}%`
}

function calculateDuration(startedAt: string, finishedAt: string | null) {
  if (!startedAt) return '-'
  
  const start = new Date(startedAt).getTime()
  const end = finishedAt ? new Date(finishedAt).getTime() : Date.now()
  return formatDuration(end - start)
}

async function handleExecute(row: any) {
  const isReExecute = row.status !== 'pending'
  const message = isReExecute 
    ? '确定要再次执行该任务吗？这将重置任务状态并重新执行。' 
    : '确定要执行该任务吗？'
  
  try {
    await ElMessageBox.confirm(message, '提示', { type: 'info' })
    const res = await request.post(`/tasks/${row.id}/execute`)
    if (res.code === 0) {
      ElMessage.success(isReExecute ? '任务已重置并开始执行' : '任务已开始执行')
      fetchData()
    }
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '执行失败')
    }
  }
}

async function handleCancel(row: any) {
  try {
    await ElMessageBox.confirm('确定要取消该任务吗？', '提示', { type: 'warning' })
    const res = await request.post(`/tasks/${row.id}/cancel`)
    if (res.code === 0) {
      ElMessage.success('任务已取消')
      fetchData()
    }
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '取消失败')
    }
  }
}

async function handleRetry(row: any) {
  try {
    const res = await request.post(`/tasks/${row.id}/retry`)
    if (res.code === 0) {
      ElMessage.success('任务已重新排队')
      fetchData()
    }
  } catch (e: any) {
    ElMessage.error(e.message || '重试失败')
  }
}

function handleDetail(row: any) {
  router.push(`/tasks/detail/${row.id}`)
}

async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm('确定要删除该任务吗？', '提示', { type: 'warning' })
    const res = await request.delete(`/tasks/${row.id}`)
    if (res.code === 0) {
      ElMessage.success('删除成功')
      fetchData()
    }
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '删除失败')
    }
  }
}

function toggleAutoRefresh(val: boolean) {
  if (val) {
    startAutoRefresh()
  } else {
    stopAutoRefresh()
  }
}

function startAutoRefresh() {
  if (refreshTimer) return
  refreshTimer = setInterval(() => {
    // 只有当有执行中的任务时才刷新
    const hasRunning = tableData.value.some(t => t.status === 'running')
    if (hasRunning) {
      fetchData()
    }
  }, 3000) // 每3秒刷新
}

function stopAutoRefresh() {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
}

onMounted(() => {
  fetchData()
  if (autoRefresh.value) {
    startAutoRefresh()
  }
})

onUnmounted(() => {
  stopAutoRefresh()
})

// 获取任务失败原因
function getTaskErrorMessage(row: any): string {
  // 如果有服务器详细数据（从详情页获取的），从中提取错误
  if (row.servers && row.servers.length > 0) {
    const failedServer = row.servers.find((s: any) => 
      s.overallStatus === 'failed' || s.deploy?.status === 'failed' || s.run?.status === 'failed' || s.cleanup?.status === 'failed'
    )
    
    if (failedServer) {
      const phases = ['deploy', 'run', 'cleanup'] as const
      for (const phase of phases) {
        const phaseData = failedServer[phase]
        if (phaseData?.status === 'failed' && phaseData?.output) {
          const errorLines = phaseData.output.split('\n')
            .filter((line: string) => line.includes('[ERROR]') || line.includes('error') || line.includes('failed'))
            .map((line: string) => line.replace(/^\[ERROR\]\s*/, '').trim())
            .filter((line: string) => line.length > 0)
          
          if (errorLines.length > 0) {
            return `${getPhaseText(phase)}失败: ${errorLines[0]}`
          }
          
          const trimmed = phaseData.output.trim()
          if (trimmed.length > 50) {
            return `${getPhaseText(phase)}失败: ${trimmed.substring(0, 50)}...`
          }
          return `${getPhaseText(phase)}失败: ${trimmed}`
        }
      }
    }
  }
  
  // 从任务状态判断失败原因
  if (row.deployStatus === 'failed') {
    return '部署阶段失败'
  }
  if (row.runStatus === 'failed') {
    return '执行阶段失败'
  }
  if (row.cleanupStatus === 'failed') {
    return '清理阶段失败'
  }
  
  // 根据统计数据提供信息
  if (row.failCount > 0) {
    return `${row.failCount} 台服务器执行失败`
  }
  
  return '执行失败，详情请查看日志'
}

// 获取阶段显示文本
function getPhaseText(phase: string): string {
  const texts: Record<string, string> = {
    deploy: '部署',
    run: '执行',
    cleanup: '清理',
  }
  return texts[phase] || phase
}
</script>

<style lang="scss" scoped>
.header-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.status-cell {
  display: flex;
  align-items: center;
  gap: 4px;
  
  .el-tag {
    .el-icon {
      margin-right: 4px;
    }
  }
  
  .error-icon {
    color: #f56c6c;
    cursor: pointer;
    font-size: 16px;
    
    &:hover {
      color: #f78989;
    }
  }
}

.progress-cell {
  .el-progress {
    margin-bottom: 4px;
  }
  
  .progress-detail {
    font-size: 12px;
    color: #909399;
    
    .success { color: #67c23a; }
    .fail { color: #f56c6c; }
    .running { color: #e6a23c; }
    .separator { margin: 0 4px; }
  }
}

.result-summary {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.text-muted {
  color: #c0c4cc;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>

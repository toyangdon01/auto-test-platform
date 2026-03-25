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
          <el-tag size="small" effect="plain" :type="getParallelModeType()">
            {{ getParallelModeText() }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatTime(task?.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="开始时间">{{ formatTime(task?.startedAt) }}</el-descriptions-item>
        <el-descriptions-item label="结束时间">{{ formatTime(task?.finishedAt) }}</el-descriptions-item>
        <el-descriptions-item label="总耗时">{{ calculateDuration(task?.startedAt, task?.finishedAt) }}</el-descriptions-item>
      </el-descriptions>
    </div>

    <!-- 执行层级可视化 -->
    <div class="page-card mt-20" v-if="executionLevels.length > 0">
      <h4 class="section-title">执行顺序</h4>
      <div class="execution-levels">
        <div v-for="(level, index) in executionLevels" :key="index" class="level-row">
          <div class="level-badge">
            <span class="level-num">第{{ index + 1 }}层</span>
            <span class="level-mode">{{ level.roles.length > 1 ? '并行' : '串行' }}</span>
          </div>
          <div class="level-roles">
            <el-tag 
              v-for="role in level.roles" 
              :key="role.name" 
              :type="role.status === 'completed' ? 'success' : role.status === 'failed' ? 'danger' : 'info'"
              effect="light"
              class="role-tag"
            >
              <el-icon v-if="role.status === 'running'" class="is-loading"><Loading /></el-icon>
              {{ role.displayName || role.name }}
              <span v-if="role.duration" class="role-duration">({{ role.duration }}s)</span>
            </el-tag>
          </div>
          <div v-if="index < executionLevels.length - 1" class="level-arrow">
            <el-icon><ArrowDown /></el-icon>
          </div>
        </div>
      </div>
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

    
    <!-- 步骤执行详情 -->
    <div class="page-card mt-20" v-if="taskSteps.length > 0">
      <h4 class="section-title">步骤执行详情</h4>
      <el-table :data="taskSteps" stripe>
        <el-table-column prop="stepName" label="步骤" width="120">
          <template #default="{ row }">
            <div class="step-name">
              <span class="step-label">{{ row.displayName || row.stepName }}</span>
              <span class="step-id text-muted" v-if="row.displayName">{{ row.stepName }}</span>
            </div>
          </template>
        </el-table-column>
        
        <el-table-column prop="serverName" label="执行服务器" width="180">
          <template #default="{ row }">
            <div class="server-info">
              <el-icon><Monitor /></el-icon>
              <span>{{ row.serverName || `Server-${row.serverId}` }}</span>
            </div>
          </template>
        </el-table-column>
        
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStepStatusType(row.status)" size="small" effect="light">
              <el-icon v-if="row.status === 'running'" class="is-loading"><Loading /></el-icon>
              <el-icon v-else-if="row.status === 'success'"><CircleCheck /></el-icon>
              <el-icon v-else-if="row.status === 'failed'"><CircleClose /></el-icon>
              <el-icon v-else-if="row.status === 'skipped'"><Warning /></el-icon>
              <el-icon v-else><Clock /></el-icon>
              {{ getStepStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        
        <el-table-column label="退出码" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.exitCode !== null && row.exitCode !== undefined" 
                    :type="row.exitCode === 0 ? 'success' : 'danger'" 
                    size="small">
              {{ row.exitCode }}
            </el-tag>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        
        <el-table-column label="开始时间" width="160">
          <template #default="{ row }">
            {{ formatTime(row.startedAt) }}
          </template>
        </el-table-column>
        
        <el-table-column label="结束时间" width="160">
          <template #default="{ row }">
            {{ formatTime(row.finishedAt) }}
          </template>
        </el-table-column>
        
        <el-table-column label="耗时" width="100">
          <template #default="{ row }">
            {{ calculateDuration(row.startedAt, row.finishedAt) }}
          </template>
        </el-table-column>
        
        <el-table-column label="依赖" min-width="120">
          <template #default="{ row }">
            <template v-if="row.dependsOn">
              <el-tag v-for="dep in row.dependsOn.split(',')" :key="dep" size="small" type="info" effect="plain" class="mr-4">
                {{ dep.trim() }}
              </el-tag>
            </template>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="showStepDetail(row)" :disabled="!row.output && !row.error && row.status === 'pending'">
              <el-icon><Document /></el-icon>详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 指标采集趋势（四个图表） -->
    <div class="page-card mt-20" v-if="task?.collectEnabled">
      <h4 class="section-title">
        <el-icon><DataLine /></el-icon>
        指标采集趋势
      </h4>
      <div class="metrics-grid">
        <div class="metric-chart-item">
          <h5 class="metric-chart-title">CPU 使用率</h5>
          <MetricSingleChart :task-id="task.id" metric-type="cpu" />
        </div>
        <div class="metric-chart-item">
          <h5 class="metric-chart-title">内存使用率</h5>
          <MetricSingleChart :task-id="task.id" metric-type="memory" />
        </div>
        <div class="metric-chart-item">
          <h5 class="metric-chart-title">磁盘 I/O</h5>
          <MetricSingleChart :task-id="task.id" metric-type="disk" />
        </div>
        <div class="metric-chart-item">
          <h5 class="metric-chart-title">网络流量</h5>
          <MetricSingleChart :task-id="task.id" metric-type="network" />
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
    
    <!-- 步骤详情弹窗 -->
    <el-dialog v-model="stepDetailVisible" title="步骤执行详情" width="800px" destroy-on-close>
      <template v-if="currentStep">
        <el-descriptions :column="2" border size="small" class="mb-20">
          <el-descriptions-item label="步骤名称">{{ currentStep.displayName || currentStep.stepName }}</el-descriptions-item>
          <el-descriptions-item label="步骤标识">{{ currentStep.stepName }}</el-descriptions-item>
          <el-descriptions-item label="执行服务器">{{ currentStep.serverName || `Server-${currentStep.serverId}` }}</el-descriptions-item>
          <el-descriptions-item label="执行脚本">{{ currentStep.script }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="getStepStatusType(currentStep.status)" size="small">
              {{ getStepStatusText(currentStep.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="退出码">
            <el-tag v-if="currentStep.exitCode !== null && currentStep.exitCode !== undefined" 
                    :type="currentStep.exitCode === 0 ? 'success' : 'danger'" size="small">
              {{ currentStep.exitCode }}
            </el-tag>
            <span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item label="开始时间">{{ formatTime(currentStep.startedAt) }}</el-descriptions-item>
          <el-descriptions-item label="结束时间">{{ formatTime(currentStep.finishedAt) }}</el-descriptions-item>
          <el-descriptions-item label="依赖步骤" :span="2">
            <template v-if="currentStep.dependsOn">
              <el-tag v-for="dep in currentStep.dependsOn.split(',')" :key="dep" size="small" type="info" class="mr-4">
                {{ dep.trim() }}
              </el-tag>
            </template>
            <span v-else>无</span>
          </el-descriptions-item>
        </el-descriptions>
        
        <div v-if="currentStep.errorMessage" class="mb-20">
          <h4 class="section-title">错误信息</h4>
          <el-alert type="error" :closable="false" show-icon>
            <pre class="error-text">{{ currentStep.errorMessage }}</pre>
          </el-alert>
        </div>
        
        <div v-if="currentStep.output">
          <h4 class="section-title">执行输出</h4>
          <div class="output-box">
            <pre class="output-content">{{ currentStep.output }}</pre>
          </div>
        </div>
        <div v-else class="no-output">
          <el-empty description="暂无执行输出" :image-size="60" />
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import MetricSingleChart from '@/components/MetricSingleChart.vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { 
  VideoPlay, VideoPause, RefreshRight, Refresh, Loading,
  Monitor, Document, DataAnalysis, DataLine, CopyDocument, ArrowDown,
  CircleCheck, CircleClose, Clock, Warning
} from '@element-plus/icons-vue'
import request from '@/utils/request'
import { taskApi, type TaskStep } from '@/api/task'
import axios from 'axios'

const route = useRoute()
const router = useRouter()
const taskId = Number(route.params.id)

const task = ref<any>(null)
const serverList = ref<any[]>([])
const taskSteps = ref<TaskStep[]>([])
const logDialogVisible = ref(false)
const resultDialogVisible = ref(false)
const stepDetailVisible = ref(false)
const currentServer = ref<any>(null)
const currentLogs = ref('')
const currentResult = ref<any>(null)
const currentLogPhase = ref<'deploy' | 'run' | 'cleanup' | 'all'>('all')
const currentStep = ref<TaskStep | null>(null)

let pollTimer: ReturnType<typeof setInterval> | null = null

// 计算属性
const successCount = computed(() => serverList.value.filter(s => s.overallStatus === 'completed').length)
const runningCount = computed(() => serverList.value.filter(s => s.overallStatus === 'running').length)
const failCount = computed(() => serverList.value.filter(s => s.overallStatus === 'failed' || s.overallStatus === 'error').length)
const pendingCount = computed(() => serverList.value.filter(s => !s.overallStatus || s.overallStatus === 'pending').length)

const overallProgress = computed(() => {
  if (serverList.value.length === 0) return 0
  const completed = successCount.value + failCount.value
  return Math.round((completed / serverList.value.length) * 100)
})

// 计算执行层级（根据角色依赖关系）
const executionLevels = computed(() => {
  if (!task.value?.servers || task.value.servers.length <= 1) return []
  
  // 按角色分组，计算每个角色的开始时间和状态
  const roleMap = new Map<string, { name: string; displayName: string; startedAt: string; finishedAt: string; status: string; duration: number }>()
  
  task.value.servers.forEach((s: any) => {
    if (s.role && !roleMap.has(s.role)) {
      const startTime = s.startedAt ? new Date(s.startedAt).getTime() : 0
      const endTime = s.finishedAt ? new Date(s.finishedAt).getTime() : 0
      const duration = startTime && endTime ? Math.round((endTime - startTime) / 1000) : 0
      
      roleMap.set(s.role, {
        name: s.role,
        displayName: s.roleName || s.role,
        startedAt: s.startedAt || '',
        finishedAt: s.finishedAt || '',
        status: s.overallStatus || 'pending',
        duration
      })
    }
  })
  
  if (roleMap.size <= 1) return []
  
  // 按开始时间分组（同一秒开始的视为同一层）
  const roles = Array.from(roleMap.values())
  roles.sort((a, b) => new Date(a.startedAt).getTime() - new Date(b.startedAt).getTime())
  
  // 按开始时间分组
  const levels: { roles: typeof roles }[] = []
  let currentLevel: typeof roles = []
  let lastStartTime = 0
  
  roles.forEach(role => {
    const startTime = new Date(role.startedAt).getTime()
    // 如果开始时间相差小于2秒，视为同一层
    if (currentLevel.length === 0 || Math.abs(startTime - lastStartTime) < 2000) {
      currentLevel.push(role)
      lastStartTime = startTime
    } else {
      levels.push({ roles: currentLevel })
      currentLevel = [role]
      lastStartTime = startTime
    }
  })
  
  if (currentLevel.length > 0) {
    levels.push({ roles: currentLevel })
  }
  
  return levels
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
    // 获取步骤数据
    await fetchSteps()
  } catch (e: any) {
    ElMessage.error('获取任务详情失败')
  }
}

async function fetchSteps() {
  try {
    const res = await taskApi.getSteps(taskId)
    if (res.code === 0) {
      taskSteps.value = res.data || []
      console.log('步骤数据:', taskSteps.value)
      // 打印每个步骤的 output 状态
      taskSteps.value.forEach((step: TaskStep) => {
        console.log(`步骤 ${step.id} (${step.stepName}): status=${step.status}, output长度=${step.output?.length || 0}`)
      })
    }
  } catch (e: any) {
    console.error('获取步骤数据失败', e)
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

function getStepStatusType(status?: string) {
  const types: Record<string, string> = {
    pending: 'info',
    waiting: 'info',
    running: 'warning',
    success: 'success',
    failed: 'danger',
    skipped: 'warning'
  }
  return types[status || ''] || 'info'
}

function getStepStatusText(status?: string) {
  const texts: Record<string, string> = {
    pending: '待执行',
    waiting: '等待中',
    running: '执行中',
    success: '成功',
    failed: '失败',
    skipped: '已跳过'
  }
  return texts[status || ''] || status || '未知'
}

function showStepDetail(step: TaskStep) {
  // 从最新的 taskSteps 中获取步骤数据，确保显示最新的输出
  const latestStep = taskSteps.value.find(s => s.id === step.id)
  currentStep.value = latestStep || step
  console.log('步骤详情 - stepId:', step.id, 'output长度:', latestStep?.output?.length, 'output:', latestStep?.output?.substring(0, 100))
  stepDetailVisible.value = true
}

// 获取并发模式显示文本
function getParallelModeText(): string {
  // 如果有多个执行层级，说明有依赖调度
  if (executionLevels.value.length > 1) {
    // 检查是否有并行层级
    const hasParallelLevel = executionLevels.value.some(level => level.roles.length > 1)
    if (hasParallelLevel) {
      return '智能并行'  // 有依赖且同层有并行
    }
    return '依赖调度'  // 有依赖分层执行，但每层单角色
  }
  // 单层多角色 = 纯并行
  if (executionLevels.value.length === 1 && executionLevels.value[0]?.roles.length > 1) {
    return '并行'
  }
  // 单角色或顺序执行
  if (task.value?.parallelMode === 'parallel') {
    return '并行'
  }
  return '顺序'
}

// 获取并发模式标签类型
function getParallelModeType(): string {
  if (executionLevels.value.length > 1) {
    return 'success'  // 依赖调度或智能并行
  }
  if (executionLevels.value.length === 1 && executionLevels.value[0]?.roles.length > 1) {
    return 'success'  // 并行
  }
  return ''
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

// 计算服务器的实际执行状态
function getServerRunStatus(server: any): string {
  // 优先使用 overallStatus
  if (server.overallStatus) {
    return server.overallStatus
  }
  return server.status || 'pending'
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
    if (server.overallStatus) {
      if (currentLogPhase.value === 'all') logs += '\n'
      logs += '========== 执行阶段 ==========\n'
      logs += `状态: ${server.overallStatus || '-'}\n`
      logs += `进度: ${server.progress ?? 0}%\n`
      logs += `开始时间: ${formatTime(task.value?.startedAt)}\n`
      logs += `结束时间: ${formatTime(task.value?.finishedAt)}\n`
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
      
      // 如果日志弹窗是打开的，实时刷新日志内容
      if (logDialogVisible.value && currentServer.value) {
        // 更新 currentServer 为最新的服务器数据
        const latestServer = serverList.value.find(s => s.serverId === currentServer.value?.serverId)
        if (latestServer) {
          currentServer.value = latestServer
        }
        refreshLogs()
      }
      
      // 如果步骤详情弹窗是打开的，实时刷新步骤输出
      if (stepDetailVisible.value && currentStep.value) {
        // 从最新的 taskSteps 中找到对应的步骤
        const latestStep = taskSteps.value.find(s => s.id === currentStep.value?.id)
        if (latestStep) {
          const oldOutputLen = currentStep.value.output?.length || 0
          currentStep.value = latestStep
          // 调试日志
          if (latestStep.output && latestStep.output.length > oldOutputLen) {
            console.log('实时刷新步骤输出 - stepId:', latestStep.id, 'output长度:', latestStep.output.length)
            // 自动滚动到输出框底部
            nextTick(() => {
              const outputBox = document.querySelector('.output-box .output-content')
              if (outputBox) {
                outputBox.scrollTop = outputBox.scrollHeight
              }
            })
          }
        }
      }
    }
  }, 1000)  // 改为1秒轮询
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

// 执行层级可视化
.execution-levels {
  padding: 8px 0;
  
  .level-row {
    display: flex;
    align-items: center;
    gap: 16px;
    padding: 8px 0;
    
    .level-badge {
      display: flex;
      flex-direction: column;
      align-items: center;
      min-width: 70px;
      
      .level-num {
        font-size: 13px;
        font-weight: 600;
        color: #303133;
      }
      
      .level-mode {
        font-size: 11px;
        color: #909399;
        margin-top: 2px;
      }
    }
    
    .level-roles {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      flex: 1;
      
      .role-tag {
        display: flex;
        align-items: center;
        gap: 4px;
        
        .role-duration {
          font-size: 11px;
          opacity: 0.8;
        }
      }
    }
    
    .level-arrow {
      position: absolute;
      left: 35px;
      margin-top: 20px;
      color: #c0c4cc;
    }
  }
  
  .level-row:not(:last-child) {
    position: relative;
    padding-bottom: 24px;
    
    &::after {
      content: '';
      position: absolute;
      left: 35px;
      top: 40px;
      width: 1px;
      height: calc(100% - 40px);
      background: #e4e7ed;
    }
  }
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

// 步骤名称样式
.step-name {
  .step-label {
    display: block;
    font-weight: 500;
  }
  .step-id {
    display: block;
    font-size: 12px;
    margin-top: 2px;
  }
}

// 错误文本
.error-text {
  margin: 0;
  font-size: 13px;
  white-space: pre-wrap;
  word-break: break-all;
}

// 输出框
.output-box {
  background: #1e1e1e;
  border-radius: 6px;
  overflow: hidden;
  
  .output-content {
    padding: 16px;
    margin: 0;
    color: #d4d4d4;
    font-family: 'Consolas', 'Fira Code', monospace;
    font-size: 13px;
    line-height: 1.6;
    overflow-x: auto;
    white-space: pre-wrap;
    word-break: break-all;
    max-height: 400px;
    overflow-y: auto;
  }
}

.mr-4 {
  margin-right: 4px;
}

// 指标采集趋势网格布局
.metrics-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
  
  @media (max-width: 1200px) {
    grid-template-columns: 1fr;
  }
}

.metric-chart-item {
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
  
  .metric-chart-title {
    font-size: 13px;
    font-weight: 600;
    color: #303133;
    margin: 0 0 12px 0;
    padding-left: 4px;
  }
}
</style>


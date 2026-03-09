<template>
  <div class="task-detail">
    <div class="page-card">
      <div class="page-header">
        <el-page-header @back="$router.back()">
          <template #content>
            <span class="title">{{ task?.name || '任务详情' }}</span>
          </template>
        </el-page-header>
        
        <div class="header-actions">
          <el-button v-if="task?.status === 'running'" type="warning" @click="handleCancel">
            取消任务
          </el-button>
          <el-button v-if="task?.status === 'failed'" type="primary" @click="handleRetry">
            重试
          </el-button>
        </div>
      </div>

      <el-descriptions :column="3" border>
        <el-descriptions-item label="任务ID">{{ task?.id }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="getStatusType(task?.status)">{{ getStatusText(task?.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="执行模式">{{ task?.executionMode === 'immediate' ? '立即执行' : '定时执行' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ task?.createdAt }}</el-descriptions-item>
        <el-descriptions-item label="开始时间">{{ task?.startedAt || '-' }}</el-descriptions-item>
        <el-descriptions-item label="结束时间">{{ task?.finishedAt || '-' }}</el-descriptions-item>
      </el-descriptions>
    </div>

    <!-- 生命周期时间线 -->
    <div class="page-card mt-20">
      <h4 class="section-title">执行进度</h4>
      <el-timeline v-if="lifecycle.length">
        <el-timeline-item
          v-for="stage in lifecycle"
          :key="stage.name"
          :type="getStageType(stage.status)"
          :timestamp="stage.time"
          placement="top"
        >
          <el-card shadow="never">
            <h4>{{ stage.label }}</h4>
            <p>状态: <el-tag :type="getStageType(stage.status)" size="small">{{ stage.status }}</el-tag></p>
            <p v-if="stage.duration">耗时: {{ stage.duration }}秒</p>
          </el-card>
        </el-timeline-item>
      </el-timeline>
    </div>

    <!-- 服务器执行状态 -->
    <div class="page-card mt-20">
      <h4 class="section-title">服务器执行状态</h4>
      <el-table :data="serverStatus" stripe>
        <el-table-column prop="serverName" label="服务器" min-width="150" />
        <el-table-column label="部署" width="100">
          <template #default="{ row }">
            <el-tag :type="getStageType(row.deploy?.status)" size="small">
              {{ row.deploy?.status || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="执行" width="100">
          <template #default="{ row }">
            <el-tag :type="getStageType(row.run?.status)" size="small">
              {{ row.run?.status || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="卸载" width="100">
          <template #default="{ row }">
            <el-tag :type="getStageType(row.cleanup?.status)" size="small">
              {{ row.cleanup?.status || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="整体状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.overallStatus)" size="small">
              {{ row.overallStatus }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button type="primary" link @click="showLogs(row)">日志</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 日志弹窗 -->
    <el-dialog v-model="logDialogVisible" title="执行日志" width="800px">
      <el-tabs v-model="logTab">
        <el-tab-pane label="部署日志" name="deploy" />
        <el-tab-pane label="执行日志" name="run" />
        <el-tab-pane label="卸载日志" name="cleanup" />
      </el-tabs>
      <pre class="log-content">{{ currentLogs }}</pre>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { taskApi, type Task } from '@/api/script'

const route = useRoute()
const router = useRouter()
const taskId = Number(route.params.id)

const task = ref<Task | null>(null)
const serverStatus = ref<any[]>([])
const lifecycle = ref<any[]>([])
const logDialogVisible = ref(false)
const logTab = ref('run')
const currentLogs = ref('')

let pollTimer: number | null = null

async function fetchDetail() {
  const res = await taskApi.get(taskId)
  if (res.code === 0) {
    task.value = res.data
    
    // 模拟生命周期数据
    lifecycle.value = [
      { name: 'deploy', label: '部署阶段', status: 'completed', time: '2026-03-09 10:00', duration: 30 },
      { name: 'run', label: '执行阶段', status: 'running', time: '2026-03-09 10:01', duration: null },
      { name: 'cleanup', label: '卸载阶段', status: 'pending', time: null, duration: null },
    ]
    
    // 模拟服务器状态
    serverStatus.value = [
      { serverId: 1, serverName: 'Server-01', deploy: { status: 'completed' }, run: { status: 'running' }, cleanup: { status: 'pending' }, overallStatus: 'running' },
      { serverId: 2, serverName: 'Server-02', deploy: { status: 'completed' }, run: { status: 'completed' }, cleanup: { status: 'pending' }, overallStatus: 'running' },
    ]
  }
}

function getStatusType(status?: string) {
  if (!status) return 'info'
  const types: Record<string, string> = {
    pending: 'info',
    running: 'warning',
    completed: 'success',
    failed: 'danger',
    cancelled: 'info',
  }
  return types[status] || 'info'
}

function getStatusText(status?: string) {
  if (!status) return '-'
  const texts: Record<string, string> = {
    pending: '待执行',
    running: '执行中',
    completed: '已完成',
    failed: '失败',
    cancelled: '已取消',
  }
  return texts[status] || status
}

function getStageType(status?: string) {
  return getStatusType(status)
}

async function handleCancel() {
  await ElMessageBox.confirm('确定要取消该任务吗？', '提示')
  const res = await taskApi.cancel(taskId)
  if (res.code === 0) {
    ElMessage.success('任务已取消')
    fetchDetail()
  }
}

async function handleRetry() {
  const res = await taskApi.retry(taskId)
  if (res.code === 0) {
    ElMessage.success('任务已重新排队')
    fetchDetail()
  }
}

function showLogs(row: any) {
  logDialogVisible.value = true
  currentLogs.value = `[${row.serverName}] 执行日志...\n正在加载中...`
}

onMounted(() => {
  fetchDetail()
  
  // 如果任务正在执行，轮询更新
  pollTimer = window.setInterval(() => {
    if (task.value?.status === 'running') {
      fetchDetail()
    }
  }, 5000)
})

onUnmounted(() => {
  if (pollTimer) {
    clearInterval(pollTimer)
  }
})
</script>

<style lang="scss" scoped>
.title {
  font-size: 16px;
  font-weight: 600;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 16px;
}

.log-content {
  max-height: 400px;
  padding: 16px;
  background: #1e1e1e;
  color: #d4d4d4;
  font-family: 'Fira Code', monospace;
  font-size: 13px;
  line-height: 1.5;
  overflow: auto;
  border-radius: var(--radius-sm);
}
</style>

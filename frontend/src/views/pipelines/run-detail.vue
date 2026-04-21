<template>
  <div class="page-card" v-loading="loading">
    <div class="page-header">
      <el-page-header @back="$router.back()">
        <template #content>
          <span class="title">执行详情</span>
        </template>
        <template #extra>
          <el-button v-if="run?.status === 'running'" type="danger" @click="handleCancel">取消执行</el-button>
        </template>
      </el-page-header>
    </div>

    <el-descriptions :column="3" border v-if="run" style="margin-bottom: 24px">
      <el-descriptions-item label="编排名称">{{ run.pipelineName }}</el-descriptions-item>
      <el-descriptions-item label="执行状态">
        <el-tag :type="getStatusType(run.status)">{{ run.status }}</el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="触发者">{{ run.triggeredBy || '-' }}</el-descriptions-item>
      <el-descriptions-item label="开始时间">{{ formatTime(run.startedAt) }}</el-descriptions-item>
      <el-descriptions-item label="结束时间">{{ formatTime(run.finishedAt) }}</el-descriptions-item>
      <el-descriptions-item label="创建时间">{{ formatTime(run.createdAt) }}</el-descriptions-item>
    </el-descriptions>

    <h4 style="margin-bottom: 16px">任务列表</h4>
    <el-table :data="tasks" v-loading="loading" stripe>
      <el-table-column prop="taskName" label="任务名称" min-width="200" />
      <el-table-column prop="status" label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="getStatusType(row.status)">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button type="primary" link @click="$router.push(`/tasks/detail/${row.taskId}`)">
            详情
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getPipelineRun, getPipelineRunTasks, cancelPipelineRun } from '@/api/pipeline'
import type { PipelineRun, PipelineRunTask } from '@/api/pipeline'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const run = ref<PipelineRun | null>(null)
const tasks = ref<PipelineRunTask[]>([])
let pollTimer: number | null = null

function getStatusType(status: string) {
  const types: Record<string, string> = {
    pending: 'info',
    running: 'warning',
    completed: 'success',
    failed: 'danger',
    cancelled: 'info',
  }
  return types[status] || 'info'
}

function formatTime(time: string) {
  if (!time) return '-'
  return time.replace('T', ' ').substring(0, 16)
}

async function loadRun(silent = false) {
  try {
    const runId = Number(route.params.runId)
    const res = await getPipelineRun(runId)
    if (res.code === 0 && res.data) {
      run.value = res.data
    }
  } catch (e) {
    if (!silent) {
      console.error('加载执行详情失败:', e)
    }
  }
}

async function loadTasks(silent = false) {
  if (!silent) {
    loading.value = true
  }
  try {
    const runId = Number(route.params.runId)
    const res = await getPipelineRunTasks(runId)
    if (res.code === 0 && res.data) {
      tasks.value = res.data
    }
  } catch (e) {
    console.error('加载任务列表失败:', e)
  } finally {
    if (!silent) {
      loading.value = false
    }
  }
}

async function handleCancel() {
  try {
    const runId = Number(route.params.runId)
    await cancelPipelineRun(runId)
    ElMessage.success('已取消执行')
    loadRun()
    loadTasks()
  } catch (e) {
    ElMessage.error('取消失败')
  }
}

function startPolling() {
  pollTimer = window.setInterval(() => {
    if (run.value?.status === 'running') {
      loadRun(true)  // 静默加载，不显示 loading
      loadTasks(true)
    }
  }, 10000)  // 10秒轮询一次
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

onMounted(async () => {
  await loadRun()
  await loadTasks()
  startPolling()
})

onUnmounted(() => {
  stopPolling()
})
</script>

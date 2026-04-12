<template>
  <div class="page-card">
    <div class="page-header">
      <h3 class="page-title">执行记录</h3>
      <el-button @click="$router.back()">返回</el-button>
    </div>

    <el-table :data="runs" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="pipelineName" label="编排名称" min-width="150" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="getStatusType(row.status)">{{ getStatusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="triggeredBy" label="触发者" width="100" />
      <el-table-column prop="startedAt" label="开始时间" width="180">
        <template #default="{ row }">
          {{ formatTime(row.startedAt) }}
        </template>
      </el-table-column>
      <el-table-column prop="finishedAt" label="结束时间" width="180">
        <template #default="{ row }">
          {{ formatTime(row.finishedAt) }}
        </template>
      </el-table-column>
      <el-table-column label="耗时" width="100">
        <template #default="{ row }">
          {{ calculateDuration(row.startedAt, row.finishedAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button type="primary" link @click="handleViewDetail(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="page"
      v-model:page-size="size"
      :total="total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      @size-change="loadRuns"
      @current-change="loadRuns"
      style="margin-top: 16px; justify-content: flex-end"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listPipelineRuns } from '@/api/pipeline'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const runs = ref<any[]>([])
const page = ref(1)
const size = ref(10)
const total = ref(0)

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

function getStatusText(status: string) {
  const texts: Record<string, string> = {
    pending: '等待中',
    running: '执行中',
    completed: '已完成',
    failed: '失败',
    cancelled: '已取消',
  }
  return texts[status] || status
}

function formatTime(time: string) {
  if (!time) return '-'
  return time.replace('T', ' ').substring(0, 16)
}

function calculateDuration(startedAt: string, finishedAt: string) {
  if (!startedAt || !finishedAt) return '-'
  const start = new Date(startedAt).getTime()
  const end = new Date(finishedAt).getTime()
  const seconds = Math.floor((end - start) / 1000)
  if (seconds < 60) return `${seconds}秒`
  const minutes = Math.floor(seconds / 60)
  const remainingSeconds = seconds % 60
  return `${minutes}分${remainingSeconds}秒`
}

async function loadRuns() {
  loading.value = true
  try {
    const pipelineId = route.query.pipelineId ? Number(route.query.pipelineId) : undefined
    const res = await listPipelineRuns({ pipelineId, page: page.value, size: size.value })
    if (res.code === 0 && res.data) {
      runs.value = res.data.records || []
      total.value = res.data.total || 0
    }
  } catch (e) {
    console.error('加载执行记录失败:', e)
  } finally {
    loading.value = false
  }
}

function handleViewDetail(row: any) {
  router.push(`/pipelines/runs/${row.id}`)
}

onMounted(() => {
  loadRuns()
})
</script>

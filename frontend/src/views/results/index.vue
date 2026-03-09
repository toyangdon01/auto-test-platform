<template>
  <div class="page-card">
    <div class="page-header">
      <h3 class="page-title">测试结果列表</h3>
    </div>

    <el-table :data="results" stripe>
      <el-table-column prop="taskId" label="任务ID" width="100" />
      <el-table-column prop="taskName" label="任务名称" min-width="200" />
      <el-table-column prop="result" label="结果" width="100">
        <template #default="{ row }">
          <el-tag :type="getResultType(row.result)">{{ row.result }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="score" label="评分" width="100" />
      <el-table-column prop="createdAt" label="生成时间" width="160" />
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button type="primary" link @click="handleDetail(row)">详情</el-button>
          <el-button type="primary" link @click="handleDownload(row)">下载</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const results = ref([
  { id: 1, taskId: 101, taskName: 'CPU压力测试', result: 'pass', score: 95, createdAt: '2026-03-09 10:30' },
  { id: 2, taskId: 102, taskName: '内存带宽测试', result: 'warning', score: 78, createdAt: '2026-03-09 09:00' },
  { id: 3, taskId: 103, taskName: '磁盘IO测试', result: 'fail', score: 45, createdAt: '2026-03-08 16:00' },
])

function getResultType(result: string) {
  const types: Record<string, string> = {
    pass: 'success',
    warning: 'warning',
    fail: 'danger',
  }
  return types[result] || 'info'
}

function handleDetail(row: any) {
  router.push(`/results/detail/${row.id}`)
}

function handleDownload(row: any) {
  console.log('download', row)
}
</script>

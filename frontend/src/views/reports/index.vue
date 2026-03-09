<template>
  <div class="page-card">
    <div class="page-header">
      <h3 class="page-title">报告列表</h3>
    </div>

    <el-table :data="reports" stripe>
      <el-table-column prop="name" label="报告名称" min-width="200" />
      <el-table-column prop="type" label="类型" width="120">
        <template #default="{ row }">
          <el-tag>{{ row.type }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'completed' ? 'success' : 'warning'">
            {{ row.status === 'completed' ? '已生成' : '生成中' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="生成时间" width="160" />
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button type="primary" link @click="handleView(row)">查看</el-button>
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
const reports = ref([
  { id: 1, name: 'CPU测试报告-20260309', type: 'PDF', status: 'completed', createdAt: '2026-03-09 11:00' },
  { id: 2, name: '综合测试报告-20260308', type: 'PDF', status: 'completed', createdAt: '2026-03-08 18:00' },
])

function handleView(row: any) {
  router.push(`/reports/detail/${row.id}`)
}

function handleDownload(row: any) {
  console.log('download', row)
}
</script>

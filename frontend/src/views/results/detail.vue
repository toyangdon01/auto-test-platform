<template>
  <div class="page-card">
    <div class="page-header">
      <el-page-header @back="$router.back()">
        <template #content>
          <span class="title">结果详情</span>
        </template>
      </el-page-header>
    </div>

    <el-descriptions :column="3" border>
      <el-descriptions-item label="任务名称">CPU压力测试</el-descriptions-item>
      <el-descriptions-item label="执行结果">
        <el-tag type="success">PASS</el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="综合评分">95分</el-descriptions-item>
    </el-descriptions>

    <el-divider content-position="left">指标数据</el-divider>

    <el-table :data="metrics" stripe>
      <el-table-column prop="name" label="指标名称" min-width="150" />
      <el-table-column prop="value" label="数值" width="120" />
      <el-table-column prop="unit" label="单位" width="80" />
      <el-table-column prop="baseline" label="基准线" width="100" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'normal' ? 'success' : 'warning'" size="small">
            {{ row.status === 'normal' ? '正常' : '异常' }}
          </el-tag>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const metrics = ref([
  { name: 'CPU使用率峰值', value: 98.5, unit: '%', baseline: 90, status: 'normal' },
  { name: 'CPU温度峰值', value: 75, unit: '°C', baseline: 80, status: 'normal' },
  { name: '平均负载', value: 8.5, unit: '', baseline: 10, status: 'normal' },
])
</script>

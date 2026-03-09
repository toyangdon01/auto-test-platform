<template>
  <div class="page-card">
    <div class="page-header">
      <h3 class="page-title">指标定义</h3>
      <el-button type="primary" @click="handleAdd">
        <el-icon><Plus /></el-icon>
        新增指标
      </el-button>
    </div>

    <el-table :data="metrics" stripe>
      <el-table-column prop="name" label="指标名称" min-width="150" />
      <el-table-column prop="key" label="指标Key" width="150" />
      <el-table-column prop="type" label="类型" width="100" />
      <el-table-column prop="unit" label="单位" width="80" />
      <el-table-column prop="baseline" label="基准线" width="100" />
      <el-table-column prop="operator" label="判定规则" width="100" />
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
          <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'

const metrics = ref([
  { id: 1, name: 'CPU使用率', key: 'cpu_usage', type: 'gauge', unit: '%', baseline: 90, operator: '<=' },
  { id: 2, name: '内存使用率', key: 'memory_usage', type: 'gauge', unit: '%', baseline: 80, operator: '<=' },
  { id: 3, name: '磁盘IO', key: 'disk_io', type: 'counter', unit: 'MB/s', baseline: 100, operator: '>=' },
])

function handleAdd() {
  ElMessage.info('新增指标')
}

function handleEdit(row: any) {
  ElMessage.info(`编辑: ${row.name}`)
}

function handleDelete(row: any) {
  ElMessage.info(`删除: ${row.name}`)
}
</script>

<template>
  <div class="page-card">
    <div class="page-header">
      <h3 class="page-title">判定规则</h3>
      <el-button type="primary" @click="handleAdd">
        <el-icon><Plus /></el-icon>
        新增规则
      </el-button>
    </div>

    <el-table :data="rules" stripe>
      <el-table-column prop="name" label="规则名称" min-width="150" />
      <el-table-column prop="type" label="类型" width="100" />
      <el-table-column prop="conditions" label="条件" min-width="300">
        <template #default="{ row }">
          <code>{{ row.conditions }}</code>
        </template>
      </el-table-column>
      <el-table-column prop="result" label="判定结果" width="100" />
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

const rules = ref([
  { id: 1, name: 'CPU测试通过规则', type: 'threshold', conditions: 'cpu_usage <= 90 AND cpu_temp <= 80', result: 'PASS' },
  { id: 2, name: 'CPU测试警告规则', type: 'threshold', conditions: 'cpu_usage > 90 AND cpu_usage <= 95', result: 'WARNING' },
  { id: 3, name: 'CPU测试失败规则', type: 'threshold', conditions: 'cpu_usage > 95 OR cpu_temp > 85', result: 'FAIL' },
])

function handleAdd() {
  ElMessage.info('新增规则')
}

function handleEdit(row: any) {
  ElMessage.info(`编辑: ${row.name}`)
}

function handleDelete(row: any) {
  ElMessage.info(`删除: ${row.name}`)
}
</script>

<style lang="scss" scoped>
code {
  padding: 2px 6px;
  background: #f5f5f5;
  border-radius: 4px;
  font-family: 'Fira Code', monospace;
  font-size: 12px;
}
</style>

<template>
  <div class="page-card">
    <div class="page-header">
      <h3 class="page-title">结果对比</h3>
    </div>

    <el-alert type="info" :closable="false" class="mb-20">
      选择2-5次测试结果进行对比分析
    </el-alert>

    <div class="compare-selector">
      <el-select v-model="selectedResults" multiple placeholder="选择测试结果" style="width: 400px">
        <el-option v-for="item in results" :key="item.id" :label="item.name" :value="item.id" />
      </el-select>
      <el-button type="primary" @click="handleCompare">开始对比</el-button>
    </div>

    <div v-if="compareData.length" class="compare-content mt-20">
      <el-table :data="compareData" stripe>
        <el-table-column prop="metric" label="指标" min-width="150" fixed="left" />
        <el-table-column v-for="col in compareColumns" :key="col.id" :label="col.label" width="120">
          <template #default="{ row }">
            <span :class="{ 'text-success': row[col.id]?.isBest, 'text-danger': row[col.id]?.isWorst }">
              {{ row[col.id]?.value || '-' }}
            </span>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'

const selectedResults = ref<number[]>([])
const results = ref([
  { id: 1, name: 'CPU测试 #101' },
  { id: 2, name: 'CPU测试 #102' },
  { id: 3, name: 'CPU测试 #103' },
])

const compareData = ref<any[]>([])
const compareColumns = computed(() =>
  selectedResults.value.map((id) => {
    const r = results.value.find((x) => x.id === id)
    return { id: `col_${id}`, label: r?.name || '' }
  })
)

function handleCompare() {
  if (selectedResults.value.length < 2) {
    return
  }
  
  compareData.value = [
    { metric: 'CPU使用率', col_1: { value: '98%', isBest: true }, col_2: { value: '95%' }, col_3: { value: '99%', isWorst: true } },
    { metric: '内存使用', col_1: { value: '4.2GB' }, col_2: { value: '3.8GB', isBest: true }, col_3: { value: '4.5GB', isWorst: true } },
  ]
}
</script>

<style lang="scss" scoped>
.compare-selector {
  display: flex;
  gap: 12px;
  align-items: center;
}

.text-success {
  color: var(--success-color);
  font-weight: 600;
}

.text-danger {
  color: var(--danger-color);
  font-weight: 600;
}
</style>

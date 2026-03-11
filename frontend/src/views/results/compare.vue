<template>
  <div class="page-card">
    <div class="page-header">
      <h3 class="page-title">结果对比</h3>
    </div>

    <!-- 对比配置 -->
    <el-card shadow="never" style="margin-bottom: 16px">
      <el-form :inline="true">
        <el-form-item label="对比方式">
          <el-select v-model="compareType" style="width: 150px">
            <el-option label="按结果ID" value="resultIds" />
            <el-option label="按任务" value="task" />
          </el-select>
        </el-form-item>

        <el-form-item v-if="compareType === 'resultIds'" label="结果ID">
          <el-select
            v-model="selectedResultIds"
            multiple
            filterable
            placeholder="选择要对比的结果"
            style="width: 400px"
          >
            <el-option
              v-for="r in allResults"
              :key="r.id"
              :label="`#${r.id} - ${r.taskName || 'Task-' + r.taskId}`"
              :value="r.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item v-else label="任务">
          <el-select v-model="selectedTaskId" placeholder="选择任务" style="width: 300px">
            <el-option
              v-for="t in tasks"
              :key="t.id"
              :label="t.name"
              :value="t.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="loading" @click="doCompare">
            <el-icon><DataAnalysis /></el-icon>
            开始对比
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 对比结果 -->
    <div v-if="compareResult" class="compare-result">
      <!-- 统计概览 -->
      <el-card shadow="never" style="margin-bottom: 16px">
        <template #header>
          <span>对比概览</span>
        </template>
        <el-row :gutter="20">
          <el-col :span="6">
            <div class="stat-item">
              <div class="stat-value">{{ compareResult.statistics?.totalResults || 0 }}</div>
              <div class="stat-label">对比结果数</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="stat-item">
              <div class="stat-value pass">{{ compareResult.statistics?.passCount || 0 }}</div>
              <div class="stat-label">通过数</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="stat-item">
              <div class="stat-value fail">{{ compareResult.statistics?.failCount || 0 }}</div>
              <div class="stat-label">失败数</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="stat-item">
              <div class="stat-value">{{ (compareResult.statistics?.avgScore || 0).toFixed(1) }}</div>
              <div class="stat-label">平均分</div>
            </div>
          </el-col>
        </el-row>
      </el-card>

      <!-- 结果列表对比 -->
      <el-card shadow="never" style="margin-bottom: 16px">
        <template #header>
          <span>结果对比</span>
        </template>
        <el-table :data="compareResult.results" stripe>
          <el-table-column prop="taskName" label="任务" min-width="150" />
          <el-table-column prop="serverName" label="服务器" width="150" />
          <el-table-column prop="result" label="结果" width="80">
            <template #default="{ row }">
              <el-tag :type="row.result === 'pass' ? 'success' : 'danger'" size="small">
                {{ row.result === 'pass' ? '通过' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="overallScore" label="分数" width="80" />
          <el-table-column prop="durationMs" label="耗时(ms)" width="100" />
          <el-table-column prop="executedAt" label="执行时间" width="160">
            <template #default="{ row }">
              {{ formatTime(row.executedAt) }}
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <!-- 指标对比 -->
      <el-card v-if="compareResult.metrics?.length > 0" shadow="never">
        <template #header>
          <span>指标对比</span>
        </template>
        <el-table :data="compareResult.metrics" stripe>
          <el-table-column prop="metricName" label="指标名称" width="200" />
          <el-table-column label="值对比" min-width="400">
            <template #default="{ row }">
              <div class="metric-values">
                <span
                  v-for="(v, i) in row.values"
                  :key="i"
                  class="metric-value"
                  :class="{ 'value-up': row.trend === 'up', 'value-down': row.trend === 'down' }"
                >
                  {{ v.displayValue }}
                </span>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="changeRate" label="变化率" width="100">
            <template #default="{ row }">
              <span v-if="row.changeRate !== null" :class="row.changeRate > 0 ? 'rate-up' : 'rate-down'">
                {{ row.changeRate > 0 ? '+' : '' }}{{ row.changeRate.toFixed(1) }}%
              </span>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column prop="trend" label="趋势" width="80">
            <template #default="{ row }">
              <el-tag v-if="row.trend === 'up'" type="success" size="small">上升</el-tag>
              <el-tag v-else-if="row.trend === 'down'" type="danger" size="small">下降</el-tag>
              <el-tag v-else type="info" size="small">稳定</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-card>

      <!-- 差异分析 -->
      <el-card v-if="compareResult.differences?.length > 0" shadow="never" style="margin-top: 16px">
        <template #header>
          <span>差异分析</span>
        </template>
        <el-table :data="compareResult.differences" stripe>
          <el-table-column prop="category" label="类别" width="100" />
          <el-table-column prop="name" label="项目" width="120" />
          <el-table-column label="变化">
            <template #default="{ row }">
              <div v-for="(c, i) in row.changes" :key="i" class="diff-change">
                <span class="old-value">{{ c.oldValue }}</span>
                <el-icon><Right /></el-icon>
                <span class="new-value">{{ c.newValue }}</span>
                <span v-if="c.changePercent" class="change-percent">
                  ({{ c.changePercent > 0 ? '+' : '' }}{{ c.changePercent.toFixed(1) }}%)
                </span>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { DataAnalysis, Right } from '@element-plus/icons-vue'
import request from '@/utils/request'

const loading = ref(false)
const compareType = ref<'resultIds' | 'task'>('resultIds')
const selectedResultIds = ref<number[]>([])
const selectedTaskId = ref<number | null>(null)
const allResults = ref<any[]>([])
const tasks = ref<any[]>([])
const compareResult = ref<any>(null)

onMounted(() => {
  fetchAllResults()
  fetchTasks()
})

async function fetchAllResults() {
  try {
    const res = await request.get('/results', { params: { size: 100 } })
    allResults.value = res.data.items || []
  } catch (error) {
    console.error('获取结果列表失败:', error)
  }
}

async function fetchTasks() {
  try {
    const res = await request.get('/tasks', { params: { size: 100 } })
    tasks.value = res.data.items || []
  } catch (error) {
    console.error('获取任务列表失败:', error)
  }
}

async function doCompare() {
  if (compareType.value === 'resultIds' && selectedResultIds.value.length < 2) {
    ElMessage.warning('请至少选择2个结果进行对比')
    return
  }
  if (compareType.value === 'task' && !selectedTaskId.value) {
    ElMessage.warning('请选择任务')
    return
  }

  loading.value = true
  try {
    const payload: any = { compareType: compareType.value }
    if (compareType.value === 'resultIds') {
      payload.resultIds = selectedResultIds.value
    } else {
      payload.taskId = selectedTaskId.value
    }

    const res = await request.post('/results/compare', payload)
    compareResult.value = res.data
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '对比失败')
  } finally {
    loading.value = false
  }
}

function formatTime(time: string) {
  if (!time) return '-'
  return time.replace('T', ' ').substring(0, 19)
}
</script>

<style scoped>
.stat-item {
  text-align: center;
  padding: 16px;
}

.stat-value {
  font-size: 28px;
  font-weight: bold;
  color: var(--el-color-primary);
}

.stat-value.pass {
  color: var(--el-color-success);
}

.stat-value.fail {
  color: var(--el-color-danger);
}

.stat-label {
  color: var(--el-text-color-secondary);
  margin-top: 8px;
}

.metric-values {
  display: flex;
  gap: 12px;
}

.metric-value {
  padding: 4px 8px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
  font-family: monospace;
}

.metric-value.value-up {
  background: var(--el-color-success-light-9);
  color: var(--el-color-success);
}

.metric-value.value-down {
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
}

.rate-up {
  color: var(--el-color-success);
}

.rate-down {
  color: var(--el-color-danger);
}

.diff-change {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
}

.old-value {
  color: var(--el-text-color-secondary);
}

.new-value {
  color: var(--el-color-primary);
  font-weight: 500;
}

.change-percent {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>

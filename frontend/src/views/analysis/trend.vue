<template>
  <div class="page-card">
    <div class="page-header">
      <h3 class="page-title">趋势分析</h3>
    </div>

    <!-- 配置 -->
    <el-card shadow="never" style="margin-bottom: 16px">
      <el-form :inline="true">
        <el-form-item label="脚本">
          <el-select v-model="selectedScriptId" placeholder="选择脚本" clearable style="width: 200px">
            <el-option v-for="s in scripts" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围">
          <el-select v-model="days" style="width: 120px">
            <el-option :value="7" label="近7天" />
            <el-option :value="14" label="近14天" />
            <el-option :value="30" label="近30天" />
            <el-option :value="60" label="近60天" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="fetchData">分析</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 整体趋势 -->
    <el-card shadow="never" style="margin-bottom: 16px">
      <template #header>
        <span>整体趋势</span>
      </template>
      <div v-if="overallTrend" class="trend-overview">
        <el-row :gutter="20">
          <el-col :span="4">
            <div class="stat-card">
              <div class="stat-value">{{ overallTrend.totalResults || 0 }}</div>
              <div class="stat-label">总测试数</div>
            </div>
          </el-col>
          <el-col :span="4">
            <div class="stat-card">
              <div class="stat-value pass">{{ overallTrend.overallPassRate?.toFixed(1) || 0 }}%</div>
              <div class="stat-label">通过率</div>
            </div>
          </el-col>
        </el-row>

        <!-- 趋势表格 -->
        <el-table :data="overallTrend.trendData" stripe style="margin-top: 16px" max-height="300">
          <el-table-column prop="date" label="日期" width="120" />
          <el-table-column prop="totalTests" label="测试数" width="100" />
          <el-table-column prop="passCount" label="通过" width="80" />
          <el-table-column prop="failCount" label="失败" width="80" />
          <el-table-column prop="passRate" label="通过率">
            <template #default="{ row }">
              <el-progress
                :percentage="row.passRate"
                :status="row.passRate >= 80 ? 'success' : row.passRate >= 60 ? 'warning' : 'exception'"
                :stroke-width="12"
              />
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>

    <!-- 任务执行趋势 -->
    <el-card shadow="never" style="margin-bottom: 16px">
      <template #header>
        <span>任务执行趋势</span>
      </template>
      <div v-if="taskTrend" class="task-trend">
        <el-row :gutter="20">
          <el-col :span="6">
            <div class="stat-card">
              <div class="stat-value">{{ taskTrend.totalTasks || 0 }}</div>
              <div class="stat-label">总任务数</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="stat-card">
              <div class="stat-value">{{ (taskTrend.avgPerDay || 0).toFixed(1) }}</div>
              <div class="stat-label">日均执行</div>
            </div>
          </el-col>
        </el-row>

        <el-table :data="taskTrend.trendData" stripe style="margin-top: 16px" max-height="300">
          <el-table-column prop="date" label="日期" width="120" />
          <el-table-column prop="count" label="执行次数" width="120" />
        </el-table>
      </div>
    </el-card>

    <!-- 异常检测 -->
    <el-card shadow="never" v-if="selectedScriptId">
      <template #header>
        <span>异常检测</span>
      </template>
      <el-table v-loading="anomalyLoading" :data="anomalies" stripe>
        <el-table-column prop="metricName" label="指标" width="150" />
        <el-table-column prop="date" label="日期" width="120" />
        <el-table-column prop="value" label="值" width="100" />
        <el-table-column prop="type" label="类型" width="80">
          <template #default="{ row }">
            <el-tag :type="row.type === 'spike' ? 'danger' : 'warning'" size="small">
              {{ row.type === 'spike' ? '突增' : '骤降' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="severity" label="严重程度" width="100">
          <template #default="{ row }">
            <el-tag :type="row.severity === 'high' ? 'danger' : row.severity === 'medium' ? 'warning' : 'info'" size="small">
              {{ row.severity === 'high' ? '高' : row.severity === 'medium' ? '中' : '低' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" />
      </el-table>
      <el-empty v-if="!anomalyLoading && anomalies.length === 0" description="暂无异常" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

const loading = ref(false)
const anomalyLoading = ref(false)
const scripts = ref<any[]>([])
const selectedScriptId = ref<number | null>(null)
const days = ref(30)
const overallTrend = ref<any>(null)
const taskTrend = ref<any>(null)
const anomalies = ref<any[]>([])

onMounted(() => {
  fetchScripts()
  fetchData()
})

async function fetchScripts() {
  try {
    const res = await request.get('/scripts', { params: { size: 100 } })
    scripts.value = res.data.items || []
  } catch (error) {
    console.error('获取脚本列表失败:', error)
  }
}

async function fetchData() {
  loading.value = true
  try {
    // 整体趋势
    const overallRes = await request.get('/trend/overall', { params: { days: days.value } })
    overallTrend.value = overallRes.data

    // 任务执行趋势
    const taskRes = await request.get('/trend/task-execution', {
      params: { scriptId: selectedScriptId.value, days: days.value }
    })
    taskTrend.value = taskRes.data

    // 异常检测
    if (selectedScriptId.value) {
      fetchAnomalies()
    }
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '获取数据失败')
  } finally {
    loading.value = false
  }
}

async function fetchAnomalies() {
  if (!selectedScriptId.value) {
    anomalies.value = []
    return
  }

  anomalyLoading.value = true
  try {
    const res = await request.get('/trend/anomalies', {
      params: { scriptId: selectedScriptId.value, days: days.value }
    })
    anomalies.value = res.data || []
  } catch (error) {
    console.error('获取异常检测失败:', error)
    anomalies.value = []
  } finally {
    anomalyLoading.value = false
  }
}

watch(selectedScriptId, () => {
  if (selectedScriptId.value) {
    fetchAnomalies()
  } else {
    anomalies.value = []
  }
})
</script>

<style scoped>
.stat-card {
  text-align: center;
  padding: 20px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
}

.stat-value {
  font-size: 28px;
  font-weight: bold;
  color: var(--el-color-primary);
}

.stat-value.pass {
  color: var(--el-color-success);
}

.stat-label {
  color: var(--el-text-color-secondary);
  margin-top: 8px;
  font-size: 14px;
}
</style>

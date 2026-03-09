<template>
  <div class="page-card">
    <div class="page-header">
      <h3 class="page-title">趋势分析</h3>
    </div>

    <el-form :inline="true" class="filter-form">
      <el-form-item label="时间范围">
        <el-date-picker v-model="dateRange" type="daterange" start-placeholder="开始日期" end-placeholder="结束日期" />
      </el-form-item>
      <el-form-item label="指标">
        <el-select v-model="selectedMetric" placeholder="选择指标">
          <el-option label="CPU使用率" value="cpu_usage" />
          <el-option label="内存使用率" value="memory_usage" />
          <el-option label="磁盘IO" value="disk_io" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="fetchData">查询</el-button>
      </el-form-item>
    </el-form>

    <div ref="chartRef" class="chart-container"></div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import * as echarts from 'echarts'

const dateRange = ref([])
const selectedMetric = ref('cpu_usage')
const chartRef = ref<HTMLElement>()

let chart: echarts.ECharts | null = null

function fetchData() {
  if (!chart) return
  
  chart.setOption({
    xAxis: {
      data: ['03-01', '03-02', '03-03', '03-04', '03-05', '03-06', '03-07', '03-08', '03-09'],
    },
    series: [
      {
        name: 'CPU使用率',
        type: 'line',
        smooth: true,
        data: [85, 92, 88, 95, 90, 87, 93, 89, 96],
      },
    ],
  })
}

onMounted(() => {
  if (chartRef.value) {
    chart = echarts.init(chartRef.value)
    chart.setOption({
      tooltip: { trigger: 'axis' },
      legend: { data: ['CPU使用率'] },
      grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
      xAxis: { type: 'category', boundaryGap: false, data: [] },
      yAxis: { type: 'value', min: 0, max: 100 },
      series: [{ name: 'CPU使用率', type: 'line', smooth: true, data: [] }],
    })
    fetchData()
  }
})
</script>

<style lang="scss" scoped>
.filter-form {
  margin-bottom: 20px;
}

.chart-container {
  width: 100%;
  height: 400px;
}
</style>

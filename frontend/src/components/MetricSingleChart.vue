<template>
  <div class="metric-single-chart" v-loading="loading">
    <!-- 图表容器 -->
    <div ref="chartRef" class="chart-container"></div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount, watch } from 'vue'
import * as echarts from 'echarts'
import axios from 'axios'
import { ElMessage } from 'element-plus'

const props = defineProps({
  taskId: {
    type: Number,
    required: true
  },
  metricType: {
    type: String,
    required: true,
    validator: (value) => ['cpu', 'memory', 'disk', 'network'].includes(value)
  }
})

const loading = ref(false)
const chartRef = ref(null)
let chartInstance = null

// 图表数据
const chartData = reactive({
  timestamps: [],
  series: []
})

// 颜色配置
const colors = {
  cpu: '#5470c6',
  memory: '#91cc75',
  disk: ['#fac858', '#ee6666'],
  network: ['#73c0de', '#3ba272']
}

// 每个类型只显示的核心指标
const coreMetrics = {
  cpu: ['usage_rate'],
  memory: ['usage_rate'],
  disk: ['read_sectors', 'write_sectors'],
  network: ['in_bytes', 'out_bytes']
}

// 初始化图表
onMounted(() => {
  initChart()
  loadData()
  
  // 窗口大小变化时重绘
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  if (chartInstance) {
    chartInstance.dispose()
  }
  window.removeEventListener('resize', handleResize)
})

// 监听类型变化
watch(() => props.metricType, () => {
  loadData()
})

// 初始化图表
function initChart() {
  if (!chartRef.value) return
  
  chartInstance = echarts.init(chartRef.value)
  
  const option = {
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'cross'
      }
    },
    grid: {
      left: '8%',
      right: '5%',
      top: '5%',
      bottom: '15%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: [],
      axisLabel: {
        rotate: 0,
        interval: 'auto',
        fontSize: 10
      }
    },
    yAxis: {
      type: 'value',
      scale: true,
      axisLabel: {
        fontSize: 10,
        formatter: '{value}'
      }
    },
    series: []
  }
  
  chartInstance.setOption(option)
}

// 加载数据
async function loadData() {
  if (!props.taskId) return
  
  loading.value = true
  
  try {
    const params = {
      metricType: props.metricType
    }
    
    const response = await axios.get(`/api/v1/metrics/tasks/${props.taskId}/timeseries`, { params })
    
    if (response.data.code === 0) {
      chartData.timestamps = response.data.data.timestamps
      // 只保留核心指标
      chartData.series = response.data.data.series.filter(s => {
        const allowedMetrics = coreMetrics[props.metricType] || []
        return allowedMetrics.includes(s.name)
      })
      
      updateChart()
    } else {
      // 无数据时不显示错误，显示空状态
      if (response.data.code !== 404) {
        ElMessage.error(response.data.message || '加载数据失败')
      }
    }
  } catch (error) {
    console.error('加载时序数据失败:', error)
    // 静默失败，不显示错误提示
  } finally {
    loading.value = false
  }
}

// 更新图表
function updateChart() {
  if (!chartInstance) return
  
  const series = chartData.series
  
  // 根据类型获取颜色
  const typeColors = colors[props.metricType]
  const colorArray = Array.isArray(typeColors) ? typeColors : [typeColors]
  
  const option = {
    xAxis: {
      data: chartData.timestamps.map(ts => formatTimestamp(ts))
    },
    series: series.map((s, index) => ({
      name: getSeriesLabel(s),
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 4,
      data: s.data,
      lineStyle: {
        width: 2
      },
      itemStyle: {
        color: colorArray[index % colorArray.length]
      },
      areaStyle: {
        opacity: 0.15,
        color: colorArray[index % colorArray.length]
      }
    }))
  }
  
  chartInstance.setOption(option)
}

// 处理窗口大小变化
function handleResize() {
  if (chartInstance) {
    chartInstance.resize()
  }
}

// 格式化时间戳
function formatTimestamp(ts) {
  if (!ts) return ''
  const date = new Date(ts)
  return date.toLocaleString('zh-CN', {
    hour12: false,
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

// 获取系列标签
function getSeriesLabel(series) {
  const nameMap = {
    usage_rate: '使用率',
    used_mb: '已用',
    read_sectors: '读取',
    write_sectors: '写入',
    in_bytes: '入站',
    out_bytes: '出站',
    load_1m: '1 分钟负载',
    load_5m: '5 分钟负载',
    load_15m: '15 分钟负载'
  }
  
  return nameMap[series.name] || series.name
}
</script>

<style scoped>
.metric-single-chart {
  width: 100%;
}

.chart-container {
  width: 100%;
  height: 220px;
  background: #fff;
}
</style>

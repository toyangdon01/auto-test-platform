<template>
  <div class="metric-timeseries-chart" v-loading="loading">
    <!-- 图表控制栏 -->
    <div class="chart-controls">
      <el-radio-group v-model="selectedType" size="small" @change="loadData">
        <el-radio-button label="cpu">CPU</el-radio-button>
        <el-radio-button label="memory">内存</el-radio-button>
        <el-radio-button label="disk">磁盘</el-radio-button>
        <el-radio-button label="network">网络</el-radio-button>
      </el-radio-group>
      
      <el-button size="small" @click="refreshData" :loading="loading">
        <el-icon><Refresh /></el-icon>
        刷新
      </el-button>
      
      <el-button size="small" @click="exportData">
        <el-icon><Download /></el-icon>
        导出数据
      </el-button>
    </div>
    
    <!-- 图表容器 -->
    <div ref="chartRef" class="chart-container"></div>
    
    <!-- 数据表格 -->
    <el-table :data="tableData" stripe size="small" max-height="400" style="margin-top: 20px">
      <el-table-column prop="timestamp" label="时间" width="180" />
      <el-table-column 
        v-for="series in chartSeries" 
        :key="series.name"
        :label="getSeriesLabel(series)"
        min-width="100"
      >
        <template #default="{ row }">
          <span :class="getValueClass(row[series.name])">
            {{ formatValue(row[series.name], series.unit) }}
          </span>
        </template>
      </el-table-column>
    </el-table>
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
  }
})

const loading = ref(false)
const chartRef = ref(null)
// 默认选择 CPU
const selectedType = ref('cpu')
let chartInstance = null

// 图表数据
const chartData = reactive({
  timestamps: [],
  series: []
})

// 表格数据
const tableData = ref([])
const chartSeries = ref([])

// 颜色配置
const colors = [
  '#5470c6', '#91cc75', '#fac858', '#ee6666', '#73c0de',
  '#3ba272', '#fc8452', '#9a60b4', '#ea7ccc'
]

// 每个类型只显示的核心指标
const coreMetrics = {
  cpu: ['usage_rate'],      // CPU 使用率
  memory: ['usage_rate'],    // 内存使用率
  disk: ['read_sectors', 'write_sectors'],  // 读写
  network: ['in_bytes', 'out_bytes']        // 出入流量
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
watch(selectedType, () => {
  loadData()
})

// 初始化图表
function initChart() {
  if (!chartRef.value) return
  
  chartInstance = echarts.init(chartRef.value)
  
  const option = {
    title: {
      text: '指标采集趋势',
      left: 'center'
    },
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'cross'
      }
    },
    legend: {
      type: 'scroll',
      bottom: 10,
      data: []
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '15%',
      containLabel: true
    },
    toolbox: {
      feature: {
        saveAsImage: {
          title: '保存图片',
          pixelRatio: 2
        },
        dataZoom: {
          title: {
            zoom: '区域缩放',
            back: '区域还原'
          }
        },
        restore: {
          title: '还原'
        }
      }
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: [],
      axisLabel: {
        rotate: 45,
        interval: 'auto'
      }
    },
    yAxis: {
      type: 'value',
      scale: true,
      axisLabel: {
        formatter: '{value}'
      }
    },
    dataZoom: [
      {
        type: 'inside',
        start: 0,
        end: 100
      },
      {
        start: 0,
        end: 100,
        handleSize: '80%',
        bottom: 40
      }
    ],
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
      metricType: selectedType.value
    }
    
    const response = await axios.get(`/api/v1/metrics/tasks/${props.taskId}/timeseries`, { params })
    
    if (response.data.code === 0) {
      chartData.timestamps = response.data.data.timestamps
      // 只保留核心指标
      chartData.series = response.data.data.series.filter(s => {
        const allowedMetrics = coreMetrics[selectedType.value] || []
        return allowedMetrics.includes(s.name)
      })
      
      updateChart()
      updateTable()
    } else {
      ElMessage.error(response.data.message || '加载数据失败')
    }
  } catch (error) {
    console.error('加载时序数据失败:', error)
    ElMessage.error('加载数据失败：' + error.message)
  } finally {
    loading.value = false
  }
}

// 更新图表
function updateChart() {
  if (!chartInstance) return
  
  const series = chartData.series
  
  chartSeries.value = series
  
  const option = {
    xAxis: {
      data: chartData.timestamps.map(ts => formatTimestamp(ts))
    },
    legend: {
      data: series.map(s => getSeriesLabel(s))
    },
    series: series.map((s, index) => ({
      name: getSeriesLabel(s),
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 6,
      data: s.data,
      lineStyle: {
        width: 2
      },
      itemStyle: {
        color: colors[index % colors.length]
      },
      areaStyle: {
        opacity: 0.1,
        color: colors[index % colors.length]
      }
    }))
  }
  
  chartInstance.setOption(option)
}

// 更新表格
function updateTable() {
  const series = chartData.series
  
  // 转换为表格数据
  tableData.value = chartData.timestamps.map((ts, index) => {
    const row = { timestamp: formatTimestamp(ts) }
    
    series.forEach(s => {
      row[s.name] = s.data[index]
      row[`${s.name}_unit`] = s.unit
    })
    
    return row
  })
}

// 刷新数据
function refreshData() {
  loadData()
}

// 导出数据
function exportData() {
  if (tableData.value.length === 0) {
    ElMessage.warning('没有可导出的数据')
    return
  }
  
  // 生成 CSV
  const series = chartSeries.value
  const headers = ['时间戳', ...series.map(s => getSeriesLabel(s))]
  const rows = chartData.timestamps.map((ts, index) => {
    const values = series.map(s => s.data[index] ?? '')
    return [ts, ...values].join(',')
  })
  
  const csv = [headers.join(','), ...rows].join('\n')
  
  // 下载
  const blob = new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8;' })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = `task_${props.taskId}_metrics_${selectedType.value}.csv`
  link.click()
  
  ElMessage.success('导出成功')
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
    minute: '2-digit',
    second: '2-digit'
  })
}

// 获取系列标签
function getSeriesLabel(series) {
  const typeMap = {
    cpu: 'CPU',
    memory: '内存',
    disk: '磁盘',
    network: '网络'
  }
  
  const nameMap = {
    usage_rate: '使用率',
    used_mb: '已用内存',
    read_sectors: '读扇区',
    write_sectors: '写扇区',
    in_bytes: '入流量',
    out_bytes: '出流量',
    load_1m: '1 分钟负载',
    load_5m: '5 分钟负载',
    load_15m: '15 分钟负载'
  }
  
  const typeText = typeMap[series.type] || series.type
  const nameText = nameMap[series.name] || series.name
  return `${typeText} - ${nameText}`
}

// 格式化值
function formatValue(value, unit) {
  if (value === null || value === undefined) return '-'
  
  const numValue = typeof value === 'number' ? value : parseFloat(value)
  if (isNaN(numValue)) return '-'
  
  // 根据数值大小格式化
  if (Math.abs(numValue) >= 1000000) {
    return (numValue / 1000000).toFixed(2) + 'M' + (unit || '')
  } else if (Math.abs(numValue) >= 1000) {
    return (numValue / 1000).toFixed(2) + 'K' + (unit || '')
  } else {
    return numValue.toFixed(2) + (unit || '')
  }
}

// 获取值样式类
function getValueClass(value) {
  if (value === null || value === undefined) return ''
  
  const numValue = typeof value === 'number' ? value : parseFloat(value)
  if (isNaN(numValue)) return ''
  
  // 根据数值大小返回不同颜色
  if (numValue > 90) return 'metric-value-high'
  if (numValue < 10) return 'metric-value-low'
  return ''
}
</script>

<style scoped>
.metric-timeseries-chart {
  width: 100%;
}

.chart-controls {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding: 10px;
  background: #f5f7fa;
  border-radius: 4px;
}

.chart-container {
  width: 100%;
  height: 500px;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
}

.metric-value-high {
  color: #f56c6c;
  font-weight: bold;
}

.metric-value-low {
  color: #67c23a;
  font-weight: bold;
}
</style>

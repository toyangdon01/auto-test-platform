<template>
  <el-card class="metric-collect-config">
    <template #header>
      <div class="card-header">
        <span>📊 指标采集配置</span>
        <el-switch
          v-model="config.enabled"
          active-text="启用"
          inactive-text="禁用"
          @change="onEnabledChange"
        />
      </div>
    </template>

    <el-form v-if="config.enabled" :model="config" label-width="120px" size="small">
      <!-- 采集频率 -->
      <el-form-item label="采集频率">
        <el-select v-model="config.frequency" placeholder="请选择采集频率" style="width: 200px">
          <el-option label="1 秒" value="1s" />
          <el-option label="5 秒" value="5s" />
          <el-option label="10 秒" value="10s" />
          <el-option label="30 秒" value="30s" />
          <el-option label="1 分钟" value="1min" />
        </el-select>
        <el-tag type="info" style="margin-left: 10px">
          {{ getFrequencyHint(config.frequency) }}
        </el-tag>
      </el-form-item>

      <!-- 指标类别选择 -->
      <el-form-item label="采集指标">
        <el-checkbox-group v-model="enabledTypes">
          <el-checkbox label="cpu">CPU</el-checkbox>
          <el-checkbox label="memory">内存</el-checkbox>
          <el-checkbox label="disk">磁盘</el-checkbox>
          <el-checkbox label="network">网络</el-checkbox>
        </el-checkbox-group>
      </el-form-item>

      <!-- CPU 配置 -->
      <el-divider v-if="enabledTypes.includes('cpu')" content-position="left">CPU 指标</el-divider>
      <el-form-item v-if="enabledTypes.includes('cpu')" label="CPU 指标">
        <el-checkbox-group v-model="config.cpu.metrics">
          <el-checkbox label="usage_rate">使用率</el-checkbox>
          <el-checkbox label="load_avg">负载</el-checkbox>
          <el-checkbox label="context_switch">上下文切换</el-checkbox>
        </el-checkbox-group>
      </el-form-item>

      <!-- 内存配置 -->
      <el-divider v-if="enabledTypes.includes('memory')" content-position="left">内存指标</el-divider>
      <el-form-item v-if="enabledTypes.includes('memory')" label="内存指标">
        <el-checkbox-group v-model="config.memory.metrics">
          <el-checkbox label="usage_rate">使用率</el-checkbox>
          <el-checkbox label="used_mb">已用内存</el-checkbox>
          <el-checkbox label="cache_mb">缓存</el-checkbox>
          <el-checkbox label="free_mb">空闲内存</el-checkbox>
        </el-checkbox-group>
      </el-form-item>

      <!-- 磁盘配置 -->
      <el-divider v-if="enabledTypes.includes('disk')" content-position="left">磁盘指标</el-divider>
      <el-form-item v-if="enabledTypes.includes('disk')" label="磁盘设备">
        <el-select 
          v-model="config.disk.devices" 
          multiple 
          placeholder="选择设备（留空采集全部）" 
          style="width: 100%"
          :loading="loadingDisks"
        >
          <el-option
            v-for="disk in availableDisks"
            :key="disk.name"
            :label="disk.label"
            :value="disk.name"
          />
        </el-select>
        <el-tag v-if="serverIds && serverIds.length > 0" type="success" style="margin-left: 10px">
          已加载 {{ availableDisks.length }} 个设备
        </el-tag>
        <el-tag v-else type="info" style="margin-left: 10px">
          选择服务器后自动加载
        </el-tag>
      </el-form-item>
      <el-form-item v-if="enabledTypes.includes('disk')" label="磁盘指标">
        <el-checkbox-group v-model="config.disk.metrics">
          <el-checkbox label="read_sectors">读扇区</el-checkbox>
          <el-checkbox label="write_sectors">写扇区</el-checkbox>
          <el-checkbox label="usage_rate">使用率</el-checkbox>
        </el-checkbox-group>
      </el-form-item>

      <!-- 网络配置 -->
      <el-divider v-if="enabledTypes.includes('network')" content-position="left">网络指标</el-divider>
      <el-form-item v-if="enabledTypes.includes('network')" label="网卡接口">
        <el-select 
          v-model="config.network.interfaces" 
          multiple 
          placeholder="选择网卡（留空采集全部）" 
          style="width: 100%"
          :loading="loadingInterfaces"
        >
          <el-option
            v-for="iface in availableInterfaces"
            :key="iface.name"
            :label="iface.label"
            :value="iface.name"
          />
        </el-select>
        <el-tag v-if="serverIds && serverIds.length > 0" type="success" style="margin-left: 10px">
          已加载 {{ availableInterfaces.length }} 个网卡
        </el-tag>
        <el-tag v-else type="info" style="margin-left: 10px">
          选择服务器后自动加载
        </el-tag>
      </el-form-item>
      <el-form-item v-if="enabledTypes.includes('network')" label="网络指标">
        <el-checkbox-group v-model="config.network.metrics">
          <el-checkbox label="in_bytes">入流量</el-checkbox>
          <el-checkbox label="out_bytes">出流量</el-checkbox>
        </el-checkbox-group>
      </el-form-item>

      <!-- 提示信息 -->
      <el-alert
        type="info"
        :closable="false"
        show-icon
        style="margin-top: 15px"
      >
        <template #title>
          高频采集（≤5s）会产生较多 SSH 连接开销，建议生产环境使用 10s 以上频率
        </template>
      </el-alert>
    </el-form>

    <!-- 禁用提示 -->
    <el-empty
      v-else
      description="指标采集已禁用，测试报告中将不包含性能指标分析"
      :image-size="80"
    />
  </el-card>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import axios from 'axios'

const props = defineProps({
  modelValue: {
    type: Object,
    default: () => ({})
  },
  // 接收服务器 ID 列表，用于动态加载设备和网卡
  serverIds: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['update:modelValue'])

// 加载状态
const loadingDisks = ref(false)
const loadingInterfaces = ref(false)

// 可用设备和网卡
const availableDisks = ref([])
const availableInterfaces = ref([])

// 默认配置
const defaultConfig = {
  enabled: true,
  frequency: '5s',
  cpu: {
    enabled: true,
    metrics: ['usage_rate', 'load_avg']
  },
  memory: {
    enabled: true,
    metrics: ['usage_rate', 'used_mb']
  },
  disk: {
    enabled: true,
    metrics: ['read_sectors', 'write_sectors', 'usage_rate'],
    devices: []
  },
  network: {
    enabled: true,
    metrics: ['in_bytes', 'out_bytes'],
    interfaces: []
  },
  customMetrics: []
}

// 配置对象
const config = ref({ ...defaultConfig, ...props.modelValue })

// 启用的指标类型
const enabledTypes = computed({
  get: () => {
    const types = []
    if (config.value.cpu?.enabled) types.push('cpu')
    if (config.value.memory?.enabled) types.push('memory')
    if (config.value.disk?.enabled) types.push('disk')
    if (config.value.network?.enabled) types.push('network')
    return types
  },
  set: (types) => {
    config.value.cpu = {
      enabled: types.includes('cpu'),
      metrics: config.value.cpu?.metrics || ['usage_rate', 'load_avg']
    }
    config.value.memory = {
      enabled: types.includes('memory'),
      metrics: config.value.memory?.metrics || ['usage_rate', 'used_mb']
    }
    config.value.disk = {
      enabled: types.includes('disk'),
      metrics: config.value.disk?.metrics || ['read_sectors', 'write_sectors'],
      devices: config.value.disk?.devices || []
    }
    config.value.network = {
      enabled: types.includes('network'),
      metrics: config.value.network?.metrics || ['in_bytes', 'out_bytes'],
      interfaces: config.value.network?.interfaces || []
    }
  }
})

// 监听配置变化，同步到父组件
watch(config, (newVal) => {
  emit('update:modelValue', newVal)
}, { deep: true })

// 监听服务器 ID 变化，重新加载设备和网卡
watch(() => props.serverIds, (newIds) => {
  if (newIds && newIds.length > 0) {
    loadServerResources(newIds)
  } else {
    // 没有服务器时清空
    availableDisks.value = []
    availableInterfaces.value = []
  }
}, { immediate: true })

// 启用/禁用切换
const onEnabledChange = (val) => {
  if (!val) {
    emit('update:modelValue', { enabled: false })
  } else {
    emit('update:modelValue', defaultConfig)
  }
}

// 频率提示
const getFrequencyHint = (freq) => {
  const hints = {
    '1s': '每秒采集，数据最精确，但 SSH 开销最大',
    '5s': '每 5 秒采集，推荐用于短期测试',
    '10s': '每 10 秒采集，推荐用于生产环境',
    '30s': '每 30 秒采集，适合长期监控',
    '1min': '每分钟采集，SSH 开销最小'
  }
  return hints[freq] || ''
}

// 加载服务器资源
async function loadServerResources(serverIds) {
  if (!serverIds || serverIds.length === 0) return

  loadingDisks.value = true
  loadingInterfaces.value = true

  try {
    const response = await axios.post('/api/v1/servers/discovery/resources', {
      serverIds: serverIds
    })

    if (response.data.code === 0) {
      availableDisks.value = response.data.data.disks || []
      availableInterfaces.value = response.data.data.interfaces || []
    }
  } catch (error) {
    console.error('加载服务器资源失败:', error)
    // 失败时使用默认值
    availableDisks.value = []
    availableInterfaces.value = []
  } finally {
    loadingDisks.value = false
    loadingInterfaces.value = false
  }
}

// 初始化
onMounted(() => {
  if (props.serverIds && props.serverIds.length > 0) {
    loadServerResources(props.serverIds)
  }
})
</script>

<style scoped>
.metric-collect-config {
  margin-top: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

:deep(.el-divider__text) {
  font-weight: 500;
}
</style>

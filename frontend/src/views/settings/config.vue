<template>
  <div class="page-card" v-loading="loading">
    <div class="page-header">
      <h3 class="page-title">系统配置</h3>
    </div>

    <el-form :model="formData" label-width="140px" style="max-width: 700px">
      <el-divider content-position="left">存储配置</el-divider>
      
      <el-form-item label="脚本存储路径">
        <el-input v-model="formData.scripts_path" placeholder="脚本文件存储路径">
          <template #prepend>
            <el-icon><Folder /></el-icon>
          </template>
        </el-input>
      </el-form-item>
      
      <el-form-item label="报告存储路径">
        <el-input v-model="formData.reports_path" placeholder="报告文件存储路径" />
      </el-form-item>
      
      <el-form-item label="临时文件路径">
        <el-input v-model="formData.temp_path" placeholder="临时文件存储路径" />
      </el-form-item>

      <el-divider content-position="left">SSH配置</el-divider>
      
      <el-form-item label="连接超时">
        <el-input-number v-model="formData.ssh_timeout" :min="1000" :max="60000" :step="1000" />
        <span class="unit">毫秒</span>
        <el-text type="info" size="small" class="hint">SSH连接超时时间</el-text>
      </el-form-item>
      
      <el-form-item label="执行超时">
        <el-input-number v-model="formData.exec_timeout" :min="60" :max="86400" :step="60" />
        <span class="unit">秒</span>
        <el-text type="info" size="small" class="hint">脚本执行最大超时时间</el-text>
      </el-form-item>

      <el-divider content-position="left">任务配置</el-divider>
      
      <el-form-item label="最大并行任务">
        <el-input-number v-model="formData.max_parallel_tasks" :min="1" :max="100" />
        <el-text type="info" size="small" class="hint">同时执行的最大任务数</el-text>
      </el-form-item>
      
      <el-form-item label="指标采集间隔">
        <el-input-number v-model="formData.collect_interval" :min="1" :max="60" />
        <span class="unit">秒</span>
        <el-text type="info" size="small" class="hint">性能指标采集间隔</el-text>
      </el-form-item>

      <el-divider content-position="left">日志配置</el-divider>
      
      <el-form-item label="日志级别">
        <el-select v-model="formData.log_level" style="width: 150px">
          <el-option label="DEBUG" value="DEBUG" />
          <el-option label="INFO" value="INFO" />
          <el-option label="WARN" value="WARN" />
          <el-option label="ERROR" value="ERROR" />
        </el-select>
      </el-form-item>
      
      <el-form-item label="日志保留天数">
        <el-input-number v-model="formData.log_retention_days" :min="1" :max="365" />
        <span class="unit">天</span>
      </el-form-item>

      <el-form-item>
        <el-button type="primary" @click="handleSave" :loading="saving">
          <el-icon><Check /></el-icon>
          保存配置
        </el-button>
        <el-button @click="handleReset">
          <el-icon><Refresh /></el-icon>
          重置
        </el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Folder, Check, Refresh } from '@element-plus/icons-vue'
import request from '@/utils/request'

// 默认配置
const defaultConfig = {
  scripts_path: 'C:/data/auto-test/scripts',
  reports_path: 'C:/data/auto-test/reports',
  temp_path: 'C:/data/auto-test/temp',
  ssh_timeout: 30000,
  exec_timeout: 3600,
  max_parallel_tasks: 10,
  collect_interval: 5,
  log_level: 'INFO',
  log_retention_days: 30
}

const formData = reactive({ ...defaultConfig })
const loading = ref(false)
const saving = ref(false)

const fetchConfig = async () => {
  loading.value = true
  try {
    const res = await request.get<Record<string, string>>('/config')
    if (res.code === 0 && res.data) {
      // 合并配置到表单
      for (const [key, value] of Object.entries(res.data)) {
        if (key in formData) {
          // 数值类型转换
          if (typeof defaultConfig[key as keyof typeof defaultConfig] === 'number') {
            (formData as any)[key] = parseInt(value, 10) || defaultConfig[key as keyof typeof defaultConfig]
          } else {
            (formData as any)[key] = value
          }
        }
      }
    }
  } catch (e) {
    console.error('获取配置失败', e)
  } finally {
    loading.value = false
  }
}

const handleSave = async () => {
  saving.value = true
  try {
    // 转换所有值为字符串
    const configMap: Record<string, string> = {}
    for (const [key, value] of Object.entries(formData)) {
      configMap[key] = String(value)
    }

    const res = await request.put('/config', configMap)
    if (res.code === 0) {
      ElMessage.success('配置已保存')
    } else {
      ElMessage.error(res.message || '保存失败')
    }
  } catch (e: any) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

const handleReset = () => {
  Object.assign(formData, defaultConfig)
  ElMessage.info('已重置为默认配置')
}

onMounted(() => {
  fetchConfig()
})
</script>

<style lang="scss" scoped>
.unit {
  margin-left: 8px;
  color: var(--el-text-color-secondary);
}

.hint {
  margin-left: 12px;
}

:deep(.el-input-number) {
  width: 150px;
}
</style>

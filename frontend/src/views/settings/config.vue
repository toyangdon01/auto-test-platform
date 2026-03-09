<template>
  <div class="page-card">
    <div class="page-header">
      <h3 class="page-title">系统配置</h3>
    </div>

    <el-form :model="formData" label-width="140px" style="max-width: 600px">
      <el-divider content-position="left">存储配置</el-divider>
      
      <el-form-item label="脚本存储路径">
        <el-input v-model="formData.scriptsPath" />
      </el-form-item>
      <el-form-item label="报告存储路径">
        <el-input v-model="formData.reportsPath" />
      </el-form-item>
      <el-form-item label="临时文件路径">
        <el-input v-model="formData.tempPath" />
      </el-form-item>

      <el-divider content-position="left">SSH配置</el-divider>
      
      <el-form-item label="连接超时">
        <el-input-number v-model="formData.sshTimeout" :min="1000" :max="60000" :step="1000" />
        <span class="unit">毫秒</span>
      </el-form-item>
      <el-form-item label="执行超时">
        <el-input-number v-model="formData.execTimeout" :min="60" :max="36000" />
        <span class="unit">秒</span>
      </el-form-item>

      <el-divider content-position="left">任务配置</el-divider>
      
      <el-form-item label="最大并行任务">
        <el-input-number v-model="formData.maxParallel" :min="1" :max="100" />
      </el-form-item>
      <el-form-item label="默认采集间隔">
        <el-input-number v-model="formData.collectInterval" :min="1" :max="60" />
        <span class="unit">秒</span>
      </el-form-item>

      <el-form-item>
        <el-button type="primary" @click="handleSave">保存配置</el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { reactive } from 'vue'
import { ElMessage } from 'element-plus'

const formData = reactive({
  scriptsPath: '/data/auto-test/scripts',
  reportsPath: '/data/auto-test/reports',
  tempPath: '/data/auto-test/temp',
  sshTimeout: 30000,
  execTimeout: 3600,
  maxParallel: 10,
  collectInterval: 5,
})

function handleSave() {
  ElMessage.success('配置已保存')
}
</script>

<style lang="scss" scoped>
.unit {
  margin-left: 8px;
  color: var(--text-secondary);
}
</style>

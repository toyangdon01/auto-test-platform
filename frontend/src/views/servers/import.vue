<template>
  <div class="page-card">
    <div class="page-header">
      <h3 class="page-title">批量导入服务器</h3>
    </div>

    <el-alert type="info" :closable="false" show-icon class="mb-20">
      <template #title>
        支持 CSV、Excel 格式文件，首次导入请先下载模板
      </template>
    </el-alert>

    <el-card shadow="never">
      <div class="import-steps">
        <el-steps :active="currentStep" align-center>
          <el-step title="下载模板" />
          <el-step title="上传文件" />
          <el-step title="确认导入" />
        </el-steps>
      </div>

      <div v-if="currentStep === 0" class="step-content">
        <el-button type="primary" @click="downloadTemplate">
          <el-icon><Download /></el-icon>
          下载导入模板
        </el-button>
      </div>

      <div v-if="currentStep === 1" class="step-content">
        <el-upload
          ref="uploadRef"
          drag
          action="#"
          :auto-upload="false"
          :limit="1"
          accept=".csv,.xlsx,.xls"
          @change="handleFileChange"
        >
          <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
          <div class="el-upload__text">
            将文件拖到此处，或 <em>点击上传</em>
          </div>
          <template #tip>
            <div class="el-upload__tip">支持 CSV、Excel 格式，单次最多导入 100 条</div>
          </template>
        </el-upload>
      </div>

      <div v-if="currentStep === 2" class="step-content">
        <el-table :data="previewData" stripe max-height="400">
          <el-table-column prop="name" label="名称" width="150" />
          <el-table-column prop="host" label="地址" width="150" />
          <el-table-column prop="port" label="端口" width="80" />
          <el-table-column prop="username" label="用户名" width="120" />
          <el-table-column prop="authType" label="认证方式" width="100" />
          <el-table-column prop="status" label="校验状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.valid ? 'success' : 'danger'">
                {{ row.valid ? '通过' : '错误' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="step-actions">
        <el-button v-if="currentStep > 0" @click="currentStep--">上一步</el-button>
        <el-button v-if="currentStep < 2" type="primary" :disabled="!canNext" @click="currentStep++">
          下一步
        </el-button>
        <el-button v-if="currentStep === 2" type="primary" @click="handleImport">
          确认导入
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, UploadFilled } from '@element-plus/icons-vue'

const currentStep = ref(0)
const canNext = ref(false)
const previewData = ref<any[]>([])
const uploadRef = ref()

function downloadTemplate() {
  // 模拟下载模板
  const link = document.createElement('a')
  link.href = 'data:text/csv;charset=utf-8,name,host,port,username,authType,authSecret,groupId,tags'
  link.download = 'server_import_template.csv'
  link.click()
  ElMessage.success('模板下载成功')
  canNext.value = true
}

function handleFileChange(file: any) {
  if (file) {
    canNext.value = true
    // 模拟预览数据
    previewData.value = [
      { name: 'Server-01', host: '192.168.1.10', port: 22, username: 'root', authType: 'password', valid: true },
      { name: 'Server-02', host: '192.168.1.11', port: 22, username: 'root', authType: 'password', valid: true },
    ]
  }
}

function handleImport() {
  ElMessage.success('导入成功')
  currentStep.value = 0
  canNext.value = false
}
</script>

<style lang="scss" scoped>
.import-steps {
  padding: 20px 0 40px;
}

.step-content {
  padding: 40px;
  text-align: center;
}

.step-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
  padding-top: 20px;
  border-top: 1px solid var(--border-lighter);
}
</style>

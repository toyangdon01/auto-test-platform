<template>
  <el-dialog
    v-model="visible"
    title="导出脚本"
    width="500px"
    :close-on-click-modal="false"
  >
    <el-form label-width="100px">
      <el-form-item label="已选择">
        {{ scriptIds.length }} 个脚本
      </el-form-item>
      
      <el-form-item label="包含资源文件">
        <el-switch v-model="options.includeResources" />
        <span class="form-tip">导出时包含关联的资源文件</span>
      </el-form-item>
    </el-form>
    
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" @click="handleExport" :loading="loading">
        导出
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import axios from 'axios'

const visible = ref(false)
const loading = ref(false)

const scriptIds = ref<number[]>([])
const options = reactive({
  includeResources: true,
  format: 'zip'
})

const open = (ids: number[]) => {
  scriptIds.value = ids
  visible.value = true
}

const handleExport = async () => {
  loading.value = true
  
  try {
    const response = await axios.post('/api/v1/scripts/import/export', {
      scriptIds: scriptIds.value,
      includeResources: options.includeResources,
      format: 'zip'
    }, {
      responseType: 'blob'
    })
    
    // 创建下载链接
    const blob = new Blob([response.data], { type: 'application/zip' })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    
    // 获取文件名
    const disposition = response.headers['content-disposition']
    let filename = 'scripts-package.zip'
    if (disposition) {
      const match = disposition.match(/filename\*=UTF-8''(.+)/)
      if (match) {
        filename = decodeURIComponent(match[1])
      }
    }
    
    link.download = filename
    link.click()
    window.URL.revokeObjectURL(url)
    
    ElMessage.success('导出成功')
    visible.value = false
  } catch (error: any) {
    ElMessage.error('导出失败：' + error.message)
  } finally {
    loading.value = false
  }
}

defineExpose({ open })
</script>

<style scoped>
.form-tip {
  margin-left: 10px;
  font-size: 12px;
  color: #909399;
}
</style>

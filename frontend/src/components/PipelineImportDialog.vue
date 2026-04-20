<template>
  <el-dialog
    v-model="visible"
    title="导入 YAML 编排"
    width="700px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <el-steps :active="activeStep" finish-status="success" align-center>
      <el-step title="选择 YAML" />
      <el-step title="预览" />
      <el-step title="导入" />
    </el-steps>
    
    <div class="step-content" style="margin-top: 20px;">
      <!-- 步骤 1：选择 YAML -->
      <div v-if="activeStep === 0">
        <el-form label-width="100px">
          <el-form-item label="导入方式">
            <el-radio-group v-model="importType">
              <el-radio value="file">上传文件</el-radio>
              <el-radio value="paste">粘贴内容</el-radio>
            </el-radio-group>
          </el-form-item>
          
          <!-- 文件上传 -->
          <el-form-item label="选择文件" v-if="importType === 'file'">
            <el-upload
              ref="uploadRef"
              drag
              :auto-upload="false"
              :limit="1"
              accept=".yaml,.yml"
              :on-change="handleFileChange"
            >
              <el-icon><upload /></el-icon>
              <div class="el-upload__text">
                拖拽文件到此处或 <em>点击选择</em>
              </div>
              <template #tip>
                <div class="el-upload__tip">
                  支持 .yaml / .yml 格式
                </div>
              </template>
            </el-upload>
          </el-form-item>
          
          <!-- 粘贴内容 -->
          <el-form-item label="YAML 内容" v-if="importType === 'paste'" label-width="120px">
            <el-input
              v-model="yamlContent"
              type="textarea"
              :rows="15"
              placeholder="pipeline:
  name: 部署流水线
  description: 自动化部署流程
  maxParallel: 5
  tasks:
    - name: 编译构建
      scriptId: 1
      timeout: 3600
      dependsOn: []"
            />
          </el-form-item>
        </el-form>
      </div>
      
      <!-- 步骤 2：预览 -->
      <div v-if="activeStep === 1">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="编排名称">
            {{ preview?.name || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="描述">
            {{ preview?.description || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="最大并行">
            <el-tag type="primary">{{ preview?.maxParallel || 5 }} 并行</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="任务数量">
            <el-tag type="success">{{ preview?.tasks?.length || 0 }} 个任务</el-tag>
          </el-descriptions-item>
        </el-descriptions>
        
        <div class="section-title" style="margin-top: 16px; margin-bottom: 8px;">
          <el-icon><List /></el-icon>
          任务列表
        </div>
        
        <el-table :data="previewTasks" style="margin-top: 10px" max-height="300" stripe>
          <el-table-column prop="name" label="任务名称" min-width="150" />
          <el-table-column prop="scriptId" label="脚本 ID" width="100" />
          <el-table-column label="依赖" min-width="150">
            <template #default="{ row }">
              <span v-if="row.dependsOn?.length">
                <el-tag v-for="dep in row.dependsOn" :key="dep" size="small" type="info" effect="plain" class="mr-4">
                  {{ dep }}
                </el-tag>
              </span>
              <span v-else class="text-muted">无</span>
            </template>
          </el-table-column>
          <el-table-column label="超时" width="100">
            <template #default="{ row }">
              {{ row.timeout ? `${row.timeout}s` : '-' }}
            </template>
          </el-table-column>
        </el-table>
        
        <!-- 警告信息 -->
        <el-alert
          v-if="previewWarnings?.length"
          type="warning"
          :closable="false"
          show-icon
          style="margin-top: 16px;"
        >
          <template #title>
            <div v-for="w in previewWarnings" :key="w" class="warning-text">
              ⚠️ {{ w }}
            </div>
          </template>
        </el-alert>
      </div>
      
      <!-- 步骤 3：导入结果 -->
      <div v-if="activeStep === 2">
        <el-result
          :icon="importSuccess ? 'success' : 'error'"
          :icon-color="importSuccess ? '#67c23a' : '#f56c6c'"
          :title="importSuccess ? '导入成功' : '导入失败'"
          :sub-title="importSuccess ? '编排已成功创建' : importError"
        >
          <template #extra>
            <div v-if="importSuccess" class="success-info">
              <el-descriptions :column="1" border>
                <el-descriptions-item label="编排名称">{{ pipelineResult?.name }}</el-descriptions-item>
                <el-descriptions-item label="任务数量">{{ pipelineResult?.taskCount }} 个</el-descriptions-item>
              </el-descriptions>
            </div>
          </template>
        </el-result>
      </div>
    </div>
    
    <template #footer>
      <el-button @click="visible = false" v-if="activeStep < 2">取消</el-button>
      <el-button 
        type="primary" 
        @click="nextStep" 
        v-if="activeStep === 0"
        :loading="loading"
        :disabled="!canProceed"
      >
        导入
      </el-button>
      <el-button @click="visible = false" v-if="activeStep === 2">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Upload, List } from '@element-plus/icons-vue'
import axios from 'axios'
import request from '@/utils/request'

const visible = ref(false)
const loading = ref(false)
const activeStep = ref(0)
const importType = ref<'file' | 'paste'>('file')
const selectedFile = ref<File | null>(null)
const yamlContent = ref('')

// 预览数据
const preview = ref<{
  name: string
  description: string
  maxParallel: number
  tasks: Array<{
    name: string
    scriptId: number
    dependsOn: string[]
    timeout: number
  }>
} | null>(null)

const previewError = ref<string>('')

// 导入结果
const importSuccess = ref(true)
const importError = ref<string>('')
const pipelineResult = ref<{
  name: string
  taskCount: number
} | null>(null)

const uploadRef = ref()

const previewTasks = computed(() => {
  return preview.value?.tasks || []
})

const canProceed = computed(() => {
  if (activeStep.value === 0) {
    if (importType.value === 'file') {
      return selectedFile.value !== null
    } else {
      return yamlContent.value && yamlContent.value.trim().length > 0
    }
  }
  return false
})

const open = () => {
  activeStep.value = 0
  preview.value = null
  previewError.value = ''
  importSuccess.value = true
  importError.value = ''
  pipelineResult.value = null
  selectedFile.value = null
  yamlContent.value = ''
  importType.value = 'file'
  visible.value = true
}

const handleFileChange = (file: any) => {
  selectedFile.value = file.raw
}

const nextStep = async () => {
  if (activeStep.value === 0) {
    await previewYaml()
  } else if (activeStep.value === 1) {
    await importYaml()
  }
}



const previewYaml = async () => {
  loading.value = true
  try {
    let yamlText = ''
    
    if (importType.value === 'file') {
      if (!selectedFile.value) return
      yamlText = await readFileAsText(selectedFile.value)
    } else {
      yamlText = yamlContent.value
    }
    
    // 调用后端 API 解析 YAML（预览时也会创建，后续改为单独的预览 API）
    const response = await request.post('/pipelines/import', yamlText, {
      headers: { 'Content-Type': 'text/plain' }
    })
    
    if (response.code === 0) {
      importSuccess.value = true
      pipelineResult.value = {
        name: response.data.name,
        taskCount: response.data.taskCount || 0
      }
      activeStep.value = 2  // 直接跳到结果页
    } else {
      previewError.value = response.message || 'YAML 解析失败'
      importSuccess.value = false
      importError.value = previewError.value
      activeStep.value = 2
    }
  } catch (error: any) {
    console.error('预览失败:', error)
    let errorMsg = 'YAML 解析失败'
    
    if (error.response?.data?.message) {
      errorMsg = error.response.data.message
    } else if (error.message) {
      errorMsg = error.message
    }
    
    // 解析错误信息中的行号
    if (errorMsg.includes('line')) {
      const lineMatch = errorMsg.match(/line (\d+)/)
      if (lineMatch) {
        errorMsg = `第 ${lineMatch[1]} 行：YAML 格式错误，请检查缩进和语法`
      }
    }
    
    previewError.value = errorMsg
    importSuccess.value = false
    importError.value = errorMsg
    activeStep.value = 2
  } finally {
    loading.value = false
  }
}

const importYaml = async () => {
  // 预览时已经导入了，这里直接显示结果
  activeStep.value = 2
}

const readFileAsText = (file: File): Promise<string> => {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result as string)
    reader.onerror = reject
    reader.readAsText(file, 'UTF-8')
  })
}

defineExpose({ open })

// 触发刷新事件
const emit = defineEmits<{
  (e: 'refresh'): void
}>()

// 处理关闭
const handleClose = () => {
  // 如果导入成功，触发刷新
  if (importSuccess.value && pipelineResult.value) {
    emit('refresh')
  }
}
</script>

<style scoped>
.step-content {
  min-height: 300px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-primary);
}

.warning-text {
  color: #e6a23c;
  font-size: 13px;
  line-height: 1.6;
}

.text-muted {
  color: var(--el-text-color-secondary);
}

.mr-4 {
  margin-right: 4px;
}
</style>

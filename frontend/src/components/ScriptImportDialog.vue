<template>
  <el-dialog
    v-model="visible"
    title="导入脚本包"
    width="600px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <el-steps :active="activeStep" finish-status="success" align-center>
      <el-step title="选择文件" />
      <el-step title="预览" />
      <el-step title="导入" />
    </el-steps>
    
    <div class="step-content" style="margin-top: 20px;">
      <!-- 步骤 1：选择文件 -->
      <div v-if="activeStep === 0">
        <el-form label-width="100px">
          <el-form-item label="导入方式">
            <el-radio-group v-model="importType">
              <el-radio value="file">离线包导入</el-radio>
              <el-radio value="online">在线导入</el-radio>
            </el-radio-group>
          </el-form-item>
          
          <!-- 离线包导入 -->
          <el-form-item label="选择文件" v-if="importType === 'file'">
            <el-upload
              ref="uploadRef"
              drag
              :auto-upload="false"
              :limit="1"
              accept=".zip"
              :on-change="handleFileChange"
            >
              <el-icon><upload /></el-icon>
              <div class="el-upload__text">
                拖拽文件到此处或 <em>点击选择</em>
              </div>
              <template #tip>
                <div class="el-upload__tip">
                  只能上传 zip 文件
                </div>
              </template>
            </el-upload>
          </el-form-item>
          
          <!-- 在线导入 -->
          <template v-if="importType === 'online'">
            <el-form-item label="仓库地址">
              <el-input 
                v-model="onlineForm.url" 
                placeholder="https://gitee.com/user/scripts 或 https://github.com/user/scripts"
              />
              <div class="form-tip">支持 Gitee / GitHub / GitLab</div>
            </el-form-item>
            
            <el-form-item label="分支/标签">
              <el-input v-model="onlineForm.branch" placeholder="main" />
              <div class="form-tip">默认 main，可指定分支或标签</div>
            </el-form-item>
            
            <el-form-item label="子目录">
              <el-input v-model="onlineForm.subDir" placeholder="scripts/" />
              <div class="form-tip">可选，脚本在仓库子目录时填写</div>
            </el-form-item>
            
            <el-form-item label="访问令牌">
              <el-input 
                v-model="onlineForm.accessToken" 
                type="password" 
                show-password 
                placeholder="可选，私有仓库需要"
              />
            </el-form-item>
          </template>
          
          <el-form-item label="冲突处理">
            <el-radio-group v-model="conflictStrategy">
              <el-radio value="SKIP">跳过已存在的</el-radio>
              <el-radio value="OVERWRITE">覆盖已存在的</el-radio>
              <el-radio value="RENAME">重命名新脚本</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-form>
      </div>
      
      <!-- 步骤 2：预览 -->
      <div v-if="activeStep === 1">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="脚本数量">
            {{ preview?.scripts?.length || 0 }}
          </el-descriptions-item>
          <el-descriptions-item label="导出日期">
            {{ preview?.exportedAt || '-' }}
          </el-descriptions-item>
        </el-descriptions>
        
        <el-table :data="previewScripts" style="margin-top: 10px" max-height="300">
          <el-table-column prop="name" label="脚本名称" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.existing ? 'warning' : 'success'">
                {{ row.existing ? '已存在' : '新增' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
      
      <!-- 步骤 3：导入结果 -->
      <div v-if="activeStep === 2">
        <el-result
          :icon="importResult?.failed === 0 ? 'success' : 'warning'"
          :title="importResult?.failed === 0 ? '导入成功' : '部分失败'"
          :sub-title="`导入 ${importResult?.imported}, 跳过 ${importResult?.skipped}, 失败 ${importResult?.failed}`"
        >
          <template #extra>
            <el-table :data="importResult?.scripts" max-height="300">
              <el-table-column prop="name" label="脚本" width="150" />
              <el-table-column label="状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="getStatusType(row.status)">
                    {{ getStatusText(row.status) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="message" label="信息" />
              <el-table-column label="详情" width="80" v-if="hasDetails">
                <template #default="{ row }">
                  <el-popover trigger="click" v-if="row.error || row.warnings?.length">
                    <template #reference>
                      <el-button text>查看</el-button>
                    </template>
                    <div v-if="row.error" class="error-text">{{ row.error }}</div>
                    <div v-if="row.warnings?.length" class="warning-text">
                      <div v-for="w in row.warnings" :key="w">⚠️ {{ w }}</div>
                    </div>
                  </el-popover>
                </template>
              </el-table-column>
            </el-table>
            
            <div v-if="importResult?.warnings?.length" class="warnings-summary" style="margin-top: 10px;">
              <el-alert type="warning" :closable="false">
                <div v-for="w in importResult.warnings" :key="w">⚠️ {{ w }}</div>
              </el-alert>
            </div>
          </template>
        </el-result>
      </div>
    </div>
    
    <template #footer>
      <el-button @click="visible = false" v-if="activeStep < 2">取消</el-button>
      <el-button @click="prevStep" v-if="activeStep > 0 && activeStep < 2">上一步</el-button>
      <el-button 
        type="primary" 
        @click="nextStep" 
        v-if="activeStep < 2"
        :loading="loading"
        :disabled="!canProceed"
      >
        {{ activeStep === 0 ? '预览' : '导入' }}
      </el-button>
      <el-button @click="visible = false" v-if="activeStep === 2">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Upload } from '@element-plus/icons-vue'
import axios from 'axios'
import { scriptApi } from '@/api/script'

const visible = ref(false)
const loading = ref(false)
const activeStep = ref(0)
const importType = ref<'file' | 'online'>('file')
const conflictStrategy = ref('SKIP')
const selectedFile = ref<File | null>(null)

// 在线导入表单
const onlineForm = reactive({
  url: 'https://gitee.com/toyangdon1/scripts-package',
  branch: 'master',
  subDir: '',
  accessToken: ''
})

// 在线导入临时路径
const onlineTempPath = ref('')

interface ScriptPreview {
  name: string
  existing: boolean
  existingId?: number
}

interface PreviewData {
  format: string
  exportedAt: string
  scripts: string[]
  scriptDetails?: ScriptPreview[]
  resources?: string[]
}

interface ImportResult {
  total: number
  imported: number
  skipped: number
  failed: number
  scripts: Array<{
    name: string
    status: string
    id?: number
    message?: string
    error?: string
    warnings?: string[]
  }>
  warnings?: string[]
}

const preview = ref<PreviewData | null>(null)
const importResult = ref<ImportResult | null>(null)

const uploadRef = ref()

const previewScripts = computed(() => {
  if (!preview.value?.scripts) return []
  // 优先使用后端返回的 scriptDetails
  if (preview.value.scriptDetails && preview.value.scriptDetails.length > 0) {
    return preview.value.scriptDetails.map(s => ({
      name: s.name,
      existing: s.existing
    }))
  }
  // 兼容旧格式（没有 scriptDetails）
  return preview.value.scripts.map((name: string) => ({
    name,
    existing: false
  }))
})

const canProceed = computed(() => {
  if (activeStep.value === 0) {
    if (importType.value === 'file') {
      return selectedFile.value !== null
    } else {
      return onlineForm.url && onlineForm.url.trim().length > 0
    }
  }
  return true
})

const hasDetails = computed(() => {
  return importResult.value?.scripts?.some(s => s.error || s.warnings?.length)
})

const open = () => {
  activeStep.value = 0
  preview.value = null
  importResult.value = null
  selectedFile.value = null
  onlineForm.url = 'https://gitee.com/toyangdon1/scripts-package'
  onlineForm.branch = 'master'
  onlineForm.subDir = ''
  onlineForm.accessToken = ''
  onlineTempPath.value = ''
  importType.value = 'file'
  visible.value = true
}

const handleFileChange = (file: any) => {
  selectedFile.value = file.raw
}

const nextStep = async () => {
  if (activeStep.value === 0) {
    await previewPackage()
  } else if (activeStep.value === 1) {
    await importPackage()
  }
}

const prevStep = () => {
  activeStep.value--
}

const previewPackage = async () => {
  loading.value = true
  try {
    if (importType.value === 'online') {
      // 在线导入预览
      const response = await scriptApi.previewOnline({
        url: onlineForm.url,
        branch: onlineForm.branch,
        subDir: onlineForm.subDir,
        accessToken: onlineForm.accessToken
      })
      preview.value = {
        format: 'online',
        exportedAt: new Date().toISOString(),
        scripts: response.data.scripts.map((s: any) => s.name)
      }
      onlineTempPath.value = response.data.tempPath
    } else {
      // 离线包导入预览
      if (!selectedFile.value) return
      const formData = new FormData()
      formData.append('file', selectedFile.value)
      
      const response = await axios.post('/api/v1/scripts/import/preview', formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      })
      preview.value = response.data.data
    }
    activeStep.value = 1
  } catch (error: any) {
    // 解析后端返回的错误信息
    let errorMsg = '预览失败'
    if (error.response?.data?.message) {
      // HTTP 错误响应
      const backendMsg = error.response.data.message
      if (backendMsg.includes('403') || backendMsg.toLowerCase().includes('forbidden')) {
        errorMsg = 'Gitee API 访问受限，请稍后重试或使用访问令牌'
      } else {
        errorMsg = backendMsg
      }
    } else if (error.message) {
      // 业务错误 (code !== 0)
      const backendMsg = error.message
      if (backendMsg.includes('403') || backendMsg.toLowerCase().includes('forbidden')) {
        errorMsg = 'Gitee API 访问受限，请稍后重试或使用访问令牌'
      } else if (backendMsg.includes('获取文件树失败')) {
        errorMsg = '获取仓库文件列表失败，请检查仓库地址和分支是否正确'
      } else {
        errorMsg = backendMsg
      }
    }
    ElMessage.error(errorMsg)
  } finally {
    loading.value = false
  }
}

const importPackage = async () => {
  loading.value = true
  try {
    if (importType.value === 'online') {
      // 在线导入执行
      const response = await scriptApi.importOnline({
        tempPath: onlineTempPath.value,
        conflictStrategy: conflictStrategy.value
      })
      importResult.value = {
        total: response.data.total,
        imported: response.data.imported,
        skipped: response.data.skipped,
        failed: response.data.failed,
        scripts: response.data.scripts || []
      }
    } else {
      // 离线包导入执行
      if (!selectedFile.value) return
      const formData = new FormData()
      formData.append('file', selectedFile.value)
      formData.append('conflictStrategy', conflictStrategy.value)
      
      const response = await axios.post('/api/v1/scripts/import/execute', formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      })
      importResult.value = response.data.data
    }
    activeStep.value = 2
  } catch (error: any) {
    // 解析后端返回的错误信息
    let errorMsg = '导入失败'
    if (error.response?.data?.message) {
      errorMsg = error.response.data.message
      if (errorMsg.includes('HTTP 403')) {
        errorMsg = 'Gitee API 访问受限，请稍后重试或使用访问令牌'
      } else if (errorMsg.includes('临时文件')) {
        errorMsg = '预览已过期，请重新预览'
      }
    } else if (error.message) {
      errorMsg = '导入失败：' + error.message
    }
    ElMessage.error(errorMsg)
  } finally {
    loading.value = false
  }
}

const getStatusType = (status: string) => {
  const types: Record<string, any> = {
    imported: 'success',
    skipped: 'warning',
    failed: 'danger'
  }
  return types[status] || 'info'
}

const getStatusText = (status: string) => {
  const texts: Record<string, string> = {
    imported: '导入成功',
    skipped: '跳过',
    failed: '失败'
  }
  return texts[status] || status
}

defineExpose({ open })

// 触发刷新事件
const emit = defineEmits<{
  (e: 'refresh'): void
}>()

// 处理关闭
const handleClose = () => {
  // 如果有导入成功，触发刷新
  if (importResult.value && importResult.value.imported > 0) {
    emit('refresh')
  }
}
</script>

<style scoped>
.step-content {
  min-height: 300px;
}

.error-text {
  color: #f56c6c;
  font-family: monospace;
  font-size: 12px;
}

.warning-text {
  color: #e6a23c;
  font-size: 12px;
}

.form-tip {
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
  margin-top: 4px;
}
</style>

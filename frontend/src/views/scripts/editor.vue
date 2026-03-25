<template>
  <div class="script-editor">
    <div class="editor-header">
      <el-page-header @back="$router.back()">
        <template #content>
          <span class="title">{{ isEdit ? '编辑脚本' : '新建脚本' }}</span>
        </template>
      </el-page-header>
      
      <div class="header-actions">
        <el-button @click="handleSave(false)">保存</el-button>
        <el-button type="primary" @click="handleSave(true)">保存并执行</el-button>
      </div>
    </div>

    <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px" class="editor-form">
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item prop="name">
            <template #label>
              脚本名称
              <el-tooltip content="脚本的唯一标识名称，用于任务创建时选择。建议使用英文和数字" placement="top">
                <el-icon class="field-tip-icon"><QuestionFilled /></el-icon>
              </el-tooltip>
            </template>
            <el-input v-model="formData.name" placeholder="如 mysql_test" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="测试类型" prop="testCategory">
            <el-select v-model="formData.testCategory" placeholder="选择测试类型" style="width: 100%" teleported>
              <el-option v-for="cat in TEST_CATEGORIES" :key="cat.value" :label="cat.label" :value="cat.value" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="脚本描述">
        <el-input v-model="formData.description" type="textarea" :rows="2" placeholder="脚本功能描述" />
      </el-form-item>

      <el-divider content-position="left">脚本文件</el-divider>

      <!-- 文件上传区域 -->
      <el-form-item label="脚本文件">
        <div class="upload-area">
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :show-file-list="false"
            :on-change="handleFileChange"
            accept=".sh,.py,.zip,.tar.gz,.tgz"
            drag
          >
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">
              拖拽文件到此处，或 <em>点击上传</em>
            </div>
            <template #tip>
              <div class="el-upload__tip">
                支持单文件（.sh, .py）或压缩包（.zip, .tar.gz），压缩包会自动解压
              </div>
            </template>
          </el-upload>

          <!-- 已上传文件列表 -->
          <div v-if="uploadedFiles.length > 0" class="uploaded-files">
            <div class="file-list-header">
              <span>文件列表</span>
              <el-button type="danger" link @click="clearFiles">清空</el-button>
            </div>
            <el-table :data="uploadedFiles" size="small" border>
              <el-table-column prop="name" label="文件名" min-width="200" />
              <el-table-column prop="path" label="路径" min-width="200" />
              <el-table-column prop="size" label="大小" width="100">
                <template #default="{ row }">
                  {{ formatFileSize(row.size) }}
                </template>
              </el-table-column>
              <el-table-column label="类型" width="80">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.type === 'sh' ? 'success' : row.type === 'py' ? 'warning' : ''">
                    {{ row.type || '文件' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="80">
                <template #default="{ row }">
                  <el-button 
                    v-if="isTextFile(row.type, row.name)" 
                    type="primary" 
                    link 
                    @click="viewFile(row)"
                  >
                    查看
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </div>
      </el-form-item>

      <el-divider content-position="left">参数配置</el-divider>

      <el-form-item label="执行参数">
        <div class="param-config">
          <div v-for="(param, index) in formData.runParams" :key="index" class="param-item">
            <el-input v-model="param.name" placeholder="参数名" style="width: 150px" />
            <el-select v-model="param.type" placeholder="类型" style="width: 100px" teleported>
              <el-option label="字符串" value="string" />
              <el-option label="数字" value="number" />
              <el-option label="布尔" value="boolean" />
            </el-select>
            <el-input v-model="param.default" placeholder="默认值" style="flex: 1" />
            <el-button type="danger" link @click="formData.runParams.splice(index, 1)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
          <el-button type="primary" link @click="addParam">
            <el-icon><Plus /></el-icon> 添加参数
          </el-button>
        </div>
      </el-form-item>

      <!-- 执行计划配置 -->
      <el-divider content-position="left">
        <span>执行计划</span>
        <el-tooltip content="定义脚本的执行步骤、依赖关系和生命周期。每个步骤可配置专属资源和输出收集" placement="top">
          <el-icon style="margin-left: 4px; cursor: help;"><QuestionFilled /></el-icon>
        </el-tooltip>
      </el-divider>

      <el-form-item label="">
        <StepConfig 
          v-model="stepsData" 
          :scriptFiles="scriptFileOptions"
        />
      </el-form-item>

      <!-- 共享资源配置 -->
      <el-divider content-position="left">
        <span>共享资源</span>
        <el-tooltip content="所有步骤共用的资源文件，如公共配置文件、证书等。步骤专属资源请在上方执行计划中配置" placement="top">
          <el-icon style="margin-left: 4px; cursor: help;"><QuestionFilled /></el-icon>
        </el-tooltip>
      </el-divider>

      <el-form-item label="">
        <ResourceConfig 
          v-model="resourceBindings" 
          :scriptId="scriptId ?? undefined" 
        />
      </el-form-item>
    </el-form>

    <!-- 文件查看对话框 -->
    <el-dialog
      v-model="fileViewDialogVisible"
      :title="currentViewFile?.name || '文件内容'"
      width="800px"
      destroy-on-close
    >
      <div class="file-viewer">
        <div class="file-path">
          <el-tag type="info" size="small">{{ currentViewFile?.path }}</el-tag>
        </div>
        <el-input
          v-model="fileContent"
          type="textarea"
          :rows="20"
          readonly
          class="code-content"
        />
      </div>
      <template #footer>
        <el-button @click="fileViewDialogVisible = false">关闭</el-button>
        <el-button type="primary" @click="copyFileContent">复制内容</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, Delete, UploadFilled, QuestionFilled } from '@element-plus/icons-vue'
import { scriptApi } from '@/api/script'
import { scriptResourceApi, type ScriptResource } from '@/api/resource'
import request from '@/utils/request'
import ResourceConfig from './ResourceConfig.vue'
import StepConfig from './StepConfig.vue'
import { TEST_CATEGORIES } from '@/config/categories'

const route = useRoute()
const router = useRouter()
const formRef = ref()
const uploadRef = ref()

const isEdit = computed(() => !!route.params.id)
const scriptId = computed(() => route.params.id ? Number(route.params.id) : null)

interface UploadedFile {
  name: string
  path: string
  size: number
  type: string
}

const uploadedFiles = ref<UploadedFile[]>([])
const tempFilePath = ref('')

// 执行步骤数据
const stepsData = ref<any>({})

// 共享资源配置（本地模式）
const resourceBindings = ref<ScriptResource[]>([])

const formData = reactive({
  name: '',
  description: '',
  testCategory: '',
  runParams: [] as { name: string; type: string; default: string }[],
  fileList: [] as any[],
})

const formRules = {
  name: [{ required: true, message: '请输入脚本名称', trigger: 'blur' }],
  testCategory: [{ required: true, message: '请选择测试类型', trigger: 'change' }],
}

// 所有脚本文件（用于执行计划下拉选择）
const scriptFileOptions = computed(() => {
  return uploadedFiles.value.filter(f => f.type === 'sh' || f.type === 'py')
})

async function handleFileChange(file: any) {
  const formDataObj = new FormData()
  const rawFile = file.raw || file
  formDataObj.append('file', rawFile)

  try {
    const res = await request.post('/scripts/upload', formDataObj, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    
    if (res.code === 0) {
      uploadedFiles.value = res.data.fileList || []
      tempFilePath.value = res.data.tempPath
      ElMessage.success(`已解析 ${uploadedFiles.value.length} 个文件`)
    }
  } catch (error: any) {
    ElMessage.error(error.message || '上传失败')
  }
}

function clearFiles() {
  uploadedFiles.value = []
  tempFilePath.value = ''
}

function formatFileSize(bytes: number) {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}

function addParam() {
  formData.runParams.push({ name: '', type: 'string', default: '' })
}

async function handleSave(andRun: boolean) {
  await formRef.value.validate()

  const data: any = {
    name: formData.name,
    description: formData.description,
    testCategory: formData.testCategory,
    fileList: uploadedFiles.value,
    tempFilePath: tempFilePath.value,
    // 执行参数（共享参数）
    parameters: formData.runParams.filter((p: any) => p.name),
    // 执行步骤
    steps: stepsData.value,
  }
  
  let savedScriptId: number | null = null
  
  if (isEdit.value) {
    savedScriptId = Number(route.params.id)
    await scriptApi.update(savedScriptId, data)
  } else {
    const res = await scriptApi.create(data)
    // 从响应获取新脚本ID
    if (res.data?.id) {
      savedScriptId = res.data.id
    }
  }
  
  // 新建模式下，保存关联资源
  if (!isEdit.value && savedScriptId && resourceBindings.value.length > 0) {
    try {
      for (const binding of resourceBindings.value) {
        await scriptResourceApi.add(savedScriptId, {
          resourceId: binding.resourceId,
          targetPath: binding.targetPath,
          permissions: binding.permissions,
          uploadOrder: binding.uploadOrder
        })
      }
    } catch (e) {
      console.error('保存关联资源失败:', e)
      ElMessage.warning('脚本保存成功，但关联资源保存失败')
    }
  }
  
  ElMessage.success('保存成功')
  
  if (andRun) {
    router.push('/tasks/create')
  } else {
    router.push('/scripts/list')
  }
}

onMounted(() => {
  if (isEdit.value) {
    loadScript()
  }
})

async function loadScript() {
  const res = await scriptApi.get(Number(route.params.id))
  if (res.code === 0 && res.data) {
    Object.assign(formData, {
      name: res.data.name,
      description: res.data.description,
      testCategory: res.data.testCategory,
      fileList: res.data.fileList || [],
    })
    
    if (res.data.fileList && res.data.fileList.length > 0) {
      uploadedFiles.value = res.data.fileList as UploadedFile[]
    }
    
    // 加载执行步骤
    if (res.data.steps) {
      stepsData.value = res.data.steps
    } else {
      stepsData.value = {}
    }
    
    // 加载共享参数定义
    if (res.data.parameters && Array.isArray(res.data.parameters)) {
      formData.runParams = res.data.parameters
    } else {
      formData.runParams = []
    }
  }
}

// 文件查看相关
const fileViewDialogVisible = ref(false)
const currentViewFile = ref<UploadedFile | null>(null)
const fileContent = ref('')

function isTextFile(type: string, name: string): boolean {
  const textExtensions = ['sh', 'py', 'txt', 'json', 'xml', 'yaml', 'yml', 'md', 'conf', 'cfg', 'ini', 'log', 'csv']
  const textTypes = ['sh', 'py', 'txt', 'json', 'xml', 'yaml', 'yml', 'md', 'conf', 'cfg', 'ini', 'log', 'csv']
  
  if (textTypes.includes(type)) return true
  
  const ext = name.split('.').pop()?.toLowerCase() || ''
  return textExtensions.includes(ext)
}

async function viewFile(file: UploadedFile) {
  if (!isEdit.value) {
    ElMessage.warning('请先保存脚本后再查看文件内容')
    return
  }
  
  currentViewFile.value = file
  fileContent.value = '加载中...'
  fileViewDialogVisible.value = true
  
  try {
    const encodedPath = file.path.split('/').map(segment => encodeURIComponent(segment)).join('/')
    const res = await request.get(`/scripts/${route.params.id}/files/${encodedPath}`)
    if (res.code === 0) {
      fileContent.value = res.data.content || ''
    } else {
      fileContent.value = '加载失败: ' + (res.message || '未知错误')
    }
  } catch (e: any) {
    fileContent.value = '加载失败: ' + (e.message || '网络错误')
  }
}

function copyFileContent() {
  if (fileContent.value) {
    navigator.clipboard.writeText(fileContent.value)
    ElMessage.success('已复制到剪贴板')
  }
}
</script>

<style lang="scss" scoped>
.script-editor {
  padding: 20px;
  background: #fff;
  border-radius: var(--radius-md);
}

.editor-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--border-lighter);
}

.title {
  font-size: 16px;
  font-weight: 600;
}

.editor-form {
  max-width: 1000px;
  
  .field-tip-icon {
    margin-left: 4px;
    color: var(--el-text-color-secondary);
    cursor: help;
    vertical-align: middle;
    transition: color 0.2s;
    
    &:hover {
      color: var(--el-color-primary);
    }
  }
}

.param-config {
  width: 100%;
}

.param-item {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
  align-items: center;
}

.upload-area {
  width: 100%;
}

.uploaded-files {
  margin-top: 16px;
  border: 1px solid var(--border-lighter);
  border-radius: var(--radius-sm);
  padding: 12px;
}

.file-list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  font-weight: 500;
}

.file-viewer {
  .file-path {
    margin-bottom: 12px;
  }
  
  .code-content {
    :deep(textarea) {
      font-family: 'Fira Code', 'Monaco', 'Menlo', monospace;
      font-size: 13px;
      line-height: 1.6;
      background: #f8f9fa;
    }
  }
}
</style>

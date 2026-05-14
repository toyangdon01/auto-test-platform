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
            <el-input v-model="formData.name" placeholder="如mysql_test" />
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
            accept="*"
            drag
          >
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">
              拖拽文件到此处，<em>点击上传</em>
            </div>
            <template #tip>
              <div class="el-upload__tip">
                支持任意文件后缀，压缩包（zip, .tar.gz）会自动解压
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
              <el-table-column prop="name" label="文件" min-width="200" />
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
              <el-table-column label="操作" width="140">
                <template #default="{ row }">
                  <el-button 
                    v-if="isTextFile(row.type, row.name)" 
                    type="primary" 
                    link 
                    @click="viewFile(row)"
                  >
                    编辑
                  </el-button>
                  <el-button 
                    type="danger" 
                    link 
                    @click="deleteFile(row)"
                  >
                    删除
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
            <el-input v-model="param.name" placeholder="参数名" style="width: 120px" />
            <el-select v-model="param.type" placeholder="类型" style="width: 90px" teleported>
              <el-option label="字符串" value="string" />
              <el-option label="数字" value="number" />
              <el-option label="布尔" value="boolean" />
            </el-select>
            <el-input v-model="param.default" placeholder="默认值" style="width: 120px" />
            <el-input v-model="param.description" placeholder="参数描述" style="flex: 1" />
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

    <!-- 文件编辑对话框 -->
    <el-dialog
      v-model="fileViewDialogVisible"
      :title="currentViewFile?.name || '文件内容'"
      width="900px"
      destroy-on-close
    >
      <div class="file-viewer">
        <div class="file-path">
          <el-tag type="info" size="small">{{ currentViewFile?.path }}</el-tag>
          <el-tag v-if="fileModified" type="warning" size="small" style="margin-left: 8px">已修改</el-tag>
        </div>
        <el-input
          v-model="fileContent"
          type="textarea"
          :rows="25"
          class="code-content"
        />
      </div>
      <template #footer>
        <el-button @click="fileViewDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingFile" @click="saveFileContent">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
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
  runParams: [] as { name: string; type: string; default: string; description: string }[],
  fileList: [] as any[],
})

const formRules = {
  name: [{ required: true, message: '请输入脚本名称', trigger: 'blur' }],
  testCategory: [{ required: true, message: '请选择测试类型', trigger: 'change' }],
}

// 所有脚本文件（用于执行计划下拉选择）
const scriptFileOptions = computed(() => {
  return uploadedFiles.value.filter(f => {
    // 优先使用 type 字段，否则从 path 推断
    const fileType = f.type || getFileType(f.path)
    return fileType === 'sh' || fileType === 'py'
  })
})

// 从文件路径推断类型
function getFileType(path: string): string {
  if (!path) return ''
  const ext = path.split('.').pop()?.toLowerCase() || ''
  if (ext === 'tar.gz') return 'tar.gz'
  return ext
}

async function handleFileChange(file: any) {
  const formDataObj = new FormData()
  const rawFile = file.raw || file
  formDataObj.append('file', rawFile)

  try {
      // 传递已存在的 tempPath，让后端将新文件追加到同一目录
      const params: any = {}
      if (tempFilePath.value) {
        params.tempPath = tempFilePath.value
      }

      const res = await request.post('/scripts/upload', formDataObj, {
        headers: { 'Content-Type': 'multipart/form-data' },
        params
      })
    
    if (res.code === 0) {
      // 追加新文件到现有列表，而不是覆盖
      const newFiles = res.data.fileList || []
      // 过滤掉重复文件（根据 path 去重）
      const existingPaths = new Set(uploadedFiles.value.map(f => f.path))
      const uniqueNewFiles = newFiles.filter((f: any) => !existingPaths.has(f.path))
      uploadedFiles.value = [...uploadedFiles.value, ...uniqueNewFiles]
      
      // 更新 tempPath（压缩包会更新为解压目录，单文件更新为文件所在目录）
      if (res.data.tempPath) {
        tempFilePath.value = res.data.tempPath
      }
      
      
      // 自动填充配置（如果有 autotest.yaml）
      if (res.data.config) {
        const config = res.data.config
        
        // 只在字段为空时填充，避免覆盖用户输入
        if (!formData.name && config.name) {
          formData.name = config.name
        }
        if (!formData.description && config.description) {
          formData.description = config.description
        }
        if (!formData.testCategory && config.category) {
          formData.testCategory = config.category
        }
        
        // 填充参数配置
        if (config.parameters && config.parameters.length > 0) {
          formData.runParams = config.parameters.map((p: any) => ({
            name: p.name || '',
            type: p.type || 'string',
            default: p.default !== undefined ? String(p.default) : '',
            description: p.description || ''
          }))
        }
        
        // 填充执行步骤
        if (config.steps && Object.keys(config.steps).length > 0) {
          stepsData.value = config.steps
        }
        
        // 填充共享资源
        if (config.resources && config.resources.length > 0) {
          resourceBindings.value = config.resources.map((r: any) => ({
            resourceId: r.resourceId || null,
            targetPath: r.targetPath || '/tmp',
            permissions: r.permissions || '644',
            uploadOrder: r.order || r.uploadOrder || 1
          }))
        }
        
        ElMessage.success(`已解压 ${uploadedFiles.value.length} 个文件，检测到 autotest.yaml 配置`)
      } else {
        ElMessage.success(`已解压 ${uploadedFiles.value.length} 个文件`)
      }
      
      // 显示配置解析错误（如果有）
      if (res.data.configError) {
        ElMessage.warning(`配置文件解析失败: ${res.data.configError}`)
      }
    }
  } catch (error: any) {
    ElMessage.error(error.message || '上传失败')
  }
}

async function clearFiles() {
  if (!isEdit.value) {
    // 新建模式：只清本地状态
    uploadedFiles.value = []
    tempFilePath.value = ''
    return
  }
  
  // 编辑模式：调用API 删除文件
  try {
    await ElMessageBox.confirm('确定要清空所有文件吗？此操作不可恢复', '警告', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    await request.delete(`/scripts/${scriptId.value}/files`)
    uploadedFiles.value = []
    tempFilePath.value = ''
    ElMessage.success('文件已清空')
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e.response?.data?.message || '操作失败')
    }
  }
}

async function deleteFile(file: UploadedFile) {
  if (!isEdit.value) {
    // 新建模式：只从列表移除
    const index = uploadedFiles.value.findIndex(f => f.path === file.path)
    if (index !== -1) {
      uploadedFiles.value.splice(index, 1)
    }
    return
  }
  
  if (!isEdit.value || (tempFilePath.value && file.path.includes(tempFilePath.value.split('/').pop() || ''))) { const index = uploadedFiles.value.findIndex(f => f.path === file.path); if (index !== -1) uploadedFiles.value.splice(index, 1); if (tempFilePath.value) ElMessage.success('文件已从列表移除，未保存到服务器'); return }
  try {
    await ElMessageBox.confirm(`确定要删除文件 "${file.name}" 吗？`, '确认删除', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    const encodedPath = file.path.split('/').map(segment => encodeURIComponent(segment)).join('/')
    await request.delete(`/scripts/${scriptId.value}/files/${encodedPath}`)
    
    // 从列表移除
    const index = uploadedFiles.value.findIndex(f => f.path === file.path)
    if (index !== -1) {
      uploadedFiles.value.splice(index, 1)
    }
    
    ElMessage.success('文件已删除')
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e.response?.data?.message || '删除失败')
    }
  }
}

function formatFileSize(bytes: number) {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}

function addParam() {
  formData.runParams.push({ name: '', type: 'string', default: '', description: '' })
}

async function handleSave(andRun: boolean) {
  await formRef.value.validate()

  const data: any = {
    name: formData.name,
    description: formData.description,
    testCategory: formData.testCategory,
    fileList: uploadedFiles.value,
    tempFilePath: tempFilePath.value,
    // 执行参数（参数配置）
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
    // 成功应该返回新建脚本ID
    if (res.data?.script?.id) { savedScriptId = res.data.script.id } else if (res.data?.id) { savedScriptId = res.data.id }
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
    // 通过scriptId 跳转到任务创建页面
    const targetScriptId = isEdit.value ? route.params.id : savedScriptId
    router.push(`/tasks/create?scriptId=${targetScriptId}`)
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
    
    // 如果使用返回的fileList（没有 type），则用 file-list API 获取
    if (res.data.fileList && res.data.fileList.length > 0) {
      // 确保 type 字段存在，否则从 path 推断
      uploadedFiles.value = res.data.fileList.map((f: any) => ({
        name: f.name,
        path: f.path,
        size: f.size,
        type: f.type || getFileType(f.path)
      }))
    } else {
      // 用file-list API 获取文件列表
      try {
        const fileListRes = await request.get(`/scripts/${route.params.id}/file-list`)
        if (fileListRes.code === 0 && fileListRes.data) {
          // 确保 name, path, size, type 字段直接使用
          uploadedFiles.value = fileListRes.data.map((f: any) => ({
            name: f.name,
            path: f.path,
            size: f.size,
            type: f.type || (f.path?.endsWith('.sh') ? 'sh' : f.path?.endsWith('.py') ? 'py' : 'zip')
          }))
        }
      } catch (e) {
        console.error('获取文件列表失败', e)
      }
    }
    
    // 加载执行步骤
    if (res.data.steps) {
      stepsData.value = res.data.steps
    } else {
      stepsData.value = {}
    }
    
    // 加载关联资源配置
    if (res.data.parameters && Array.isArray(res.data.parameters)) {
      formData.runParams = res.data.parameters
    } else {
      formData.runParams = []
    }
  }
}

// 文件查看状态
const fileViewDialogVisible = ref(false)
const currentViewFile = ref<UploadedFile | null>(null)
const fileContent = ref('')
const fileModified = ref(false)
const savingFile = ref(false)
const originalContent = ref('')

function isTextFile(type: string, name: string): boolean {
  const textExtensions = ['sh', 'py', 'txt', 'json', 'xml', 'yaml', 'yml', 'md', 'conf', 'cfg', 'ini', 'log', 'csv']
  const textTypes = ['sh', 'py', 'txt', 'json', 'xml', 'yaml', 'yml', 'md', 'conf', 'cfg', 'ini', 'log', 'csv']
  
  if (textTypes.includes(type)) return true
  
  const ext = name.split('.').pop()?.toLowerCase() || ''
  return textExtensions.includes(ext)
}

async function viewFile(file: UploadedFile) {
  currentViewFile.value = file
  fileContent.value = '加载中...'
  fileModified.value = false
  originalContent.value = ''
  fileViewDialogVisible.value = true
  
  try {
    let res: any
    
    if (isEdit.value) {
      // 编辑模式：通过脚本ID读取文件
      const encodedPath = file.path.split('/').map(segment => encodeURIComponent(segment)).join('/')
      res = await request.get(`/scripts/${route.params.id}/files/${encodedPath}`)
    } else {
      // 新建模式：通过临时路径读取文件
      const encodedPath = file.path.split('/').map(segment => encodeURIComponent(segment)).join('/')
      res = await request.get(`/scripts/temp-files`, {
        tempPath: tempFilePath.value,
        filePath: file.path
      })
    }
    
    if (res.code === 0) {
      fileContent.value = res.data.content || ''
      originalContent.value = res.data.content || ''
    } else {
      fileContent.value = '加载失败: ' + (res.message || '未知错误')
    }
  } catch (e: any) {
    fileContent.value = '加载失败: ' + (e.message || '网络错误')
  }
}

// 监听文件内容变化
watch(fileContent, (newVal) => {
  fileModified.value = newVal !== originalContent.value
})

async function saveFileContent() {
  if (!currentViewFile.value || !fileModified.value) return
  
  savingFile.value = true
  try {
    let res: any
    
    if (isEdit.value) {
      // 编辑模式：保存到脚本目录
      const encodedPath = currentViewFile.value.path.split('/').map(segment => encodeURIComponent(segment)).join('/')
      res = await request.put(`/scripts/${route.params.id}/files/${encodedPath}`, {
        content: fileContent.value
      })
    } else {
      // 新建模式：保存到临时目录
      res = await request.put(`/scripts/temp-files`, {
        content: fileContent.value
      }, {
        params: {
          tempPath: tempFilePath.value,
          filePath: currentViewFile.value.path
        }
      })
    }
    
    if (res.code === 0) {
      ElMessage.success('文件保存成功')
      fileModified.value = false
      originalContent.value = fileContent.value
    } else {
      ElMessage.error(res.message || '保存失败')
    }
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '保存失败')
  } finally {
    savingFile.value = false
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
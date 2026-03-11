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
            <el-select v-model="formData.testCategory" placeholder="选择测试类型" style="width: 100%">
              <el-option v-for="cat in TEST_CATEGORIES" :key="cat.value" :label="cat.label" :value="cat.value" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item>
        <template #label>
          生命周期
          <el-tooltip content="简单模式仅执行测试；完整模式包含部署、执行、清理三个阶段" placement="top">
            <el-icon class="field-tip-icon"><QuestionFilled /></el-icon>
          </el-tooltip>
        </template>
        <el-radio-group v-model="formData.lifecycleMode">
          <el-radio value="simple">简单模式（仅执行）</el-radio>
          <el-radio value="full">完整模式（部署→执行→卸载）</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item label="脚本描述">
        <el-input v-model="formData.description" type="textarea" :rows="2" placeholder="脚本功能描述" />
      </el-form-item>

      <el-divider content-position="left">脚本文件</el-divider>

      <!-- 文件上传区域 -->
      <el-form-item label="上传方式">
        <el-radio-group v-model="uploadMode" :disabled="isEdit && formData.fileList.length > 0">
          <el-radio value="upload">上传文件</el-radio>
          <el-radio value="edit">在线编辑</el-radio>
        </el-radio-group>
      </el-form-item>

      <!-- 上传模式 -->
      <el-form-item v-if="uploadMode === 'upload'" label="脚本文件">
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
              <el-table-column label="入口" width="80">
                <template #default="{ row }">
                  <el-radio v-model="formData.entryFile" :value="row.path">&nbsp;</el-radio>
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

      <!-- 入口文件选择 -->
      <el-form-item v-if="uploadMode === 'upload' && uploadedFiles.length > 0" label="入口文件">
        <el-select v-model="formData.entryFile" placeholder="选择入口脚本" style="width: 100%">
          <el-option
            v-for="file in entryFileOptions"
            :key="file.path"
            :label="file.path"
            :value="file.path"
          />
        </el-select>
        <div class="form-tip">选择测试执行时的入口脚本文件</div>
      </el-form-item>

      <!-- 完整模式的入口文件 -->
      <template v-if="formData.lifecycleMode === 'full' && uploadMode === 'upload' && uploadedFiles.length > 0">
        <el-form-item label="部署入口">
          <el-select v-model="formData.deployEntry" placeholder="选择部署脚本（可选）" style="width: 100%" clearable>
            <el-option
              v-for="file in scriptFileOptions"
              :key="file.path"
              :label="file.path"
              :value="file.path"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="卸载入口">
          <el-select v-model="formData.cleanupEntry" placeholder="选择卸载脚本（可选）" style="width: 100%" clearable>
            <el-option
              v-for="file in scriptFileOptions"
              :key="file.path"
              :label="file.path"
              :value="file.path"
            />
          </el-select>
        </el-form-item>
      </template>

      <!-- 在线编辑模式 -->
      <template v-if="uploadMode === 'edit'">
        <el-tabs v-model="activeTab" class="script-tabs">
          <el-tab-pane label="执行脚本 (run)" name="run">
            <div class="code-editor">
              <el-input
                v-model="formData.runContent"
                type="textarea"
                :rows="15"
                placeholder="#!/bin/bash&#10;# 执行测试脚本..."
                class="code-textarea"
              />
            </div>
          </el-tab-pane>
          
          <el-tab-pane v-if="formData.lifecycleMode === 'full'" label="部署脚本 (deploy)" name="deploy">
            <div class="code-editor">
              <el-input
                v-model="formData.deployContent"
                type="textarea"
                :rows="15"
                placeholder="#!/bin/bash&#10;# 部署脚本..."
                class="code-textarea"
              />
            </div>
          </el-tab-pane>
          
          <el-tab-pane v-if="formData.lifecycleMode === 'full'" label="卸载脚本 (cleanup)" name="cleanup">
            <div class="code-editor">
              <el-input
                v-model="formData.cleanupContent"
                type="textarea"
                :rows="15"
                placeholder="#!/bin/bash&#10;# 卸载脚本..."
                class="code-textarea"
              />
            </div>
          </el-tab-pane>
          
          <el-tab-pane label="关联资源" name="resources">
            <ResourceConfig v-if="scriptId" :scriptId="scriptId" />
            <el-empty v-else description="请先保存脚本后再配置资源" :image-size="60" />
          </el-tab-pane>
        </el-tabs>
      </template>

      <el-divider content-position="left">参数配置</el-divider>

      <el-form-item label="执行参数">
        <div class="param-config">
          <div v-for="(param, index) in formData.runParams" :key="index" class="param-item">
            <el-input v-model="param.name" placeholder="参数名" style="width: 150px" />
            <el-select v-model="param.type" placeholder="类型" style="width: 100px">
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

      <!-- 角色配置 -->
      <el-divider content-position="left">
        <span>多角色配置</span>
        <el-tooltip content="用于多服务器协同测试场景，如 client-server 架构" placement="top">
          <el-icon style="margin-left: 4px; cursor: help;"><QuestionFilled /></el-icon>
        </el-tooltip>
      </el-divider>

      <el-form-item label="">
        <RoleConfig v-model="rolesData" />
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
import request from '@/utils/request'
import RoleConfig from './RoleConfig.vue'
import ResourceConfig from './ResourceConfig.vue'
import { TEST_CATEGORIES } from '@/config/categories'

const route = useRoute()
const router = useRouter()
const formRef = ref()
const uploadRef = ref()
const activeTab = ref('run')
const uploadMode = ref<'upload' | 'edit'>('upload')

const isEdit = computed(() => !!route.params.id)
const scriptId = computed(() => route.params.id ? Number(route.params.id) : null)

interface UploadedFile {
  name: string
  path: string
  size: number
  type: string
  isEntry: boolean
}

const uploadedFiles = ref<UploadedFile[]>([])
const tempFilePath = ref('')

// 角色定义数据
const rolesData = ref<any>({})

const formData = reactive({
  name: '',
  description: '',
  testCategory: '',
  lifecycleMode: 'simple' as 'simple' | 'full',
  entryFile: '',
  deployEntry: '',
  cleanupEntry: '',
  runContent: '',
  deployContent: '',
  cleanupContent: '',
  runParams: [] as { name: string; type: string; default: string }[],
  fileList: [] as any[],
})

const formRules = {
  name: [{ required: true, message: '请输入脚本名称', trigger: 'blur' }],
  testCategory: [{ required: true, message: '请选择测试类型', trigger: 'change' }],
}

// 可作为入口的文件（仅 .sh 和 .py）
const entryFileOptions = computed(() => {
  return uploadedFiles.value.filter(f => f.type === 'sh' || f.type === 'py')
})

// 所有脚本文件
const scriptFileOptions = computed(() => {
  return uploadedFiles.value.filter(f => f.type === 'sh' || f.type === 'py')
})

async function handleFileChange(file: any) {
  const formDataObj = new FormData()
  formDataObj.append('file', file.raw)

  try {
    const res = await request.post('/scripts/upload', formDataObj, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    
    if (res.code === 0) {
      uploadedFiles.value = res.data.fileList || []
      tempFilePath.value = res.data.tempPath
      
      // 自动选择建议的入口文件
      if (res.data.suggestedEntry) {
        formData.entryFile = res.data.suggestedEntry
      }
      
      ElMessage.success(`已解析 ${uploadedFiles.value.length} 个文件`)
    }
  } catch (error: any) {
    ElMessage.error(error.message || '上传失败')
  }
}

function clearFiles() {
  uploadedFiles.value = []
  tempFilePath.value = ''
  formData.entryFile = ''
  formData.deployEntry = ''
  formData.cleanupEntry = ''
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
    lifecycleMode: formData.lifecycleMode,
    entryFile: formData.entryFile,
    deployEntry: formData.deployEntry,
    cleanupEntry: formData.cleanupEntry,
    hasDeploy: formData.lifecycleMode === 'full' && !!formData.deployEntry,
    hasCleanup: formData.lifecycleMode === 'full' && !!formData.cleanupEntry,
    fileList: uploadedFiles.value,
    tempFilePath: tempFilePath.value,
    // 角色定义
    roles: rolesData.value,
  }
  
  // 在线编辑模式下，添加脚本内容
  if (uploadMode === 'edit') {
    data.runContent = formData.runContent
    if (formData.lifecycleMode === 'full') {
      data.deployContent = formData.deployContent
      data.cleanupContent = formData.cleanupContent
    }
  }
  
  if (isEdit.value) {
    await scriptApi.update(Number(route.params.id), data)
  } else {
    await scriptApi.create(data)
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
    // 加载脚本详情
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
      lifecycleMode: res.data.lifecycleMode || 'simple',
      entryFile: res.data.entryFile,
      deployEntry: res.data.deployEntry,
      cleanupEntry: res.data.cleanupEntry,
      fileList: res.data.fileList || [],
    })
    
    if (res.data.fileList && res.data.fileList.length > 0) {
      uploadedFiles.value = res.data.fileList as UploadedFile[]
      uploadMode.value = 'upload'
    } else {
      uploadMode.value = 'edit'
    }
    
    // 加载角色定义 - 检查是否有实际的角色数据
    if (res.data.roles && res.data.roles.roles && res.data.roles.roles.length > 0) {
      rolesData.value = res.data.roles
    } else {
      rolesData.value = { roles: [] }
    }
  }
}

// 文件查看相关
const fileViewDialogVisible = ref(false)
const currentViewFile = ref<UploadedFile | null>(null)
const fileContent = ref('')

// 判断是否为文本文件
function isTextFile(type: string, name: string): boolean {
  const textExtensions = ['sh', 'py', 'txt', 'json', 'xml', 'yaml', 'yml', 'md', 'conf', 'cfg', 'ini', 'log', 'csv']
  const textTypes = ['sh', 'py', 'txt', 'json', 'xml', 'yaml', 'yml', 'md', 'conf', 'cfg', 'ini', 'log', 'csv']
  
  if (textTypes.includes(type)) return true
  
  // 根据文件扩展名判断
  const ext = name.split('.').pop()?.toLowerCase() || ''
  return textExtensions.includes(ext)
}

// 查看文件内容
async function viewFile(file: UploadedFile) {
  if (!isEdit.value) {
    // 新建脚本时，文件还未上传到服务器，无法查看
    ElMessage.warning('请先保存脚本后再查看文件内容')
    return
  }
  
  currentViewFile.value = file
  fileContent.value = '加载中...'
  fileViewDialogVisible.value = true
  
  try {
    // 不编码路径中的 /，只编码特殊字符（保留 / 和字母数字）
    // Spring MVC 的 {*filePath} 需要路径包含原始的 / 字符
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

// 复制文件内容
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
  
  // 字段 tip 图标样式
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

.script-tabs {
  margin: 20px 0;
}

.code-editor {
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.code-textarea {
  :deep(textarea) {
    font-family: 'Fira Code', 'Monaco', 'Menlo', monospace;
    font-size: 13px;
    line-height: 1.6;
    border: none;
    border-radius: 0;
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

.form-tip {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-secondary);
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

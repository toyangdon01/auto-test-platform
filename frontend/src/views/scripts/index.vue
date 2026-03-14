<template>
  <div class="page-card">
    <div class="page-header">
      <h3 class="page-title">脚本列表</h3>
      <el-button type="primary" @click="$router.push('/scripts/create')">
        <el-icon><Plus /></el-icon>
        新建脚本
      </el-button>
    </div>

    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-input
        v-model="queryParams.name"
        placeholder="搜索脚本名称"
        clearable
        style="width: 240px"
        @keyup.enter="fetchData"
      />
      
      <el-select v-model="queryParams.testCategory" placeholder="测试类型" clearable style="width: 140px">
        <el-option v-for="cat in TEST_CATEGORIES" :key="cat.value" :label="cat.label" :value="cat.value" />
      </el-select>

      <el-button type="primary" @click="fetchData">查询</el-button>
      <el-button @click="resetQuery">重置</el-button>
    </div>

    <!-- 数据表格 -->
    <el-table v-loading="loading" :data="tableData" stripe>
      <el-table-column prop="name" label="脚本名称" min-width="200">
        <template #default="{ row }">
          <div class="script-name">
            <span class="name-text">{{ formatScriptName(row.name) }}</span>
            <span v-if="row.remark" class="remark-text">{{ row.remark }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="testCategory" label="测试类型" width="120">
        <template #default="{ row }">
          <el-tag>{{ getCategoryText(row.testCategory) }}</el-tag>
        </template>
      </el-table-column>
      <!-- 版本功能暂时隐藏 -->
      <!-- <el-table-column prop="currentVersion" label="当前版本" width="100" /> -->
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'enabled' ? 'success' : 'info'">
            {{ row.status === 'enabled' ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="updatedAt" label="更新时间" width="160">
        <template #default="{ row }">
          {{ formatTime(row.updatedAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="400" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click="handleDetail(row)">详情</el-button>
          <el-button type="primary" link @click="handleRun(row)">执行</el-button>
          <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
          <!-- 版本功能暂时隐藏 -->
          <!-- <el-button type="primary" link @click="handleVersions(row)">版本</el-button> -->
          <el-button type="primary" link @click="handleParseRules(row)">解析</el-button>
          <el-button type="primary" link @click="handleExport(row)">导出</el-button>
          <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pagination-wrap">
      <el-pagination
        v-model:current-page="queryParams.page"
        v-model:page-size="queryParams.size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @size-change="fetchData"
        @current-change="fetchData"
      />
    </div>

    <!-- 版本管理弹窗 - 暂时隐藏 -->
    <!-- <VersionManager
      v-model="versionDialogVisible"
      :script="currentScript"
      @refresh="fetchData"
    /> -->

    <!-- 解析规则配置弹窗 -->
    <ParseRuleConfig
      v-model="parseDialogVisible"
      :script="currentScript"
      @refresh="fetchData"
    />

    <!-- 脚本详情弹窗 -->
    <el-dialog
      v-model="detailDialogVisible"
      title="脚本详情"
      width="800px"
      destroy-on-close
    >
      <el-descriptions :column="2" border v-if="detailScript">
        <el-descriptions-item label="脚本名称" :span="2">
          {{ detailScript.name }}
        </el-descriptions-item>
        <el-descriptions-item label="脚本ID">
          {{ detailScript.id }}
        </el-descriptions-item>
        <el-descriptions-item label="当前版本">
          {{ detailScript.currentVersion }}
        </el-descriptions-item>
        <el-descriptions-item label="测试类型">
          <el-tag>{{ getCategoryText(detailScript.testCategory) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="脚本类型">
          {{ detailScript.scriptType === 'shell' ? 'Shell' : 'Python' }}
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="detailScript.status === 'enabled' ? 'success' : 'info'">
            {{ detailScript.status === 'enabled' ? '启用' : '禁用' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="默认超时">
          {{ detailScript.defaultTimeout ? detailScript.defaultTimeout + ' 秒' : '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="描述" :span="2">
          {{ detailScript.description || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">
          {{ formatTime(detailScript.createdAt) }}
        </el-descriptions-item>
        <el-descriptions-item label="更新时间">
          {{ formatTime(detailScript.updatedAt) }}
        </el-descriptions-item>
      </el-descriptions>

      <!-- 步骤定义 -->
      <div v-if="detailScript?.steps && Object.keys(detailScript.steps).length" style="margin-top: 20px;">
        <h4 style="margin-bottom: 15px;">
          <el-icon style="vertical-align: middle; margin-right: 5px;"><List /></el-icon>
          步骤定义 ({{ Object.keys(detailScript.steps).length }} 个步骤)
        </h4>
        <el-table :data="getStepsArray(detailScript.steps)" size="small" border>
          <el-table-column prop="displayName" label="步骤名称" width="150">
            <template #default="{ row }">
              <div>
                <div style="font-weight: 500;">{{ row.displayName || row.stepName }}</div>
                <div style="color: #909399; font-size: 12px;" v-if="row.displayName">{{ row.stepName }}</div>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="script" label="执行脚本" width="150">
            <template #default="{ row }">
              <code style="color: #409eff;">{{ row.script || '-' }}</code>
            </template>
          </el-table-column>
          <el-table-column label="依赖" min-width="120">
            <template #default="{ row }">
              <template v-if="row.dependsOn">
                <template v-if="Array.isArray(row.dependsOn)">
                  <el-tag v-for="dep in row.dependsOn" :key="dep" size="small" type="info" style="margin-right: 4px;">
                    {{ dep }}
                  </el-tag>
                </template>
                <template v-else-if="typeof row.dependsOn === 'string' && row.dependsOn">
                  <el-tag v-for="dep in row.dependsOn.split(',')" :key="dep" size="small" type="info" style="margin-right: 4px;">
                    {{ dep.trim() }}
                  </el-tag>
                </template>
              </template>
              <span v-else style="color: #909399;">-</span>
            </template>
          </el-table-column>
          <el-table-column label="结果收集" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="row.resultCollector ? 'success' : 'info'" size="small">
                {{ row.resultCollector ? '是' : '否' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="参数" width="80" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.params && (Array.isArray(row.params) ? row.params.length : Object.keys(row.params).length)" size="small" type="warning">
                {{ Array.isArray(row.params) ? row.params.length : Object.keys(row.params).length }}
              </el-tag>
              <span v-else style="color: #909399;">-</span>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- 文件列表 -->
      <div v-if="detailScript?.fileList?.length" style="margin-top: 20px;">
        <h4 style="margin-bottom: 10px;">脚本文件</h4>
        <el-table :data="detailScript.fileList" size="small" border>
          <el-table-column prop="path" label="文件路径" />
          <el-table-column prop="size" label="大小" width="120">
            <template #default="{ row }">
              {{ formatFileSize(row.size) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80">
            <template #default="{ row }">
              <el-button 
                v-if="isTextFile(row.type, row.name)" 
                type="primary" 
                link 
                @click="viewFileContent(detailScript.id, row)"
              >
                查看
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <template #footer>
        <el-button @click="detailDialogVisible = false">关闭</el-button>
        <el-button type="primary" @click="handleEditFromDetail">编辑</el-button>
      </template>
    </el-dialog>

    <!-- 文件内容查看对话框 -->
    <el-dialog
      v-model="fileViewDialogVisible"
      :title="currentViewFileName"
      width="800px"
      destroy-on-close
    >
      <div class="file-viewer">
        <div class="file-path">
          <el-tag type="info" size="small">{{ currentViewFilePath }}</el-tag>
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
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, List } from '@element-plus/icons-vue'
import { scriptApi, type Script } from '@/api/script'
import { formatTime } from '@/utils/format'
import { TEST_CATEGORIES, getCategoryLabel } from '@/config/categories'
import axios from 'axios'
// 版本功能暂时隐藏
// import VersionManager from './VersionManager.vue'
import ParseRuleConfig from './ParseRuleConfig.vue'

const router = useRouter()
const loading = ref(false)
const tableData = ref<Script[]>([])
const total = ref(0)

const queryParams = reactive({
  page: 1,
  size: 20,
  name: '',
  testCategory: '',
})

// 版本功能暂时隐藏
// const versionDialogVisible = ref(false)
const currentScript = ref<Script | null>(null)

// 解析规则配置
const parseDialogVisible = ref(false)

// 脚本详情
const detailDialogVisible = ref(false)
const detailScript = ref<Script | null>(null)

// 格式化文件大小
function formatFileSize(bytes: number) {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

// 将 steps 对象转为数组
function getStepsArray(steps: Record<string, any>) {
  return Object.entries(steps).map(([stepName, config]) => ({
    stepName,
    ...config
  }))
}

// 文件查看相关
const fileViewDialogVisible = ref(false)
const currentViewFileName = ref('')
const currentViewFilePath = ref('')
const fileContent = ref('')

// 判断是否为文本文件
function isTextFile(type: string, name: string): boolean {
  const textExtensions = ['sh', 'py', 'txt', 'json', 'xml', 'yaml', 'yml', 'md', 'conf', 'cfg', 'ini', 'log', 'csv']
  const textTypes = ['sh', 'py', 'txt', 'json', 'xml', 'yaml', 'yml', 'md', 'conf', 'cfg', 'ini', 'log', 'csv']
  
  if (textTypes.includes(type)) return true
  
  const ext = name.split('.').pop()?.toLowerCase() || ''
  return textExtensions.includes(ext)
}

// 查看文件内容
async function viewFileContent(scriptId: number, file: any) {
  currentViewFileName.value = file.name || file.path
  currentViewFilePath.value = file.path
  fileContent.value = '加载中...'
  fileViewDialogVisible.value = true
  
  try {
    // 不编码路径中的 /，只编码特殊字符（保留 / 和字母数字）
    // Spring MVC 的 {*filePath} 需要路径包含原始的 / 字符
    const encodedPath = file.path.split('/').map(segment => encodeURIComponent(segment)).join('/')
    const res = await axios.get(`/api/v1/scripts/${scriptId}/files/${encodedPath}`)
    if (res.data.code === 0) {
      fileContent.value = res.data.data.content || ''
    } else {
      fileContent.value = '加载失败: ' + (res.data.message || '未知错误')
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

async function fetchData() {
  loading.value = true
  try {
    const res = await scriptApi.list(queryParams)
    if (res.code === 0) {
      tableData.value = res.data.items
      total.value = res.data.total
    }
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  queryParams.name = ''
  queryParams.testCategory = ''
  queryParams.page = 1
  fetchData()
}

function getCategoryText(category: string) {
  return getCategoryLabel(category)
}

// 格式化脚本名称
function formatScriptName(name: string) {
  if (!name) return '-'
  
  // 如果名称包含下划线和日期格式（自动生成的名称），提取有意义的部分
  // 例如: "测试脚本_49a7318a-2026-03-10" -> "测试脚本 (2026-03-10)"
  const match = name.match(/^(.+?)_[a-f0-9]+-(\d{4}-\d{2}-\d{2})$/)
  if (match) {
    return `${match[1]} (${match[2]})`
  }
  
  return name
}

function handleRun(row: Script) {
  router.push({ path: '/tasks/create', query: { scriptId: row.id } })
}

function handleEdit(row: Script) {
  router.push(`/scripts/edit/${row.id}`)
}

// 查看脚本详情
async function handleDetail(row: Script) {
  try {
    const res = await scriptApi.get(row.id)
    if (res.code === 0) {
      detailScript.value = res.data
      detailDialogVisible.value = true
    }
  } catch (e: any) {
    ElMessage.error(e.message || '获取脚本详情失败')
  }
}

// 从详情弹窗跳转编辑
function handleEditFromDetail() {
  if (detailScript.value) {
    detailDialogVisible.value = false
    router.push(`/scripts/edit/${detailScript.value.id}`)
  }
}

// 版本功能暂时隐藏
// function handleVersions(row: Script) {
//   currentScript.value = row
//   versionDialogVisible.value = true
// }

function handleParseRules(row: Script) {
  currentScript.value = row
  parseDialogVisible.value = true
}

async function handleDelete(row: Script) {
  await ElMessageBox.confirm('确定要删除该脚本吗？', '提示', { type: 'warning' })
  const res = await scriptApi.delete(row.id)
  if (res.code === 0) {
    ElMessage.success('删除成功')
    fetchData()
  }
}

async function handleExport(row: Script) {
  try {
    ElMessage.info('正在导出脚本...')
    
    // 直接使用 axios，避免响应拦截器处理 blob
    const response = await axios.get(`/api/v1/scripts/${row.id}/export`, {
      params: { format: 'zip' },
      responseType: 'blob'
    })
    
    // 创建下载链接
    const blob = new Blob([response.data], { type: 'application/zip' })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${row.name}.zip`
    
    // 添加到 DOM 并触发点击
    document.body.appendChild(link)
    link.click()
    
    // 清理
    setTimeout(() => {
      document.body.removeChild(link)
      window.URL.revokeObjectURL(url)
    }, 100)
    
    ElMessage.success('导出成功')
  } catch (e: any) {
    console.error('导出失败:', e)
    ElMessage.error(e.message || '导出失败')
  }
}

onMounted(() => {
  fetchData()
})
</script>

<style lang="scss" scoped>
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.script-name {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.name-text {
  font-weight: 500;
}

.remark-text {
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

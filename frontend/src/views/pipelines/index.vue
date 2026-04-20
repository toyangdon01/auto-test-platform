<template>
  <div class="page-card">
    <div class="page-header">
      <h3 class="page-title">任务编排</h3>
      <div class="header-actions">
        <el-button type="info" @click="downloadTemplate">
          <el-icon><Document /></el-icon>
          导出模板
        </el-button>
        <el-button type="success" @click="showImportDialog">
          <el-icon><Download /></el-icon>
          导入 YAML
        </el-button>
        <el-button type="primary" @click="$router.push('/pipelines/create')">
          <el-icon><Plus /></el-icon>
          新建编排
        </el-button>
      </div>
    </div>

    <el-table :data="pipelines" v-loading="loading" stripe>
      <el-table-column prop="name" label="名称" min-width="150" />
      <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
      <el-table-column prop="maxParallel" label="最大并行" width="100">
        <template #default="{ row }">
          <el-tag type="primary">{{ row.maxParallel || 5 }} 并行</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="enabled" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'">
            {{ row.enabled ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="180">
        <template #default="{ row }">
          {{ formatTime(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="300">
        <template #default="{ row }">
          <el-button type="primary" size="small" @click="handleExecute(row)">执行</el-button>
          <el-button type="default" size="small" @click="handleRuns(row)">执行记录</el-button>
          <el-button type="default" size="small" @click="handleEdit(row)">编辑</el-button>
          <el-button type="warning" size="small" @click="handleExport(row)">导出</el-button>
          <el-button type="danger" size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="page"
      v-model:page-size="size"
      :total="total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      @size-change="loadPipelines"
      @current-change="loadPipelines"
      style="margin-top: 16px; justify-content: flex-end"
    />
    
    <!-- YAML 导入对话框 -->
    <PipelineImportDialog ref="importDialog" @refresh="loadPipelines" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Download, Document } from '@element-plus/icons-vue'
import { listPipelines, deletePipeline, executePipeline, exportPipeline } from '@/api/pipeline'
import type { Pipeline } from '@/api/pipeline'
import PipelineImportDialog from '@/components/PipelineImportDialog.vue'

const router = useRouter()
const loading = ref(false)
const pipelines = ref<Pipeline[]>([])
const page = ref(1)
const size = ref(10)
const total = ref(0)

const importDialogVisible = ref(false)
const importDialog = ref()

function formatTime(time: string) {
  if (!time) return '-'
  return time.replace('T', ' ').substring(0, 16)
}

async function loadPipelines() {
  loading.value = true
  try {
    const res = await listPipelines({ page: page.value, size: size.value })
    if (res.code === 0 && res.data) {
      pipelines.value = res.data.records || []
      total.value = res.data.total || 0
    }
  } catch (e) {
    console.error('加载编排列表失败:', e)
  } finally {
    loading.value = false
  }
}

async function handleExecute(row: Pipeline) {
  try {
    const res = await executePipeline(row.id)
    if (res.code === 0 && res.data) {
      ElMessage.success('开始执行')
      router.push(`/pipelines/runs/${res.data.id}`)
    }
  } catch (e) {
    ElMessage.error('执行失败')
  }
}

function handleEdit(row: Pipeline) {
  router.push(`/pipelines/edit/${row.id}`)
}

function handleRuns(row: Pipeline) {
  router.push(`/pipelines/runs?pipelineId=${row.id}`)
}

async function handleExport(row: Pipeline) {
  try {
    const res = await exportPipeline(row.id)
    if (res.code === 0 && res.data) {
      // 创建下载
      const blob = new Blob([res.data], { type: 'text/yaml;charset=utf-8' })
      const url = window.URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `${row.name}.yaml`
      document.body.appendChild(link)
      link.click()
      setTimeout(() => {
        document.body.removeChild(link)
        window.URL.revokeObjectURL(url)
      }, 100)
      ElMessage.success('导出成功')
    }
  } catch (e) {
    ElMessage.error('导出失败')
  }
}

async function handleDelete(row: Pipeline) {
  try {
    await ElMessageBox.confirm('确定删除该编排吗？', '提示', { type: 'warning' })
    const res = await deletePipeline(row.id)
    if (res.code === 0) {
      ElMessage.success('删除成功')
      loadPipelines()
    }
  } catch (e) {
    // 取消删除
  }
}

function showImportDialog() {
  importDialog.value?.open()
}

function downloadTemplate() {
  const template = `# Pipeline YAML 配置模板
# 用于通过 YAML 文件创建任务编排

# ==================== 基本信息 ====================
name: 示例流水线
description: 这是一个示例流水线
maxParallel: 3                    # 最大并行数，默认 5

# ==================== 服务器定义（可选） ====================
# - 定义新服务器，平台不存在时自动创建
# - 已存在（按 name 匹配）则更新
servers:
  - name: test-server-1
    host: 192.168.1.100
    port: 22                      # 默认 22
    username: root
    authType: password            # password | ssh_key，默认 password
    authSecret: your-password     # 密码或私钥内容
    tags: [test]                  # 可选，标签列表
    remark: 测试服务器             # 可选，备注

  - name: test-server-2
    host: 192.168.1.101
    username: root
    authSecret: your-password

# ==================== 任务列表 ====================
tasks:
  - name: 环境准备
    script: env-check             # 脚本名称（必须存在于平台中）
    timeout: 300                  # 超时时间（秒）
    stepServerMapping:            # 步骤服务器映射
      check: [test-server-1]

  - name: 执行测试
    script: performance-test
    dependsOn: [环境准备]         # 依赖任务（可选）
    timeout: 1800
    stepServerMapping:
      prepare: [test-server-1]
      run_test: [test-server-1, test-server-2]
      cleanup: [test-server-1]
    sharedParams:                 # 共享参数（可选）
      RUNTIME: 60
      SIZE: 5G
    stepParams:                   # 步骤参数（可选）
      run_test:
        TEST_MODE: randrw

  - name: 结果收集
    script: result-summary
    dependsOn: [执行测试]
    timeout: 600
    stepServerMapping:
      collect: [test-server-1]
`

  const blob = new Blob([template], { type: 'text/yaml;charset=utf-8' })
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = 'pipeline-template.yaml'
  document.body.appendChild(link)
  link.click()
  setTimeout(() => {
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
  }, 100)
  ElMessage.success('模板下载成功')
}

onMounted(() => {
  loadPipelines()
})
</script>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  gap: 12px;
}
</style>

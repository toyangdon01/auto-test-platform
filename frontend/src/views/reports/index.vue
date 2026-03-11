<template>
  <div class="page-card">
    <div class="page-header">
      <h3 class="page-title">报告中心</h3>
      <el-button type="primary" @click="showGenerateDialog = true">
        <el-icon><Plus /></el-icon>
        生成报告
      </el-button>
    </div>

    <!-- 生成报告对话框 -->
    <el-dialog v-model="showGenerateDialog" title="生成报告" width="500px">
      <el-form :model="generateForm" label-width="100px">
        <el-form-item label="任务" required>
          <el-select v-model="generateForm.taskId" placeholder="选择任务" style="width: 100%">
            <el-option
              v-for="task in tasks"
              :key="task.id"
              :label="task.name"
              :value="task.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="报告标题">
          <el-input v-model="generateForm.title" placeholder="留空自动生成" />
        </el-form-item>
        <el-form-item label="文件格式">
          <el-radio-group v-model="generateForm.fileFormat">
            <el-radio value="html">HTML</el-radio>
            <el-radio value="pdf">PDF</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="包含内容">
          <el-checkbox v-model="generateForm.include.summary">执行概览</el-checkbox>
          <el-checkbox v-model="generateForm.include.metrics">指标统计</el-checkbox>
          <el-checkbox v-model="generateForm.include.comparison">对比分析</el-checkbox>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showGenerateDialog = false">取消</el-button>
        <el-button type="primary" @click="handleGenerate" :loading="generating">生成</el-button>
      </template>
    </el-dialog>

    <!-- 报告列表 -->
    <el-table :data="reports" stripe v-loading="loading">
      <el-table-column prop="title" label="报告标题" min-width="250">
        <template #default="{ row }">
          <div class="report-title">
            <el-icon><Document /></el-icon>
            <span>{{ row.title }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="conclusion" label="结论" width="100">
        <template #default="{ row }">
          <el-tag :type="conclusionTagType(row.conclusion)">
            {{ conclusionText(row.conclusion) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="fileFormat" label="格式" width="80">
        <template #default="{ row }">
          <el-tag size="small" type="info">{{ row.fileFormat?.toUpperCase() }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="summary" label="摘要" min-width="200" show-overflow-tooltip />
      <el-table-column prop="createdAt" label="生成时间" width="160">
        <template #default="{ row }">
          {{ formatTime(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click="handleView(row)">查看</el-button>
          <el-button type="primary" link @click="handleDownload(row)">下载</el-button>
          <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrapper">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @size-change="fetchReports"
        @current-change="fetchReports"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Document } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { formatTime } from '@/utils/format'
import axios from 'axios'

interface Task {
  id: number
  name: string
}

interface Report {
  id: number
  taskId: number
  title: string
  summary: string
  conclusion: string
  fileFormat: string
  createdAt: string
}

const router = useRouter()
const loading = ref(false)
const reports = ref<Report[]>([])
const tasks = ref<Task[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)

const showGenerateDialog = ref(false)
const generating = ref(false)
const generateForm = reactive({
  taskId: null as number | null,
  title: '',
  fileFormat: 'html',
  include: {
    summary: true,
    metrics: true,
    comparison: true
  }
})

const conclusionTagType = (conclusion: string) => {
  const map: Record<string, string> = {
    pass: 'success',
    warning: 'warning',
    fail: 'danger'
  }
  return map[conclusion] || 'info'
}

const conclusionText = (conclusion: string) => {
  const map: Record<string, string> = {
    pass: '通过',
    warning: '警告',
    fail: '失败'
  }
  return map[conclusion] || conclusion
}

const fetchReports = async () => {
  loading.value = true
  try {
    const res = await request.get<{ list: Report[]; total: number }>('/reports', {
      params: { page: page.value, pageSize: pageSize.value }
    })
    if (res.code === 0) {
      reports.value = res.data?.list || res.data?.items || []
      total.value = res.data?.total || 0
    }
  } catch (e) {
    console.error('获取报告列表失败', e)
  } finally {
    loading.value = false
  }
}

const fetchTasks = async () => {
  try {
    const res = await request.get<{ items: Task[] }>('/tasks', {
      params: { pageSize: 100 }
    })
    if (res.code === 0) {
      tasks.value = res.data?.items || []
    }
  } catch (e) {
    console.error('获取任务列表失败', e)
  }
}

const handleGenerate = async () => {
  if (!generateForm.taskId) {
    ElMessage.warning('请选择任务')
    return
  }

  generating.value = true
  try {
    const res = await request.post<Report>('/reports/generate', {
      taskIds: [generateForm.taskId],
      title: generateForm.title || undefined,
      fileFormat: generateForm.fileFormat,
      include: generateForm.include
    })
    if (res.code === 0) {
      ElMessage.success('报告生成成功')
      showGenerateDialog.value = false
      generateForm.taskId = null
      generateForm.title = ''
      fetchReports()
    } else {
      ElMessage.error(res.message || '生成失败')
    }
  } catch (e: any) {
    ElMessage.error(e.message || '生成失败')
  } finally {
    generating.value = false
  }
}

const handleView = (row: Report) => {
  router.push(`/reports/detail/${row.id}`)
}

const handleDownload = async (row: Report) => {
  try {
    ElMessage.info('正在下载报告...')
    
    const format = row.fileFormat || 'html'
    const response = await axios.get(`/api/v1/reports/${row.id}/export`, {
      params: { format },
      responseType: 'blob'
    })
    
    const blob = new Blob([response.data], { type: format === 'pdf' ? 'application/pdf' : 'text/html' })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `${row.title}.${format}`
    document.body.appendChild(link)
    link.click()
    
    setTimeout(() => {
      document.body.removeChild(link)
      window.URL.revokeObjectURL(url)
    }, 100)
    
    ElMessage.success('下载成功')
  } catch (e: any) {
    console.error('下载失败:', e)
    ElMessage.error(e.message || '下载失败')
  }
}

const handleDelete = async (row: Report) => {
  try {
    await ElMessageBox.confirm('确定删除该报告吗？', '提示', { type: 'warning' })
    const res = await request.delete(`/reports/${row.id}`)
    if (res.code === 0) {
      ElMessage.success('删除成功')
      fetchReports()
    }
  } catch (e) {
    // 取消删除
  }
}

onMounted(() => {
  fetchReports()
  fetchTasks()
})
</script>

<style scoped>
.report-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>

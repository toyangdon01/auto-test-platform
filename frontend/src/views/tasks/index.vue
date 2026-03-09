<template>
  <div class="page-card">
    <div class="page-header">
      <h3 class="page-title">任务列表</h3>
      <el-button type="primary" @click="$router.push('/tasks/create')">
        <el-icon><Plus /></el-icon>
        创建任务
      </el-button>
    </div>

    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-input
        v-model="queryParams.name"
        placeholder="搜索任务名称"
        clearable
        style="width: 240px"
        @keyup.enter="fetchData"
      />
      
      <el-select v-model="queryParams.status" placeholder="状态" clearable style="width: 120px">
        <el-option label="待执行" value="pending" />
        <el-option label="执行中" value="running" />
        <el-option label="已完成" value="completed" />
        <el-option label="失败" value="failed" />
        <el-option label="已取消" value="cancelled" />
      </el-select>

      <el-button type="primary" @click="fetchData">查询</el-button>
      <el-button @click="resetQuery">重置</el-button>
    </div>

    <!-- 数据表格 -->
    <el-table v-loading="loading" :data="tableData" stripe>
      <el-table-column prop="name" label="任务名称" min-width="200">
        <template #default="{ row }">
          <el-link type="primary" @click="$router.push(`/tasks/detail/${row.id}`)">
            {{ row.name }}
          </el-link>
        </template>
      </el-table-column>
      <el-table-column prop="executionMode" label="执行模式" width="100">
        <template #default="{ row }">
          <el-tag>{{ row.executionMode === 'immediate' ? '立即执行' : '定时执行' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="getStatusType(row.status)">{{ getStatusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="progress" label="进度" width="150">
        <template #default="{ row }">
          <el-progress
            v-if="row.status === 'running'"
            :percentage="row.progress || 0"
            :stroke-width="8"
          />
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="startedAt" label="开始时间" width="160">
        <template #default="{ row }">
          {{ row.startedAt || '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="finishedAt" label="结束时间" width="160">
        <template #default="{ row }">
          {{ row.finishedAt || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <template v-if="row.status === 'pending'">
            <el-button type="primary" link @click="handleExecute(row)">执行</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
          <template v-else-if="row.status === 'running'">
            <el-button type="warning" link @click="handleCancel(row)">取消</el-button>
          </template>
          <template v-else-if="row.status === 'failed'">
            <el-button type="primary" link @click="handleRetry(row)">重试</el-button>
          </template>
          <el-button type="primary" link @click="handleDetail(row)">详情</el-button>
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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { taskApi, type Task } from '@/api/script'

const router = useRouter()
const loading = ref(false)
const tableData = ref<Task[]>([])
const total = ref(0)

const queryParams = reactive({
  page: 1,
  size: 20,
  name: '',
  status: '',
})

async function fetchData() {
  loading.value = true
  try {
    const res = await taskApi.list(queryParams)
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
  queryParams.status = ''
  queryParams.page = 1
  fetchData()
}

function getStatusType(status: string) {
  const types: Record<string, string> = {
    pending: 'info',
    running: 'warning',
    completed: 'success',
    failed: 'danger',
    cancelled: 'info',
  }
  return types[status] || 'info'
}

function getStatusText(status: string) {
  const texts: Record<string, string> = {
    pending: '待执行',
    running: '执行中',
    completed: '已完成',
    failed: '失败',
    cancelled: '已取消',
  }
  return texts[status] || status
}

async function handleExecute(row: Task) {
  await ElMessageBox.confirm('确定要执行该任务吗？', '提示')
  const res = await taskApi.execute(row.id)
  if (res.code === 0) {
    ElMessage.success('任务已开始执行')
    fetchData()
  }
}

async function handleCancel(row: Task) {
  await ElMessageBox.confirm('确定要取消该任务吗？', '提示')
  const res = await taskApi.cancel(row.id)
  if (res.code === 0) {
    ElMessage.success('任务已取消')
    fetchData()
  }
}

async function handleRetry(row: Task) {
  const res = await taskApi.retry(row.id)
  if (res.code === 0) {
    ElMessage.success('任务已重新排队')
    fetchData()
  }
}

function handleDetail(row: Task) {
  router.push(`/tasks/detail/${row.id}`)
}

async function handleDelete(row: Task) {
  await ElMessageBox.confirm('确定要删除该任务吗？', '提示', { type: 'warning' })
  const res = await taskApi.delete(row.id)
  if (res.code === 0) {
    ElMessage.success('删除成功')
    fetchData()
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
</style>

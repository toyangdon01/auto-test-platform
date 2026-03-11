<template>
  <div class="page-card">
    <div class="page-header">
      <h3 class="page-title">定时任务</h3>
      <el-button type="primary" @click="handleCreate">
        <el-icon><Plus /></el-icon>
        新建定时任务
      </el-button>
    </div>

    <!-- 数据表格 -->
    <el-table v-loading="loading" :data="tableData" stripe>
      <el-table-column prop="name" label="任务名称" min-width="150" />
      <el-table-column prop="taskId" label="测试任务" width="150">
        <template #default="{ row }">
          {{ getTaskName(row.taskId) }}
        </template>
      </el-table-column>
      <el-table-column prop="scheduleType" label="调度类型" width="100">
        <template #default="{ row }">
          <el-tag size="small">{{ getScheduleTypeName(row.scheduleType) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="cronExpression" label="Cron表达式" width="140" />
      <el-table-column prop="nextRunTime" label="下次执行" width="160">
        <template #default="{ row }">
          {{ formatTime(row.nextRunTime) }}
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="getStatusType(row.status)" size="small">
            {{ getStatusName(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="runCount" label="执行次数" width="80" />
      <el-table-column prop="failCount" label="失败次数" width="80" />
      <el-table-column label="操作" width="250" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status !== 'enabled'"
            type="success"
            link
            @click="handleEnable(row)"
          >启用</el-button>
          <el-button
            v-else
            type="warning"
            link
            @click="handleDisable(row)"
          >禁用</el-button>
          <el-button type="primary" link @click="handleExecuteNow(row)">立即执行</el-button>
          <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
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

    <!-- 编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editId ? '编辑定时任务' : '新建定时任务'"
      width="600px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="任务名称" prop="name">
          <el-input v-model="form.name" placeholder="输入任务名称" />
        </el-form-item>
        <el-form-item label="测试任务" prop="taskId">
          <el-select v-model="form.taskId" placeholder="选择测试任务" style="width: 100%">
            <el-option v-for="t in tasks" :key="t.id" :label="t.name" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="调度类型" prop="scheduleType">
          <el-radio-group v-model="form.scheduleType">
            <el-radio value="cron">Cron表达式</el-radio>
            <el-radio value="interval">固定间隔</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.scheduleType === 'cron'" label="Cron表达式" prop="cronExpression">
          <el-input v-model="form.cronExpression" placeholder="如: 0 0 2 * * ? (每天凌晨2点)" />
          <div class="cron-help">
            <span>常用: </span>
            <el-link @click="form.cronExpression = '0 0 2 * * ?'">每天凌晨2点</el-link>
            <el-link @click="form.cronExpression = '0 0 */6 * * ?'">每6小时</el-link>
            <el-link @click="form.cronExpression = '0 30 9 * * ?'">每天9:30</el-link>
          </div>
        </el-form-item>
        <el-form-item v-else label="执行间隔" prop="intervalMinutes">
          <el-input-number v-model="form.intervalMinutes" :min="1" :max="10080" />
          <span style="margin-left: 8px">分钟</span>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="任务说明" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { formatTime } from '@/utils/format'

interface ScheduledTask {
  id: number
  name: string
  taskId: number
  cronExpression: string
  scheduleType: string
  intervalMinutes: number
  nextRunTime: string
  status: string
  runCount: number
  failCount: number
  remark: string
}

const loading = ref(false)
const tableData = ref<ScheduledTask[]>([])
const total = ref(0)
const tasks = ref<any[]>([])

const queryParams = reactive({
  page: 1,
  size: 20,
  status: ''
})

const dialogVisible = ref(false)
const editId = ref<number | null>(null)
const saving = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  name: '',
  taskId: null as number | null,
  scheduleType: 'cron',
  cronExpression: '',
  intervalMinutes: 60,
  remark: ''
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  taskId: [{ required: true, message: '请选择测试任务', trigger: 'change' }],
  scheduleType: [{ required: true, message: '请选择调度类型', trigger: 'change' }]
}

onMounted(() => {
  fetchTasks()
  fetchData()
})

async function fetchTasks() {
  try {
    const res = await request.get('/tasks', { params: { size: 100 } })
    tasks.value = res.data.items || []
  } catch (error) {
    console.error('获取任务列表失败:', error)
  }
}

async function fetchData() {
  loading.value = true
  try {
    const res = await request.get('/scheduled-tasks', { params: queryParams })
    tableData.value = res.data.items
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function getTaskName(taskId: number) {
  const task = tasks.value.find(t => t.id === taskId)
  return task?.name || taskId
}

function getScheduleTypeName(type: string) {
  const map: Record<string, string> = { cron: 'Cron', interval: '固定间隔', once: '一次性' }
  return map[type] || type
}

function getStatusType(status: string) {
  const map: Record<string, string> = { enabled: 'success', disabled: 'info', running: 'warning' }
  return map[status] || 'info'
}

function getStatusName(status: string) {
  const map: Record<string, string> = { enabled: '启用', disabled: '禁用', running: '运行中' }
  return map[status] || status
}

function handleCreate() {
  editId.value = null
  form.name = ''
  form.taskId = null
  form.scheduleType = 'cron'
  form.cronExpression = ''
  form.intervalMinutes = 60
  form.remark = ''
  dialogVisible.value = true
}

function handleEdit(row: ScheduledTask) {
  editId.value = row.id
  form.name = row.name
  form.taskId = row.taskId
  form.scheduleType = row.scheduleType || 'cron'
  form.cronExpression = row.cronExpression || ''
  form.intervalMinutes = row.intervalMinutes || 60
  form.remark = row.remark || ''
  dialogVisible.value = true
}

async function handleSave() {
  const valid = await formRef.value?.validate()
  if (!valid) return

  saving.value = true
  try {
    if (editId.value) {
      await request.put(`/scheduled-tasks/${editId.value}`, form)
      ElMessage.success('更新成功')
    } else {
      await request.post('/scheduled-tasks', form)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    fetchData()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function handleDelete(row: ScheduledTask) {
  await ElMessageBox.confirm('确定删除该定时任务？', '提示', { type: 'warning' })
  await request.delete(`/scheduled-tasks/${row.id}`)
  ElMessage.success('删除成功')
  fetchData()
}

async function handleEnable(row: ScheduledTask) {
  await request.post(`/scheduled-tasks/${row.id}/enable`)
  ElMessage.success('已启用')
  fetchData()
}

async function handleDisable(row: ScheduledTask) {
  await request.post(`/scheduled-tasks/${row.id}/disable`)
  ElMessage.success('已禁用')
  fetchData()
}

async function handleExecuteNow(row: ScheduledTask) {
  await ElMessageBox.confirm('确定立即执行该任务？', '提示')
  await request.post(`/scheduled-tasks/${row.id}/execute`)
  ElMessage.success('任务已开始执行')
  fetchData()
}
</script>

<style scoped>
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.cron-help {
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.cron-help .el-link {
  margin-left: 8px;
}
</style>

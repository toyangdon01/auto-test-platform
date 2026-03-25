<template>
  <div class="page-card">
    <div class="page-header">
      <h3 class="page-title">服务器列表</h3>
      <el-button type="primary" @click="handleAdd">
        <el-icon><Plus /></el-icon>
        添加服务器
      </el-button>
    </div>

    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-input
        v-model="queryParams.name"
        placeholder="搜索服务器名称/地址"
        clearable
        style="width: 240px"
        @clear="fetchData"
        @keyup.enter="fetchData"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      
      <el-select v-model="queryParams.status" placeholder="状态" clearable style="width: 120px">
        <el-option label="在线" value="online" />
        <el-option label="离线" value="offline" />
        <el-option label="异常" value="error" />
      </el-select>

      <el-select v-model="queryParams.groupId" placeholder="分组" clearable style="width: 140px">
        <el-option
          v-for="group in groups"
          :key="group.id"
          :label="group.name"
          :value="group.id"
        />
      </el-select>

      <el-button type="primary" @click="fetchData">查询</el-button>
      <el-button @click="resetQuery">重置</el-button>
    </div>

    <!-- 数据表格 -->
    <el-table v-loading="loading" :data="tableData" stripe>
      <el-table-column prop="name" label="名称" min-width="150" />
      <el-table-column prop="host" label="地址" min-width="180">
        <template #default="{ row }">
          {{ row.host }}:{{ row.port }}
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="getStatusType(row.status)">{{ getStatusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="cpuCores" label="CPU" width="100">
        <template #default="{ row }">
          <span v-if="row.cpuCores">{{ row.cpuCores }} 核</span>
          <span v-else class="text-secondary">-</span>
        </template>
      </el-table-column>
      <el-table-column prop="cpuModel" label="CPU 型号" min-width="200">
        <template #default="{ row }">
          <span v-if="row.cpuModel" :title="row.cpuModel">{{ row.cpuModel }}</span>
          <span v-else class="text-secondary">-</span>
        </template>
      </el-table-column>
      <el-table-column prop="cpuArch" label="架构" width="80">
        <template #default="{ row }">
          <span v-if="row.cpuArch">{{ row.cpuArch }}</span>
          <span v-else class="text-secondary">-</span>
        </template>
      </el-table-column>
      <el-table-column prop="memorySize" label="内存" width="100">
        <template #default="{ row }">
          <span v-if="row.memorySize">{{ row.memorySize }}</span>
          <span v-else class="text-secondary">-</span>
        </template>
      </el-table-column>
      <el-table-column prop="groupId" label="所属分组" width="120">
        <template #default="{ row }">
          <el-tag v-if="row.groupId" size="small" effect="plain">
            {{ getGroupName(row.groupId) }}
          </el-tag>
          <span v-else class="text-secondary">-</span>
        </template>
      </el-table-column>
      <el-table-column prop="lastCheckAt" label="最后检测" width="160">
        <template #default="{ row }">
          {{ formatTime(row.lastCheckAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click="handleTerminal(row)">
            <el-icon><Monitor /></el-icon>
            终端
          </el-button>
          <el-button type="primary" link :loading="row.testing" @click="handleTest(row)">测试</el-button>
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
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="fetchData"
        @current-change="fetchData"
      />
    </div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="600px"
      destroy-on-close
    >
      <el-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="100px"
      >
        <el-form-item prop="name">
          <template #label>
            服务器名称
            <el-tooltip content="服务器的显示名称，便于识别和管理" placement="top">
              <el-icon class="field-tip-icon"><QuestionFilled /></el-icon>
            </el-tooltip>
          </template>
          <el-input v-model="formData.name" placeholder="请输入服务器名称" />
        </el-form-item>
        <el-form-item prop="host">
          <template #label>
            主机地址
            <el-tooltip content="服务器的 IP 地址或主机名，用于 SSH 连接" placement="top">
              <el-icon class="field-tip-icon"><QuestionFilled /></el-icon>
            </el-tooltip>
          </template>
          <el-input v-model="formData.host" placeholder="IP 或域名" />
        </el-form-item>
        <el-form-item label="SSH 端口" prop="port">
          <el-input-number v-model="formData.port" :min="1" :max="65535" />
        </el-form-item>
        <el-form-item label="用户名" prop="username">
          <el-input v-model="formData.username" placeholder="SSH 用户名" />
        </el-form-item>
        <el-form-item prop="authType">
          <template #label>
            认证方式
            <el-tooltip content="密码认证：使用用户名密码登录；密钥认证：使用 SSH 私钥登录" placement="top">
              <el-icon class="field-tip-icon"><QuestionFilled /></el-icon>
            </el-tooltip>
          </template>
          <el-select v-model="formData.authType" style="width: 100%">
            <el-option label="密码" value="password" />
            <el-option label="密钥" value="key" />
          </el-select>
        </el-form-item>
        <el-form-item prop="authSecret">
          <template #label>
            认证密钥
            <el-tooltip content="认证凭据：密码认证时填写密码，密钥认证时填写私钥内容" placement="top">
              <el-icon class="field-tip-icon"><QuestionFilled /></el-icon>
            </el-tooltip>
          </template>
          <el-input
            v-model="formData.authSecret"
            :type="formData.authType === 'password' ? 'password' : 'textarea'"
            :rows="4"
            placeholder="密码或私钥内容"
          />
        </el-form-item>
        <el-form-item label="所属分组">
          <el-select v-model="formData.groupId" placeholder="选择分组" clearable style="width: 100%">
            <el-option
              v-for="group in groups"
              :key="group.id"
              :label="group.name"
              :value="group.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="标签">
          <el-select
            v-model="formData.tags"
            multiple
            filterable
            allow-create
            default-first-option
            placeholder="输入标签"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="formData.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Monitor, QuestionFilled } from '@element-plus/icons-vue'
import { serverApi, serverGroupApi, type Server, type ServerGroup, type ServerCreateParams } from '@/api/server'
import { formatTime } from '@/utils/format'

const router = useRouter()

// 扩展 Server 类型，添加 testing 状态
interface ServerWithState extends Server {
  testing?: boolean
}

// 查询参数
const queryParams = reactive({
  page: 1,
  size: 20,
  name: '',
  status: '',
  groupId: undefined as number | undefined,
})

// 数据
const loading = ref(false)
const tableData = ref<ServerWithState[]>([])
const total = ref(0)
const groups = ref<ServerGroup[]>([])

// 弹窗
const dialogVisible = ref(false)
const dialogTitle = ref('添加服务器')
const submitting = ref(false)
const formRef = ref()
const editId = ref<number | null>(null)

// 表单数据
const formData = reactive<ServerCreateParams>({
  name: '',
  host: '',
  port: 22,
  username: '',
  authType: 'password',
  authSecret: '',
  groupId: undefined,
  tags: [],
  remark: '',
})

// 表单校验规则
const formRules = {
  name: [{ required: true, message: '请输入服务器名称', trigger: 'blur' }],
  host: [{ required: true, message: '请输入主机地址', trigger: 'blur' }],
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  authType: [{ required: true, message: '请选择认证方式', trigger: 'change' }],
  authSecret: [{ required: true, message: '请输入认证密钥', trigger: 'blur' }],
}

// 获取数据
async function fetchData() {
  loading.value = true
  try {
    const res = await serverApi.list(queryParams)
    if (res.code === 0) {
      tableData.value = res.data.items
      total.value = res.data.total
    }
  } finally {
    loading.value = false
  }
}

// 获取分组列表
async function fetchGroups() {
  const res = await serverGroupApi.list()
  if (res.code === 0) {
    groups.value = res.data
  }
}

// 重置查询
function resetQuery() {
  queryParams.name = ''
  queryParams.status = ''
  queryParams.groupId = undefined
  queryParams.page = 1
  fetchData()
}

// 获取状态类型
function getStatusType(status: string) {
  const types: Record<string, string> = {
    online: 'success',
    offline: 'info',
    error: 'danger',
  }
  return types[status] || 'info'
}

// 获取状态文本
function getStatusText(status: string) {
  const texts: Record<string, string> = {
    online: '在线',
    offline: '离线',
    error: '异常',
  }
  return texts[status] || status
}

// 获取分组名称
function getGroupName(groupId: number): string {
  const group = groups.value.find(g => g.id === groupId)
  return group ? group.name : '未知分组'
}

// 新增
function handleAdd() {
  dialogTitle.value = '添加服务器'
  editId.value = null
  Object.assign(formData, {
    name: '',
    host: '',
    port: 22,
    username: '',
    authType: 'password',
    authSecret: '',
    groupId: undefined,
    tags: [],
    remark: '',
  })
  dialogVisible.value = true
}

// 打开终端
function handleTerminal(row: Server) {
  router.push(`/servers/terminal/${row.id}`)
}

// 编辑
function handleEdit(row: Server) {
  dialogTitle.value = '编辑服务器'
  editId.value = row.id
  Object.assign(formData, {
    name: row.name,
    host: row.host,
    port: row.port,
    username: row.username,
    authType: row.authType,
    authSecret: '',
    groupId: row.groupId,
    tags: row.tags || [],
    remark: row.remark || '',
  })
  dialogVisible.value = true
}

// 测试连接
async function handleTest(row: ServerWithState) {
  row.testing = true
  try {
    const res = await serverApi.testConnection(row.id)
    if (res.code === 0 && res.data.connected) {
      ElMessage.success(`连接成功 (${res.data.responseTime || 0}ms)`)
      // 刷新服务器信息（获取 CPU、内存等）
      const refreshRes = await serverApi.refresh(row.id)
      if (refreshRes.code === 0) {
        // 更新服务器状态和信息
        Object.assign(row, refreshRes.data)
      } else {
        row.status = 'online'
        row.lastCheckAt = new Date().toISOString()
      }
    } else {
      ElMessage.error(res.data?.error || '连接失败')
      row.status = 'error'
    }
  } catch (error: any) {
    ElMessage.error(error.message || '连接失败')
    row.status = 'error'
  } finally {
    row.testing = false
  }
}

// 删除
async function handleDelete(row: Server) {
  await ElMessageBox.confirm('确定要删除该服务器吗？', '提示', { type: 'warning' })
  const res = await serverApi.delete(row.id)
  if (res.code === 0) {
    ElMessage.success('删除成功')
    fetchData()
  }
}

// 提交表单
async function handleSubmit() {
  await formRef.value.validate()
  
  submitting.value = true
  try {
    if (editId.value) {
      await serverApi.update(editId.value, formData)
    } else {
      await serverApi.create(formData)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    fetchData()
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  fetchData()
  fetchGroups()
})
</script>

<style lang="scss" scoped>
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.text-secondary {
  color: var(--text-secondary);
}

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
</style>

<template>
  <div class="page-card">
    <div class="page-header">
      <h3 class="page-title">分组管理</h3>
      <el-button type="primary" @click="handleAdd">
        <el-icon><Plus /></el-icon>
        新建分组
      </el-button>
    </div>

    <el-table v-loading="loading" :data="tableData" stripe>
      <el-table-column prop="name" label="分组名称" min-width="180" />
      <el-table-column prop="serverCount" label="服务器数量" width="120" align="center">
        <template #default="{ row }">
          <el-tag type="info" size="small">{{ row.serverCount || 0 }} 台</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="描述" min-width="250">
        <template #default="{ row }">
          {{ row.description || '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="180">
        <template #default="{ row }">
          {{ formatTime(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
          <el-button type="danger" link @click="handleDelete(row)" :disabled="row.serverCount > 0">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 弹窗 -->
    <el-dialog v-model="dialogVisible" :title="editId ? '编辑分组' : '新建分组'" width="500px">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="80px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="formData.name" placeholder="请输入分组名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="formData.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { serverGroupApi, type ServerGroup } from '@/api/server'

const loading = ref(false)
const tableData = ref<ServerGroup[]>([])
const dialogVisible = ref(false)
const editId = ref<number | null>(null)
const formRef = ref()

const formData = reactive({
  name: '',
  description: '',
})

const formRules = {
  name: [{ required: true, message: '请输入分组名称', trigger: 'blur' }],
}

// 格式化时间
function formatTime(time: string) {
  if (!time) return '-'
  return time.replace('T', ' ').substring(0, 19)
}

async function fetchData() {
  loading.value = true
  try {
    const res = await serverGroupApi.list()
    if (res.code === 0) {
      tableData.value = res.data
    }
  } finally {
    loading.value = false
  }
}

function handleAdd() {
  editId.value = null
  formData.name = ''
  formData.description = ''
  dialogVisible.value = true
}

function handleEdit(row: ServerGroup) {
  editId.value = row.id
  formData.name = row.name
  formData.description = row.description || ''
  dialogVisible.value = true
}

async function handleDelete(row: ServerGroup) {
  await ElMessageBox.confirm('确定要删除该分组吗？', '提示', { type: 'warning' })
  const res = await serverGroupApi.delete(row.id)
  if (res.code === 0) {
    ElMessage.success('删除成功')
    fetchData()
  }
}

async function handleSubmit() {
  await formRef.value.validate()
  
  if (editId.value) {
    await serverGroupApi.update(editId.value, formData)
  } else {
    await serverGroupApi.create(formData)
  }
  
  ElMessage.success('保存成功')
  dialogVisible.value = false
  fetchData()
}

onMounted(() => {
  fetchData()
})
</script>

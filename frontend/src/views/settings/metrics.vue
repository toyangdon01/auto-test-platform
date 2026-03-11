<template>
  <div class="page-card">
    <div class="page-header">
      <h3 class="page-title">指标定义</h3>
      <el-button type="primary" @click="handleAdd">
        <el-icon><Plus /></el-icon>
        新增指标
      </el-button>
    </div>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="showDialog" :title="editing ? '编辑指标' : '新增指标'" width="550px">
      <el-form :model="formData" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="指标名称" prop="name">
          <el-input v-model="formData.name" placeholder="唯一标识，如 cpu_usage" />
        </el-form-item>
        <el-form-item label="显示名称" prop="displayName">
          <el-input v-model="formData.displayName" placeholder="如 CPU使用率" />
        </el-form-item>
        <el-form-item label="分类" prop="category">
          <el-select v-model="formData.category" style="width: 100%">
            <el-option label="CPU" value="cpu" />
            <el-option label="内存" value="memory" />
            <el-option label="磁盘" value="disk" />
            <el-option label="网络" value="network" />
            <el-option label="应用" value="application" />
            <el-option label="其他" value="other" />
          </el-select>
        </el-form-item>
        <el-form-item label="单位" prop="unit">
          <el-input v-model="formData.unit" placeholder="如 %、MB、ms" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="formData.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="对比模式">
          <el-radio-group v-model="formData.comparisonMode">
            <el-radio value="higher_better">越高越好</el-radio>
            <el-radio value="lower_better">越低越好</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="基准值">
          <el-input-number v-model="formData.baselineValue" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">保存</el-button>
      </template>
    </el-dialog>

    <!-- 指标列表 -->
    <el-table :data="metrics" stripe v-loading="loading">
      <el-table-column prop="displayName" label="指标名称" min-width="150">
        <template #default="{ row }">
          <div>
            <strong>{{ row.displayName }}</strong>
            <el-text type="info" size="small" class="ml-2">{{ row.name }}</el-text>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="category" label="分类" width="100">
        <template #default="{ row }">
          <el-tag size="small">{{ categoryText(row.category) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="unit" label="单位" width="80" />
      <el-table-column prop="baselineValue" label="基准值" width="100">
        <template #default="{ row }">
          {{ row.baselineConfig?.value ?? '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="comparisonMode" label="对比模式" width="100">
        <template #default="{ row }">
          <el-tag :type="row.comparisonMode === 'higher_better' ? 'success' : 'warning'" size="small">
            {{ row.comparisonMode === 'higher_better' ? '越高越好' : '越低越好' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="描述" min-width="150" show-overflow-tooltip />
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
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
        @size-change="fetchMetrics"
        @current-change="fetchMetrics"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, FormInstance, FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import request from '@/utils/request'

interface MetricDefinition {
  id: number
  name: string
  displayName: string
  category: string
  unit: string
  description: string
  baselineConfig: { value?: number } | null
  comparisonMode: string
}

const loading = ref(false)
const saving = ref(false)
const metrics = ref<MetricDefinition[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)

const showDialog = ref(false)
const editing = ref(false)
const formRef = ref<FormInstance>()
const formData = reactive({
  id: 0,
  name: '',
  displayName: '',
  category: 'other',
  unit: '',
  description: '',
  comparisonMode: 'higher_better',
  baselineValue: 0
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入指标名称', trigger: 'blur' }],
  displayName: [{ required: true, message: '请输入显示名称', trigger: 'blur' }],
  category: [{ required: true, message: '请选择分类', trigger: 'change' }]
}

const categoryText = (category: string) => {
  const map: Record<string, string> = {
    cpu: 'CPU',
    memory: '内存',
    disk: '磁盘',
    network: '网络',
    application: '应用',
    other: '其他'
  }
  return map[category] || category
}

const fetchMetrics = async () => {
  loading.value = true
  try {
    const res = await request.get<{ list: MetricDefinition[]; total: number }>('/metrics/definitions', {
      params: { page: page.value, pageSize: pageSize.value }
    })
    if (res.code === 0) {
      metrics.value = res.data?.list || res.data?.items || []
      total.value = res.data?.total || 0
    }
  } catch (e) {
    console.error('获取指标列表失败', e)
  } finally {
    loading.value = false
  }
}

const resetForm = () => {
  formData.id = 0
  formData.name = ''
  formData.displayName = ''
  formData.category = 'other'
  formData.unit = ''
  formData.description = ''
  formData.comparisonMode = 'higher_better'
  formData.baselineValue = 0
}

const handleAdd = () => {
  resetForm()
  editing.value = false
  showDialog.value = true
}

const handleEdit = (row: MetricDefinition) => {
  formData.id = row.id
  formData.name = row.name
  formData.displayName = row.displayName
  formData.category = row.category
  formData.unit = row.unit || ''
  formData.description = row.description || ''
  formData.comparisonMode = row.comparisonMode || 'higher_better'
  formData.baselineValue = row.baselineConfig?.value || 0
  editing.value = true
  showDialog.value = true
}

const handleSave = async () => {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }

  saving.value = true
  try {
    const data = {
      name: formData.name,
      displayName: formData.displayName,
      category: formData.category,
      unit: formData.unit,
      description: formData.description,
      comparisonMode: formData.comparisonMode,
      baselineConfig: { value: formData.baselineValue }
    }

    let res
    if (editing.value) {
      res = await request.put(`/metrics/definitions/${formData.id}`, data)
    } else {
      res = await request.post('/metrics/definitions', data)
    }

    if (res.code === 0) {
      ElMessage.success(editing.value ? '更新成功' : '创建成功')
      showDialog.value = false
      fetchMetrics()
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch (e: any) {
    ElMessage.error(e.message || '操作失败')
  } finally {
    saving.value = false
  }
}

const handleDelete = async (row: MetricDefinition) => {
  try {
    await ElMessageBox.confirm(`确定删除指标 "${row.displayName}" 吗？`, '提示', { type: 'warning' })
    const res = await request.delete(`/metrics/definitions/${row.id}`)
    if (res.code === 0) {
      ElMessage.success('删除成功')
      fetchMetrics()
    }
  } catch {
    // 取消删除
  }
}

onMounted(() => {
  fetchMetrics()
})
</script>

<style scoped>
.ml-2 {
  margin-left: 8px;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>

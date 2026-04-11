<template>
  <div class="resource-config">
    <div class="header">
      <span class="title">关联资源文件</span>
      <el-button type="primary" size="small" @click="showAddDialog = true">
        <el-icon><Plus /></el-icon>
        添加资源
      </el-button>
    </div>

    <el-table :data="resources" v-loading="loading" size="small" stripe>
      <el-table-column label="文件名" min-width="150">
        <template #default="{ row }">
          <span>{{ getResourceName(row.resourceId) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="targetPath" label="目标路径" width="180" />
      <el-table-column prop="permissions" label="权限" width="80" />
      <el-table-column prop="uploadOrder" label="顺序" width="60" />
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="editResource(row)">编辑</el-button>
          <el-button link type="danger" size="small" @click="removeResource(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!loading && resources.length === 0" description="暂无关联资源" :image-size="60" />

    <!-- 添加/编辑对话框 -->
    <el-dialog v-model="showAddDialog" :title="editingIndex !== null ? '编辑资源关联' : '添加资源关联'" width="600px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="选择资源" v-if="editingIndex === null">
          <el-select v-model="form.resourceId" placeholder="请选择资源文件" filterable style="width: 100%" teleported>
            <el-option
              v-for="item in availableResources"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            >
              <span>{{ item.name }}</span>
              <span style="color: #999; margin-left: 10px; font-size: 12px;">{{ formatSize(item.fileSize) }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="目标路径">
          <el-input v-model="form.targetPath" placeholder="/tmp 或 /opt/tools 等" />
        </el-form-item>
        <el-form-item label="权限">
          <el-input v-model="form.permissions" placeholder="如 644、755" />
        </el-form-item>
        <el-form-item label="上传顺序">
          <el-input-number v-model="form.uploadOrder" :min="1" :max="100" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddDialog = false">取消</el-button>
        <el-button type="primary" @click="saveResource" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { resourceApi, scriptResourceApi, type ResourceFile, type ScriptResource } from '@/api/resource'

const props = defineProps<{
  scriptId?: number
  modelValue?: ScriptResource[]
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: ScriptResource[]): void
}>()

const loading = ref(false)
const saving = ref(false)
const resources = ref<ScriptResource[]>([])
const allResources = ref<ResourceFile[]>([])
const showAddDialog = ref(false)
const editingIndex = ref<number | null>(null)

const form = ref({
  resourceId: null as number | null,
  targetPath: '/tmp',
  permissions: '644',
  uploadOrder: 1
})

// 可选资源（排除已关联的）
const availableResources = computed(() => {
  const linkedIds = resources.value.map(r => r.resourceId)
  return allResources.value.filter(r => !linkedIds.includes(r.id))
})

// 根据 resourceId 获取资源名称
function getResourceName(resourceId: number): string {
  const res = allResources.value.find(r => r.id === resourceId)
  return res?.name || '-'
}

// 是否是本地模式（无 scriptId）
const isLocalMode = computed(() => !props.scriptId)

// 初始化：从 modelValue 加载本地数据
watch(() => props.modelValue, (val) => {
  if (isLocalMode.value && val) {
    resources.value = val
  }
}, { immediate: true })

// 变化时通知父组件
watch(resources, (val) => {
  if (isLocalMode.value) {
    emit('update:modelValue', val)
  }
}, { deep: true })

async function loadResources() {
  if (isLocalMode.value) return
  
  loading.value = true
  try {
    const response = await scriptResourceApi.getByScriptId(props.scriptId!)
    resources.value = response.data || []
  } catch (error) {
    console.error('加载资源列表失败:', error)
  } finally {
    loading.value = false
  }
}

async function loadAllResources() {
  try {
    const response = await resourceApi.getPage({ pageNum: 1, pageSize: 1000 })
    allResources.value = response.data.records || []
  } catch (error) {
    console.error('加载资源列表失败:', error)
  }
}

function editResource(row: ScriptResource) {
  const index = resources.value.indexOf(row)
  editingIndex.value = index
  form.value = {
    resourceId: row.resourceId,
    targetPath: row.targetPath,
    permissions: row.permissions,
    uploadOrder: row.uploadOrder
  }
  showAddDialog.value = true
}

async function removeResource(row: ScriptResource) {
  try {
    await ElMessageBox.confirm('确定要移除该资源关联吗？', '提示', {
      type: 'warning'
    })
    
    if (isLocalMode.value) {
      // 本地模式：直接从数组移除
      const index = resources.value.indexOf(row)
      if (index > -1) {
        resources.value.splice(index, 1)
      }
      ElMessage.success('移除成功')
    } else {
      // API 模式
      await scriptResourceApi.remove(props.scriptId!, row.resourceId)
      ElMessage.success('移除成功')
      loadResources()
    }
  } catch (e) {
    // 用户取消
  }
}

async function saveResource() {
  if (!form.value.resourceId && editingIndex.value === null) {
    ElMessage.warning('请选择资源')
    return
  }
  
  saving.value = true
  try {
    const data: ScriptResource = {
      resourceId: editingIndex.value !== null ? resources.value[editingIndex.value].resourceId : form.value.resourceId!,
      targetPath: form.value.targetPath,
      permissions: form.value.permissions,
      uploadOrder: form.value.uploadOrder
    }
    
    if (editingIndex.value !== null) {
      // 编辑模式
      if (isLocalMode.value) {
        resources.value[editingIndex.value] = { ...resources.value[editingIndex.value], ...data }
        ElMessage.success('保存成功')
      } else {
        await scriptResourceApi.update(props.scriptId!, data.resourceId, data)
        ElMessage.success('保存成功')
        loadResources()
      }
    } else {
      // 添加模式
      if (isLocalMode.value) {
        resources.value.push(data)
        ElMessage.success('添加成功')
      } else {
        await scriptResourceApi.add(props.scriptId!, data)
        ElMessage.success('添加成功')
        loadResources()
      }
    }
    
    showAddDialog.value = false
    editingIndex.value = null
    resetForm()
  } catch (error) {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

function resetForm() {
  form.value = {
    resourceId: null,
    targetPath: '/tmp',
    permissions: '644',
    uploadOrder: 1
  }
}

function formatSize(size: number) {
  if (!size) return '-'
  if (size < 1024) return size + ' B'
  if (size < 1024 * 1024) return (size / 1024).toFixed(1) + ' KB'
  return (size / (1024 * 1024)).toFixed(1) + ' MB'
}

onMounted(() => {
  loadAllResources()
  if (!isLocalMode.value) {
    loadResources()
  }
})
</script>

<style scoped>
.resource-config {
  padding: 16px;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.title {
  font-weight: 500;
  font-size: 14px;
}
</style>

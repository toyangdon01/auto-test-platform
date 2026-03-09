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
        <el-option label="CPU测试" value="cpu" />
        <el-option label="内存测试" value="memory" />
        <el-option label="磁盘测试" value="disk" />
        <el-option label="网络测试" value="network" />
        <el-option label="综合测试" value="mixed" />
      </el-select>

      <el-button type="primary" @click="fetchData">查询</el-button>
      <el-button @click="resetQuery">重置</el-button>
    </div>

    <!-- 数据表格 -->
    <el-table v-loading="loading" :data="tableData" stripe>
      <el-table-column prop="name" label="脚本名称" min-width="200" />
      <el-table-column prop="testCategory" label="测试类型" width="120">
        <template #default="{ row }">
          <el-tag>{{ getCategoryText(row.testCategory) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="lifecycleMode" label="生命周期" width="100">
        <template #default="{ row }">
          <el-tag :type="row.lifecycleMode === 'full' ? 'success' : 'info'">
            {{ row.lifecycleMode === 'full' ? '完整' : '简单' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="currentVersion" label="当前版本" width="100" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'active' ? 'success' : 'info'">
            {{ row.status === 'active' ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="updatedAt" label="更新时间" width="160" />
      <el-table-column label="操作" width="250" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click="handleRun(row)">执行</el-button>
          <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
          <el-button type="primary" link @click="handleVersions(row)">版本</el-button>
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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { scriptApi, type Script } from '@/api/script'

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
  const texts: Record<string, string> = {
    cpu: 'CPU测试',
    memory: '内存测试',
    disk: '磁盘测试',
    network: '网络测试',
    mixed: '综合测试',
  }
  return texts[category] || category
}

function handleRun(row: Script) {
  router.push({ path: '/tasks/create', query: { scriptId: row.id } })
}

function handleEdit(row: Script) {
  router.push(`/scripts/edit/${row.id}`)
}

function handleVersions(row: Script) {
  ElMessage.info(`查看 ${row.name} 版本历史`)
}

async function handleDelete(row: Script) {
  await ElMessageBox.confirm('确定要删除该脚本吗？', '提示', { type: 'warning' })
  const res = await scriptApi.delete(row.id)
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

<template>
  <div class="page-card">
    <div class="page-header">
      <h3 class="page-title">判定规则管理</h3>
      <el-button type="primary" @click="handleCreate">
        <el-icon><Plus /></el-icon>
        新建规则
      </el-button>
    </div>

    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-select v-model="queryParams.scriptId" placeholder="脚本筛选" clearable style="width: 200px">
        <el-option label="全局规则" :value="null" />
        <el-option v-for="s in scripts" :key="s.id" :label="s.name" :value="s.id" />
      </el-select>
      <el-select v-model="queryParams.enabled" placeholder="状态" clearable style="width: 120px">
        <el-option label="启用" :value="true" />
        <el-option label="禁用" :value="false" />
      </el-select>
      <el-button type="primary" @click="fetchData">查询</el-button>
      <el-button @click="resetQuery">重置</el-button>
    </div>

    <!-- 数据表格 -->
    <el-table v-loading="loading" :data="tableData" stripe>
      <el-table-column prop="name" label="规则名称" min-width="150" />
      <el-table-column prop="scriptId" label="适用范围" width="120">
        <template #default="{ row }">
          <el-tag v-if="!row.scriptId" type="info">全局</el-tag>
          <el-tag v-else>{{ getScriptName(row.scriptId) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="priority" label="优先级" width="80" />
      <el-table-column prop="enabled" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'">
            {{ row.enabled ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
      <el-table-column prop="createdAt" label="创建时间" width="160" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click="handleTest(row)">测试</el-button>
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
      :title="editId ? '编辑规则' : '新建规则'"
      width="800px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="规则名称" prop="name">
              <el-input v-model="form.name" placeholder="输入规则名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="适用脚本">
              <el-select v-model="form.scriptId" placeholder="留空为全局规则" clearable style="width: 100%">
                <el-option v-for="s in scripts" :key="s.id" :label="s.name" :value="s.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="优先级">
              <el-input-number v-model="form.priority" :min="0" :max="100" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="是否启用">
              <el-switch v-model="form.enabled" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="规则描述" />
        </el-form-item>

        <!-- 规则配置 -->
        <el-divider content-position="left">规则配置</el-divider>

        <el-form-item label="成功条件">
          <div class="condition-config">
            <el-select v-model="form.rules.successCondition.matchType" placeholder="匹配方式" style="width: 120px">
              <el-option label="任一匹配" value="any" />
              <el-option label="全部匹配" value="all" />
              <el-option label="正则匹配" value="regex" />
            </el-select>
            <el-select
              v-if="form.rules.successCondition.matchType !== 'regex'"
              v-model="form.rules.successCondition.keywords"
              multiple
              filterable
              allow-create
              placeholder="成功关键字"
              style="flex: 1; margin-left: 8px"
            />
            <el-input
              v-else
              v-model="form.rules.successCondition.pattern"
              placeholder="成功正则表达式"
              style="flex: 1; margin-left: 8px"
            />
          </div>
        </el-form-item>

        <el-form-item label="失败条件">
          <div class="condition-config">
            <el-select v-model="form.rules.failCondition.matchType" placeholder="匹配方式" style="width: 120px">
              <el-option label="任一匹配" value="any" />
              <el-option label="全部匹配" value="all" />
              <el-option label="正则匹配" value="regex" />
            </el-select>
            <el-select
              v-if="form.rules.failCondition.matchType !== 'regex'"
              v-model="form.rules.failCondition.keywords"
              multiple
              filterable
              allow-create
              placeholder="失败关键字"
              style="flex: 1; margin-left: 8px"
            />
            <el-input
              v-else
              v-model="form.rules.failCondition.pattern"
              placeholder="失败正则表达式"
              style="flex: 1; margin-left: 8px"
            />
          </div>
        </el-form-item>

        <el-form-item label="退出码">
          <el-select v-model="form.rules.exitCodes" multiple placeholder="成功的退出码（可多选）">
            <el-option :value="0" label="0" />
            <el-option :value="1" label="1" />
            <el-option :value="2" label="2" />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 测试弹窗 -->
    <el-dialog v-model="testDialogVisible" title="规则测试" width="600px">
      <el-form label-width="100px">
        <el-form-item label="示例输出">
          <el-input
            v-model="testOutput"
            type="textarea"
            :rows="6"
            placeholder="粘贴脚本输出内容"
          />
        </el-form-item>
        <el-form-item label="退出码">
          <el-input-number v-model="testExitCode" :min="0" :max="255" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="testing" @click="executeTest">执行测试</el-button>
        </el-form-item>

        <div v-if="testResult" class="test-result">
          <el-alert
            :title="testResult.result === 'pass' ? '判定结果：通过' : '判定结果：失败'"
            :type="testResult.result === 'pass' ? 'success' : 'error'"
            :closable="false"
            show-icon
          />
          <div v-if="testResult.details" class="match-details">
            <p v-if="testResult.details.successKeywords?.length > 0">
              匹配的成功关键字: {{ testResult.details.successKeywords.join(', ') }}
            </p>
            <p v-if="testResult.details.failKeywords?.length > 0">
              匹配的失败关键字: {{ testResult.details.failKeywords.join(', ') }}
            </p>
          </div>
        </div>
      </el-form>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import request from '@/utils/request'

interface ResultRule {
  id: number
  scriptId: number | null
  name: string
  description: string
  rules: {
    successCondition: { matchType: string; keywords: string[]; pattern: string }
    failCondition: { matchType: string; keywords: string[]; pattern: string }
    exitCodes: number[]
  }
  priority: number
  enabled: boolean
  createdAt: string
}

interface Script {
  id: number
  name: string
}

const loading = ref(false)
const tableData = ref<ResultRule[]>([])
const total = ref(0)
const scripts = ref<Script[]>([])

const queryParams = reactive({
  page: 1,
  size: 20,
  scriptId: null as number | null,
  enabled: null as boolean | null
})

const dialogVisible = ref(false)
const editId = ref<number | null>(null)
const saving = ref(false)
const formRef = ref<FormInstance>()

const form = reactive({
  name: '',
  scriptId: null as number | null,
  description: '',
  priority: 0,
  enabled: true,
  rules: {
    successCondition: { matchType: 'any', keywords: [] as string[], pattern: '' },
    failCondition: { matchType: 'any', keywords: [] as string[], pattern: '' },
    exitCodes: [0]
  }
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入规则名称', trigger: 'blur' }]
}

// 测试
const testDialogVisible = ref(false)
const testRuleId = ref<number | null>(null)
const testOutput = ref('')
const testExitCode = ref(0)
const testing = ref(false)
const testResult = ref<any>(null)

onMounted(() => {
  fetchScripts()
  fetchData()
})

async function fetchScripts() {
  try {
    const res = await request.get('/scripts', { params: { size: 100 } })
    scripts.value = res.data.items || []
  } catch (error) {
    console.error('获取脚本列表失败:', error)
  }
}

async function fetchData() {
  loading.value = true
  try {
    const res = await request.get('/result-rules', { params: queryParams })
    tableData.value = res.data.items
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  queryParams.scriptId = null
  queryParams.enabled = null
  queryParams.page = 1
  fetchData()
}

function getScriptName(scriptId: number) {
  const script = scripts.value.find(s => s.id === scriptId)
  return script?.name || scriptId
}

function handleCreate() {
  editId.value = null
  form.name = ''
  form.scriptId = null
  form.description = ''
  form.priority = 0
  form.enabled = true
  form.rules = {
    successCondition: { matchType: 'any', keywords: [], pattern: '' },
    failCondition: { matchType: 'any', keywords: [], pattern: '' },
    exitCodes: [0]
  }
  dialogVisible.value = true
}

function handleEdit(row: ResultRule) {
  editId.value = row.id
  form.name = row.name
  form.scriptId = row.scriptId
  form.description = row.description || ''
  form.priority = row.priority || 0
  form.enabled = row.enabled !== false
  form.rules = {
    successCondition: row.rules?.successCondition || { matchType: 'any', keywords: [], pattern: '' },
    failCondition: row.rules?.failCondition || { matchType: 'any', keywords: [], pattern: '' },
    exitCodes: row.rules?.exitCodes || [0]
  }
  dialogVisible.value = true
}

async function handleSave() {
  const valid = await formRef.value?.validate()
  if (!valid) return

  saving.value = true
  try {
    if (editId.value) {
      await request.put(`/result-rules/${editId.value}`, form)
      ElMessage.success('更新成功')
    } else {
      await request.post('/result-rules', form)
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

async function handleDelete(row: ResultRule) {
  await ElMessageBox.confirm('确定删除该规则？', '提示', { type: 'warning' })
  await request.delete(`/result-rules/${row.id}`)
  ElMessage.success('删除成功')
  fetchData()
}

function handleTest(row: ResultRule) {
  testRuleId.value = row.id
  testOutput.value = ''
  testExitCode.value = 0
  testResult.value = null
  testDialogVisible.value = true
}

async function executeTest() {
  if (!testOutput.value.trim()) {
    ElMessage.warning('请输入示例输出')
    return
  }

  testing.value = true
  try {
    const res = await request.post(`/result-rules/${testRuleId.value}/test`, {
      sampleOutput: testOutput.value,
      exitCode: testExitCode.value
    })
    testResult.value = res.data
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '测试失败')
  } finally {
    testing.value = false
  }
}
</script>

<style scoped>
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.condition-config {
  display: flex;
  align-items: center;
  width: 100%;
}

.test-result {
  margin-top: 16px;
  padding: 16px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
}

.match-details {
  margin-top: 12px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.match-details p {
  margin: 4px 0;
}
</style>

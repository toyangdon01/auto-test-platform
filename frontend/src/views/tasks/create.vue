<template>
  <div class="page-card">
    <div class="page-header">
      <h3 class="page-title">创建任务</h3>
    </div>

    <el-steps :active="currentStep" finish-status="success" class="create-steps">
      <el-step title="选择脚本" />
      <el-step title="选择服务器" />
      <el-step title="配置参数" />
      <el-step title="执行设置" />
    </el-steps>

    <div class="step-content">
      <!-- Step 1: 选择脚本 -->
      <div v-show="currentStep === 0" class="step-panel">
        <el-table :data="scripts" highlight-current-row @current-change="handleScriptSelect">
          <el-table-column prop="name" label="脚本名称" min-width="200" />
          <el-table-column prop="testCategory" label="测试类型" width="120">
            <template #default="{ row }">
              <el-tag>{{ getCategoryText(row.testCategory) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="lifecycleMode" label="生命周期" width="100">
            <template #default="{ row }">
              {{ row.lifecycleMode === 'full' ? '完整' : '简单' }}
            </template>
          </el-table-column>
          <el-table-column prop="currentVersion" label="版本" width="100" />
        </el-table>
      </div>

      <!-- Step 2: 选择服务器 -->
      <div v-show="currentStep === 1" class="step-panel">
        <el-transfer
          v-model="selectedServers"
          :data="servers"
          :titles="['可选服务器', '已选服务器']"
          :props="{ key: 'id', label: 'name' }"
          filterable
          filter-placeholder="搜索服务器"
        />
      </div>

      <!-- Step 3: 配置参数 -->
      <div v-show="currentStep === 2" class="step-panel">
        <el-form :model="formData" label-width="120px">
          <el-divider content-position="left">共享参数</el-divider>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="测试时长">
                <el-input-number v-model="formData.duration" :min="1" :max="3600" />
                <span class="unit">秒</span>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="并发数">
                <el-input-number v-model="formData.concurrency" :min="1" :max="100" />
              </el-form-item>
            </el-col>
          </el-row>

          <template v-if="selectedScript?.lifecycleMode === 'full'">
            <el-divider content-position="left">部署参数</el-divider>
            <el-row :gutter="20">
              <el-col :span="12">
                <el-form-item label="部署目录">
                  <el-input v-model="formData.deployDir" placeholder="/tmp/test" />
                </el-form-item>
              </el-col>
            </el-row>
          </template>
        </el-form>
      </div>

      <!-- Step 4: 执行设置 -->
      <div v-show="currentStep === 3" class="step-panel">
        <el-form ref="formRef" :model="formData" :rules="formRules" label-width="120px">
          <el-form-item label="任务名称" prop="name">
            <el-input v-model="formData.name" placeholder="请输入任务名称" />
          </el-form-item>

          <el-form-item label="执行模式" prop="executionMode">
            <el-radio-group v-model="formData.executionMode">
              <el-radio value="immediate">立即执行</el-radio>
              <el-radio value="scheduled">定时执行</el-radio>
            </el-radio-group>
          </el-form-item>

          <el-form-item v-if="formData.executionMode === 'scheduled'" label="执行时间" prop="scheduledTime">
            <el-date-picker
              v-model="formData.scheduledTime"
              type="datetime"
              placeholder="选择执行时间"
            />
          </el-form-item>

          <el-form-item label="并行模式">
            <el-radio-group v-model="formData.parallelMode">
              <el-radio value="sequential">顺序执行</el-radio>
              <el-radio value="parallel">并行执行</el-radio>
            </el-radio-group>
          </el-form-item>

          <el-form-item v-if="formData.parallelMode === 'parallel'" label="最大并行数">
            <el-input-number v-model="formData.maxParallel" :min="1" :max="20" />
          </el-form-item>

          <el-form-item label="失败策略">
            <el-radio-group v-model="formData.failureStrategy">
              <el-radio value="continue">继续执行</el-radio>
              <el-radio value="stop">停止执行</el-radio>
            </el-radio-group>
          </el-form-item>

          <el-form-item label="指标采集">
            <el-switch v-model="formData.collectEnabled" />
          </el-form-item>
        </el-form>
      </div>
    </div>

    <div class="step-actions">
      <el-button v-if="currentStep > 0" @click="currentStep--">上一步</el-button>
      <el-button v-if="currentStep < 3" type="primary" :disabled="!canNext" @click="currentStep++">
        下一步
      </el-button>
      <el-button v-if="currentStep === 3" type="primary" @click="handleSubmit">
        创建任务
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { scriptApi, taskApi, type Script } from '@/api/script'
import { serverApi, type Server } from '@/api/server'

const router = useRouter()
const route = useRoute()
const formRef = ref()
const currentStep = ref(0)

const scripts = ref<Script[]>([])
const servers = ref<Server[]>([])
const selectedScript = ref<Script | null>(null)
const selectedServers = ref<number[]>([])

const formData = reactive({
  name: '',
  executionMode: 'immediate' as 'immediate' | 'scheduled',
  scheduledTime: '',
  parallelMode: 'sequential' as 'sequential' | 'parallel',
  maxParallel: 1,
  failureStrategy: 'continue' as 'continue' | 'stop',
  collectEnabled: true,
  duration: 60,
  concurrency: 1,
  deployDir: '/tmp/test',
})

const formRules = {
  name: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  executionMode: [{ required: true, message: '请选择执行模式', trigger: 'change' }],
}

const canNext = computed(() => {
  if (currentStep.value === 0) return !!selectedScript.value
  if (currentStep.value === 1) return selectedServers.value.length > 0
  return true
})

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

function handleScriptSelect(row: Script | null) {
  selectedScript.value = row
  formData.name = row ? `${row.name}-${new Date().toISOString().slice(0, 10)}` : ''
}

async function fetchScripts() {
  const res = await scriptApi.list({ page: 1, size: 100 })
  if (res.code === 0) {
    scripts.value = res.data.items
  }
}

async function fetchServers() {
  const res = await serverApi.list({ page: 1, size: 100 })
  if (res.code === 0) {
    servers.value = res.data.items
  }
}

async function handleSubmit() {
  await formRef.value.validate()
  
  const data = {
    name: formData.name,
    scriptId: selectedScript.value!.id,
    scriptVersion: selectedScript.value!.currentVersion,
    serverIds: selectedServers.value,
    executionMode: formData.executionMode,
    scheduledTime: formData.scheduledTime,
    parallelMode: formData.parallelMode,
    maxParallel: formData.maxParallel,
    failureStrategy: formData.failureStrategy,
    collectEnabled: formData.collectEnabled,
    sharedParams: { duration: formData.duration, concurrency: formData.concurrency },
    deployParams: { deployDir: formData.deployDir },
  }
  
  const res = await taskApi.create(data)
  if (res.code === 0) {
    ElMessage.success('任务创建成功')
    router.push('/tasks/list')
  }
}

onMounted(() => {
  fetchScripts()
  fetchServers()
  
  // 从 URL 获取预选脚本
  if (route.query.scriptId) {
    const scriptId = Number(route.query.scriptId)
    scriptApi.get(scriptId).then((res) => {
      if (res.code === 0) {
        selectedScript.value = res.data
        formData.name = `${res.data.name}-${new Date().toISOString().slice(0, 10)}`
      }
    })
  }
})
</script>

<style lang="scss" scoped>
.create-steps {
  margin-bottom: 30px;
}

.step-panel {
  min-height: 300px;
  padding: 20px;
}

.step-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid var(--border-lighter);
}

.unit {
  margin-left: 8px;
  color: var(--text-secondary);
}
</style>

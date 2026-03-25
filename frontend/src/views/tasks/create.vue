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
        <!-- 如果已预选脚本，显示确认信息 -->
        <div v-if="preselectedScript" class="preselected-info">
          <el-alert type="info" :closable="false" show-icon>
            <template #title>
              已从脚本中心选择: <strong>{{ selectedScript?.name }}</strong>
            </template>
          </el-alert>
          <div class="script-detail">
            <p><strong>测试类型:</strong> {{ getCategoryText(selectedScript?.testCategory) }}</p>
            <p><strong>版本:</strong> {{ selectedScript?.currentVersion }}</p>
          </div>
          <div class="preselected-actions">
            <el-button @click="preselectedScript = false">重新选择脚本</el-button>
          </div>
        </div>
        
        <!-- 脚本列表 -->
        <el-table 
          v-if="!preselectedScript"
          :data="scripts" 
          highlight-current-row 
          :current-row-key="selectedScript?.id"
          row-key="id"
          @current-change="handleScriptSelect"
        >
          <el-table-column prop="name" label="脚本名称" min-width="200">
            <template #default="{ row }">
              {{ formatScriptName(row.name) }}
            </template>
          </el-table-column>
          <el-table-column prop="testCategory" label="测试类型" width="120">
            <template #default="{ row }">
              <el-tag>{{ getCategoryText(row.testCategory) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="currentVersion" label="版本" width="100" />
        </el-table>
      </div>

      <!-- Step 2: 选择服务器（按步骤分配） -->
      <div v-show="currentStep === 1" class="step-panel">
        <div v-if="scriptSteps.length === 0" class="no-steps">
          <el-empty description="脚本未定义执行步骤">
            <el-button type="primary" @click="currentStep = 0">返回选择脚本</el-button>
          </el-empty>
        </div>
        
        <div v-else class="step-server-assignment">
          <el-alert type="info" :closable="false" show-icon class="step-tip">
            <template #title>
              该脚本定义了 <strong>{{ scriptSteps.length }}</strong> 个执行步骤，请为每个步骤选择执行服务器
            </template>
          </el-alert>
          
          <div class="steps-list">
            <div v-for="(step, index) in scriptSteps" :key="step.name" class="step-card">
              <div class="step-header">
                <div class="step-index">{{ index + 1 }}</div>
                <div class="step-title">{{ step.displayName || step.name }}</div>
                <div class="step-deps" v-if="step.dependsOn?.length">
                  <el-tag size="small" type="info">依赖: {{ step.dependsOn.join(', ') }}</el-tag>
                </div>
              </div>
              
              <div class="step-content">
                <div class="step-row">
                  <div class="field-label">执行服务器</div>
                  <el-select 
                    v-model="stepServerConfigs[index].serverId" 
                    placeholder="请选择服务器"
                    filterable
                    style="width: 280px"
                    @change="(val) => onStepServerChange(index, val)"
                  >
                    <el-option
                      v-for="server in servers"
                      :key="server.id"
                      :label="`${server.name} (${server.host})`"
                      :value="server.id"
                    >
                      <span>{{ server.name }}</span>
                      <span style="color: #999; margin-left: 10px;">{{ server.host }}</span>
                    </el-option>
                  </el-select>
                </div>
                
                <!-- 步骤参数 -->
                <div v-if="step.params?.length" class="step-params">
                  <div class="field-label">步骤参数</div>
                  <div class="params-grid">
                    <div v-for="param in step.params" :key="param.name" class="param-item">
                      <span class="param-label">{{ param.displayName || param.name }}</span>
                      <el-input
                        v-if="param.type === 'string' || !param.type"
                        v-model="stepServerConfigs[index].stepParams[param.name]"
                        size="small"
                        :placeholder="param.defaultValue?.toString() || param.default?.toString()"
                        :type="param.type === 'password' ? 'password' : 'text'"
                      />
                      <el-input-number
                        v-else-if="param.type === 'number'"
                        v-model="stepServerConfigs[index].stepParams[param.name]"
                        size="small"
                        :placeholder="param.defaultValue || param.default"
                      />
                      <el-switch
                        v-else-if="param.type === 'boolean'"
                        v-model="stepServerConfigs[index].stepParams[param.name]"
                        size="small"
                      />
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
          
          <div class="selected-summary" v-if="assignedServerCount > 0">
            <el-tag type="success">已分配 {{ assignedServerCount }}/{{ scriptSteps.length }} 个步骤</el-tag>
          </div>
        </div>
      </div>

      <!-- Step 3: 配置参数 -->
      <div v-show="currentStep === 2" class="step-panel">
        <el-alert type="info" :closable="false" show-icon class="mb-4">
          <template #title>
            <div class="builtin-params-tip">
              <strong>支持的内置参数：</strong>
              <div class="params-list">
                <span class="param-tag">TASK_ID</span>
                <span class="param-tag">SCRIPT_ID</span>
                <span class="param-tag">TASK_NAME</span>
                <span class="param-tag">SCRIPT_VERSION</span>
                <span class="param-tag">SERVER_ID</span>
                <span class="param-tag">SERVER_NAME</span>
                <span class="param-tag">SERVER_HOST</span>
              </div>
              <div class="tip-text">这些参数会自动传递给脚本，可在脚本中作为环境变量使用，或在文件路径中使用（如 /tmp/result_${TASK_ID}.txt）</div>
            </div>
          </template>
        </el-alert>
        
        <el-form :model="formData" label-width="120px">
          <template v-if="scriptParameters.length > 0">
            <el-divider content-position="left">共享参数</el-divider>
            <el-row :gutter="20">
              <el-col :span="12" v-for="param in scriptParameters" :key="param.name">
                <el-form-item :label="param.displayName || param.name">
                  <el-input
                    v-if="param.type === 'string' || !param.type"
                    v-model="formData.sharedParams[param.name]"
                    :placeholder="'默认: ' + param.default"
                  />
                  <el-input-number
                    v-else-if="param.type === 'number'"
                    v-model="formData.sharedParams[param.name]"
                  />
                  <el-switch
                    v-else-if="param.type === 'boolean'"
                    v-model="formData.sharedParams[param.name]"
                  />
                </el-form-item>
              </el-col>
            </el-row>
          </template>
          <el-empty v-else description="该脚本未定义共享参数" />
        </el-form>
      </div>

      <!-- Step 4: 执行设置 -->
      <div v-show="currentStep === 3" class="step-panel">
        <el-form ref="formRef" :model="formData" :rules="formRules" label-width="120px">
          <el-form-item prop="name">
            <template #label>
              任务名称
              <el-tooltip content="任务的标识名称，用于区分不同的测试任务" placement="top">
                <el-icon class="field-tip-icon"><QuestionFilled /></el-icon>
              </el-tooltip>
            </template>
            <el-input v-model="formData.name" placeholder="请输入任务名称" />
          </el-form-item>
          
          <el-form-item label="超时时间">
            <template #label>
              超时时间
              <el-tooltip content="单步骤执行超时时间，超时后任务将失败" placement="top">
                <el-icon class="field-tip-icon"><QuestionFilled /></el-icon>
              </el-tooltip>
            </template>
            <el-input-number 
              v-model="formData.timeout" 
              :min="60" 
              :max="3600" 
              :step="60"
              placeholder="默认 300 秒"
            />
            <span class="unit">秒</span>
            <span class="field-hint">（范围: 60-3600秒，默认300秒）</span>
          </el-form-item>
        </el-form>

        <!-- 指标采集配置组件 -->
        <MetricCollectConfig 
          v-model="formData.collectConfig" 
          :server-ids="selectedServerIds" 
        />
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
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { QuestionFilled } from '@element-plus/icons-vue'
import { scriptApi, taskApi, type Script } from '@/api/script'
import MetricCollectConfig from '@/components/MetricCollectConfig.vue'
import { serverApi, type Server } from '@/api/server'

const router = useRouter()
const route = useRoute()
const formRef = ref()
const currentStep = ref(0)

const scripts = ref<Script[]>([])
const servers = ref<Server[]>([])
const selectedScript = ref<Script | null>(null)
const preselectedScript = ref(false)  // 是否从脚本中心预选

// 步骤相关
interface StepDefinition {
  name: string
  displayName: string
  params: { name: string; displayName: string; type: string; defaultValue?: any; default?: any; required?: boolean }[]
  dependsOn: string[]
  resultCollector: boolean
}

// 步骤-服务器配置（每个步骤选择一台服务器）
interface StepServerConfig {
  stepName: string
  displayName: string
  serverId: number | null
  serverName: string
  stepParams: Record<string, any>
}

const scriptSteps = ref<StepDefinition[]>([])
const stepServerConfigs = ref<StepServerConfig[]>([])
const scriptParameters = ref<{ name: string; displayName?: string; type?: string; default?: any }[]>([])

// 已分配服务器的步骤数量
const assignedServerCount = computed(() => {
  return stepServerConfigs.value.filter(c => c.serverId).length
})

const formData = reactive({
  name: '',
  timeout: 300,  // 默认 5 分钟
  // 共享参数值（动态）
  sharedParams: {} as Record<string, any>,
  // 指标采集配置（默认禁用）
  collectConfig: { enabled: false } as any,
})

const formRules = {
  name: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
}

// 已选择的服务器 ID 列表（用于动态加载设备和网卡）
const selectedServerIds = computed(() => {
  if (scriptSteps.value.length > 0) {
    return stepServerConfigs.value
      .map(c => c.serverId)
      .filter(id => id !== null && id !== undefined)
  }
  return []
})

const canNext = computed(() => {
  if (currentStep.value === 0) return !!selectedScript.value
  if (currentStep.value === 1) {
    // 检查所有步骤是否都已分配服务器
    if (scriptSteps.value.length > 0) {
      return stepServerConfigs.value.every(c => c.serverId)
    }
    return true
  }
  return true
})

function getCategoryText(category: string | undefined) {
  if (!category) return '-'
  const texts: Record<string, string> = {
    cpu: 'CPU测试',
    memory: '内存测试',
    disk: '磁盘测试',
    network: '网络测试',
    mixed: '综合测试',
  }
  return texts[category] || category
}

// 格式化脚本名称
function formatScriptName(name: string) {
  if (!name) return '-'
  const match = name.match(/^(.+?)_[a-f0-9]+-(\d{4}-\d{2}-\d{2})$/)
  if (match) {
    return `${match[1]} (${match[2]})`
  }
  return name
}

function handleScriptSelect(row: Script | null) {
  selectedScript.value = row
  formData.name = row ? `${row.name}-${new Date().toISOString().slice(0, 10)}` : ''
  
  // 加载脚本的步骤定义（从 script.steps 字段获取）
  if (row) {
    loadScriptSteps(row)
  }
}

// 加载脚本步骤定义
async function loadScriptSteps(script: Script) {
  try {
    // 先尝试从脚本对象中获取 steps（需要完整加载）
    const res = await scriptApi.get(script.id)
    if (res.code === 0 && res.data) {
      const stepsData = (res.data as any).steps || {}
      
      // 将 steps 对象转换为数组
      const steps: StepDefinition[] = []
      for (const [stepName, stepConfig] of Object.entries(stepsData)) {
        // 跳过元数据字段
        if (stepName === '_meta') continue
        
        const config = stepConfig as any
        steps.push({
          name: stepName,
          displayName: config.displayName || stepName,
          params: config.params || [],
          dependsOn: config.dependsOn || [],
          resultCollector: config.resultCollector !== false
        })
      }
      
      scriptSteps.value = steps
      
      // 初始化每个步骤的服务器配置
      stepServerConfigs.value = scriptSteps.value.map(step => ({
        stepName: step.name,
        displayName: step.displayName || step.name,
        serverId: null,
        serverName: '',
        stepParams: {}
      }))
      
      // 设置默认参数值
      stepServerConfigs.value.forEach(config => {
        const step = scriptSteps.value.find(s => s.name === config.stepName)
        if (step?.params) {
          step.params.forEach(p => {
            config.stepParams[p.name] = p.defaultValue !== undefined ? p.defaultValue : p.default
          })
        }
      })
      
      // 加载共享参数定义
      const parametersData = (res.data as any).parameters || []
      scriptParameters.value = parametersData
      
      // 设置共享参数默认值
      parametersData.forEach((p: any) => {
        if (p.default !== undefined) {
          formData.sharedParams[p.name] = p.default
        }
      })
      
      // 如果没有步骤定义，创建一个默认步骤
      if (scriptSteps.value.length === 0) {
        scriptSteps.value = [{
          name: 'default',
          displayName: '执行脚本',
          params: [],
          dependsOn: [],
          resultCollector: true
        }]
        stepServerConfigs.value = [{
          stepName: 'default',
          displayName: '执行脚本',
          serverId: null,
          serverName: '',
          stepParams: {}
        }]
      }
    }
  } catch (e) {
    console.error('加载脚本步骤失败', e)
    // 创建默认步骤
    scriptSteps.value = [{
      name: 'default',
      displayName: '执行脚本',
      params: [],
      dependsOn: [],
      resultCollector: true
    }]
    stepServerConfigs.value = [{
      stepName: 'default',
      displayName: '执行脚本',
      serverId: null,
      serverName: '',
      stepParams: {}
    }]
  }
}

// 当步骤选择服务器时
function onStepServerChange(stepIndex: number, serverId: number) {
  const server = servers.value.find(s => s.id === serverId)
  if (server) {
    stepServerConfigs.value[stepIndex].serverName = server.name
  }
}

async function fetchScripts() {
  try {
    const res = await scriptApi.list()
    if (res.code === 0) {
      scripts.value = Array.isArray(res.data) ? res.data : (res.data.items || res.data.records || [])
    }
  } catch (e) {
    console.error('加载脚本列表失败', e)
  }
}

async function fetchServers() {
  try {
    const res = await serverApi.list()
    if (res.code === 0) {
      servers.value = Array.isArray(res.data) ? res.data : (res.data.items || res.data.records || [])
    }
  } catch (e) {
    console.error('加载服务器列表失败', e)
  }
}

async function handleSubmit() {
  await formRef.value.validate()
  
  // 检查所有步骤是否都已分配服务器
  if (scriptSteps.value.length > 0) {
    const unassignedSteps = stepServerConfigs.value.filter(c => !c.serverId)
    if (unassignedSteps.length > 0) {
      ElMessage.error(`请为步骤 "${unassignedSteps[0].displayName}" 选择服务器`)
      return
    }
  }
  
  // 构建步骤-服务器映射
  const stepServerMapping: Record<string, number[]> = {}
  stepServerConfigs.value.forEach(config => {
    if (!stepServerMapping[config.stepName]) {
      stepServerMapping[config.stepName] = []
    }
    if (config.serverId && !stepServerMapping[config.stepName].includes(config.serverId)) {
      stepServerMapping[config.stepName].push(config.serverId)
    }
  })
  
  // 构建步骤参数映射
  const stepParams: Record<string, Record<string, any>> = {}
  stepServerConfigs.value.forEach(config => {
    if (config.serverId && Object.keys(config.stepParams).length > 0) {
      if (!stepParams[config.stepName]) {
        stepParams[config.stepName] = config.stepParams
      }
    }
  })
  
  // 获取所有选中的服务器ID
  const allServerIds = [...new Set(stepServerConfigs.value.map(c => c.serverId!).filter(Boolean))]
  
  if (allServerIds.length === 0) {
    ElMessage.error('请选择至少一台服务器')
    return
  }
  
  const data = {
    name: formData.name,
    scriptId: selectedScript.value!.id,
    scriptVersion: selectedScript.value!.currentVersion,
    serverIds: allServerIds,
    stepServerMapping,
    stepParams,
    executionMode: 'immediate',
    parallelMode: 'sequential',
    maxParallel: 1,
    failureStrategy: 'continue',
    timeout: formData.timeout * 1000,  // 转换为毫秒
    collectEnabled: formData.collectConfig?.enabled !== false,
    collectConfig: formData.collectConfig,
    sharedParams: formData.sharedParams,
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
        preselectedScript.value = true
        // 加载步骤定义
        loadScriptSteps(res.data)
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

.preselected-info {
  text-align: center;
  padding: 40px 20px;
  
  .el-alert {
    max-width: 400px;
    margin: 0 auto 20px;
  }
  
  .script-detail {
    background: var(--el-fill-color-light);
    padding: 16px;
    border-radius: 4px;
    margin-bottom: 20px;
    max-width: 400px;
    margin-left: auto;
    margin-right: auto;
    text-align: left;
    
    p {
      margin: 8px 0;
      color: var(--el-text-color-regular);
      
      strong {
        color: var(--el-text-color-primary);
      }
    }
  }
}

.step-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid var(--border-lighter);
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

.role-info {
  margin-bottom: 15px;
}

.role-assignment {
  margin-top: 20px;
}

.role-params {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.role-params .param-item {
  display: flex;
  align-items: center;
  gap: 5px;
}

.role-params .param-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.no-params {
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}

.unit {
  margin-left: 8px;
  color: var(--text-secondary);
}

// 步骤服务器分配样式
.step-server-assignment {
  .step-tip {
    margin-bottom: 20px;
  }
  
  .steps-list {
    display: flex;
    flex-direction: column;
    gap: 16px;
  }
  
  .step-card {
    border: 1px solid var(--el-border-color);
    border-radius: 8px;
    overflow: hidden;
    
    .step-header {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 12px 16px;
      background: var(--el-fill-color-light);
      border-bottom: 1px solid var(--el-border-color);
      
      .step-index {
        width: 28px;
        height: 28px;
        display: flex;
        align-items: center;
        justify-content: center;
        background: var(--el-color-primary);
        color: white;
        border-radius: 50%;
        font-size: 14px;
        font-weight: 600;
      }
      
      .step-title {
        font-weight: 600;
        font-size: 15px;
      }
      
      .step-deps {
        margin-left: auto;
      }
    }
    
    .step-content {
      padding: 16px;
      
      .step-row {
        display: flex;
        align-items: center;
        gap: 12px;
        
        .field-label {
          flex-shrink: 0;
          width: 80px;
          color: var(--el-text-color-regular);
          font-size: 14px;
        }
      }
      
      .step-params {
        margin-top: 12px;
        
        .field-label {
          color: var(--el-text-color-regular);
          font-size: 14px;
          margin-bottom: 8px;
        }
        
        .params-grid {
          display: flex;
          flex-wrap: wrap;
          gap: 12px;
        }
        
        .param-item {
          display: flex;
          align-items: center;
          gap: 8px;
          
          .param-label {
            font-size: 13px;
            color: var(--el-text-color-secondary);
          }
        }
      }
    }
  }
  
  .selected-summary {
    margin-top: 16px;
    text-align: center;
  }
}

.no-steps {
  padding: 40px;
}

.unit {
  margin-left: 8px;
  color: var(--el-text-color-secondary);
}

.field-hint {
  margin-left: 12px;
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}

// 内置参数提示样式
.builtin-params-tip {
  .params-list {
    margin-top: 8px;
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }
  
  .param-tag {
    display: inline-block;
    padding: 2px 8px;
    background: var(--el-color-primary-light-9);
    color: var(--el-color-primary);
    border-radius: 4px;
    font-size: 12px;
    font-family: monospace;
  }
  
  .tip-text {
    margin-top: 8px;
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
}

.mb-4 {
  margin-bottom: 16px;
}
</style>

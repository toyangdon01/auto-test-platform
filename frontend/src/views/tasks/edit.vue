<template>
  <div class="page-card">
    <div class="page-header">
      <el-page-header @back="$router.back()">
        <template #content>
          <h3 class="page-title">编辑任务</h3>
        </template>
      </el-page-header>
    </div>

    <el-steps :active="currentStep" finish-status="success" class="create-steps">
      <el-step title="选择脚本" />
      <el-step title="选择服务器" />
      <el-step title="配置参数" />
      <el-step title="执行设置" />
    </el-steps>

    <div class="step-content" v-loading="loading">
      <!-- Step 1: 选择脚本（只读显示） -->
      <div v-show="currentStep === 0" class="step-panel">
        <el-alert type="info" :closable="false" show-icon class="mb-4">
          <template #title>
            已选择脚本: <strong>{{ selectedScript?.name }}</strong> (版本: {{ formData.scriptVersion }})
          </template>
        </el-alert>
        
        <div class="script-detail">
          <p><strong>测试类型:</strong> {{ getCategoryText(selectedScript?.testCategory) }}</p>
          <p><strong>版本:</strong> {{ formData.scriptVersion }}</p>
        </div>
      </div>

      <!-- Step 2: 选择服务器（按步骤分配） -->
      <div v-show="currentStep === 1" class="step-panel">
        <div v-if="scriptSteps.length === 0" class="no-steps">
          <el-empty description="脚本未定义执行步骤，无法创建任务" />
        </div>
        
        <div v-else class="step-server-assignment">
          <el-alert type="info" :closable="false" show-icon class="step-tip">
            <template #title>
              该脚本定义了 <strong>{{ scriptSteps.length }}</strong> 个执行步骤
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
                    <!-- 本地环境选项 -->
                    <el-option
                      :value="-1"
                      label="⭐ 本地环境"
                    >
                      <span>⭐ 本地环境</span>
                      <span style="color: #999; margin-left: 10px;">平台本地执行</span>
                    </el-option>
                    <el-divider style="margin: 5px 0;" />
                    <!-- 远程服务器选项 -->
                    <el-option
                      v-for="server in enabledServers"
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
                      <div class="param-header">
                        <span class="param-label">{{ param.displayName || param.name }}</span>
                        <span v-if="param.description" class="param-desc">{{ param.description }}</span>
                      </div>
                      <el-input
                        v-model="stepServerConfigs[index].stepParams[param.name]"
                        size="small"
                        :placeholder="param.defaultValue?.toString() || param.default?.toString()"
                      />
                    </div>
                  </div>
                </div>
              </div>
            </div>
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
            </div>
          </template>
        </el-alert>
        
        <el-form :model="formData" label-width="120px">
          <template v-if="scriptParameters.length > 0">
            <el-divider content-position="left">共享参数</el-divider>
            <el-row :gutter="20">
              <el-col :span="12" v-for="param in scriptParameters" :key="param.name">
                <el-form-item :label="param.displayName || param.name">
                  <template #label>
                    {{ param.displayName || param.name }}
                    <el-tooltip v-if="param.description" :content="param.description" placement="top">
                      <el-icon class="field-tip-icon"><QuestionFilled /></el-icon>
                    </el-tooltip>
                  </template>
                  <el-input
                    v-model="formData.sharedParams[param.name]"
                    :placeholder="'默认: ' + param.default"
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
          
          <el-form-item label="描述">
            <el-input v-model="formData.description" type="textarea" :rows="3" placeholder="请输入任务描述" />
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
              :max="86400"
              :step="60"
            />
            <span class="unit">秒</span>
            <span class="field-hint">（范围: 60-86400秒）</span>
          </el-form-item>
        </el-form>

        <!-- 指标采集配置（禁用，提示收费） -->
        <div class="metric-collect-disabled">
          <el-alert 
            type="warning" 
            title="指标采集功能已禁用" 
            description="此功能为付费功能，如需使用请联系管理员开通。" 
            :closable="false" 
            show-icon 
          />
          <div class="disabled-mask">
            <MetricCollectConfig 
              v-model="formData.collectConfig" 
              :server-ids="selectedServerIds" 
              disabled 
            />
          </div>
        </div>
      </div>
    </div>

    <div class="step-actions">
      <el-button v-if="currentStep > 0" @click="currentStep--">上一步</el-button>
      <el-button v-if="currentStep < 3" type="primary" :disabled="!canNext" @click="currentStep++">
        下一步
      </el-button>
      <el-button v-if="currentStep === 3" type="primary" @click="handleSubmit" :loading="submitting">
        保存修改
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { QuestionFilled } from '@element-plus/icons-vue'
import { scriptApi, taskApi, type Script } from '@/api/script'
import MetricCollectConfig from '@/components/MetricCollectConfig.vue'
import { serverApi, type Server } from '@/api/server'
import request from '@/utils/request'

const router = useRouter()
const route = useRoute()
const formRef = ref()
const currentStep = ref(0)
const loading = ref(true)
const submitting = ref(false)
const taskId = Number(route.params.id)

const servers = ref<Server[]>([])
const selectedScript = ref<Script | null>(null)

// 过滤掉禁用的服务器
const enabledServers = computed(() => servers.value.filter(s => s.enabled !== false))

// 步骤相关
interface StepDefinition {
  name: string
  displayName: string
  params: { name: string; displayName: string; type: string; defaultValue?: any; default?: any; required?: boolean; description?: string }[]
  dependsOn: string[]
  resultCollector: boolean
}

interface StepServerConfig {
  stepName: string
  displayName: string
  serverId: number | null
  serverName: string
  isLocal: boolean
  stepParams: Record<string, any>
}

const scriptSteps = ref<StepDefinition[]>([])
const stepServerConfigs = ref<StepServerConfig[]>([])
const scriptParameters = ref<{ name: string; displayName?: string; default?: any; description?: string }[]>([])

const formData = reactive({
  name: '',
  description: '',
  scriptVersion: '',
  timeout: 86400,
  sharedParams: {} as Record<string, any>,
  collectConfig: { enabled: false } as any,
})

const formRules = {
  name: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
}

const selectedServerIds = computed(() => {
  return stepServerConfigs.value
    .map(c => c.serverId)
    .filter(id => id !== null && id !== undefined)
})

const canNext = computed(() => {
  if (currentStep.value === 0) return !!selectedScript.value
  if (currentStep.value === 1) {
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

function onStepServerChange(stepIndex: number, serverId: number) {
  if (serverId === -1) {
    // 本地环境
    stepServerConfigs.value[stepIndex].serverName = '本地环境'
    stepServerConfigs.value[stepIndex].isLocal = true
  } else {
    const server = servers.value.find(s => s.id === serverId)
    if (server) {
      stepServerConfigs.value[stepIndex].serverName = server.name
      stepServerConfigs.value[stepIndex].isLocal = false
    }
  }
}

async function loadTaskData() {
  loading.value = true
  try {
    // 获取任务详情
    const res = await request.get(`/tasks/${taskId}`)
    if (res.code === 0) {
      const task = res.data
      
      formData.name = task.name
      formData.description = task.description || ''
      formData.scriptVersion = task.scriptVersion
      formData.timeout = Math.round((task.timeout || 86400000) / 1000)
      formData.sharedParams = task.sharedParams || {}
      formData.collectConfig = task.collectConfig || { enabled: false }
      
      // 获取脚本详情
      if (task.scriptId) {
        const scriptRes = await scriptApi.get(task.scriptId)
        if (scriptRes.code === 0) {
          selectedScript.value = scriptRes.data
          loadScriptSteps(scriptRes.data)
        }
      }
      
      // 恢复步骤服务器配置
      if (task.stepServerMapping) {
        // 延迟执行，等 scriptSteps 加载完成
        setTimeout(() => {
          restoreStepServerConfigs(task.stepServerMapping, task.stepParams)
        }, 100)
      }
    }
  } catch (e: any) {
    ElMessage.error(e.message || '加载任务数据失败')
  } finally {
    loading.value = false
  }
}

function loadScriptSteps(script: Script) {
  const stepsData = (script as any).steps || {}
  
  const steps: StepDefinition[] = []
  for (const [stepName, stepConfig] of Object.entries(stepsData)) {
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
  
  // 初始化步骤服务器配置
  stepServerConfigs.value = scriptSteps.value.map(step => ({
    stepName: step.name,
    displayName: step.displayName || step.name,
    serverId: null,
    serverName: '',
    isLocal: false,
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
  const parametersData = (script as any).parameters || []
  scriptParameters.value = parametersData
}

// 恢复步骤服务器配置
function restoreStepServerConfigs(stepServerMapping: Record<string, number[]>, stepParams?: Record<string, Record<string, any>>) {
  for (const [stepName, serverIds] of Object.entries(stepServerMapping)) {
    const configIndex = stepServerConfigs.value.findIndex(c => c.stepName === stepName)
    if (configIndex >= 0 && serverIds && serverIds.length > 0) {
      const serverId = typeof serverIds[0] === 'number' ? serverIds[0] : Number(serverIds[0])
      stepServerConfigs.value[configIndex].serverId = serverId
      
      if (serverId === -1) {
        // 本地环境
        stepServerConfigs.value[configIndex].serverName = '本地环境'
        stepServerConfigs.value[configIndex].isLocal = true
      } else {
        const server = servers.value.find(s => s.id === serverId)
        if (server) {
          stepServerConfigs.value[configIndex].serverName = server.name
          stepServerConfigs.value[configIndex].isLocal = false
        }
      }
      
      // 恢复步骤参数
      if (stepParams && stepParams[stepName]) {
        stepServerConfigs.value[configIndex].stepParams = { ...stepParams[stepName] }
      }
    }
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
  await formRef.value?.validate()
  
  // 检查是否有步骤
  if (scriptSteps.value.length === 0) {
    ElMessage.error('脚本未定义执行步骤，无法创建任务')
    return
  }
  
  // 检查所有步骤是否都已分配服务器
  const unassignedSteps = stepServerConfigs.value.filter(c => !c.serverId && c.serverId !== -1)
  if (unassignedSteps.length > 0) {
    ElMessage.error(`请为步骤 "${unassignedSteps[0].displayName}" 选择服务器`)
    return
  }
  
  submitting.value = true
  
  try {
    // 构建步骤-服务器映射
    const stepServerMapping: Record<string, number[]> = {}
    stepServerConfigs.value.forEach(config => {
      if (!stepServerMapping[config.stepName]) {
        stepServerMapping[config.stepName] = []
      }
      // 本地执行使用 -1
      const serverIdToAdd = config.isLocal ? -1 : config.serverId
      if (serverIdToAdd !== null && serverIdToAdd !== undefined && !stepServerMapping[config.stepName].includes(serverIdToAdd)) {
        stepServerMapping[config.stepName].push(serverIdToAdd)
      }
    })
    
    // 构建步骤参数映射
    const stepParams: Record<string, Record<string, any>> = {}
    stepServerConfigs.value.forEach(config => {
      if (Object.keys(config.stepParams).length > 0) {
        stepParams[config.stepName] = config.stepParams
      }
    })
    
    // 获取所有服务器ID（排除本地执行的 -1）
    const allServerIds = [...new Set(stepServerConfigs.value
      .map(c => c.serverId)
      .filter(id => id !== null && id !== undefined && id !== -1))]
    
    // 检查是否有本地执行
    const hasLocalExecution = stepServerConfigs.value.some(c => c.isLocal)
    
    const data = {
      name: formData.name,
      description: formData.description,
      scriptId: selectedScript.value!.id,
      scriptVersion: formData.scriptVersion,
      serverIds: allServerIds,
      isLocal: hasLocalExecution,
      stepServerMapping,
      stepParams,
      executionMode: 'immediate',
      parallelMode: 'sequential',
      maxParallel: 1,
      failureStrategy: 'continue',
      timeout: formData.timeout * 1000,
      collectEnabled: formData.collectConfig?.enabled !== false,
      collectConfig: formData.collectConfig,
      sharedParams: formData.sharedParams,
    }
    
    const res = await request.put(`/tasks/${taskId}`, data)
    if (res.code === 0) {
      ElMessage.success('任务更新成功')
      router.push('/tasks/list')
    }
  } catch (e: any) {
    ElMessage.error(e.message || '更新失败')
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  await fetchServers()
  await loadTaskData()
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

.script-detail {
  background: var(--el-fill-color-light);
  padding: 16px;
  border-radius: 4px;
  margin-bottom: 20px;
  
  p {
    margin: 8px 0;
    color: var(--el-text-color-regular);
    
    strong {
      color: var(--el-text-color-primary);
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

.field-tip-icon {
  margin-left: 4px;
  color: var(--el-text-color-secondary);
  cursor: help;
  vertical-align: middle;
  
  &:hover {
    color: var(--el-color-primary);
  }
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
          display: grid;
          grid-template-columns: repeat(2, 1fr);
          gap: 12px;
        }
        
        .param-item {
          display: flex;
          flex-direction: column;
          align-items: flex-start;
          gap: 4px;
          
          .param-header {
            display: flex;
            flex-direction: column;
            gap: 2px;
          }
          
          .param-label {
            font-size: 13px;
            color: var(--el-text-color-secondary);
          }
          
          .param-desc {
            font-size: 12px;
            color: var(--el-text-color-placeholder);
          }
        }
      }
    }
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
}

.mb-4 {
  margin-bottom: 16px;
}

// 修复长参数名显示
:deep(.el-form-item__label) {
  word-break: break-all;
  white-space: normal;
  line-height: 1.5;
}

.param-name-label {
  word-break: break-all;
  white-space: normal;
  line-height: 1.5;
  min-width: 150px;
}

// 指标采集禁用样式
.metric-collect-disabled {
  position: relative;
  margin-top: 16px;
  
  .disabled-mask {
    position: relative;
    opacity: 0.5;
    pointer-events: none;
    margin-top: 12px;
  }
}
</style>
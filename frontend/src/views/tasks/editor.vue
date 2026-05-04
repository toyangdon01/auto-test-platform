<template>
  <div class="page-card">
    <div class="page-header">
      <el-page-header @back="$router.back()">
        <template #content>
          <h3 class="page-title">{{ isEdit ? '编辑任务' : '创建任务' }}</h3>
        </template>
      </el-page-header>
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
        <!-- 编辑模式或已预选脚本：显示确认信息 -->
        <div v-if="isEdit || preselectedScript" class="preselected-info">
          <el-alert type="info" :closable="false" show-icon>
            <template #title>
              {{ isEdit ? '任务脚本' : '已从脚本中心选择' }}: <strong>{{ selectedScript?.name }}</strong>
            </template>
          </el-alert>
          <div class="script-detail">
            <p><strong>测试类型:</strong> {{ getCategoryText(selectedScript?.testCategory) }}</p>
            <p><strong>版本:</strong> {{ formData.scriptVersion || selectedScript?.currentVersion }}</p>
          </div>
          <div v-if="!isEdit" class="preselected-actions">
            <el-button @click="preselectedScript = false">重新选择脚本</el-button>
          </div>
        </div>
        
        <!-- 创建模式：脚本列表 -->
        <el-table 
          v-if="!isEdit && !preselectedScript"
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
                    style="width: 320px"
                    @change="(val) => onStepServerChange(index, val)"
                  >
                    <!-- 普通服务器选项 -->
                    <el-option-group label="服务器">
                      <el-option
                        v-for="server in enabledServers"
                        :key="server.id"
                        :label="`${server.name} (${server.host})`"
                        :value="server.id"
                      >
                        <span>{{ server.name }}</span>
                        <span style="color: #999; margin-left: 10px;">{{ server.host }}</span>
                      </el-option>
                    </el-option-group>
                    <!-- 本地执行选项 -->
                    <el-option
                      key="local"
                      label="本地环境 (平台本地执行)"
                      :value="-1"
                    >
                      <span>⭐ 本地环境</span>
                      <span style="color: #999; margin-left: 10px;">平台本地执行</span>
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
                
                <!-- 后台执行选项 -->
                <div class="step-background-option">
                  <el-checkbox 
                    v-model="stepServerConfigs[index].stepParams._BACKGROUND"
                    :true-label="true"
                    :false-label="false"
                    :disabled="stepServerConfigs[index].serverId === -1"
                  >
                    后台执行
                  </el-checkbox>
                  <el-tooltip :content="stepServerConfigs[index].serverId === -1 ? '本地执行不支持后台运行' : '后台执行的步骤不受网络中断影响，进程会在服务器上持续运行'" placement="top">
                    <el-icon class="field-tip-icon"><QuestionFilled /></el-icon>
                  </el-tooltip>
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
          
          <!-- 执行模式 -->
          <el-form-item label="执行模式">
            <el-radio-group v-model="formData.executionMode">
              <el-radio value="immediate">立即执行</el-radio>
              <el-radio value="scheduled_once">指定时间</el-radio>
              <el-radio value="scheduled_cron">周期执行</el-radio>
            </el-radio-group>
          </el-form-item>
          
          <!-- 指定时间 -->
          <el-form-item v-if="formData.executionMode === 'scheduled_once'" label="执行时间" required>
            <el-date-picker
              v-model="formData.scheduledTime"
              type="datetime"
              placeholder="选择执行时间"
              format="YYYY-MM-DD HH:mm"
              value-format="YYYY-MM-DDTHH:mm:ss"
              :disabled-date="(time: Date) => time.getTime() < Date.now() - 86400000"
            />
          </el-form-item>
          
          <!-- Cron 表达式 -->
          <el-form-item v-if="formData.executionMode === 'scheduled_cron'" label="Cron表达式" required>
            <el-input v-model="formData.cronExpression" placeholder="如: 0 0 2 * * ? (每天凌晨2点)" />
            <div class="cron-quick-select">
              <span>常用:</span>
              <el-link type="primary" @click="formData.cronExpression = '0 0 2 * * ?'">每天凌晨2点</el-link>
              <el-link type="primary" @click="formData.cronExpression = '0 0 */6 * * ?'">每6小时</el-link>
              <el-link type="primary" @click="formData.cronExpression = '0 0 * * * ?'">每小时</el-link>
              <el-link type="primary" @click="formData.cronExpression = '0 */30 * * * ?'">每30分钟</el-link>
            </div>
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
              :min="1" 
              :step="60"
              placeholder="默认 300 秒"
            />
            <span class="unit">秒</span>
            <span class="field-hint">（范围: 60-3600秒，默认300秒）</span>
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
import request from '@/utils/request'

const router = useRouter()
const route = useRoute()
const formRef = ref()
const currentStep = ref(0)
const loading = ref(false)

// 编辑模式
const taskId = computed(() => route.params.id ? Number(route.params.id) : null)
const isEdit = computed(() => !!taskId.value)

const scripts = ref<Script[]>([])
const servers = ref<Server[]>([])
const selectedScript = ref<Script | null>(null)
const preselectedScript = ref(false)  // 是否从脚本中心预选

// 过滤掉禁用的服务器
const enabledServers = computed(() => servers.value.filter(s => s.enabled !== false))

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
const scriptParameters = ref<{ name: string; displayName?: string; default?: any; description?: string }[]>([])

// 已分配服务器的步骤数量
const assignedServerCount = computed(() => {
  return stepServerConfigs.value.filter(c => c.serverId).length
})

const formData = reactive({
  name: '',
  description: '',
  scriptVersion: '',
  timeout: 86400,  // 默认 24 小时
  executionMode: 'immediate',  // 执行模式: immediate/scheduled_once/scheduled_cron
  scheduledTime: '',  // 指定执行时间
  cronExpression: '',  // Cron 表达式
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
    const server = servers.value.find(s => s.id === serverId)
// 当步骤选择服务器时
function onStepServerChange(stepIndex: number, serverId: number) {
  if (serverId === -1) {
    // 本地执行
    stepServerConfigs.value[stepIndex].serverName = '本地环境'
  } else {
    const server = servers.value.find(s => s.id === serverId)
    if (server) {
      stepServerConfigs.value[stepIndex].serverName = server.name
    }
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
    const unassignedSteps = stepServerConfigs.value.filter(c => c.serverId === null)
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
    if (config.serverId) {
      // 本地执行使用 -1 作为特殊标记
      if (!stepServerMapping[config.stepName].includes(config.serverId)) {
        stepServerMapping[config.stepName].push(config.serverId)
      }
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
  
  // 获取所有选中的服务器ID（包含本地执行的 -1）
  const allServerIds = [...new Set(stepServerConfigs.value
    .map(c => c.serverId)
    .filter(id => id !== null && id !== undefined))]
  
  // 检查是否有服务器或本地执行
  if (allServerIds.length === 0) {
    ElMessage.error('请选择至少一台服务器或本地执行')
    return
  }
  
  const data = {
    name: formData.name,
    scriptId: selectedScript.value!.id,
    scriptVersion: formData.scriptVersion || selectedScript.value!.currentVersion,
    serverIds: allServerIds,
    stepServerMapping,
    stepParams,
    executionMode: formData.executionMode === 'immediate' ? 'immediate' : 'scheduled',
    scheduledTime: formData.executionMode === 'scheduled_once' ? formData.scheduledTime : undefined,
    cronExpression: formData.executionMode === 'scheduled_cron' ? formData.cronExpression : undefined,
    parallelMode: 'sequential',
    maxParallel: 1,
    failureStrategy: 'continue',
    timeout: formData.timeout * 1000,  // 转换为毫秒
    collectEnabled: formData.collectConfig?.enabled !== false,
    collectConfig: formData.collectConfig,
    sharedParams: formData.sharedParams,
  }
  
  try {
    let res
    if (isEdit.value) {
      res = await request.put(`/tasks/${taskId.value}`, data)
    } else {
      res = await taskApi.create(data)
    }
    if (res.code === 0) {
      ElMessage.success(isEdit.value ? '任务更新成功' : '任务创建成功')
      router.push('/tasks/list')
    }
  } catch (e: any) {
    ElMessage.error(e.message || (isEdit.value ? '更新失败' : '创建失败'))
  }
}

onMounted(async () => {
  await fetchScripts()
  await fetchServers()
  
  if (isEdit.value) {
    // 编辑模式：加载任务数据
    await loadTaskData()
  } else if (route.query.scriptId) {
    // 创建模式：从 URL 获取预选脚本
    const scriptId = Number(route.query.scriptId)
    scriptApi.get(scriptId).then((res) => {
      if (res.code === 0) {
        selectedScript.value = res.data
        formData.name = `${res.data.name}-${new Date().toISOString().slice(0, 10)}`
        preselectedScript.value = true
        loadScriptSteps(res.data)
      }
    })
  }
})

// 加载任务数据（编辑模式）
async function loadTaskData() {
  loading.value = true
  try {
    const res = await request.get(`/tasks/${taskId.value}`)
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

// 恢复步骤服务器配置
function restoreStepServerConfigs(stepServerMapping: Record<string, number[]>, stepParams?: Record<string, Record<string, any>>) {
  for (const [stepName, serverIds] of Object.entries(stepServerMapping)) {
    const configIndex = stepServerConfigs.value.findIndex(c => c.stepName === stepName)
    if (configIndex >= 0 && serverIds && serverIds.length > 0) {
      const serverId = typeof serverIds[0] === 'number' ? serverIds[0] : Number(serverIds[0])
      stepServerConfigs.value[configIndex].serverId = serverId
      
      if (serverId === -1) {
        stepServerConfigs.value[configIndex].serverName = '本地环境'
      } else {
        const server = servers.value.find(s => s.id === serverId)
        if (server) {
          stepServerConfigs.value[configIndex].serverName = server.name
        }
      }
    }
  }
  
  // 恢复步骤参数
  if (stepParams) {
    for (const [stepName, params] of Object.entries(stepParams)) {
      const config = stepServerConfigs.value.find(c => c.stepName === stepName)
      if (config && params) {
        config.stepParams = { ...config.stepParams, ...params }
      }
    }
  }
}
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
          flex-direction: column;
          align-items: flex-start;
          gap: 4px;
          
          .param-header {
            display: flex;
            align-items: center;
            gap: 8px;
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
      
      .step-background-option {
        margin-top: 12px;
        padding: 8px 12px;
        background: var(--el-fill-color-light);
        border-radius: 4px;
        display: flex;
        align-items: center;
        gap: 8px;
        
        .el-checkbox {
          font-weight: 500;
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

.cron-quick-select {
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  
  .el-link {
    margin-left: 12px;
  }
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

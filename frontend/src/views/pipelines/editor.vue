<template>
  <div class="page-card">
    <div class="page-header">
      <h3 class="page-title">{{ isEdit ? '编辑编排' : '新建编排' }}</h3>
      <div>
        <el-button @click="$router.back()">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">保存</el-button>
      </div>
    </div>

    <el-form :model="form" label-width="100px" style="max-width: 1200px">
      <el-form-item label="名称" required>
        <el-input v-model="form.name" placeholder="请输入编排名称" style="width: 400px" />
      </el-form-item>
      <el-form-item label="描述">
        <el-input v-model="form.description" type="textarea" :rows="2" placeholder="请输入描述" style="width: 400px" />
      </el-form-item>
      <el-form-item label="最大并行数">
        <el-input-number v-model="form.maxParallel" :min="1" :max="20" />
        <span class="hint">（同时执行的最大任务数，默认5）</span>
      </el-form-item>

      <el-divider />

      <div class="tasks-header">
        <h4>任务列表</h4>
        <el-button type="primary" size="small" @click="addTask">添加任务</el-button>
      </div>

      <div v-if="form.tasks.length > 0" class="tasks-list">
        <div v-for="(task, index) in form.tasks" :key="index" class="task-card">
          <div class="task-header">
            <div class="task-index">{{ index + 1 }}</div>
            <el-input v-model="task.name" placeholder="任务名称" style="width: 200px" />
            <div class="task-actions">
              <el-button type="danger" size="small" @click="removeTask(index)">删除</el-button>
            </div>
          </div>

          <div class="task-content">
            <el-row :gutter="20">
              <el-col :span="8">
                <el-form-item label="选择脚本">
                  <el-select v-model="task.scriptId" placeholder="选择脚本" filterable @change="onScriptChange(task)">
                    <el-option
                      v-for="script in scripts"
                      :key="script.id"
                      :label="script.name"
                      :value="script.id"
                    />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="依赖任务">
                  <el-select v-model="task.dependsOn" multiple placeholder="选择依赖任务" clearable>
                    <el-option
                      v-for="(t, i) in form.tasks.filter((_, j) => j !== index)"
                      :key="i"
                      :label="t.name"
                      :value="t.name"
                    />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="超时时间">
                  <el-input-number v-model="task.timeout" :min="60" :max="86400" :step="60" />
                  <span class="unit">秒</span>
                </el-form-item>
              </el-col>
            </el-row>

            <!-- 步骤服务器配置 -->
            <div v-if="task.scriptSteps && task.scriptSteps.length > 0" class="step-config">
              <el-divider content-position="left">步骤配置</el-divider>
              
              <div class="steps-list">
                <div v-for="(step, stepIndex) in task.scriptSteps" :key="step.name" class="step-item">
                  <div class="step-header">
                    <div class="step-index">{{ stepIndex + 1 }}</div>
                    <div class="step-title">{{ step.displayName || step.name }}</div>
                    <div class="step-deps" v-if="step.dependsOn?.length">
                      <el-tag size="small" type="info">依赖: {{ step.dependsOn.join(', ') }}</el-tag>
                    </div>
                  </div>
                  
                  <div class="step-body">
                    <el-row :gutter="16">
                      <el-col :span="8">
                        <div class="field-label">执行服务器</div>
                        <el-select 
                          v-model="task.stepServerConfigs[stepIndex].serverId" 
                          placeholder="请选择服务器"
                          filterable
                          style="width: 100%"
                        >
                          <el-option
                            v-for="server in enabledServers"
                            :key="server.id"
                            :label="`${server.name} (${server.host})`"
                            :value="server.id"
                          />
                          <el-option :value="-1" label="本地环境 (平台本地执行)" />
                        </el-select>
                      </el-col>
                      
                      <!-- 步骤参数 -->
                      <el-col :span="16" v-if="step.params?.length">
                        <div class="field-label">步骤参数</div>
                        <div class="params-row">
                          <div v-for="param in step.params" :key="param.name" class="param-item">
                            <span class="param-label">{{ param.displayName || param.name }}</span>
                            <el-input
                              v-model="task.stepServerConfigs[stepIndex].stepParams[param.name]"
                              size="small"
                              :placeholder="param.defaultValue?.toString() || param.default?.toString()"
                            />
                          </div>
                        </div>
                      </el-col>
                    </el-row>
                  </div>
                </div>
              </div>
            </div>

            <!-- 共享参数配置 -->
            <div v-if="task.scriptParameters && task.scriptParameters.length > 0" class="shared-params">
              <el-divider content-position="left">共享参数</el-divider>
              <el-row :gutter="20">
                <el-col :span="8" v-for="param in task.scriptParameters" :key="param.name">
                  <el-form-item :label="param.displayName || param.name">
                    <el-input
                      v-model="task.sharedParams[param.name]"
                      :placeholder="'默认: ' + (param.default || '')"
                    />
                  </el-form-item>
                </el-col>
              </el-row>
            </div>
          </div>
        </div>
      </div>
      <el-empty v-else description="暂无任务，请添加" />
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getPipeline, createPipeline, updatePipeline, getPipelineTasks } from '@/api/pipeline'
import { scriptApi } from '@/api/script'
import { serverApi } from '@/api/server'

interface StepDefinition {
  name: string
  displayName: string
  params: { name: string; displayName: string; type: string; defaultValue?: any; default?: any }[]
  dependsOn: string[]
}

interface StepServerConfig {
  stepName: string
  displayName: string
  serverId: number | null
  stepParams: Record<string, any>
}

interface TaskConfig {
  name: string
  scriptId: number | null
  dependsOn: string[]
  timeout: number
  scriptSteps: StepDefinition[]
  scriptParameters: { name: string; displayName?: string; default?: any }[]
  stepServerConfigs: StepServerConfig[]
  sharedParams: Record<string, any>
}

const route = useRoute()
const router = useRouter()

const isEdit = computed(() => !!route.params.id)
const saving = ref(false)

const form = ref({
  name: '',
  description: '',
  maxParallel: 5,
  tasks: [] as TaskConfig[],
})

const scripts = ref<any[]>([])
const servers = ref<any[]>([])
const enabledServers = computed(() => servers.value.filter(s => s.enabled !== false))

const scriptCache = ref<Map<number, any>>(new Map())

async function loadScripts() {
  try {
    const res = await scriptApi.list({ page: 1, size: 100 })
    if (res.data) {
      scripts.value = res.data.records || res.data.items || []
    }
  } catch (e) {
    console.error('加载脚本失败:', e)
  }
}

async function loadServers() {
  try {
    const res = await serverApi.list({ page: 1, size: 100 })
    if (res.data) {
      servers.value = res.data.records || res.data.items || []
    }
  } catch (e) {
    console.error('加载服务器失败:', e)
  }
}

async function loadScriptDetail(scriptId: number, task: TaskConfig) {
  if (scriptCache.value.has(scriptId)) {
    const script = scriptCache.value.get(scriptId)
    applyScriptToTask(script, task)
    return
  }

  try {
    const res = await scriptApi.get(scriptId)
    if (res.code === 0 && res.data) {
      scriptCache.value.set(scriptId, res.data)
      applyScriptToTask(res.data, task)
    }
  } catch (e) {
    console.error('加载脚本详情失败:', e)
  }
}

function applyScriptToTask(script: any, task: TaskConfig) {
  // 如果任务名是默认的"任务X"，则自动使用脚本名
  const defaultNamePattern = /^任务\d+$/
  if (defaultNamePattern.test(task.name) || !task.name) {
    // 生成唯一的任务名称
    let baseName = script.name
    let finalName = baseName
    let suffix = 1
    
    // 检查是否与其他任务重名
    const existingNames = form.value.tasks
      .filter(t => t !== task)
      .map(t => t.name)
    
    while (existingNames.includes(finalName)) {
      finalName = `${baseName}_${suffix}`
      suffix++
    }
    
    task.name = finalName
  }
  
  // 解析步骤
  const stepsData = script.steps || {}
  const steps: StepDefinition[] = []
  for (const [stepName, stepConfig] of Object.entries(stepsData)) {
    if (stepName === '_meta') continue
    const config = stepConfig as any
    steps.push({
      name: stepName,
      displayName: config.displayName || stepName,
      params: config.params || [],
      dependsOn: config.dependsOn || []
    })
  }
  
  task.scriptSteps = steps
  task.scriptParameters = script.parameters || []
  
  // 初始化步骤服务器配置
  task.stepServerConfigs = steps.map(step => ({
    stepName: step.name,
    displayName: step.displayName || step.name,
    serverId: null,
    stepParams: {}
  }))
  
  // 设置默认参数值
  task.stepServerConfigs.forEach(config => {
    const step = steps.find(s => s.name === config.stepName)
    if (step?.params) {
      step.params.forEach(p => {
        config.stepParams[p.name] = p.defaultValue !== undefined ? p.defaultValue : p.default
      })
    }
  })
  
  // 设置共享参数默认值（仅在 empty 时初始化）
  if (!task.sharedParams || Object.keys(task.sharedParams).length === 0) {
    task.sharedParams = {}
    task.scriptParameters.forEach(p => {
      if (p.default !== undefined) {
        task.sharedParams[p.name] = p.default
      }
    })
  }
  
  // 如果没有步骤定义，创建默认步骤
  if (steps.length === 0) {
    task.scriptSteps = [{
      name: 'default',
      displayName: '执行脚本',
      params: [],
      dependsOn: []
    }]
    task.stepServerConfigs = [{
      stepName: 'default',
      displayName: '执行脚本',
      serverId: null,
      stepParams: {}
    }]
  }
}

function onScriptChange(task: TaskConfig) {
  if (task.scriptId) {
    loadScriptDetail(task.scriptId, task)
  } else {
    task.scriptSteps = []
    task.scriptParameters = []
    task.stepServerConfigs = []
    task.sharedParams = {}
  }
}

function createEmptyTask(): TaskConfig {
  return {
    name: `任务${form.value.tasks.length + 1}`,
    scriptId: null,
    dependsOn: [],
    timeout: 86400,
    scriptSteps: [],
    scriptParameters: [],
    stepServerConfigs: [],
    sharedParams: {}
  }
}

function addTask() {
  form.value.tasks.push(createEmptyTask())
}

function removeTask(index: number) {
  form.value.tasks.splice(index, 1)
}

async function loadPipeline() {
  if (!isEdit.value) return

  try {
    const id = Number(route.params.id)
    const [pipelineRes, tasksRes] = await Promise.all([
      getPipeline(id),
      getPipelineTasks(id),
    ])

    if (pipelineRes.code === 0 && pipelineRes.data) {
      form.value.name = pipelineRes.data.name
      form.value.description = pipelineRes.data.description || ''
      form.value.maxParallel = pipelineRes.data.maxParallel || 5
    }

    if (tasksRes.code === 0 && tasksRes.data) {
      form.value.tasks = []
      for (const t of tasksRes.data) {
        const task: TaskConfig = {
          name: t.name,
          scriptId: t.scriptId,
          dependsOn: t.dependsOn ? JSON.parse(t.dependsOn) : [],
          timeout: t.timeout || 86400,
          scriptSteps: [],
          scriptParameters: [],
          stepServerConfigs: [],
          sharedParams: t.sharedParams ? JSON.parse(t.sharedParams) : {}
        }
        
        // 加载脚本详情
        if (t.scriptId) {
          await loadScriptDetail(t.scriptId, task)
          
          // 恢复步骤服务器配置
          if (t.stepServerMapping) {
            const mapping = JSON.parse(t.stepServerMapping)
            task.stepServerConfigs.forEach(config => {
              const serverIds = mapping[config.stepName]
              if (serverIds && serverIds.length > 0) {
                config.serverId = serverIds[0]
              }
            })
          }
          
          // 恢复步骤参数
          if (t.stepParams) {
            const stepParams = JSON.parse(t.stepParams)
            task.stepServerConfigs.forEach(config => {
              if (stepParams[config.stepName]) {
                config.stepParams = { ...config.stepParams, ...stepParams[config.stepName] }
              }
            })
          }
        }
        
        form.value.tasks.push(task)
      }
    }
  } catch (e) {
    ElMessage.error('加载编排失败')
    router.back()
  }
}

async function handleSave() {
  if (!form.value.name) {
    ElMessage.warning('请输入编排名称')
    return
  }

  saving.value = true
  try {
    // 转换任务数据
    const tasks = form.value.tasks.map(task => {
      // 构建步骤服务器映射
      const stepServerMapping: Record<string, number[]> = {}
      task.stepServerConfigs.forEach(config => {
        if (config.serverId) {
          stepServerMapping[config.stepName] = [config.serverId]
        }
      })
      
      // 构建步骤参数
      const stepParams: Record<string, Record<string, any>> = {}
      task.stepServerConfigs.forEach(config => {
        if (Object.keys(config.stepParams).length > 0) {
          stepParams[config.stepName] = config.stepParams
        }
      })
      
      return {
        name: task.name,
        scriptId: task.scriptId,
        dependsOn: JSON.stringify(task.dependsOn),
        timeout: task.timeout * 1000, // 转毫秒
        stepServerMapping: JSON.stringify(stepServerMapping),
        stepParams: JSON.stringify(stepParams),
        sharedParams: JSON.stringify(task.sharedParams)
      }
    })

    const data = {
      name: form.value.name,
      description: form.value.description,
      maxParallel: form.value.maxParallel,
      tasks
    }

    if (isEdit.value) {
      await updatePipeline(Number(route.params.id), data)
      ElMessage.success('更新成功')
    } else {
      await createPipeline(data)
      ElMessage.success('创建成功')
    }
    router.push('/pipelines/list')
  } catch (e) {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadScripts(), loadServers()])
  await loadPipeline()
})
</script>

<style scoped lang="scss">
.tasks-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  
  h4 {
    margin: 0;
  }
}

.tasks-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.task-card {
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  overflow: hidden;
  
  .task-header {
    display: flex;
    align-items: center;
    gap: 16px;
    padding: 12px 16px;
    background: var(--el-fill-color-light);
    border-bottom: 1px solid var(--el-border-color);
    
    .task-index {
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
    
    .task-actions {
      margin-left: auto;
    }
  }
  
  .task-content {
    padding: 16px;
  }
}

.hint {
  margin-left: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.unit {
  margin-left: 8px;
  color: var(--el-text-color-secondary);
}

.step-config {
  margin-top: 16px;
  
  .steps-list {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }
  
  .step-item {
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 6px;
    overflow: hidden;
    
    .step-header {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 10px 12px;
      background: var(--el-fill-color-lighter);
      border-bottom: 1px solid var(--el-border-color-lighter);
      
      .step-index {
        width: 22px;
        height: 22px;
        display: flex;
        align-items: center;
        justify-content: center;
        background: var(--el-color-primary-light-5);
        color: white;
        border-radius: 50%;
        font-size: 12px;
        font-weight: 600;
      }
      
      .step-title {
        font-weight: 500;
        font-size: 14px;
      }
    }
    
    .step-body {
      padding: 12px;
      
      .field-label {
        font-size: 13px;
        color: var(--el-text-color-secondary);
        margin-bottom: 6px;
      }
      
      .params-row {
        display: flex;
        flex-wrap: wrap;
        gap: 12px;
        
        .param-item {
          display: flex;
          align-items: center;
          gap: 8px;
          
          .param-label {
            font-size: 12px;
            color: var(--el-text-color-regular);
            white-space: nowrap;
          }
          
          .el-input {
            width: 120px;
          }
        }
      }
    }
  }
}

.shared-params {
  margin-top: 16px;
}

.field-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-bottom: 6px;
}
</style>

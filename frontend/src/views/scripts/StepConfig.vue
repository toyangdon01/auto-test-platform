<template>
  <div class="step-config">
    <div class="config-header">
      <span class="title">执行计划</span>
      <el-button type="primary" link @click="addStep">
        <el-icon><Plus /></el-icon> 添加步骤
      </el-button>
    </div>

    <div v-for="(step, index) in steps" :key="index" class="step-item">
      <el-card shadow="hover" class="step-card">
        <template #header>
          <div class="step-header">
            <div class="step-title">
              <el-tag type="info" effect="plain" class="step-tag">步骤 {{ index + 1 }}</el-tag>
              <el-input
                v-model="step.displayName"
                placeholder="步骤名称（如：部署MySQL、执行压测）"
                style="width: 200px; margin-left: 10px"
              />
            </div>
            <el-button v-if="steps.length > 1" type="danger" link @click="removeStep(index)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
        </template>

        <el-form label-width="80px" size="small">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="执行脚本" required>
                <el-select 
                  v-model="step.script" 
                  placeholder="选择脚本或输入命令" 
                  style="width: 100%"
                  filterable
                  allow-create
                  :default-first-option="true"
                  teleported
                  :popper-options="{ modifiers: [{ name: 'flip', enabled: false }] }"
                >
                  <el-option
                    v-for="file in scriptFileOptions"
                    :key="file.path"
                    :label="file.path"
                    :value="file.path"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="依赖步骤">
                <el-select
                  v-model="step.dependsOn"
                  multiple
                  placeholder="选择依赖的步骤（可选）"
                  style="width: 100%"
                  clearable
                  teleported
                  :popper-options="{ modifiers: [{ name: 'flip', enabled: false }] }"
                >
                  <el-option
                    v-for="(s, i) in steps.filter((_, idx) => idx !== index)"
                    :key="s.name"
                    :label="`步骤${i + 1}: ${s.displayName || s.name || '未命名'}`"
                    :value="s.name"
                  />
                </el-select>
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="启动探测">
                <el-button 
                  v-if="!step.startupProbe" 
                  type="primary" 
                  link 
                  @click="addStartupProbe(step)"
                >
                  <el-icon><Plus /></el-icon> 添加探测
                </el-button>
                <div v-else class="probe-config">
                  <el-select v-model="step.startupProbe.type" style="width: 80px" teleported>
                    <el-option label="TCP" value="tcp" />
                    <el-option label="HTTP" value="http" />
                  </el-select>
                  <el-input
                    v-model="step.startupProbe.port"
                    placeholder="端口"
                    style="width: 80px; margin-left: 5px"
                    v-if="step.startupProbe.type === 'tcp'"
                  />
                  <el-input
                    v-model="step.startupProbe.path"
                    placeholder="路径"
                    style="width: 120px; margin-left: 5px"
                    v-if="step.startupProbe.type === 'http'"
                  />
                  <el-button type="danger" link @click="step.startupProbe = null" style="margin-left: 5px">
                    <el-icon><Close /></el-icon>
                  </el-button>
                </div>
              </el-form-item>
            </el-col>
          </el-row>

          <!-- 步骤参数 -->
          <el-form-item label="步骤参数">
            <div class="params-container">
              <div v-for="(param, pIndex) in step.params" :key="pIndex" class="param-row">
                <el-input v-model="param.name" placeholder="参数名" style="width: 150px" />
                <el-input v-model="param.defaultValue" placeholder="默认值" style="width: 150px; margin-left: 5px" />
                <el-input v-model="param.description" placeholder="说明" style="width: 200px; margin-left: 5px" />
                <el-button type="danger" link @click="step.params.splice(pIndex, 1)" style="margin-left: 5px">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </div>
              <el-button type="primary" link @click="addParam(step)">
                <el-icon><Plus /></el-icon> 添加参数
              </el-button>
            </div>
          </el-form-item>

          <!-- 步骤资源配置 -->
          <el-form-item>
            <template #label>
              <span>步骤资源</span>
              <el-tooltip content="此步骤执行时自动上传到服务器的资源文件" placement="top">
                <el-icon style="margin-left: 4px; cursor: help; color: #909399;"><QuestionFilled /></el-icon>
              </el-tooltip>
            </template>
            <div class="step-resources">
              <div v-for="(res, rIndex) in step.resources" :key="rIndex" class="resource-row">
                <el-select 
                  v-model="res.resourceId" 
                  placeholder="选择资源" 
                  filterable
                  style="width: 180px"
                  teleported
                >
                  <el-option
                    v-for="item in availableResources"
                    :key="item.id"
                    :label="item.name"
                    :value="item.id"
                  >
                    <span>{{ item.name }}</span>
                    <span style="color: #999; margin-left: 8px; font-size: 12px;">{{ formatSize(item.fileSize) }}</span>
                  </el-option>
                </el-select>
                <el-input v-model="res.targetPath" placeholder="目标路径" style="width: 120px; margin-left: 5px" />
                <el-input v-model="res.permissions" placeholder="权限" style="width: 80px; margin-left: 5px" />
                <el-button type="danger" link @click="step.resources.splice(rIndex, 1)" style="margin-left: 5px">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </div>
              <el-button type="primary" link @click="addResource(step)">
                <el-icon><Plus /></el-icon> 添加资源
              </el-button>
            </div>
          </el-form-item>

          <!-- 文件收集 -->
          <el-form-item>
            <template #label>
              <span>文件收集</span>
              <el-tooltip content="此步骤完成后从服务器收集的输出文件" placement="top">
                <el-icon style="margin-left: 4px; cursor: help; color: #909399;"><QuestionFilled /></el-icon>
              </el-tooltip>
            </template>
            <div class="step-outputs">
              <div class="output-switch">
                <el-switch
                  v-model="step.fileCollectEnabled"
                  active-text="启用"
                  inactive-text="禁用"
                />
              </div>
              <template v-if="step.fileCollectEnabled">
                <div v-for="(output, oIndex) in step.fileCollects" :key="oIndex" class="output-row">
                  <el-input v-model="output.name" placeholder="名称" style="width: 100px" />
                  <el-input v-model="output.path" placeholder="文件路径（如 /tmp/result.log）" style="width: 200px; margin-left: 5px" />
                  <el-select v-model="output.type" style="width: 90px; margin-left: 5px" teleported>
                    <el-option label="单文件" value="file" />
                    <el-option label="目录" value="directory" />
                    <el-option label="通配符" value="pattern" />
                  </el-select>
                  <el-checkbox v-model="output.required" style="margin-left: 8px">必须</el-checkbox>
                  <el-button type="danger" link @click="step.fileCollects.splice(oIndex, 1)" style="margin-left: 5px">
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </div>
                <el-button type="primary" link @click="addFileCollect(step)">
                  <el-icon><Plus /></el-icon> 添加收集规则
                </el-button>
              </template>
            </div>
          </el-form-item>

          <!-- 结果解析 -->
          <el-form-item>
            <template #label>
              <span>结果解析</span>
              <el-tooltip content="启用后解析此步骤的输出结果。多步骤场景下只能启用一个步骤的结果解析" placement="top">
                <el-icon style="margin-left: 4px; cursor: help; color: #909399;"><QuestionFilled /></el-icon>
              </el-tooltip>
            </template>
            <div class="result-parser">
              <el-radio-group 
                :model-value="resultParserStep === step.name ? step.name : ''"
                @change="(val: string) => handleResultParserChange(val, step.name)"
              >
                <el-radio :value="step.name">启用解析</el-radio>
                <el-radio value="">不解析</el-radio>
              </el-radio-group>
            </div>
          </el-form-item>

          <!-- 结果解析配置（仅当该步骤启用解析时显示） -->
          <template v-if="resultParserStep === step.name">
            <el-divider content-position="left">
              <span>解析规则配置</span>
            </el-divider>
            
            <el-form-item>
              <template #label>
                <span>解析方式</span>
              </template>
              <el-radio-group v-model="step.parseRule.parserType">
                <el-radio value="builtin">内置规则</el-radio>
                <el-radio value="script">自定义脚本</el-radio>
              </el-radio-group>
            </el-form-item>

            <!-- 内置规则配置 -->
            <template v-if="step.parseRule.parserType === 'builtin'">
              <el-form-item label="内置格式">
                <el-select v-model="step.parseRule.builtinFormat" placeholder="选择内置格式" style="width: 200px" teleported>
                  <el-option label="Key-Value 格式 (key=value 或 key: value)" value="key_value" />
                  <el-option label="JSON 格式" value="json" />
                </el-select>
              </el-form-item>
            </template>

            <!-- 自定义脚本配置 -->
            <template v-if="step.parseRule.parserType === 'script'">
              <el-form-item label="脚本来源">
                <el-radio-group v-model="step.parseRule.scriptSource">
                  <el-radio value="package">从脚本包选择</el-radio>
                  <el-radio value="inline">直接编写</el-radio>
                </el-radio-group>
              </el-form-item>

              <el-form-item v-if="step.parseRule.scriptSource === 'package'" label="解析脚本">
                <el-select v-model="step.parseRule.scriptPath" placeholder="选择解析脚本" style="width: 200px" teleported>
                  <el-option
                    v-for="file in scriptFileOptions"
                    :key="file.path"
                    :label="file.path"
                    :value="file.path"
                  />
                </el-select>
              </el-form-item>

              <template v-if="step.parseRule.scriptSource === 'inline'">
                <el-form-item label="脚本语言">
                  <el-select v-model="step.parseRule.scriptLanguage" style="width: 120px" teleported>
                    <el-option label="Python" value="python" />
                    <el-option label="Shell" value="shell" />
                  </el-select>
                </el-form-item>

                <el-form-item label="解析脚本">
                  <el-input
                    v-model="step.parseRule.scriptContent"
                    type="textarea"
                    :rows="8"
                    placeholder="#!/usr/bin/env python3
import sys, json

# 从标准输入读取
content = sys.stdin.read()

# 解析逻辑
result = { ... }

# 输出 JSON
print(json.dumps(result))"
                    class="code-textarea"
                  />
                </el-form-item>
              </template>
            </template>

            <el-form-item label="输入来源">
              <el-radio-group v-model="step.parseRule.inputSource">
                <el-radio value="stdout">标准输出</el-radio>
                <el-radio value="file">指定文件</el-radio>
              </el-radio-group>
            </el-form-item>

            <el-form-item v-if="step.parseRule.inputSource === 'file'" label="文件路径">
              <el-input v-model="step.parseRule.filePattern" placeholder="如 results/.*\.json" style="width: 300px" />
            </el-form-item>
          </template>
        </el-form>
      </el-card>
    </div>

    <!-- 空状态 -->
    <el-empty v-if="steps.length === 0" description="暂无执行步骤">
      <el-button type="primary" @click="addStep">添加第一个步骤</el-button>
    </el-empty>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted } from 'vue'
import { Plus, Delete, Close, QuestionFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { resourceApi, type ResourceFile } from '@/api/resource'

// 防止循环更新的标志
let isUpdating = false

interface StepParam {
  name: string
  defaultValue: string
  description: string
}

interface StartupProbe {
  type: 'tcp' | 'http'
  port?: string
  path?: string
  timeoutSeconds?: number
}

interface StepResource {
  resourceId: number | null
  targetPath: string
  permissions: string
}

interface FileCollect {
  name: string
  path: string
  type: 'file' | 'directory' | 'pattern'
  required: boolean
}

interface ParseRule {
  parserType: 'builtin' | 'script'
  builtinFormat: 'key_value' | 'json'
  scriptSource: 'package' | 'inline'
  scriptPath: string
  scriptContent: string
  scriptLanguage: 'python' | 'shell'
  inputSource: 'stdout' | 'file'
  filePattern: string
}

interface Step {
  name: string
  displayName: string
  script: string
  dependsOn: string[]
  startupProbe: StartupProbe | null
  params: StepParam[]
  resources: StepResource[]
  fileCollectEnabled: boolean
  fileCollects: FileCollect[]
  parseRule: ParseRule
}

const props = defineProps<{
  modelValue: Record<string, any> | null
  scriptFiles: Array<{ path: string; name: string }>
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: Record<string, any>): void
}>()

// 步骤列表
const steps = ref<Step[]>([])

// 结果解析启用的步骤名称（单选）
const resultParserStep = ref<string>('')

// 所有可用资源
const allResources = ref<ResourceFile[]>([])

// 脚本文件选项
const scriptFileOptions = computed(() => {
  return props.scriptFiles || []
})

// 可用资源列表
const availableResources = computed(() => {
  return allResources.value
})

// 格式化文件大小
function formatSize(size: number): string {
  if (!size) return '-'
  if (size < 1024) return size + ' B'
  if (size < 1024 * 1024) return (size / 1024).toFixed(1) + ' KB'
  return (size / (1024 * 1024)).toFixed(1) + ' MB'
}

// 默认解析规则
function defaultParseRule(): ParseRule {
  return {
    parserType: 'builtin',
    builtinFormat: 'json',
    scriptSource: 'inline',
    scriptPath: '',
    scriptContent: '',
    scriptLanguage: 'python',
    inputSource: 'stdout',
    filePattern: ''
  }
}

// 加载所有资源
async function loadAllResources() {
  try {
    const response = await resourceApi.getPage({ pageNum: 1, pageSize: 1000 })
    allResources.value = response.data.records || []
  } catch (error) {
    console.error('加载资源列表失败:', error)
  }
}

// 生成步骤名称
const generateStepName = (index: number): string => {
  return `step_${index + 1}`
}

// 处理结果解析切换（单选逻辑）
const handleResultParserChange = (selectedStep: string, currentStep: string) => {
  if (selectedStep === currentStep) {
    resultParserStep.value = currentStep
  } else {
    resultParserStep.value = ''
  }
  updateModelValue()
}

// 添加步骤
const addStep = () => {
  const newStep: Step = {
    name: generateStepName(steps.value.length),
    displayName: '',
    script: '',
    dependsOn: [],
    startupProbe: null,
    params: [],
    resources: [],
    fileCollectEnabled: false,
    fileCollects: [],
    parseRule: defaultParseRule()
  }
  steps.value.push(newStep)
  
  // 不再默认启用结果解析
  updateModelValue()
}

// 删除步骤
const removeStep = (index: number) => {
  const removedName = steps.value[index].name
  
  // 如果删除的是启用了结果解析的步骤，清空选择
  if (resultParserStep.value === removedName) {
    resultParserStep.value = ''
  }
  
  // 清理其他步骤对该步骤的依赖
  steps.value.forEach(step => {
    if (step.dependsOn) {
      step.dependsOn = step.dependsOn.filter(d => d !== removedName)
    }
  })
  
  steps.value.splice(index, 1)
  
  // 重新生成步骤名称
  steps.value.forEach((step, idx) => {
    step.name = generateStepName(idx)
  })
  
  // 更新 resultParserStep 引用
  if (resultParserStep.value) {
    const idx = parseInt(resultParserStep.value.split('_')[1]) - 1
    if (idx >= index && idx > 0) {
      resultParserStep.value = `step_${idx}`
    }
  }
  
  updateModelValue()
}

// 添加启动探测
const addStartupProbe = (step: Step) => {
  step.startupProbe = {
    type: 'tcp',
    port: '',
    timeoutSeconds: 60
  }
}

// 添加参数
const addParam = (step: Step) => {
  if (!step.params) {
    step.params = []
  }
  step.params.push({
    name: '',
    defaultValue: '',
    description: ''
  })
}

// 添加资源
const addResource = (step: Step) => {
  if (!step.resources) {
    step.resources = []
  }
  step.resources.push({
    resourceId: null,
    targetPath: '/tmp',
    permissions: '644'
  })
}

// 添加文件收集规则
const addFileCollect = (step: Step) => {
  if (!step.fileCollects) {
    step.fileCollects = []
  }
  step.fileCollects.push({
    name: '',
    path: '',
    type: 'file',
    required: false
  })
}

// 解析模型值
const parseModelValue = (value: Record<string, any> | null) => {
  if (isUpdating) return // 防止循环更新
  
  if (!value || Object.keys(value).length === 0) {
    // 默认创建一个步骤，不启用结果解析
    steps.value = [{
      name: 'step_1',
      displayName: '',
      script: '',
      dependsOn: [],
      startupProbe: null,
      params: [],
      resources: [],
      fileCollectEnabled: false,
      fileCollects: [],
      parseRule: defaultParseRule()
    }]
    resultParserStep.value = '' // 默认不开启
    return
  }

  const parsedSteps: Step[] = []
  let foundParserStep = ''
  
  // 支持新格式 { step_1: {...}, step_2: {...} }
  for (const [stepName, stepData] of Object.entries(value)) {
    if (stepName === 'roles' || stepName === 'steps' || stepName === '_meta') continue // 跳过特殊字段
    
    if (typeof stepData === 'object' && stepData !== null) {
      const step: Step = {
        name: stepName,
        displayName: stepData.displayName || '',
        script: stepData.script || stepData.entryScript || '',
        dependsOn: stepData.dependsOn || [],
        startupProbe: stepData.startupProbe || null,
        params: stepData.params || [],
        resources: stepData.resources || [],
        fileCollectEnabled: stepData.fileCollectEnabled === true || stepData.outputCollectEnabled === true,
        fileCollects: stepData.fileCollects || stepData.outputs || [],
        parseRule: stepData.parseRule ? {
          parserType: stepData.parseRule.parserType || 'builtin',
          builtinFormat: stepData.parseRule.builtinFormat || 'json',
          scriptSource: stepData.parseRule.scriptSource || 'inline',
          scriptPath: stepData.parseRule.scriptPath || '',
          scriptContent: stepData.parseRule.scriptContent || '',
          scriptLanguage: stepData.parseRule.scriptLanguage || 'python',
          inputSource: stepData.parseRule.inputSource || 'stdout',
          filePattern: stepData.parseRule.filePattern || ''
        } : defaultParseRule()
      }
      
      // 检查是否启用了结果解析
      if (stepData.resultParser === true || stepData.resultCollector === true) {
        foundParserStep = stepName
      }
      
      parsedSteps.push(step)
    }
  }
  
  if (parsedSteps.length === 0) {
    // 支持旧格式 { roles: [...] }
    const roles = (value as any).roles || []
    roles.forEach((role: any, index: number) => {
      const step: Step = {
        name: role.name || generateStepName(index),
        displayName: role.displayName || '',
        script: role.script || role.entryScript || '',
        dependsOn: role.dependsOn || [],
        startupProbe: role.startupProbe || null,
        params: role.params || [],
        resources: [],
        fileCollectEnabled: false,
        fileCollects: [],
        parseRule: defaultParseRule()
      }
      if (role.resultCollector !== false) {
        foundParserStep = step.name
      }
      parsedSteps.push(step)
    })
  }
  
  steps.value = parsedSteps.length > 0 ? parsedSteps : [{
    name: 'step_1',
    displayName: '',
    script: '',
    dependsOn: [],
    startupProbe: null,
    params: [],
    resources: [],
    fileCollectEnabled: false,
    fileCollects: [],
    parseRule: defaultParseRule()
  }]
  
  // 恢复 resultParserStep 状态
  resultParserStep.value = foundParserStep
}

// 更新模型值
const updateModelValue = () => {
  isUpdating = true
  
  const result: Record<string, any> = {
    _meta: {
      resultParserStep: resultParserStep.value
    }
  }
  
  steps.value.forEach(step => {
    result[step.name] = {
      displayName: step.displayName,
      script: step.script,
      dependsOn: step.dependsOn,
      resultParser: resultParserStep.value === step.name,
      startupProbe: step.startupProbe,
      params: step.params,
      resources: step.resources,
      fileCollectEnabled: step.fileCollectEnabled,
      fileCollects: step.fileCollects,
      parseRule: step.parseRule
    }
  })
  
  emit('update:modelValue', result)
  
  // 在下一个 tick 重置标志
  nextTick(() => {
    isUpdating = false
  })
}

// 监听模型值变化
watch(() => props.modelValue, (newVal, oldVal) => {
  // 只有当值真正改变时才解析
  // 防止在用户输入时重新解析导致数据丢失
  if (isUpdating) return
  
  // 比较新旧值，如果相同则不重新解析
  if (JSON.stringify(newVal) === JSON.stringify(oldVal)) return
  
  parseModelValue(newVal)
}, { immediate: true })

// 监听步骤变化（仅在用户交互时触发）
watch(steps, () => {
  if (!isUpdating) {
    updateModelValue()
  }
}, { deep: true })

// 监听 resultParserStep 变化
watch(resultParserStep, () => {
  if (!isUpdating) {
    updateModelValue()
  }
})

// 初始化加载资源
onMounted(() => {
  loadAllResources()
})
</script>

<style lang="scss" scoped>
.step-config {
  width: 100%;
  
  .config-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 15px;
    
    .title {
      font-size: 14px;
      font-weight: 600;
      color: #303133;
    }
  }
  
  .step-item {
    margin-bottom: 15px;
    
    .step-card {
      width: 100%;
      
      :deep(.el-card__body) {
        width: 100%;
      }
    }
    
    .step-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      
      .step-title {
        display: flex;
        align-items: center;
        
        .step-tag {
          font-size: 12px;
        }
      }
    }
  }
  
  .probe-config {
    display: flex;
    align-items: center;
  }
  
  .params-container {
    width: 100%;
    
    .param-row {
      display: flex;
      align-items: center;
      margin-bottom: 8px;
    }
  }
  
  .step-resources {
    width: 100%;
    
    .resource-row {
      display: flex;
      align-items: center;
      margin-bottom: 8px;
    }
  }
  
  .step-outputs {
    width: 100%;
    
    .output-switch {
      margin-bottom: 10px;
    }
    
    .output-row {
      display: flex;
      align-items: center;
      margin-bottom: 8px;
    }
  }
  
  .result-parser {
    width: 100%;
  }
  
  .code-textarea {
    :deep(textarea) {
      font-family: 'Fira Code', 'Monaco', 'Menlo', monospace;
      font-size: 13px;
      line-height: 1.5;
    }
  }
}
</style>

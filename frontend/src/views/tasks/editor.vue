<template>
  <div class="page-card task-editor" v-loading="loading">
    <div class="page-header">
      <el-page-header @back="$router.back()">
        <template #content>
          <h3 class="page-title">{{ isEdit ? '编辑任务' : '创建任务' }}</h3>
        </template>
      </el-page-header>
    </div>

    <!-- ① 选择脚本 -->
    <section id="task-section-script" class="form-section" :class="sectionClass('script')">
      <header class="section-head">
        <span class="section-index">1</span>
        <div class="section-heading">
          <h4 class="section-title">选择脚本</h4>
          <p class="section-sub">选定后自动加载该脚本的步骤定义与共享参数</p>
        </div>
        <span class="section-state">{{ sectionBadge('script') }}</span>
      </header>

      <div class="section-body">
        <el-select
          v-model="selectedScriptId"
          filterable
          clearable
          :disabled="isEdit"
          :placeholder="isEdit ? '编辑任务时不可更换脚本' : '搜索脚本名称'"
          class="script-select"
          @change="onScriptChange"
          @clear="onScriptClear"
        >
          <el-option v-for="s in scripts" :key="s.id" :label="formatScriptName(s.name)" :value="s.id">
            <div class="option-row">
              <span>{{ formatScriptName(s.name) }}</span>
              <span class="option-meta">{{ getCategoryText(s.testCategory) }} · {{ s.currentVersion }}</span>
            </div>
          </el-option>
        </el-select>

        <div v-if="selectedScript" class="script-summary">
          <div class="summary-item">
            <span class="summary-label">脚本名称</span>
            <span class="summary-value">{{ formatScriptName(selectedScript.name) }}</span>
          </div>
          <div class="summary-item">
            <span class="summary-label">测试类型</span>
            <el-tag size="small">{{ getCategoryText(selectedScript.testCategory) }}</el-tag>
          </div>
          <div class="summary-item">
            <span class="summary-label">版本</span>
            <span class="summary-value">{{ formData.scriptVersion || selectedScript.currentVersion }}</span>
          </div>
          <div class="summary-item">
            <span class="summary-label">执行步骤</span>
            <span class="summary-value">{{ scriptSteps.length }} 个</span>
          </div>
        </div>
      </div>
    </section>

    <!-- ② 步骤与执行服务器 -->
    <section id="task-section-server" class="form-section" :class="sectionClass('server')">
      <header class="section-head">
        <span class="section-index">2</span>
        <div class="section-heading">
          <h4 class="section-title">步骤与执行服务器</h4>
          <p class="section-sub">为每个步骤指定执行服务器，并可单独覆盖该步骤的参数</p>
        </div>
        <span class="section-state">{{ sectionBadge('server') }}</span>
      </header>

      <div class="section-body">
        <div v-if="!selectedScript" class="section-placeholder">请先在上方选择脚本</div>
        <div v-else-if="scriptSteps.length === 0" class="section-placeholder">该脚本未定义执行步骤</div>

        <div v-else class="step-server-assignment">
          <div class="steps-list">
            <div v-for="(step, index) in scriptSteps" :key="step.name" class="step-card" :class="{ 'is-unassigned': !stepServerConfigs[index]?.serverId }">
              <div class="step-header">
                <div class="step-index">{{ index + 1 }}</div>
                <div class="step-title">{{ step.displayName || step.name }}</div>
                <div class="step-deps" v-if="step.dependsOn?.length">
                  <el-tag size="small" type="info">依赖: {{ step.dependsOn.join(', ') }}</el-tag>
                </div>
              </div>

              <div class="step-body">
                <div class="step-row">
                  <div class="field-label">执行服务器</div>
                  <el-select
                    v-model="stepServerConfigs[index].serverId"
                    placeholder="请选择服务器"
                    filterable
                    style="width: 320px"
                    @change="(val: number) => onStepServerChange(index, val)"
                  >
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
                    <el-option key="local" label="本地环境 (平台本地执行)" :value="-1">
                      <span>⭐ 本地环境</span>
                      <span style="color: #999; margin-left: 10px;">平台本地执行</span>
                    </el-option>
                  </el-select>
                </div>

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

                <div class="step-background-option">
                  <el-checkbox
                    v-model="stepServerConfigs[index].stepParams._BACKGROUND"
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
        </div>
      </div>
    </section>

    <!-- ③ 共享参数 -->
    <section id="task-section-params" class="form-section">
      <header class="section-head">
        <span class="section-index">3</span>
        <div class="section-heading">
          <h4 class="section-title">共享参数</h4>
          <p class="section-sub">所有步骤共用的参数，留空则使用脚本定义的默认值</p>
        </div>
        <span class="section-state neutral">{{ selectedScript ? `${scriptParameters.length} 个参数` : '待选择脚本' }}</span>
      </header>

      <div class="section-body">
        <div v-if="!selectedScript" class="section-placeholder">请先在上方选择脚本</div>

        <template v-else>
          <el-alert type="info" :closable="false" class="mb-4">
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

          <el-form v-if="scriptParameters.length > 0" :model="formData" label-width="140px" class="params-form">
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
          </el-form>
          <div v-else class="section-placeholder">该脚本未定义共享参数</div>
        </template>
      </div>
    </section>

    <!-- ④ 执行设置 -->
    <section id="task-section-exec" class="form-section" :class="sectionClass('exec')">
      <header class="section-head">
        <span class="section-index">4</span>
        <div class="section-heading">
          <h4 class="section-title">执行设置</h4>
          <p class="section-sub">任务名称、触发方式与超时策略</p>
        </div>
        <span class="section-state">{{ sectionBadge('exec') }}</span>
      </header>

      <div class="section-body">
        <el-form :model="formData" label-width="140px">
          <el-form-item>
            <template #label>
              任务名称
              <el-tooltip content="任务的标识名称，用于区分不同的测试任务" placement="top">
                <el-icon class="field-tip-icon"><QuestionFilled /></el-icon>
              </el-tooltip>
            </template>
            <el-input
              v-model="formData.name"
              placeholder="请输入任务名称"
              :class="{ 'field-error': submitAttempted && !formData.name.trim() }"
            />
          </el-form-item>

          <el-form-item label="任务描述">
            <el-input
              v-model="formData.description"
              type="textarea"
              :rows="2"
              maxlength="200"
              show-word-limit
              placeholder="选填，用于说明本次测试的目的或背景"
            />
          </el-form-item>

          <el-form-item label="执行模式">
            <el-radio-group v-model="formData.executionMode">
              <el-radio value="immediate">立即执行</el-radio>
              <el-radio value="scheduled_once">指定时间</el-radio>
              <el-radio value="scheduled_cron">周期执行</el-radio>
            </el-radio-group>
          </el-form-item>

          <el-form-item v-if="formData.executionMode === 'scheduled_once'" label="执行时间">
            <el-date-picker
              v-model="formData.scheduledTime"
              type="datetime"
              placeholder="选择执行时间"
              format="YYYY-MM-DD HH:mm"
              value-format="YYYY-MM-DDTHH:mm:ss"
              :disabled-date="(time: Date) => time.getTime() < Date.now() - 86400000"
              :class="{ 'field-error': submitAttempted && !formData.scheduledTime }"
            />
          </el-form-item>

          <el-form-item v-if="formData.executionMode === 'scheduled_cron'" label="Cron表达式">
            <el-input
              v-model="formData.cronExpression"
              placeholder="如: 0 0 2 * * ? (每天凌晨2点)"
              :class="{ 'field-error': submitAttempted && !formData.cronExpression.trim() }"
            />
            <div class="cron-quick-select">
              <span>常用:</span>
              <el-link type="primary" @click="formData.cronExpression = '0 0 2 * * ?'">每天凌晨2点</el-link>
              <el-link type="primary" @click="formData.cronExpression = '0 0 */6 * * ?'">每6小时</el-link>
              <el-link type="primary" @click="formData.cronExpression = '0 0 * * * ?'">每小时</el-link>
              <el-link type="primary" @click="formData.cronExpression = '0 */30 * * * ?'">每30分钟</el-link>
            </div>
          </el-form-item>

          <el-form-item>
            <template #label>
              超时时间
              <el-tooltip content="单步骤执行超时时间，超时后任务将失败" placement="top">
                <el-icon class="field-tip-icon"><QuestionFilled /></el-icon>
              </el-tooltip>
            </template>
            <el-input-number
              v-model="formData.timeout"
              :min="1"
              :step="600"
            />
            <span class="unit">秒</span>
            <span class="field-hint">（约 {{ Math.round(formData.timeout / 3600 * 10) / 10 }} 小时，默认 86400 秒 = 24 小时）</span>
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
    </section>

    <!-- 底部粘性操作栏 -->
    <div class="editor-footer" :class="{ 'is-ready': issues.length === 0 }">
      <div class="footer-status">
        <template v-if="issues.length === 0">
          <el-icon class="status-icon ok"><CircleCheckFilled /></el-icon>
          <span>配置完整，可以提交</span>
        </template>
        <template v-else>
          <el-icon class="status-icon warn"><WarningFilled /></el-icon>
          <span class="status-text">还有 {{ issues.length }} 项待完成：</span>
          <button
            v-for="group in groupedIssues"
            :key="group.section"
            type="button"
            class="issue-chip"
            :title="group.items[0].message"
            @click="jumpTo(group.section)"
          >
            {{ group.title }}
            <em>{{ group.items.length }}</em>
          </button>
        </template>
      </div>

      <el-button type="primary" :loading="submitting" @click="handleSubmit">
        {{ isEdit ? '保存修改' : '创建任务' }}
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { QuestionFilled, CircleCheckFilled, WarningFilled } from '@element-plus/icons-vue'
import { scriptApi, taskApi, type Script } from '@/api/script'
import MetricCollectConfig from '@/components/MetricCollectConfig.vue'
import { serverApi, type Server } from '@/api/server'
import request from '@/utils/request'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const submitting = ref(false)
const submitAttempted = ref(false)

// 编辑模式
const taskId = computed(() => route.params.id ? Number(route.params.id) : null)
const isEdit = computed(() => !!taskId.value)

const scripts = ref<Script[]>([])
const servers = ref<Server[]>([])
const selectedScript = ref<Script | null>(null)
const selectedScriptId = ref<number | null>(null)

// 过滤掉禁用的服务器
const enabledServers = computed(() => servers.value.filter(s => (s as any).enabled !== false))

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
  stepParams: Record<string, any>
}

const scriptSteps = ref<StepDefinition[]>([])
const stepServerConfigs = ref<StepServerConfig[]>([])
const scriptParameters = ref<{ name: string; displayName?: string; default?: any; description?: string }[]>([])

const formData = reactive({
  name: '',
  description: '',
  scriptVersion: '',
  timeout: 86400,  // 默认 24 小时
  executionMode: 'immediate',  // immediate/scheduled_once/scheduled_cron
  scheduledTime: '',
  cronExpression: '',
  sharedParams: {} as Record<string, any>,
  collectConfig: { enabled: false } as any,
})

// 已选择的服务器 ID 列表（用于动态加载设备和网卡）
const selectedServerIds = computed(() => {
  if (scriptSteps.value.length > 0) {
    return stepServerConfigs.value
      .map(c => c.serverId)
      .filter(id => id !== null && id !== undefined)
  }
  return []
})

type SectionKey = 'script' | 'server' | 'exec'

interface Issue {
  section: SectionKey
  message: string
}

const SECTION_TITLES: Record<SectionKey, string> = {
  script: '选择脚本',
  server: '步骤与服务器',
  exec: '执行设置',
}

// 平铺布局下没有「下一步」这个卡点，改为集中式校验：
// 每个区块的完成度与底部汇总条都由这一份 issues 派生，避免出现两套判断逻辑。
const issues = computed<Issue[]>(() => {
  const list: Issue[] = []

  if (!selectedScript.value) {
    list.push({ section: 'script', message: '请选择要执行的脚本' })
  } else {
    stepServerConfigs.value.forEach(config => {
      if (config.serverId === null || config.serverId === undefined) {
        list.push({ section: 'server', message: `步骤「${config.displayName}」未选择执行服务器` })
      }
    })
  }

  if (!formData.name.trim()) {
    list.push({ section: 'exec', message: '请输入任务名称' })
  }
  if (formData.executionMode === 'scheduled_once' && !formData.scheduledTime) {
    list.push({ section: 'exec', message: '请选择定时执行时间' })
  }
  if (formData.executionMode === 'scheduled_cron' && !formData.cronExpression.trim()) {
    list.push({ section: 'exec', message: '请填写 Cron 表达式' })
  }

  return list
})

const groupedIssues = computed(() => {
  const order: SectionKey[] = ['script', 'server', 'exec']
  return order
    .map(section => ({ section, title: SECTION_TITLES[section], items: issues.value.filter(i => i.section === section) }))
    .filter(g => g.items.length > 0)
})

function issueCount(section: SectionKey) {
  return issues.value.filter(i => i.section === section).length
}

// 未尝试提交前显示中性的「待完成」，尝试后才升级为红色「未通过」，避免一进来满屏报错。
function sectionClass(section: SectionKey): string {
  if (section === 'server' && !selectedScript.value) return 'is-idle'
  const n = issueCount(section)
  if (n === 0) return 'is-done'
  return submitAttempted.value ? 'is-error' : 'is-todo'
}

function sectionBadge(section: SectionKey): string {
  if (section === 'server' && !selectedScript.value) return '待选择脚本'
  const n = issueCount(section)
  if (n === 0) {
    if (section === 'server') return `${stepServerConfigs.value.length} 个步骤已分配`
    return '已完成'
  }
  return submitAttempted.value ? `${n} 项未完成` : '待填写'
}

function jumpTo(section: SectionKey) {
  document.getElementById(`task-section-${section}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

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

function onScriptChange(scriptId: number | null) {
  const script = scriptId ? scripts.value.find(s => s.id === scriptId) || null : null
  handleScriptSelect(script)
}

function onScriptClear() {
  handleScriptSelect(null)
}

function handleScriptSelect(row: Script | null) {
  selectedScript.value = row
  selectedScriptId.value = row ? row.id : null
  formData.name = row ? `${row.name}-${new Date().toISOString().slice(0, 10)}` : ''

  if (row) {
    loadScriptSteps(row)
  } else {
    scriptSteps.value = []
    stepServerConfigs.value = []
    scriptParameters.value = []
  }
}

// 加载脚本步骤定义
async function loadScriptSteps(script: Script) {
  try {
    const res = await scriptApi.get(script.id)
    if (res.code === 0 && res.data) {
      const stepsData = (res.data as any).steps || {}

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

      stepServerConfigs.value = scriptSteps.value.map(step => ({
        stepName: step.name,
        displayName: step.displayName || step.name,
        serverId: null,
        serverName: '',
        stepParams: {}
      }))

      stepServerConfigs.value.forEach(config => {
        const step = scriptSteps.value.find(s => s.name === config.stepName)
        if (step?.params) {
          step.params.forEach(p => {
            // 只在未配置时使用默认值，保留用户的已有配置
            if (config.stepParams[p.name] === undefined) {
              config.stepParams[p.name] = p.defaultValue !== undefined ? p.defaultValue : p.default
            }
          })
        }
      })

      const parametersData = (res.data as any).parameters || []
      scriptParameters.value = parametersData

      parametersData.forEach((p: any) => {
        if (p.default !== undefined && formData.sharedParams[p.name] === undefined) {
          formData.sharedParams[p.name] = p.default
        }
      })

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
  submitAttempted.value = true

  if (issues.value.length > 0) {
    ElMessage.warning(issues.value[0].message)
    jumpTo(issues.value[0].section)
    return
  }

  // 构建步骤-服务器映射
  const stepServerMapping: Record<string, number[]> = {}
  stepServerConfigs.value.forEach(config => {
    if (!stepServerMapping[config.stepName]) {
      stepServerMapping[config.stepName] = []
    }
    if (config.serverId !== null && !stepServerMapping[config.stepName].includes(config.serverId)) {
      // 本地执行使用 -1 作为特殊标记
      stepServerMapping[config.stepName].push(config.serverId)
    }
  })

  // 构建步骤参数映射
  const stepParams: Record<string, Record<string, any>> = {}
  stepServerConfigs.value.forEach(config => {
    if (config.serverId !== null && Object.keys(config.stepParams).length > 0) {
      if (!stepParams[config.stepName]) {
        stepParams[config.stepName] = config.stepParams
      }
    }
  })

  const allServerIds = [...new Set(stepServerConfigs.value
    .map(c => c.serverId)
    .filter(id => id !== null && id !== undefined) as number[])]

  const data = {
    name: formData.name,
    description: formData.description,
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

  submitting.value = true
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
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  await fetchScripts()
  await fetchServers()

  if (isEdit.value) {
    await loadTaskData()
  } else if (route.query.scriptId) {
    // 从脚本中心「执行」跳转过来时带预选脚本
    const scriptId = Number(route.query.scriptId)
    const res = await scriptApi.get(scriptId)
    if (res.code === 0) {
      handleScriptSelect(res.data)
    }
  }
})

// 加载任务数据（编辑模式）
async function loadTaskData() {
  loading.value = true
  try {
    const res = await request.get<any>(`/tasks/${taskId.value}`)
    if (res.code === 0) {
      const task = res.data

      formData.name = task.name
      formData.description = task.description || ''
      formData.scriptVersion = task.scriptVersion
      formData.timeout = Math.round((task.timeout || 86400000) / 1000)
      formData.sharedParams = task.sharedParams || {}
      formData.collectConfig = task.collectConfig || { enabled: false }
      formData.executionMode = restoreExecutionMode(task)

      if (task.scriptId) {
        const scriptRes = await scriptApi.get(task.scriptId)
        if (scriptRes.code === 0) {
          const script = scriptRes.data
          // 编辑模式下列表可能不含该脚本，补进选项以保证下拉能正确显示名称
          if (!scripts.value.some(s => s.id === script.id)) {
            scripts.value = [...scripts.value, script]
          }
          // 不走 handleScriptSelect：它会用「脚本名-日期」重生成任务名，覆盖上面恢复的已存值
          selectedScript.value = script
          selectedScriptId.value = script.id
          await loadScriptSteps(script)
        }
      }

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

// 后端只存 executionMode=immediate/scheduled，需结合已存字段还原成三态单选
function restoreExecutionMode(task: any): string {
  if (task.executionMode !== 'scheduled') return 'immediate'
  return task.cronExpression ? 'scheduled_cron' : 'scheduled_once'
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
.task-editor {
  // 给底部粘性操作栏留出空间
  padding-bottom: 88px;
}

// ===== 区块 =====
.form-section {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  margin-bottom: 16px;
  scroll-margin-top: 12px;
  transition: border-color 0.2s;

  &.is-error {
    border-color: var(--el-color-danger);
  }

  &.is-done {
    border-color: var(--el-border-color-lighter);
  }
}

.section-head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 18px;
  background: var(--el-fill-color-light);
  border-bottom: 1px solid var(--el-border-color-lighter);
  border-radius: 8px 8px 0 0;

  .section-index {
    flex-shrink: 0;
    width: 26px;
    height: 26px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: var(--el-color-primary);
    color: #fff;
    border-radius: 50%;
    font-size: 13px;
    font-weight: 600;
  }

  .section-heading {
    flex: 1;
    min-width: 0;
  }

  .section-title {
    margin: 0;
    font-size: 15px;
    font-weight: 600;
    color: var(--el-text-color-primary);
  }

  .section-sub {
    margin: 2px 0 0;
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }

  .section-state {
    flex-shrink: 0;
    font-size: 12px;
    padding: 2px 10px;
    border-radius: 10px;
    background: var(--el-color-info-light-9);
    color: var(--el-text-color-secondary);

    .is-done > & {
      background: var(--el-color-success-light-9);
      color: var(--el-color-success);
    }

    .is-error > & {
      background: var(--el-color-danger-light-9);
      color: var(--el-color-danger);
    }

    &.neutral {
      background: var(--el-color-info-light-9);
      color: var(--el-text-color-secondary);
    }
  }
}

.is-error > .section-head .section-index {
  background: var(--el-color-danger);
}

.section-body {
  padding: 18px;
}

.section-placeholder {
  padding: 24px;
  text-align: center;
  font-size: 13px;
  color: var(--el-text-color-placeholder);
  background: var(--el-fill-color-lighter);
  border-radius: 6px;
}

// ===== 脚本选择 =====
.script-select {
  width: 420px;
  max-width: 100%;
}

.option-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;

  .option-meta {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
}

.script-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 28px;
  margin-top: 14px;
  padding: 12px 16px;
  background: var(--el-fill-color-lighter);
  border-radius: 6px;

  .summary-item {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .summary-label {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }

  .summary-value {
    font-size: 13px;
    font-weight: 600;
    color: var(--el-text-color-primary);
  }
}

// ===== 步骤服务器分配 =====
.step-server-assignment {
  .steps-list {
    display: flex;
    flex-direction: column;
    gap: 16px;
  }

  .step-card {
    border: 1px solid var(--el-border-color);
    border-radius: 8px;
    overflow: hidden;
    transition: border-color 0.2s;

    &.is-unassigned {
      border-color: var(--el-color-warning-light-5);
      border-style: dashed;
    }

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

    .step-body {
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
}

.params-form {
  margin-top: 4px;
}

// ===== 字段辅助 =====
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

.unit {
  margin-left: 8px;
  color: var(--el-text-color-secondary);
}

.field-hint {
  margin-left: 12px;
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}

.field-error {
  :deep(.el-input__wrapper),
  :deep(.el-textarea__inner) {
    box-shadow: 0 0 0 1px var(--el-color-danger) inset;
  }
}

.cron-quick-select {
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);

  .el-link {
    margin-left: 12px;
  }
}

.mb-4 {
  margin-bottom: 16px;
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
    font-weight: 400;
    color: var(--el-text-color-secondary);
  }
}

// 修复长参数名显示
:deep(.el-form-item__label) {
  word-break: break-all;
  white-space: normal;
  line-height: 1.5;
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

// ===== 底部粘性操作栏 =====
.editor-footer {
  position: sticky;
  bottom: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  // 抵消 .page-card 的 20px 内边距，让操作栏贴齐卡片边缘
  margin: 20px -20px -20px;
  padding: 12px 20px;
  background: var(--el-bg-color);
  border-top: 1px solid var(--el-border-color-lighter);
  border-radius: 0 0 var(--radius-md) var(--radius-md);
  box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.04);

  &.is-ready {
    border-top-color: var(--el-color-success-light-5);
  }
}

.footer-status {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  font-size: 13px;
  color: var(--el-text-color-regular);
  flex-wrap: wrap;

  .status-icon {
    font-size: 16px;

    &.ok {
      color: var(--el-color-success);
    }

    &.warn {
      color: var(--el-color-warning);
    }
  }

  .status-text {
    flex-shrink: 0;
  }
}

.issue-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 10px;
  font-size: 12px;
  font-family: inherit;
  color: var(--el-color-danger);
  background: var(--el-color-danger-light-9);
  border: 1px solid var(--el-color-danger-light-7);
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s;

  em {
    font-style: normal;
    font-weight: 600;
  }

  &:hover {
    background: var(--el-color-danger-light-8);
  }
}
</style>

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
            <p><strong>生命周期:</strong> {{ selectedScript?.lifecycleMode === 'full' ? '完整模式' : '简单模式' }}</p>
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
        <!-- 如果脚本定义了角色，显示角色配置 -->
        <div v-if="scriptRoles.length > 0" class="role-info">
          <el-alert type="info" :closable="false" show-icon>
            <template #title>
              该脚本定义了 <strong>{{ scriptRoles.length }}</strong> 个角色，请为服务器分配角色
            </template>
          </el-alert>
        </div>

        <el-transfer
          v-model="selectedServers"
          :data="servers"
          :titles="['可选服务器', '已选服务器']"
          :props="{ key: 'id', label: 'name' }"
          filterable
          filter-placeholder="搜索服务器"
        />

        <!-- 角色分配表格 -->
        <div v-if="scriptRoles.length > 0 && selectedServers.length > 0" class="role-assignment">
          <el-divider content-position="left">角色分配</el-divider>
          <el-table :data="serverRoleConfigs" border size="small">
            <el-table-column prop="serverName" label="服务器" width="200" />
            <el-table-column label="角色" width="180">
              <template #default="{ row }">
                <el-select v-model="row.role" placeholder="选择角色" @change="onRoleChange(row)">
                  <el-option
                    v-for="role in scriptRoles"
                    :key="role.name"
                    :label="role.displayName || role.name"
                    :value="role.name"
                  />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="角色参数">
              <template #default="{ row }">
                <div v-if="row.roleParams && Object.keys(row.roleParams).length > 0" class="role-params">
                  <div v-for="(param, key) in getRoleParamDefs(row.role)" :key="key" class="param-item">
                    <span class="param-label">{{ param.displayName || param.name }}:</span>
                    <el-input
                      v-if="param.type === 'string'"
                      v-model="row.roleParams[param.name]"
                      size="small"
                      style="width: 150px"
                    />
                    <el-input-number
                      v-else-if="param.type === 'number'"
                      v-model="row.roleParams[param.name]"
                      size="small"
                      style="width: 150px"
                    />
                    <el-switch
                      v-else-if="param.type === 'boolean'"
                      v-model="row.roleParams[param.name]"
                      size="small"
                    />
                  </div>
                </div>
                <span v-else class="no-params">无参数</span>
              </template>
            </el-table-column>
          </el-table>
        </div>
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
          <el-form-item prop="name">
            <template #label>
              任务名称
              <el-tooltip content="任务的标识名称，用于区分不同的测试任务" placement="top">
                <el-icon class="field-tip-icon"><QuestionFilled /></el-icon>
              </el-tooltip>
            </template>
            <el-input v-model="formData.name" placeholder="请输入任务名称" />
          </el-form-item>

          <el-form-item prop="executionMode">
            <template #label>
              执行模式
              <el-tooltip content="立即执行：创建后立即开始测试；定时执行：在指定时间自动开始" placement="top">
                <el-icon class="field-tip-icon"><QuestionFilled /></el-icon>
              </el-tooltip>
            </template>
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

          <el-form-item>
            <template #label>
              并行模式
              <el-tooltip content="顺序执行：按顺序逐个服务器执行；并行执行：多个服务器同时执行" placement="top">
                <el-icon class="field-tip-icon"><QuestionFilled /></el-icon>
              </el-tooltip>
            </template>
            <el-radio-group v-model="formData.parallelMode">
              <el-radio value="sequential">顺序执行</el-radio>
              <el-radio value="parallel">并行执行</el-radio>
            </el-radio-group>
          </el-form-item>

          <el-form-item v-if="formData.parallelMode === 'parallel'" label="最大并行数">
            <el-input-number v-model="formData.maxParallel" :min="1" :max="20" />
          </el-form-item>

          <el-form-item>
            <template #label>
              失败策略
              <el-tooltip content="继续执行：某服务器失败后继续执行其他服务器；停止执行：有失败时立即停止所有执行" placement="top">
                <el-icon class="field-tip-icon"><QuestionFilled /></el-icon>
              </el-tooltip>
            </template>
            <el-radio-group v-model="formData.failureStrategy">
              <el-radio value="continue">继续执行</el-radio>
              <el-radio value="stop">停止执行</el-radio>
            </el-radio-group>
          </el-form-item>

          <el-form-item label="指标采集">
            <template #label>
              指标采集
              <el-tooltip content="开启后会在测试过程中采集 CPU、内存等性能指标" placement="top">
                <el-icon class="field-tip-icon"><QuestionFilled /></el-icon>
              </el-tooltip>
            </template>
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
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { QuestionFilled } from '@element-plus/icons-vue'
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
const preselectedScript = ref(false)  // 是否从脚本中心预选

// 角色相关
interface RoleDefinition {
  name: string
  displayName: string
  params: { name: string; displayName: string; type: string; default: string }[]
  dependsOn: string[]
  resultCollector: boolean
}

interface ServerRoleConfig {
  serverId: number
  serverName: string
  role: string
  roleParams: Record<string, any>
}

const scriptRoles = ref<RoleDefinition[]>([])
const serverRoleConfigs = ref<ServerRoleConfig[]>([])

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
  
  // 加载脚本角色定义
  if (row) {
    loadScriptRoles(row.id, row.currentVersion)
  }
}

// 加载脚本角色定义
async function loadScriptRoles(scriptId: number, version: string) {
  try {
    const res = await scriptApi.getRoles(scriptId, version)
    if (res.code === 0 && res.data && Array.isArray(res.data)) {
      scriptRoles.value = res.data
    } else {
      scriptRoles.value = []
    }
  } catch (e) {
    scriptRoles.value = []
  }
}

// 获取角色参数定义
function getRoleParamDefs(roleName: string) {
  const role = scriptRoles.value.find(r => r.name === roleName)
  return role?.params || []
}

// 当角色变化时
function onRoleChange(row: ServerRoleConfig) {
  const role = scriptRoles.value.find(r => r.name === row.role)
  if (role && role.params) {
    // 初始化参数默认值
    row.roleParams = {}
    role.params.forEach(p => {
      row.roleParams[p.name] = p.default
    })
  } else {
    row.roleParams = {}
  }
}

// 监听服务器选择变化
watch(selectedServers, (newVal) => {
  // 更新角色配置列表
  const existingIds = new Set(serverRoleConfigs.value.map(c => c.serverId))
  
  // 添加新选中的服务器
  newVal.forEach(serverId => {
    if (!existingIds.has(serverId)) {
      const server = servers.value.find(s => s.id === serverId)
      serverRoleConfigs.value.push({
        serverId,
        serverName: server?.name || `服务器${serverId}`,
        role: 'default',
        roleParams: {}
      })
    }
  })
  
  // 移除取消选择的服务器
  serverRoleConfigs.value = serverRoleConfigs.value.filter(c => newVal.includes(c.serverId))
})

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
  
  // 构建服务器角色配置
  const serverRoles = selectedServers.value.map(serverId => {
    const config = serverRoleConfigs.value.find(c => c.serverId === serverId)
    return {
      serverId,
      role: config?.role || 'default',
      roleParams: config?.roleParams || {}
    }
  })
  
  const data = {
    name: formData.name,
    scriptId: selectedScript.value!.id,
    scriptVersion: selectedScript.value!.currentVersion,
    serverIds: selectedServers.value,
    serverRoles,
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
        preselectedScript.value = true  // 标记为预选脚本
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
</style>

<template>
  <el-dialog
    v-model="visible"
    title="参数配置"
    width="800px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <div class="param-header">
      <h4>{{ script?.name }} - 参数配置</h4>
      <div class="param-actions">
        <el-radio-group v-model="activeTab" size="small">
          <el-radio-button label="shared">共享参数</el-radio-button>
          <el-radio-button label="deploy" :disabled="script?.lifecycleMode !== 'full'">部署参数</el-radio-button>
          <el-radio-button label="run">执行参数</el-radio-button>
        </el-radio-group>
      </div>
    </div>

    <div class="param-tip">
      <el-alert
        v-if="activeTab === 'shared'"
        title="共享参数：部署和执行阶段都会使用"
        type="info"
        :closable="false"
        show-icon
      />
      <el-alert
        v-else-if="activeTab === 'deploy'"
        title="部署参数：仅在部署阶段使用"
        type="info"
        :closable="false"
        show-icon
      />
      <el-alert
        v-else
        title="执行参数：仅在执行阶段使用"
        type="info"
        :closable="false"
        show-icon
      />
    </div>

    <!-- 参数列表 -->
    <div class="param-list">
      <div v-for="(param, index) in currentParams" :key="index" class="param-item">
        <el-card shadow="never">
          <div class="param-item-header">
            <span class="param-name">{{ param.label || param.name }}</span>
            <el-button type="danger" link size="small" @click="removeParam(index)">
              <el-icon><Delete /></el-icon>
              删除
            </el-button>
          </div>
          <el-form label-width="100px" size="small">
            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="参数名称">
                  <el-input v-model="param.name" placeholder="如: thread_count" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="显示标签">
                  <el-input v-model="param.label" placeholder="如: 线程数" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="是否必填">
                  <el-switch v-model="param.required" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="默认值">
                  <el-input v-model="param.default" placeholder="默认值" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="单位">
                  <el-input v-model="param.unit" placeholder="如: 秒、MB" />
                </el-form-item>
              </el-col>
            </el-row>

            <el-form-item label="描述">
              <el-input v-model="param.description" type="textarea" :rows="2" placeholder="参数说明" />
            </el-form-item>
          </el-form>
        </el-card>
      </div>

      <el-empty v-if="currentParams.length === 0" description="暂无参数" />

      <el-button type="primary" plain style="margin-top: 16px" @click="addParam">
        <el-icon><Plus /></el-icon>
        添加参数
      </el-button>
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Delete } from '@element-plus/icons-vue'
import request from '@/utils/request'
import type { Script } from '@/api/script'

interface ParamDefinition {
  name: string
  label: string
  required: boolean
  default: any
  unit: string
  description: string
  options?: Array<{ label: string; value: string }>
}

const props = defineProps<{
  modelValue: boolean
  script: Script | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'refresh'): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const activeTab = ref<'shared' | 'deploy' | 'run'>('shared')
const saving = ref(false)

// 参数数据
const params = ref<{
  shared: ParamDefinition[]
  deploy: ParamDefinition[]
  run: ParamDefinition[]
}>({
  shared: [],
  deploy: [],
  run: []
})

// 当前显示的参数列表
const currentParams = computed({
  get: () => params.value[activeTab.value],
  set: (val) => {
    params.value[activeTab.value] = val
  }
})

// 监听弹窗打开
watch(visible, (val) => {
  if (val && props.script) {
    loadParams()
  }
})

// 加载参数
const loadParams = () => {
  if (props.script?.parameters) {
    params.value = {
      shared: props.script.parameters.shared || [],
      deploy: props.script.parameters.deploy || [],
      run: props.script.parameters.run || []
    }
  } else {
    params.value = {
      shared: [],
      deploy: [],
      run: []
    }
  }
}

// 添加参数
const addParam = () => {
  currentParams.value.push({
    name: '',
    label: '',
    required: false,
    default: '',
    unit: '',
    description: '',
    options: []
  })
}

// 删除参数
const removeParam = (index: number) => {
  currentParams.value.splice(index, 1)
}

// 添加下拉选项
const addOption = (param: ParamDefinition) => {
  if (!param.options) {
    param.options = []
  }
  param.options.push({ label: '', value: '' })
}

// 保存参数
const handleSave = async () => {
  // 验证参数
  for (const [group, groupParams] of Object.entries(params.value)) {
    for (const param of groupParams) {
      if (!param.name) {
        ElMessage.warning(`${getGroupLabel(group)}: 请填写参数名称`)
        return
      }
      if (!param.label) {
        ElMessage.warning(`${getGroupLabel(group)}: 请填写显示标签`)
        return
      }
    }
  }

  saving.value = true
  try {
    await request.put(`/scripts/${props.script!.id}`, {
      parameters: params.value
    })
    ElMessage.success('保存成功')
    visible.value = false
    emit('refresh')
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

const getGroupLabel = (group: string) => {
  const map: Record<string, string> = {
    shared: '共享参数',
    deploy: '部署参数',
    run: '执行参数'
  }
  return map[group] || group
}

const handleClose = () => {
  params.value = { shared: [], deploy: [], run: [] }
  activeTab.value = 'shared'
}
</script>

<style scoped>
.param-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.param-header h4 {
  margin: 0;
  font-size: 16px;
}

.param-tip {
  margin-bottom: 16px;
}

.param-list {
  max-height: 500px;
  overflow-y: auto;
}

.param-item {
  margin-bottom: 12px;
}

.param-item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.param-name {
  font-weight: 500;
  color: var(--el-text-color-primary);
}

.select-options {
  margin: 12px 0;
  padding: 12px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
}

.option-item {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
}
</style>

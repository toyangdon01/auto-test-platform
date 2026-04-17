<template>
  <el-dialog
    :model-value="modelValue"
    :title="rule ? '编辑解析规则' : '添加解析规则'"
    width="700px"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
      <el-form-item label="规则名称" prop="name">
        <el-input v-model="formData.name" placeholder="请输入规则名称" />
      </el-form-item>

      <el-form-item label="描述">
        <el-input v-model="formData.description" type="textarea" :rows="2" placeholder="规则描述（可选）" />
      </el-form-item>

      <el-divider content-position="left">解析方式</el-divider>

      <el-form-item prop="parserType">
        <template #label>
          解析方式
          <el-tooltip content="内置规则：系统预置格式解析；解析脚本：自定义脚本解析" placement="top">
            <el-icon class="field-tip-icon"><QuestionFilled /></el-icon>
          </el-tooltip>
        </template>
        <el-radio-group v-model="formData.parserType">
          <el-radio value="builtin">内置规则</el-radio>
          <el-radio value="script">解析脚本</el-radio>
        </el-radio-group>
      </el-form-item>

      <!-- 内置规则配置 -->
      <template v-if="formData.parserType === 'builtin'">
        <el-form-item prop="builtinFormat">
          <template #label>
            内置格式
            <el-tooltip content="Key-Value：解析 key=value 格式；JSON：解析 JSON 格式" placement="top">
              <el-icon class="field-tip-icon"><QuestionFilled /></el-icon>
            </el-tooltip>
          </template>
          <el-select v-model="formData.builtinFormat" placeholder="选择内置格式" style="width: 100%">
            <el-option label="Key-Value 格式 (key=value 或 key: value)" value="key_value" />
            <el-option label="JSON 格式" value="json" />
          </el-select>
        </el-form-item>
      </template>

      <!-- 解析脚本配置 -->
      <template v-if="formData.parserType === 'script'">
        <el-form-item prop="scriptSource">
          <template #label>
            脚本来源
            <el-tooltip content="从脚本包选择已有的解析脚本，或直接编写新脚本" placement="top">
              <el-icon class="field-tip-icon"><QuestionFilled /></el-icon>
            </el-tooltip>
          </template>
          <el-radio-group v-model="formData.scriptSource">
            <el-radio value="package">从脚本包选择</el-radio>
            <el-radio value="inline">直接编写</el-radio>
          </el-radio-group>
        </el-form-item>

        <!-- 从脚本包选择 -->
        <el-form-item v-if="formData.scriptSource === 'package'" prop="scriptPath">
          <el-select v-model="formData.scriptPath" placeholder="选择解析脚本" style="width: 100%">
            <el-option
              v-for="file in scriptFiles"
              :key="file"
              :label="file"
              :value="file"
            />
          </el-select>
        </el-form-item>

        <!-- 直接编写脚本 -->
        <template v-if="formData.scriptSource === 'inline'">
          <el-form-item prop="scriptLanguage">
            <el-select v-model="formData.scriptLanguage" placeholder="选择脚本语言" style="width: 200px">
              <el-option label="Python" value="python" />
              <el-option label="Shell" value="shell" />
            </el-select>
          </el-form-item>

          <el-form-item prop="scriptContent">
            <template #label>
              解析脚本
              <el-tooltip content="脚本通过标准输入接收原始数据，输出 JSON 到标准输出" placement="top">
                <el-icon class="field-tip-icon"><QuestionFilled /></el-icon>
              </el-tooltip>
            </template>
            <el-input
              v-model="formData.scriptContent"
              type="textarea"
              :rows="10"
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

      <el-divider content-position="left">输入配置</el-divider>

      <el-form-item prop="inputSource">
        <template #label>
          输入来源
          <el-tooltip content="标准输出：解析任务执行输出；指定文件：解析测试生成的文件" placement="top">
            <el-icon class="field-tip-icon"><QuestionFilled /></el-icon>
          </el-tooltip>
        </template>
        <el-radio-group v-model="formData.inputSource">
          <el-radio value="stdout">标准输出</el-radio>
          <el-radio value="file">指定文件</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item v-if="formData.inputSource === 'file'" prop="filePattern">
        <template #label>
          文件路径模式
          <el-tooltip content="支持正则表达式匹配文件路径，如 results/.*\.json" placement="top">
            <el-icon class="field-tip-icon"><QuestionFilled /></el-icon>
          </el-tooltip>
        </template>
        <el-input v-model="formData.filePattern" placeholder="results/.*\.json" />
      </el-form-item>

      <el-divider content-position="left">输出配置</el-divider>

      <el-form-item prop="outputFormat">
        <template #label>
          输出格式
          <el-tooltip content="解析结果的输出格式" placement="top">
            <el-icon class="field-tip-icon"><QuestionFilled /></el-icon>
          </el-tooltip>
        </template>
        <el-select v-model="formData.outputFormat" placeholder="选择输出格式" style="width: 200px">
          <el-option label="JSON" value="json" />
        </el-select>
      </el-form-item>

      <!-- 测试解析 -->
      <el-divider content-position="left">测试解析</el-divider>

      <el-form-item label="示例输入">
        <el-input
          v-model="testInput"
          type="textarea"
          :rows="5"
          placeholder="输入示例数据测试解析效果"
        />
      </el-form-item>

      <el-form-item>
        <el-button type="primary" :loading="testing" @click="handleTestParse">测试解析</el-button>
      </el-form-item>

      <el-form-item v-if="testResult" label="解析结果">
        <pre class="test-result">{{ testResult }}</pre>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { QuestionFilled } from '@element-plus/icons-vue'
import { resultRuleApi, type ResultRule, type TestParseParams } from '@/api/resultRule'
import { scriptApi } from '@/api/script'

const props = defineProps<{
  modelValue: boolean
  rule: ResultRule | null
  scriptId: number
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'saved'): void
}>()

const formRef = ref()
const submitting = ref(false)
const scriptFiles = ref<string[]>([])
const testing = ref(false)
const testInput = ref('')
const testResult = ref<string | null>(null)

const formData = reactive<Partial<ResultRule>>({
  name: '',
  description: '',
  parserType: 'builtin',
  builtinFormat: 'json',
  scriptSource: 'inline',
  scriptPath: '',
  scriptContent: '',
  scriptLanguage: 'python',
  inputSource: 'stdout',
  filePattern: '',
  outputFormat: 'json',
  enabled: true,
})

const formRules = {
  name: [{ required: true, message: '请输入规则名称', trigger: 'blur' }],
  parserType: [{ required: true, message: '请选择解析方式', trigger: 'change' }],
  builtinFormat: [{ required: true, message: '请选择内置格式', trigger: 'change' }],
  scriptSource: [{ required: true, message: '请选择脚本来源', trigger: 'change' }],
  scriptLanguage: [{ required: true, message: '请选择脚本语言', trigger: 'change' }],
  scriptContent: [{ required: true, message: '请输入解析脚本', trigger: 'blur' }],
  inputSource: [{ required: true, message: '请选择输入来源', trigger: 'change' }],
  outputFormat: [{ required: true, message: '请选择输出格式', trigger: 'change' }],
}

// 监听 rule 变化，填充表单
watch(() => props.rule, (newRule) => {
  if (newRule) {
    Object.assign(formData, {
      name: newRule.name,
      description: newRule.description || '',
      parserType: newRule.parserType,
      builtinFormat: newRule.builtinFormat || 'json',
      scriptSource: newRule.scriptSource || 'inline',
      scriptPath: newRule.scriptPath || '',
      scriptContent: newRule.scriptContent || '',
      scriptLanguage: newRule.scriptLanguage || 'python',
      inputSource: newRule.inputSource,
      filePattern: newRule.filePattern || '',
      outputFormat: newRule.outputFormat || 'json',
      enabled: newRule.enabled,
    })
  } else {
    resetForm()
  }
}, { immediate: true })

// 加载脚本文件列表
async function loadScriptFiles() {
  if (!props.scriptId) return
  try {
    const response = await scriptApi.listFiles(props.scriptId)
    scriptFiles.value = response.data || []
  } catch (error) {
    console.error('加载脚本文件列表失败:', error)
    scriptFiles.value = []
  }
}

// 监听对话框打开，加载文件列表
watch(() => props.modelValue, (open) => {
  if (open && props.scriptId) {
    loadScriptFiles()
  }
})

// 组件挂载时也加载
onMounted(() => {
  if (props.scriptId) {
    loadScriptFiles()
  }
})

function resetForm() {
  Object.assign(formData, {
    name: '',
    description: '',
    parserType: 'builtin',
    builtinFormat: 'json',
    scriptSource: 'inline',
    scriptPath: '',
    scriptContent: '',
    scriptLanguage: 'python',
    inputSource: 'stdout',
    filePattern: '',
    outputFormat: 'json',
    enabled: true,
  })
  testInput.value = ''
  testResult.value = null
}

async function handleTestParse() {
  if (!testInput.value) {
    ElMessage.warning('请输入示例数据')
    return
  }

  testing.value = true
  testResult.value = null

  try {
    const params: TestParseParams = {
      parserType: formData.parserType as 'builtin' | 'script',
      sampleInput: testInput.value,
    }

    if (formData.parserType === 'builtin') {
      params.builtinFormat = formData.builtinFormat as 'key_value' | 'json'
    } else {
      params.scriptSource = formData.scriptSource as 'package' | 'inline'
      params.scriptContent = formData.scriptContent
      params.scriptLanguage = formData.scriptLanguage as 'python' | 'shell'
    }

    const result = await resultRuleApi.testParse(params)
    testResult.value = JSON.stringify(result, null, 2)
    ElMessage.success('解析成功')
  } catch (error: any) {
    testResult.value = `解析失败: ${error.message || error}`
    ElMessage.error('解析失败')
  } finally {
    testing.value = false
  }
}

async function handleSubmit() {
  await formRef.value.validate()

  submitting.value = true
  try {
    const data: Partial<ResultRule> = {
      ...formData,
      scriptId: props.scriptId,
    }

    if (props.rule) {
      await resultRuleApi.update(props.rule.id, data)
      ElMessage.success('更新成功')
    } else {
      await resultRuleApi.create(data)
      ElMessage.success('创建成功')
    }

    emit('update:modelValue', false)
    emit('saved')
  } catch (error) {
    ElMessage.error('保存失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style lang="scss" scoped>
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

.code-textarea {
  :deep(textarea) {
    font-family: 'Fira Code', 'Monaco', 'Menlo', monospace;
    font-size: 13px;
    line-height: 1.5;
  }
}

.test-result {
  background: var(--el-fill-color-light);
  padding: 12px;
  border-radius: 4px;
  font-family: monospace;
  font-size: 13px;
  max-height: 200px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
}
</style>

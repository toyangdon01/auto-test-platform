<template>
  <div class="script-editor">
    <div class="editor-header">
      <el-page-header @back="$router.back()">
        <template #content>
          <span class="title">{{ isEdit ? '编辑脚本' : '新建脚本' }}</span>
        </template>
      </el-page-header>
      
      <div class="header-actions">
        <el-button @click="handleSave(false)">保存</el-button>
        <el-button type="primary" @click="handleSave(true)">保存并执行</el-button>
      </div>
    </div>

    <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px" class="editor-form">
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="脚本名称" prop="name">
            <el-input v-model="formData.name" placeholder="请输入脚本名称" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="测试类型" prop="testCategory">
            <el-select v-model="formData.testCategory" placeholder="选择测试类型" style="width: 100%">
              <el-option label="CPU测试" value="cpu" />
              <el-option label="内存测试" value="memory" />
              <el-option label="磁盘测试" value="disk" />
              <el-option label="网络测试" value="network" />
              <el-option label="综合测试" value="mixed" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="生命周期">
        <el-radio-group v-model="formData.lifecycleMode">
          <el-radio value="simple">简单模式（仅执行）</el-radio>
          <el-radio value="full">完整模式（部署→执行→卸载）</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item label="脚本描述">
        <el-input v-model="formData.description" type="textarea" :rows="2" placeholder="脚本功能描述" />
      </el-form-item>

      <el-divider content-position="left">脚本内容</el-divider>

      <el-tabs v-model="activeTab" class="script-tabs">
        <el-tab-pane label="执行脚本 (run)" name="run">
          <div class="code-editor">
            <el-input
              v-model="formData.runContent"
              type="textarea"
              :rows="15"
              placeholder="请输入执行脚本内容..."
              class="code-textarea"
            />
          </div>
        </el-tab-pane>
        
        <el-tab-pane v-if="formData.lifecycleMode === 'full'" label="部署脚本 (deploy)" name="deploy">
          <div class="code-editor">
            <el-input
              v-model="formData.deployContent"
              type="textarea"
              :rows="15"
              placeholder="请输入部署脚本内容..."
              class="code-textarea"
            />
          </div>
        </el-tab-pane>
        
        <el-tab-pane v-if="formData.lifecycleMode === 'full'" label="卸载脚本 (cleanup)" name="cleanup">
          <div class="code-editor">
            <el-input
              v-model="formData.cleanupContent"
              type="textarea"
              :rows="15"
              placeholder="请输入卸载脚本内容..."
              class="code-textarea"
            />
          </div>
        </el-tab-pane>
      </el-tabs>

      <el-divider content-position="left">参数配置</el-divider>

      <el-form-item label="执行参数">
        <div class="param-config">
          <div v-for="(param, index) in formData.runParams" :key="index" class="param-item">
            <el-input v-model="param.name" placeholder="参数名" style="width: 150px" />
            <el-select v-model="param.type" placeholder="类型" style="width: 100px">
              <el-option label="字符串" value="string" />
              <el-option label="数字" value="number" />
              <el-option label="布尔" value="boolean" />
            </el-select>
            <el-input v-model="param.default" placeholder="默认值" style="flex: 1" />
            <el-button type="danger" link @click="formData.runParams.splice(index, 1)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
          <el-button type="primary" link @click="addParam('run')">
            <el-icon><Plus /></el-icon> 添加参数
          </el-button>
        </div>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, Delete } from '@element-plus/icons-vue'
import { scriptApi } from '@/api/script'

const route = useRoute()
const router = useRouter()
const formRef = ref()
const activeTab = ref('run')

const isEdit = computed(() => !!route.params.id)

const formData = reactive({
  name: '',
  description: '',
  testCategory: '',
  lifecycleMode: 'simple' as 'simple' | 'full',
  runContent: '',
  deployContent: '',
  cleanupContent: '',
  runParams: [] as { name: string; type: string; default: string }[],
  deployParams: [] as { name: string; type: string; default: string }[],
})

const formRules = {
  name: [{ required: true, message: '请输入脚本名称', trigger: 'blur' }],
  testCategory: [{ required: true, message: '请选择测试类型', trigger: 'change' }],
}

function addParam(type: 'run' | 'deploy') {
  const params = type === 'run' ? formData.runParams : formData.deployParams
  params.push({ name: '', type: 'string', default: '' })
}

async function handleSave(andRun: boolean) {
  await formRef.value.validate()
  
  const data = {
    name: formData.name,
    description: formData.description,
    testCategory: formData.testCategory,
    lifecycleMode: formData.lifecycleMode,
    hasDeploy: formData.lifecycleMode === 'full' && !!formData.deployContent,
    hasCleanup: formData.lifecycleMode === 'full' && !!formData.cleanupContent,
  }
  
  if (isEdit.value) {
    await scriptApi.update(Number(route.params.id), data)
  } else {
    await scriptApi.create(data)
  }
  
  ElMessage.success('保存成功')
  
  if (andRun) {
    router.push('/tasks/create')
  } else {
    router.push('/scripts/list')
  }
}

onMounted(() => {
  if (isEdit.value) {
    // TODO: 加载脚本详情
  }
})
</script>

<style lang="scss" scoped>
.script-editor {
  padding: 20px;
  background: #fff;
  border-radius: var(--radius-md);
}

.editor-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--border-lighter);
}

.title {
  font-size: 16px;
  font-weight: 600;
}

.editor-form {
  max-width: 1000px;
}

.script-tabs {
  margin: 20px 0;
}

.code-editor {
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.code-textarea {
  :deep(textarea) {
    font-family: 'Fira Code', 'Monaco', 'Menlo', monospace;
    font-size: 13px;
    line-height: 1.6;
    border: none;
    border-radius: 0;
  }
}

.param-config {
  width: 100%;
}

.param-item {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
  align-items: center;
}
</style>

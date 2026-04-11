<template>
  <div class="result-rule-list">
    <div class="list-header">
      <span class="title">解析规则</span>
      <el-button type="primary" size="small" @click="handleAdd">
        <el-icon><Plus /></el-icon>
        添加规则
      </el-button>
    </div>

    <el-empty v-if="rules.length === 0" description="暂无解析规则" :image-size="80" />

    <div v-else class="rule-cards">
      <div v-for="rule in rules" :key="rule.id" class="rule-card">
        <div class="rule-header">
          <span class="rule-name">{{ rule.name }}</span>
          <el-tag size="small" :type="rule.enabled ? 'success' : 'info'">
            {{ rule.enabled ? '已启用' : '已禁用' }}
          </el-tag>
        </div>
        
        <div class="rule-info">
          <span class="info-item">
            <el-icon><Document /></el-icon>
            {{ getParserTypeText(rule.parserType) }}
            <template v-if="rule.parserType === 'builtin'">
              ({{ getBuiltinFormatText(rule.builtinFormat) }})
            </template>
          </span>
          <span class="info-item">
            <el-icon><Folder /></el-icon>
            {{ getInputSourceText(rule.inputSource) }}
            <template v-if="rule.inputSource === 'file'">
              : {{ rule.filePattern }}
            </template>
          </span>
        </div>

        <p v-if="rule.description" class="rule-desc">{{ rule.description }}</p>

        <div class="rule-actions">
          <el-button type="primary" link size="small" @click="handleEdit(rule)">编辑</el-button>
          <el-button 
            :type="rule.enabled ? 'warning' : 'success'" 
            link 
            size="small"
            @click="handleToggleEnabled(rule)"
          >
            {{ rule.enabled ? '禁用' : '启用' }}
          </el-button>
          <el-button type="danger" link size="small" @click="handleDelete(rule)">删除</el-button>
        </div>
      </div>
    </div>

    <!-- 编辑弹窗 -->
    <ResultRuleDialog
      v-model="dialogVisible"
      :rule="editingRule"
      :script-id="scriptId"
      :script-files="scriptFiles"
      @saved="handleSaved"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Document, Folder } from '@element-plus/icons-vue'
import { resultRuleApi, type ResultRule } from '@/api/resultRule'
import ResultRuleDialog from './ResultRuleDialog.vue'

const props = defineProps<{
  scriptId: number
  scriptFiles?: string[]
}>()

const emit = defineEmits<{
  (e: 'changed'): void
}>()

const rules = ref<ResultRule[]>([])
const dialogVisible = ref(false)
const editingRule = ref<ResultRule | null>(null)

async function fetchRules() {
  try {
    const response = await resultRuleApi.listByScript(props.scriptId)
    rules.value = response.data || []
  } catch (error) {
    console.error('获取解析规则失败:', error)
  }
}

function handleAdd() {
  editingRule.value = null
  dialogVisible.value = true
}

function handleEdit(rule: ResultRule) {
  editingRule.value = { ...rule }
  dialogVisible.value = true
}

async function handleToggleEnabled(rule: ResultRule) {
  try {
    await resultRuleApi.setEnabled(rule.id, !rule.enabled)
    rule.enabled = !rule.enabled
    ElMessage.success(rule.enabled ? '已启用' : '已禁用')
    emit('changed')
  } catch (error) {
    ElMessage.error('操作失败')
  }
}

async function handleDelete(rule: ResultRule) {
  try {
    await ElMessageBox.confirm(`确定删除解析规则 "${rule.name}" 吗？`, '确认删除', {
      type: 'warning',
    })
    
    await resultRuleApi.delete(rule.id)
    ElMessage.success('删除成功')
    fetchRules()
    emit('changed')
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

function handleSaved() {
  fetchRules()
  emit('changed')
}

function getParserTypeText(type: string) {
  return type === 'builtin' ? '内置规则' : '解析脚本'
}

function getBuiltinFormatText(format: string | null) {
  const texts: Record<string, string> = {
    key_value: 'Key-Value',
    json: 'JSON',
  }
  return format ? texts[format] || format : ''
}

function getInputSourceText(source: string) {
  return source === 'stdout' ? '标准输出' : '指定文件'
}

onMounted(fetchRules)

watch(() => props.scriptId, fetchRules)
</script>

<style lang="scss" scoped>
.result-rule-list {
  margin-top: 20px;
}

.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;

  .title {
    font-size: 16px;
    font-weight: 600;
  }
}

.rule-cards {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.rule-card {
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  padding: 16px;
  background: var(--el-fill-color-blank);
  transition: all 0.2s;

  &:hover {
    border-color: var(--el-color-primary-light-5);
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  }
}

.rule-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.rule-name {
  font-size: 15px;
  font-weight: 500;
}

.rule-info {
  display: flex;
  gap: 16px;
  margin-bottom: 8px;
  font-size: 13px;
  color: var(--el-text-color-secondary);

  .info-item {
    display: flex;
    align-items: center;
    gap: 4px;
  }
}

.rule-desc {
  font-size: 13px;
  color: var(--el-text-color-regular);
  margin: 8px 0;
}

.rule-actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--el-border-color-lighter);
}
</style>

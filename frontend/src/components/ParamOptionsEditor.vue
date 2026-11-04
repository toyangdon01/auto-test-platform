<template>
  <el-popover placement="bottom-start" :width="360" trigger="click">
    <template #reference>
      <el-button size="small" :type="choices.length > 0 ? 'primary' : ''" plain>
        可选值{{ choices.length > 0 ? ` (${choices.length})` : '' }}
      </el-button>
    </template>
    <div class="options-editor">
      <div v-if="choices.length === 0" class="options-empty">未限定取值，用户可任意填写</div>
      <div v-for="(choice, index) in choices" :key="index" class="options-row">
        <el-input v-model="choice.label" size="small" placeholder="显示名" />
        <el-input v-model="choice.value" size="small" placeholder="提交值" />
        <el-button type="danger" link size="small" @click="remove(index)">
          <el-icon><Delete /></el-icon>
        </el-button>
      </div>
      <el-button type="primary" link size="small" @click="add">
        <el-icon><Plus /></el-icon> 添加可选值
      </el-button>
      <div class="options-tip">限定后提交任务只接受这些取值，界面会把它渲染成下拉框</div>
    </div>
  </el-popover>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Delete, Plus } from '@element-plus/icons-vue'
import type { ParamDefinition, ParamOption } from '@/api/script'
import { optionChoices } from '@/utils/paramContract'

interface Props {
  options?: ParamDefinition['options']
}

const props = withDefaults(defineProps<Props>(), { options: undefined })

const emit = defineEmits<{ (e: 'update:options', value: ParamOption[] | undefined): void }>()

// 编辑态统一展开成可写数组：裸字符串按 label=value 同值处理
const choices = computed<ParamOption[]>(() => optionChoices({ name: '', options: props.options }))

function commit(next: ParamOption[]) {
  const cleaned = next.filter(c => (c.value ?? '').trim() !== '' || (c.label ?? '').trim() !== '')
  emit('update:options', cleaned.length > 0 ? cleaned : undefined)
}

function add() {
  commit([...choices.value, { label: '', value: '' }])
}

function remove(index: number) {
  const next = [...choices.value]
  next.splice(index, 1)
  commit(next)
}
</script>

<style lang="scss" scoped>
.options-editor {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.options-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.options-empty,
.options-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>

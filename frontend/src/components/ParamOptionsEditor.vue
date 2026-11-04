<template>
  <el-popover placement="bottom-start" :width="360" trigger="click" @before-enter="seed">
    <template #reference>
      <el-button size="small" :type="choices.length > 0 ? 'primary' : ''" plain>
        可选值{{ choices.length > 0 ? ` (${choices.length})` : '' }}
      </el-button>
    </template>
    <div class="options-editor">
      <div v-if="draft.length === 0" class="options-empty">未限定取值，用户可任意填写</div>
      <div v-for="(choice, index) in draft" :key="index" class="options-row">
        <el-input v-model="choice.label" size="small" placeholder="显示名" @update:model-value="commit" />
        <el-input v-model="choice.value" size="small" placeholder="提交值" @update:model-value="commit" />
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
import { computed, ref } from 'vue'
import { Delete, Plus } from '@element-plus/icons-vue'
import type { ParamDefinition, ParamOption } from '@/api/script'
import { optionChoices } from '@/utils/paramContract'

interface Props {
  options?: ParamDefinition['options']
}

const props = withDefaults(defineProps<Props>(), { options: undefined })

const emit = defineEmits<{ (e: 'update:options', value: ParamOption[] | undefined): void }>()

// 已提交给父级的取值，只用于按钮上的计数
const choices = computed<ParamOption[]>(() => optionChoices({ name: '', options: props.options }))

// 弹层内的编辑态：打开时从 props 播种，之后只由用户操作驱动。
// 不能直接绑 props —— optionChoices 每次返回新建对象，写进去的是临时副本，父级永远收不到。
const draft = ref<ParamOption[]>([])

function seed() {
  draft.value = choices.value.map(choice => ({ ...choice }))
}

/**
 * 归一化后回吐：与后端 ScriptConfigService.parseOptions 一致，
 * label / value 缺一个就用另一个补，两个都空才丢弃；一条不剩时给 undefined 而不是空数组，
 * 否则契约里会多出"声明了白名单但白名单为空"，后端会把所有取值都拒掉。
 */
function commit() {
  const next: ParamOption[] = []
  for (const choice of draft.value) {
    const value = (choice.value ?? '').trim()
    const label = (choice.label ?? '').trim()
    if (value === '' && label === '') continue
    next.push({ label: label !== '' ? label : value, value: value !== '' ? value : label })
  }
  emit('update:options', next.length > 0 ? next : undefined)
}

function add() {
  draft.value.push({ label: '', value: '' })
}

function remove(index: number) {
  draft.value.splice(index, 1)
  commit()
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

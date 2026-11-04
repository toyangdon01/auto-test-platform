<template>
  <el-select
    v-if="choices.length > 0"
    :model-value="selected"
    size="small"
    clearable
    :placeholder="placeholder"
    @update:model-value="onSelect"
  >
    <el-option v-for="choice in choices" :key="choice.value" :label="choice.label" :value="choice.value" />
  </el-select>
  <el-switch
    v-else-if="isBoolean"
    :model-value="String(modelValue ?? '')"
    size="small"
    active-value="true"
    inactive-value="false"
    @update:model-value="emit('update:modelValue', $event)"
  />
  <el-input
    v-else
    :model-value="String(modelValue ?? '')"
    :size="size"
    :placeholder="placeholder"
    @update:model-value="emit('update:modelValue', $event)"
  />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { ParamDefinition } from '@/api/script'
import { optionChoices } from '@/utils/paramContract'

interface Props {
  param: ParamDefinition
  modelValue?: unknown
  size?: 'default' | 'small' | 'large'
  placeholder?: string
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: undefined,
  size: 'small',
  placeholder: '',
})

const emit = defineEmits<{ (e: 'update:modelValue', value: unknown): void }>()

const choices = computed(() => optionChoices(props.param))
const isBoolean = computed(() => props.param.type === 'boolean')
// 后端按字符串比较可选值，界面统一成字符串再绑定，避免 0 / "0" 这类错位
const selected = computed(() => (props.modelValue === undefined || props.modelValue === null ? '' : String(props.modelValue)))

function onSelect(value: string | undefined) {
  emit('update:modelValue', value === undefined || value === null ? '' : value)
}
</script>

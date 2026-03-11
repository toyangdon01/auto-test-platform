<template>
  <div class="form-item-with-tip">
    <div class="label-wrapper">
      <label class="form-label">
        {{ label }}
        <span v-if="required" class="required-mark">*</span>
      </label>
      <el-tooltip 
        v-if="tip" 
        :content="tip" 
        placement="top"
        :effect="effect"
        :max-width="maxWidth"
      >
        <el-icon class="tip-icon" :size="14">
          <QuestionFilled />
        </el-icon>
      </el-tooltip>
    </div>
    <div class="form-content">
      <slot></slot>
    </div>
  </div>
</template>

<script setup lang="ts">
import { QuestionFilled } from '@element-plus/icons-vue'

interface Props {
  label: string
  tip?: string
  required?: boolean
  effect?: 'dark' | 'light'
  maxWidth?: number
}

withDefaults(defineProps<Props>(), {
  required: false,
  effect: 'dark',
  maxWidth: 300,
})
</script>

<style lang="scss" scoped>
.form-item-with-tip {
  margin-bottom: 18px;
}

.label-wrapper {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
  gap: 6px;
}

.form-label {
  font-size: 14px;
  color: var(--el-text-color-regular);
  font-weight: 500;
}

.required-mark {
  color: var(--el-color-danger);
  margin-left: 2px;
}

.tip-icon {
  color: var(--el-text-color-secondary);
  cursor: help;
  transition: color 0.2s;
  
  &:hover {
    color: var(--el-color-primary);
  }
}

.form-content {
  width: 100%;
}
</style>

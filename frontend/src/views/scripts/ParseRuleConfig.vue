<template>
  <el-dialog
    v-model="visible"
    title="解析规则配置"
    width="900px"
    :close-on-click-modal="false"
    destroy-on-close
  >
    <div v-if="script" class="parse-config-container">
      <div class="script-info">
        <span class="label">脚本：</span>
        <span class="name">{{ script.name }}</span>
        <el-tag size="small" style="margin-left: 8px">{{ getCategoryText(script.testCategory) }}</el-tag>
      </div>

      <ResultRuleList
        :script-id="script.id"
        :script-files="scriptFiles"
        @changed="handleChanged"
      />
    </div>

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { Script } from '@/api/script'
import ResultRuleList from './ResultRuleList.vue'

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

const scriptFiles = ref<string[]>([])

function getCategoryText(category: string) {
  const texts: Record<string, string> = {
    cpu: 'CPU测试',
    memory: '内存测试',
    disk: '磁盘测试',
    network: '网络测试',
    mixed: '综合测试',
  }
  return texts[category] || category
}

function handleChanged() {
  emit('refresh')
}

// 监听脚本变化，获取脚本文件列表
watch(() => props.script, async (newScript) => {
  if (newScript) {
    // TODO: 获取脚本文件列表
    scriptFiles.value = []
  }
}, { immediate: true })
</script>

<style lang="scss" scoped>
.parse-config-container {
  min-height: 300px;
}

.script-info {
  margin-bottom: 16px;
  padding: 12px 16px;
  background: var(--el-fill-color-light);
  border-radius: 4px;

  .label {
    color: var(--el-text-color-secondary);
  }

  .name {
    font-weight: 500;
  }
}
</style>

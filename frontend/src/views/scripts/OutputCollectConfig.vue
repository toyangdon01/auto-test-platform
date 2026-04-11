<template>
  <div class="output-collect-config">
    <div class="config-header">
      <span class="title">输出文件收集</span>
      <el-switch v-model="collectEnabled" active-text="启用" inactive-text="禁用" />
    </div>

    <template v-if="collectEnabled">
      <div class="collect-rules">
        <div v-for="(rule, index) in rules" :key="index" class="rule-item">
          <el-card shadow="hover" size="small">
            <div class="rule-header">
              <span class="rule-index">规则 {{ index + 1 }}</span>
              <el-button type="danger" link @click="removeRule(index)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
            
            <el-form label-width="80px" size="small">
              <el-row :gutter="16">
                <el-col :span="8">
                  <el-form-item label="规则名称">
                    <el-input v-model="rule.name" placeholder="如：日志文件" />
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item label="收集类型">
                    <el-select v-model="rule.type" style="width: 100%" teleported>
                      <el-option label="单个文件" value="file" />
                      <el-option label="目录" value="directory" />
                      <el-option label="通配符" value="pattern" />
                    </el-select>
                  </el-form-item>
                </el-col>
                <el-col :span="8">
                  <el-form-item label="必须存在">
                    <el-checkbox v-model="rule.required">文件必须存在</el-checkbox>
                  </el-form-item>
                </el-col>
              </el-row>
              
              <el-form-item label="文件路径">
                <el-input 
                  v-model="rule.path" 
                  placeholder="如：/tmp/test/result.log 或 /tmp/test/logs/ 或 /tmp/test/*.log"
                >
                  <template #prepend>
                    <el-icon><Folder /></el-icon>
                  </template>
                </el-input>
                <div class="path-tip">
                  <span v-if="rule.type === 'file'">收集指定文件</span>
                  <span v-else-if="rule.type === 'directory'">收集整个目录（会打包）</span>
                  <span v-else>支持通配符，如 *.log、test_*.txt</span>
                </div>
              </el-form-item>
              
              <el-form-item label="大小限制">
                <el-input v-model="rule.maxSize" placeholder="如：100MB" style="width: 200px" />
                <span class="size-tip">超过此大小将被截断，留空则不限制</span>
              </el-form-item>
            </el-form>
          </el-card>
        </div>
        
        <el-button type="primary" link @click="addRule">
          <el-icon><Plus /></el-icon> 添加收集规则
        </el-button>
      </div>
    </template>
    
    <el-empty v-else description="未启用输出文件收集" :image-size="60" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { Plus, Delete, Folder } from '@element-plus/icons-vue'

interface CollectRule {
  name: string
  path: string
  type: 'file' | 'directory' | 'pattern'
  required: boolean
  maxSize: string
}

const props = defineProps<{
  modelValue: any
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: any): void
}>()

const collectEnabled = ref(false)
const rules = ref<CollectRule[]>([])

// 解析传入的值
function parseModelValue(value: any) {
  if (!value) {
    collectEnabled.value = false
    rules.value = []
    return
  }
  
  collectEnabled.value = value.collectEnabled === true
  rules.value = (value.collectRules || []).map((r: any) => ({
    name: r.name || '',
    path: r.path || '',
    type: r.type || 'file',
    required: r.required === true,
    maxSize: r.maxSize || ''
  }))
}

// 监听输入变化
watch(() => props.modelValue, (val) => {
  parseModelValue(val)
}, { immediate: true })

// 计算输出值
const outputValue = computed(() => {
  if (!collectEnabled.value) {
    return { collectEnabled: false, collectRules: [] }
  }
  
  return {
    collectEnabled: true,
    collectRules: rules.value.map(r => ({
      name: r.name,
      path: r.path,
      type: r.type,
      required: r.required,
      maxSize: r.maxSize || null
    }))
  }
})

// 监听变化并输出
watch([collectEnabled, rules], () => {
  emit('update:modelValue', outputValue.value)
}, { deep: true })

function addRule() {
  rules.value.push({
    name: '',
    path: '',
    type: 'file',
    required: false,
    maxSize: ''
  })
}

function removeRule(index: number) {
  rules.value.splice(index, 1)
}
</script>

<style lang="scss" scoped>
.output-collect-config {
  width: 100%;
  
  .config-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;
    
    .title {
      font-size: 14px;
      font-weight: 500;
    }
  }
  
  .collect-rules {
    .rule-item {
      margin-bottom: 12px;
      
      .rule-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 12px;
        
        .rule-index {
          font-size: 13px;
          font-weight: 500;
          color: #606266;
        }
      }
    }
  }
  
  .path-tip {
    margin-top: 4px;
    font-size: 12px;
    color: #909399;
  }
  
  .size-tip {
    margin-left: 8px;
    font-size: 12px;
    color: #909399;
  }
}
</style>

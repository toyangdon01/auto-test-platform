<template>
  <div class="page-card">
    <div class="page-header">
      <h3 class="page-title">批量导入服务器</h3>
    </div>

    <el-alert type="info" :closable="false" show-icon class="mb-20">
      <template #title>
        支持 CSV、Excel 格式文件，首次导入请先下载模板
      </template>
    </el-alert>

    <el-card shadow="never">
      <div class="import-steps">
        <el-steps :active="currentStep" align-center>
          <el-step title="下载模板" />
          <el-step title="上传文件" />
          <el-step title="确认导入" />
        </el-steps>
      </div>

      <div v-if="currentStep === 0" class="step-content">
        <el-button type="primary" @click="downloadTemplate">
          <el-icon><Download /></el-icon>
          下载导入模板
        </el-button>
        <div class="template-preview mt-20">
          <el-descriptions title="模板字段说明" :column="1" border size="small">
            <el-descriptions-item label="name">服务器名称（必填）</el-descriptions-item>
            <el-descriptions-item label="host">主机地址/IP（必填）</el-descriptions-item>
            <el-descriptions-item label="port">SSH端口，默认22</el-descriptions-item>
            <el-descriptions-item label="username">用户名（必填）</el-descriptions-item>
            <el-descriptions-item label="authType">认证方式：password 或 key（必填）</el-descriptions-item>
            <el-descriptions-item label="authSecret">密码或私钥内容（必填）</el-descriptions-item>
            <el-descriptions-item label="groupId">分组ID</el-descriptions-item>
            <el-descriptions-item label="tags">标签，多个用逗号分隔</el-descriptions-item>
          </el-descriptions>
        </div>
      </div>

      <div v-if="currentStep === 1" class="step-content">
        <el-upload
          ref="uploadRef"
          drag
          action="#"
          :auto-upload="false"
          :limit="1"
          accept=".csv,.xlsx,.xls"
          :on-change="handleFileChange"
          :on-remove="handleFileRemove"
        >
          <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
          <div class="el-upload__text">
            将文件拖到此处，或 <em>点击上传</em>
          </div>
          <template #tip>
            <div class="el-upload__tip">支持 CSV、Excel 格式，单次最多导入 100 条</div>
          </template>
        </el-upload>
      </div>

      <div v-if="currentStep === 2" class="step-content">
        <el-alert v-if="importResult" :title="`预览数据: ${previewData.length} 条`" type="info" class="mb-10" />
        <el-table :data="previewData" stripe max-height="400">
          <el-table-column prop="name" label="名称" width="150" />
          <el-table-column prop="host" label="地址" width="150" />
          <el-table-column prop="port" label="端口" width="80" />
          <el-table-column prop="username" label="用户名" width="120" />
          <el-table-column prop="authType" label="认证方式" width="100" />
          <el-table-column prop="groupId" label="分组" width="80">
            <template #default="{ row }">
              {{ row.groupId || '-' }}
            </template>
          </el-table-column>
          <el-table-column prop="valid" label="校验状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.valid ? 'success' : 'danger'" size="small">
                {{ row.valid ? '通过' : '错误' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div v-if="currentStep === 3" class="step-content">
        <el-result
          :icon="importResult?.success > 0 ? 'success' : 'error'"
          :title="importResult?.success > 0 ? '导入完成' : '导入失败'"
        >
          <template #sub-title>
            <div>
              成功: {{ importResult?.success }} 条，失败: {{ importResult?.failed }} 条
              <div v-if="importResult?.errors" class="mt-10 text-danger">
                {{ importResult.errors }}
              </div>
            </div>
          </template>
          <template #extra>
            <el-button type="primary" @click="resetImport">继续导入</el-button>
            <el-button @click="$router.push('/servers/list')">查看服务器列表</el-button>
          </template>
        </el-result>
      </div>

      <div v-if="currentStep < 3" class="step-actions">
        <el-button v-if="currentStep > 0" @click="currentStep--">上一步</el-button>
        <el-button v-if="currentStep < 2" type="primary" :disabled="!canNext" @click="currentStep++">
          下一步
        </el-button>
        <el-button v-if="currentStep === 2" type="primary" :loading="importing" @click="handleImport">
          确认导入
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, UploadFilled } from '@element-plus/icons-vue'
import { serverApi, type ServerCreateParams } from '@/api/server'

interface PreviewRow extends ServerCreateParams {
  valid: boolean
}

const currentStep = ref(0)
const canNext = ref(false)
const previewData = ref<PreviewRow[]>([])
const uploadRef = ref()
const importing = ref(false)
const importResult = ref<{ total: number; success: number; failed: number; errors: string } | null>(null)

function downloadTemplate() {
  // 生成 CSV 模板
  const headers = 'name,host,port,username,authType,authSecret,groupId,tags'
  const example = '\nServer-01,192.168.1.10,22,root,password,yourpassword,,test,prod'
  const content = headers + example
  
  const blob = new Blob(['\ufeff' + content], { type: 'text/csv;charset=utf-8' })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = 'server_import_template.csv'
  link.click()
  URL.revokeObjectURL(link.href)
  
  ElMessage.success('模板下载成功')
  canNext.value = true
}

function handleFileChange(uploadFile: any) {
  const file = uploadFile.raw
  if (!file) return

  const reader = new FileReader()
  reader.onload = (e) => {
    try {
      const content = e.target?.result as string
      const lines = content.split('\n').filter((line) => line.trim())
      
      // 跳过标题行
      const dataLines = lines.slice(1)
      previewData.value = dataLines.slice(0, 100).map((line) => {
        const cols = line.split(',').map((c) => c.trim())
        const row: PreviewRow = {
          name: cols[0] || '',
          host: cols[1] || '',
          port: parseInt(cols[2]) || 22,
          username: cols[3] || '',
          authType: cols[4] || 'password',
          authSecret: cols[5] || '',
          groupId: cols[6] ? parseInt(cols[6]) : undefined,
          tags: cols[7] ? cols[7].split(';').filter(Boolean) : [],
          remark: '',
          valid: !!(cols[0] && cols[1] && cols[3] && cols[4] && cols[5]),
        }
        return row
      })
      
      canNext.value = previewData.value.length > 0
      ElMessage.success(`解析成功，共 ${previewData.value.length} 条数据`)
    } catch (err) {
      ElMessage.error('文件解析失败')
    }
  }
  reader.readAsText(file)
}

function handleFileRemove() {
  previewData.value = []
  canNext.value = false
}

async function handleImport() {
  const validData = previewData.value.filter((row) => row.valid)
  if (validData.length === 0) {
    ElMessage.warning('没有有效数据可导入')
    return
  }

  importing.value = true
  try {
    const res = await serverApi.batchCreate(validData)
    importResult.value = res.data
    currentStep.value = 3
  } catch (err) {
    ElMessage.error('导入失败')
  } finally {
    importing.value = false
  }
}

function resetImport() {
  currentStep.value = 0
  canNext.value = false
  previewData.value = []
  importResult.value = null
}
</script>

<style lang="scss" scoped>
.import-steps {
  padding: 20px 0 40px;
}

.step-content {
  padding: 40px;
  text-align: center;
}

.step-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
  padding-top: 20px;
  border-top: 1px solid var(--border-lighter);
}

.template-preview {
  text-align: left;
  max-width: 600px;
  margin: 0 auto;
}

.text-danger {
  color: var(--danger-color);
}
</style>

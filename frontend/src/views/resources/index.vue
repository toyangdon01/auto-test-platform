<template>
  <div class="resource-manage">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>资源文件管理</span>
          <el-button type="primary" @click="showUploadDialog = true">
            <el-icon><Upload /></el-icon>
            上传文件
          </el-button>
        </div>
      </template>

      <!-- 搜索栏 -->
      <el-form :inline="true" class="search-form">
        <el-form-item label="文件名">
          <el-input v-model="searchForm.name" placeholder="搜索文件名" clearable @keyup.enter="loadData" />
        </el-form-item>
        <el-form-item label="文件类型">
          <el-select v-model="searchForm.fileType" placeholder="全部类型" clearable>
            <el-option label="二进制文件" value="binary" />
            <el-option label="RPM 包" value="rpm" />
            <el-option label="压缩包" value="tar" />
            <el-option label="ZIP 包" value="zip" />
            <el-option label="配置文件" value="config" />
            <el-option label="其他" value="other" />
          </el-select>
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="searchForm.category" placeholder="全部分类" clearable>
            <el-option v-for="cat in TEST_CATEGORIES" :key="cat.value" :label="cat.label" :value="cat.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 文件列表 -->
      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column prop="name" label="文件名" min-width="200">
          <template #default="{ row }">
            <el-tooltip :content="row.name" placement="top">
              <span class="file-name">{{ row.name }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column prop="fileType" label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="getFileTypeTag(row.fileType)">
              {{ getFileTypeLabel(row.fileType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="category" label="分类" width="120">
          <template #default="{ row }">
            {{ getCategoryLabel(row.category) }}
          </template>
        </el-table-column>
        <el-table-column prop="fileSize" label="大小" width="100">
          <template #default="{ row }">
            {{ formatSize(row.fileSize) }}
          </template>
        </el-table-column>
        <el-table-column prop="checksum" label="MD5" width="200">
          <template #default="{ row }">
            <el-tooltip :content="row.checksum" placement="top">
              <span class="checksum">{{ row.checksum?.substring(0, 16) }}...</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="150" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="上传时间" width="160">
          <template #default="{ row }">
            {{ formatTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="downloadFile(row)">下载</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :page-sizes="[20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next"
        @size-change="loadData"
        @current-change="loadData"
        class="pagination"
      />
    </el-card>

    <!-- 上传对话框 -->
    <el-dialog v-model="showUploadDialog" title="上传资源文件" width="500px">
      <el-form :model="uploadForm" label-width="80px">
        <el-form-item label="选择文件" required>
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :limit="1"
            :on-change="handleFileChange"
            drag
          >
            <el-icon class="el-icon--upload"><upload-filled /></el-icon>
            <div class="el-upload__text">
              拖拽文件到此处或 <em>点击上传</em>
            </div>
            <template #tip>
              <div class="el-upload__tip">
                支持最大 20GB 文件
              </div>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item label="文件类型">
          <el-select v-model="uploadForm.fileType" placeholder="请选择类型">
            <el-option label="二进制文件" value="binary" />
            <el-option label="RPM 包" value="rpm" />
            <el-option label="压缩包" value="tar" />
            <el-option label="ZIP 包" value="zip" />
            <el-option label="配置文件" value="config" />
            <el-option label="其他" value="other" />
          </el-select>
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="uploadForm.category" placeholder="请选择分类">
            <el-option v-for="cat in TEST_CATEGORIES" :key="cat.value" :label="cat.label" :value="cat.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="uploadForm.description" type="textarea" :rows="3" placeholder="文件描述（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showUploadDialog = false">取消</el-button>
        <el-button type="primary" @click="handleUpload" :loading="uploading">
          {{ uploading ? '上传中...' : '上传' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload, UploadFilled } from '@element-plus/icons-vue'
import { resourceApi, type ResourceFile } from '@/api/resource'
import { TEST_CATEGORIES, getCategoryLabel } from '@/config/categories'

const loading = ref(false)
const tableData = ref<ResourceFile[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)

const searchForm = ref({
  name: '',
  fileType: '',
  category: ''
})

const showUploadDialog = ref(false)
const uploading = ref(false)
const uploadRef = ref()
const uploadForm = ref({
  file: null as File | null,
  fileType: '',
  category: '',
  description: ''
})

onMounted(() => {
  loadData()
})

async function loadData() {
  loading.value = true
  try {
    const response = await resourceApi.getPage({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      ...searchForm.value
    })
    tableData.value = response.data.records || []
    total.value = response.data.total || 0
  } catch (error) {
    console.error('加载数据失败:', error)
  } finally {
    loading.value = false
  }
}

function resetSearch() {
  searchForm.value = { name: '', fileType: '', category: '' }
  pageNum.value = 1
  loadData()
}

function handleFileChange(file: any) {
  uploadForm.value.file = file.raw
}

async function handleUpload() {
  if (!uploadForm.value.file) {
    ElMessage.warning('请选择文件')
    return
  }

  uploading.value = true
  try {
    await resourceApi.upload(
      uploadForm.value.file,
      uploadForm.value.fileType,
      uploadForm.value.category,
      uploadForm.value.description
    )
    ElMessage.success('上传成功')
    showUploadDialog.value = false
    uploadForm.value = { file: null, fileType: '', category: '', description: '' }
    uploadRef.value?.clearFiles()
    loadData()
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '上传失败')
  } finally {
    uploading.value = false
  }
}

function downloadFile(row: ResourceFile) {
  const url = resourceApi.getDownloadUrl(row.id)
  window.open(url, '_blank')
}

async function handleDelete(row: ResourceFile) {
  try {
    await ElMessageBox.confirm(`确定删除文件 "${row.name}" 吗？`, '确认删除', { type: 'warning' })
    await resourceApi.delete(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

function getFileTypeTag(type: string) {
  const map: Record<string, string> = {
    binary: 'danger',
    rpm: 'warning',
    tar: 'success',
    zip: 'success',
    config: 'info',
    other: ''
  }
  return map[type] || ''
}

function getFileTypeLabel(type: string) {
  const map: Record<string, string> = {
    binary: '二进制',
    rpm: 'RPM',
    tar: '压缩包',
    zip: 'ZIP',
    config: '配置',
    other: '其他'
  }
  return map[type] || type
}

function formatSize(bytes: number) {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(2) + ' KB'
  if (bytes < 1024 * 1024 * 1024) return (bytes / 1024 / 1024).toFixed(2) + ' MB'
  return (bytes / 1024 / 1024 / 1024).toFixed(2) + ' GB'
}

function formatTime(time: string) {
  if (!time) return ''
  return time.replace('T', ' ').substring(0, 16)
}
</script>

<style scoped>
.resource-manage {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.search-form {
  margin-bottom: 20px;
}

.file-name {
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: inline-block;
}

.checksum {
  font-family: monospace;
  font-size: 12px;
}

.pagination {
  margin-top: 20px;
  justify-content: flex-end;
}
</style>

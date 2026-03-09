<template>
  <div class="page-card">
    <div class="page-header">
      <h3 class="page-title">工作台</h3>
    </div>
    
    <!-- 统计卡片 -->
    <el-row :gutter="20" class="stat-cards">
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-icon server">
            <el-icon :size="28"><Monitor /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.serverCount }}</div>
            <div class="stat-label">服务器总数</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-icon script">
            <el-icon :size="28"><Document /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.scriptCount }}</div>
            <div class="stat-label">脚本总数</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-icon task">
            <el-icon :size="28"><List /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.runningTasks }}</div>
            <div class="stat-label">执行中任务</div>
          </div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-icon success">
            <el-icon :size="28"><CircleCheck /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.successRate }}%</div>
            <div class="stat-label">成功率</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 快捷操作 -->
    <div class="section">
      <h4 class="section-title">快捷操作</h4>
      <el-row :gutter="20">
        <el-col :span="6">
          <el-card shadow="hover" class="action-card" @click="$router.push('/servers/list')">
            <el-icon :size="32" color="#409eff"><Monitor /></el-icon>
            <span>服务器管理</span>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="action-card" @click="$router.push('/scripts/list')">
            <el-icon :size="32" color="#67c23a"><Document /></el-icon>
            <span>脚本中心</span>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="action-card" @click="$router.push('/tasks/create')">
            <el-icon :size="32" color="#e6a23c"><Plus /></el-icon>
            <span>创建任务</span>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover" class="action-card" @click="$router.push('/results/list')">
            <el-icon :size="32" color="#909399"><DataAnalysis /></el-icon>
            <span>测试结果</span>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <!-- 最近任务 -->
    <div class="section">
      <h4 class="section-title">最近任务</h4>
      <el-table :data="recentTasks" stripe>
        <el-table-column prop="name" label="任务名称" min-width="200" />
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="$router.push(`/tasks/detail/${row.id}`)">
              详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Monitor, Document, List, CircleCheck, Plus, DataAnalysis } from '@element-plus/icons-vue'

// 统计数据
const stats = ref({
  serverCount: 0,
  scriptCount: 0,
  runningTasks: 0,
  successRate: 0,
})

// 最近任务
const recentTasks = ref([
  { id: 1, name: 'CPU压力测试', status: 'running', createdAt: '2026-03-09 10:00' },
  { id: 2, name: '内存带宽测试', status: 'completed', createdAt: '2026-03-09 09:30' },
  { id: 3, name: '磁盘IO测试', status: 'failed', createdAt: '2026-03-08 16:00' },
])

// 获取状态标签类型
function getStatusType(status: string) {
  const types: Record<string, string> = {
    pending: 'info',
    running: 'warning',
    completed: 'success',
    failed: 'danger',
    cancelled: 'info',
  }
  return types[status] || 'info'
}

onMounted(() => {
  // TODO: 加载统计数据
  stats.value = {
    serverCount: 12,
    scriptCount: 25,
    runningTasks: 3,
    successRate: 95.5,
  }
})
</script>

<style lang="scss" scoped>
.stat-cards {
  margin-bottom: 24px;
}

.stat-card {
  display: flex;
  align-items: center;
  padding: 20px;
  background: #fff;
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-sm);

  .stat-icon {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 56px;
    height: 56px;
    border-radius: 12px;
    margin-right: 16px;

    &.server {
      background: rgba(64, 158, 255, 0.1);
      color: #409eff;
    }

    &.script {
      background: rgba(103, 194, 58, 0.1);
      color: #67c23a;
    }

    &.task {
      background: rgba(230, 162, 60, 0.1);
      color: #e6a23c;
    }

    &.success {
      background: rgba(103, 194, 58, 0.1);
      color: #67c23a;
    }
  }

  .stat-value {
    font-size: 28px;
    font-weight: 600;
    color: var(--text-primary);
  }

  .stat-label {
    font-size: 14px;
    color: var(--text-secondary);
    margin-top: 4px;
  }
}

.section {
  margin-top: 24px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 16px;
}

.action-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 24px;
  cursor: pointer;
  transition: transform 0.2s;

  &:hover {
    transform: translateY(-4px);
  }

  span {
    font-size: 14px;
    color: var(--text-primary);
  }
}
</style>

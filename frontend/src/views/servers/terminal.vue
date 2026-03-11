<template>
  <div class="terminal-page">
    <div class="page-header">
      <div class="header-left">
        <el-button link @click="router.back()">
          <el-icon><ArrowLeft /></el-icon>
          返回
        </el-button>
        <el-divider direction="vertical" />
        <span class="page-title">服务器终端</span>
        <el-tag v-if="serverInfo" type="info" size="small">
          {{ serverInfo.name }}
        </el-tag>
      </div>
    </div>
    
    <div class="terminal-container">
      <WebShell 
        v-if="serverId" 
        :server-id="serverId" 
        :server-info="serverInfo"
      />
      <el-empty v-else description="服务器不存在" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import WebShell from '@/components/WebShell.vue'
import request from '@/utils/request'

const route = useRoute()
const router = useRouter()

const serverId = ref<number>(0)
const serverInfo = ref<{ name: string; host: string } | null>(null)

// 获取服务器信息
async function loadServerInfo() {
  const id = Number(route.params.id)
  if (!id) {
    return
  }
  
  serverId.value = id
  
  try {
    const res = await request.get(`/servers/${id}`)
    if (res.code === 0 && res.data) {
      serverInfo.value = {
        name: res.data.name,
        host: res.data.host,
      }
    }
  } catch (e) {
    console.error('获取服务器信息失败:', e)
  }
}

onMounted(() => {
  loadServerInfo()
})
</script>

<style lang="scss" scoped>
.terminal-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 100px);
  padding: 16px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.page-title {
  font-size: 16px;
  font-weight: 500;
}

.terminal-container {
  flex: 1;
  min-height: 0;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}
</style>

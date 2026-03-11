<template>
  <div class="webshell-container">
    <!-- 顶部工具栏 -->
    <div class="terminal-header">
      <div class="server-info">
        <el-tag v-if="serverInfo" type="success">
          {{ serverInfo.name }} ({{ serverInfo.host }})
        </el-tag>
        <el-tag v-else type="info">未连接</el-tag>
        <el-tag v-if="connected" type="success" size="small">
          <el-icon><Link /></el-icon> 已连接
        </el-tag>
        <el-tag v-else type="danger" size="small">
          <el-icon><CircleClose /></el-icon> 未连接
        </el-tag>
      </div>
      <div class="toolbar">
        <el-button 
          size="small" 
          @click="reconnect" 
          :disabled="connected"
          v-if="!connected"
        >
          重新连接
        </el-button>
        <el-button 
          size="small" 
          @click="clearTerminal"
        >
          清屏
        </el-button>
        <el-button 
          size="small" 
          :type="isFullscreen ? 'primary' : 'default'"
          @click="toggleFullscreen"
        >
          {{ isFullscreen ? '退出全屏' : '全屏' }}
        </el-button>
      </div>
    </div>

    <!-- 终端容器 -->
    <div 
      ref="terminalRef" 
      class="terminal-wrapper"
      :class="{ 'fullscreen': isFullscreen }"
    ></div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { Terminal } from '@xterm/xterm'
import { FitAddon } from '@xterm/addon-fit'
import { WebLinksAddon } from '@xterm/addon-web-links'
import '@xterm/xterm/css/xterm.css'
import { ElMessage } from 'element-plus'
import { Link, CircleClose } from '@element-plus/icons-vue'

interface Props {
  serverId: number
  serverInfo?: {
    name: string
    host: string
  }
}

const props = defineProps<Props>()

// 状态
const terminalRef = ref<HTMLElement>()
const connected = ref(false)
const isFullscreen = ref(false)

// Terminal 实例
let terminal: Terminal | null = null
let fitAddon: FitAddon | null = null
let socket: WebSocket | null = null

// 初始化终端
function initTerminal() {
  if (!terminalRef.value) return

  // 创建终端
  terminal = new Terminal({
    fontSize: 14,
    fontFamily: 'Consolas, "Courier New", monospace',
    theme: {
      background: '#1e1e1e',
      foreground: '#d4d4d4',
      cursor: '#ffffff',
      cursorAccent: '#000000',
      selection: 'rgba(255, 255, 255, 0.3)',
      black: '#000000',
      red: '#cd3131',
      green: '#0dbc79',
      yellow: '#e5e510',
      blue: '#2472c8',
      magenta: '#bc3fbc',
      cyan: '#11a8cd',
      white: '#e5e5e5',
      brightBlack: '#666666',
      brightRed: '#f14c4c',
      brightGreen: '#23d18b',
      brightYellow: '#f5f543',
      brightBlue: '#3b8eea',
      brightMagenta: '#d670d6',
      brightCyan: '#29b8db',
      brightWhite: '#ffffff',
    },
    cursorBlink: true,
    cursorStyle: 'block',
    scrollback: 5000,
    tabStopWidth: 4,
  })

  // 加载插件
  fitAddon = new FitAddon()
  terminal.loadAddon(fitAddon)
  terminal.loadAddon(new WebLinksAddon())

  // 打开终端
  terminal.open(terminalRef.value)
  fitAddon.fit()

  // 监听终端输入
  terminal.onData((data) => {
    if (socket && socket.readyState === WebSocket.OPEN) {
      socket.send(data)
    }
  })

  // 监听终端大小变化
  terminal.onResize(({ cols, rows }) => {
    if (socket && socket.readyState === WebSocket.OPEN) {
      // 发送 resize 消息给服务端
      socket.send(JSON.stringify({ type: 'resize', cols, rows }))
    }
  })

  // 写入欢迎信息
  terminal.writeln('\x1b[32m正在连接服务器...\x1b[0m')
}

// 连接 WebSocket
function connectWebSocket() {
  if (!props.serverId) {
    ElMessage.error('服务器ID不能为空')
    return
  }

  // 构建 WebSocket URL
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const host = window.location.host
  const wsUrl = `${protocol}//${host}/api/v1/webshell/${props.serverId}`

  terminal?.writeln(`\x1b[33m连接地址: ${wsUrl}\x1b[0m`)

  // 创建 WebSocket 连接
  socket = new WebSocket(wsUrl)

  socket.onopen = () => {
    connected.value = true
    terminal?.writeln('\x1b[32mWebSocket 连接成功\x1b[0m')
    ElMessage.success('终端连接成功')
  }

  socket.onmessage = (event) => {
    // 将服务端数据写入终端
    terminal?.write(event.data)
  }

  socket.onerror = (error) => {
    connected.value = false
    terminal?.writeln('\x1b[31mWebSocket 连接错误\x1b[0m')
    ElMessage.error('终端连接失败')
    console.error('WebSocket error:', error)
  }

  socket.onclose = (event) => {
    connected.value = false
    terminal?.writeln('\x1b[33mWebSocket 连接已关闭\x1b[0m')
    if (event.code !== 1000) {
      terminal?.writeln(`\x1b[31m关闭原因: ${event.reason || '未知'}\x1b[0m`)
    }
  }
}

// 重新连接
function reconnect() {
  if (socket) {
    socket.close()
  }
  terminal?.writeln('\x1b[33m正在重新连接...\x1b[0m')
  connectWebSocket()
}

// 清屏
function clearTerminal() {
  terminal?.clear()
}

// 全屏切换
function toggleFullscreen() {
  isFullscreen.value = !isFullscreen.value
  setTimeout(() => {
    fitAddon?.fit()
  }, 100)
}

// 处理窗口大小变化
function handleResize() {
  fitAddon?.fit()
}

// 清理资源
function cleanup() {
  if (socket) {
    socket.close()
    socket = null
  }
  if (terminal) {
    terminal.dispose()
    terminal = null
  }
}

onMounted(() => {
  initTerminal()
  connectWebSocket()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  cleanup()
  window.removeEventListener('resize', handleResize)
})

// 监听 serverId 变化
watch(() => props.serverId, (newId, oldId) => {
  if (newId !== oldId) {
    cleanup()
    initTerminal()
    connectWebSocket()
  }
})
</script>

<style lang="scss" scoped>
.webshell-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #1e1e1e;
  border-radius: 8px;
  overflow: hidden;
}

.terminal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 16px;
  background: #2d2d2d;
  border-bottom: 1px solid #3d3d3d;
}

.server-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.toolbar {
  display: flex;
  gap: 8px;
}

.terminal-wrapper {
  flex: 1;
  padding: 8px;
  overflow: hidden;
  
  &.fullscreen {
    position: fixed;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    z-index: 9999;
    border-radius: 0;
  }
}

// 终端样式覆盖
:deep(.xterm) {
  padding: 8px;
}

:deep(.xterm-viewport) {
  &::-webkit-scrollbar {
    width: 8px;
  }
  
  &::-webkit-scrollbar-thumb {
    background: #4d4d4d;
    border-radius: 4px;
    
    &:hover {
      background: #5d5d5d;
    }
  }
}
</style>

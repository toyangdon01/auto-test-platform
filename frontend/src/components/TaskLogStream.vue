<template>
  <div class="log-stream-container">
    <div class="log-toolbar">
      <div class="log-status">
        <el-tag :type="connected ? 'success' : 'danger'" size="small">
          {{ connected ? '已连接' : '未连接' }}
        </el-tag>
        <span v-if="charCount > 0" class="char-count">{{ formatSize(charCount) }}</span>
      </div>
      <div class="log-actions">
        <el-button size="small" @click="clearLogs">
          <el-icon><Delete /></el-icon>清空
        </el-button>
        <el-button size="small" @click="copyLogs">
          <el-icon><CopyDocument /></el-icon>复制
        </el-button>
        <el-button size="small" @click="toggleAutoScroll">
          <el-icon><Bottom v-if="autoScroll" /><VideoPause v-else /></el-icon>
          {{ autoScroll ? '自动滚动' : '暂停滚动' }}
        </el-button>
      </div>
    </div>
    <div ref="terminalRef" class="log-terminal"></div>
    <div v-if="taskCompleted" class="log-complete">
      <el-tag type="info">任务执行完成</el-tag>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { Terminal } from '@xterm/xterm'
import { FitAddon } from '@xterm/addon-fit'
import { ElMessage } from 'element-plus'
import { Delete, CopyDocument, Bottom, VideoPause } from '@element-plus/icons-vue'
import '@xterm/xterm/css/xterm.css'

const props = defineProps<{
  taskId: number
  taskStatus?: string
}>()

const terminalRef = ref<HTMLElement | null>(null)
const connected = ref(false)
const autoScroll = ref(true)
const charCount = ref(0)
const taskCompleted = ref(false)

let terminal: Terminal | null = null
let fitAddon: FitAddon | null = null
let ws: WebSocket | null = null
let reconnectTimer: ReturnType<typeof setTimeout> | null = null
let reconnectAttempts = 0
const MAX_RECONNECT_ATTEMPTS = 5
const RECONNECT_DELAY = 3000

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

function initTerminal() {
  if (!terminalRef.value) return

  terminal = new Terminal({
    fontSize: 13,
    fontFamily: 'Consolas, "Courier New", monospace',
    theme: {
      background: '#1e1e1e',
      foreground: '#d4d4d4',
      cursor: '#ffffff',
      cursorAccent: '#000000',
      selection: 'rgba(255, 255, 255, 0.3)',
    },
    cursorBlink: false,
    cursorStyle: 'block',
    scrollback: 10000,
    tabStopWidth: 4,
    convertEol: true, // 重要：启用后 \n 会正确处理为换行
  })

  fitAddon = new FitAddon()
  terminal.loadAddon(fitAddon)

  terminal.open(terminalRef.value)
  
  // 禁用鼠标输入（只读）
  terminal.options.disableStdin = true
  
  // 监听滚动事件
  terminal.onScroll(() => {
    if (!terminal) return
    const isAtBottom = terminal.buffer.active.cursorY >= terminal.buffer.active.length - terminal.rows
    if (!isAtBottom && autoScroll.value) {
      autoScroll.value = false
    } else if (isAtBottom && !autoScroll.value) {
      autoScroll.value = true
    }
  })

  setTimeout(() => fitAddon?.fit(), 100)
}

function connect() {
  if (ws) {
    ws.close()
  }

  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const host = window.location.host
  const wsUrl = `${protocol}//${host}/ws/task-log/${props.taskId}`
  
  console.log('[WebSocket] 连接:', wsUrl)
  terminal?.writeln(`\x1b[33m正在连接日志服务器...\x1b[0m`)
  
  ws = new WebSocket(wsUrl)
  
  ws.onopen = () => {
    console.log('[WebSocket] 已连接:', wsUrl)
    connected.value = true
    reconnectAttempts = 0
  }
  
  ws.onmessage = (event) => {
    const data = event.data
    
    if (data.startsWith('history:')) {
      const chunk = data.substring(8)
      terminal?.write(chunk)
      updateCharCount()
    } else if (data.startsWith('log:')) {
      const line = data.substring(4)
      terminal?.write(line + '\r\n')
      updateCharCount()
    } else if (data.startsWith('chunk:')) {
      const chunk = data.substring(6)
      terminal?.write(chunk)
      updateCharCount()
    } else if (data.startsWith('complete:')) {
      taskCompleted.value = true
      terminal?.writeln('')
      terminal?.writeln('\x1b[32m===== 任务执行完成 =====\x1b[0m')
    }
  }
  
  ws.onerror = (error) => {
    console.error('[WebSocket] 错误:', error)
    terminal?.writeln('\x1b[31mWebSocket 连接错误\x1b[0m')
  }
  
  ws.onclose = () => {
    console.log('[WebSocket] 连接关闭')
    connected.value = false
    
    if (!taskCompleted.value && props.taskStatus === 'running') {
      scheduleReconnect()
    }
  }
}

function updateCharCount() {
  if (!terminal) return
  const buffer = terminal.buffer.active
  let count = 0
  for (let i = 0; i < buffer.length; i++) {
    count += buffer.getLine(i)?.length || 0
  }
  charCount.value = count
  
  if (autoScroll.value) {
    terminal?.scrollToBottom()
  }
}

function scrollToBottom() {
  terminal?.scrollToBottom()
}

function handleResize() {
  fitAddon?.fit()
}

function clearLogs() {
  terminal?.clear()
  charCount.value = 0
}

function copyLogs() {
  if (!terminal) return
  const buffer = terminal.buffer.active
  let text = ''
  for (let i = 0; i < buffer.length; i++) {
    const line = buffer.getLine(i)
    if (line) {
      text += line.translateToString() + '\n'
    }
  }
  navigator.clipboard.writeText(text).then(() => {
    ElMessage.success('已复制到剪贴板')
  }).catch(() => {
    ElMessage.error('复制失败')
  })
}

function toggleAutoScroll() {
  autoScroll.value = !autoScroll.value
  if (autoScroll.value) {
    scrollToBottom()
  }
}

function scheduleReconnect() {
  if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
    console.log('[WebSocket] 达到最大重连次数')
    terminal?.writeln('\x1b[33m已达到最大重连次数\x1b[0m')
    return
  }
  
  reconnectAttempts++
  console.log(`[WebSocket] ${RECONNECT_DELAY / 1000}秒后重连 (${reconnectAttempts}/${MAX_RECONNECT_ATTEMPTS})`)
  terminal?.writeln(`\x1b[33m${RECONNECT_DELAY / 1000}秒后重连... (${reconnectAttempts}/${MAX_RECONNECT_ATTEMPTS})\x1b[0m`)
  
  reconnectTimer = setTimeout(() => {
    connect()
  }, RECONNECT_DELAY)
}

// 心跳保活
let pingTimer: ReturnType<typeof setInterval> | null = null
function startPing() {
  pingTimer = setInterval(() => {
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send('ping')
    }
  }, 30000)
}

function stopPing() {
  if (pingTimer) {
    clearInterval(pingTimer)
    pingTimer = null
  }
}

// 监听任务状态变化
watch(() => props.taskStatus, (newStatus) => {
  if (newStatus === 'running' && !connected.value) {
    connect()
  }
  if (newStatus !== 'running' && newStatus !== 'pending') {
    taskCompleted.value = true
  }
})

onMounted(() => {
  initTerminal()
  connect()
  startPing()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  if (ws) {
    ws.close()
  }
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
  }
  stopPing()
  if (terminal) {
    terminal.dispose()
  }
  window.removeEventListener('resize', handleResize)
})
</script>

<style scoped>
.log-stream-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #1e1e1e;
  border-radius: 4px;
  overflow: hidden;
}

.log-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: #2d2d2d;
  border-bottom: 1px solid #404040;
}

.log-status {
  display: flex;
  align-items: center;
  gap: 12px;
}

.char-count {
  color: #888;
  font-size: 12px;
}

.log-actions {
  display: flex;
  gap: 8px;
}

.log-terminal {
  flex: 1;
  padding: 8px;
  overflow: hidden;
}

.log-terminal :deep(.xterm) {
  height: 100%;
}

.log-terminal :deep(.xterm-viewport) {
  overflow-y: auto;
}

.log-terminal :deep(.xterm-viewport)::-webkit-scrollbar {
  width: 8px;
}

.log-terminal :deep(.xterm-viewport)::-webkit-scrollbar-thumb {
  background: #4d4d4d;
  border-radius: 4px;
}

.log-terminal :deep(.xterm-viewport)::-webkit-scrollbar-thumb:hover {
  background: #5d5d5d;
}

.log-complete {
  padding: 8px 12px;
  text-align: center;
  background: #2d2d2d;
  border-top: 1px solid #404040;
}
</style>
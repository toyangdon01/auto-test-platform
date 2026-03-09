import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface SidebarState {
  collapsed: boolean
}

export const useAppStore = defineStore('app', () => {
  // 侧边栏状态
  const sidebar = ref<SidebarState>({
    collapsed: false,
  })

  // 切换侧边栏
  function toggleSidebar() {
    sidebar.value.collapsed = !sidebar.value.collapsed
  }

  // 设置侧边栏状态
  function setSidebarCollapsed(collapsed: boolean) {
    sidebar.value.collapsed = collapsed
  }

  return {
    sidebar,
    toggleSidebar,
    setSidebarCollapsed,
  }
})

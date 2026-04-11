import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export interface SidebarState {
  collapsed: boolean
}

export interface User {
  id: number
  username: string
  nickname: string
  role: string
}

const TOKEN_KEY = 'test_platform_token'
const USER_KEY = 'test_platform_user'

export const useAppStore = defineStore('app', () => {
  // 侧边栏状态
  const sidebar = ref<SidebarState>({
    collapsed: false,
  })

  // 用户信息
  const token = ref<string | null>(localStorage.getItem(TOKEN_KEY))
  const user = ref<User | null>(null)
  
  // 初始化用户信息
  const savedUser = localStorage.getItem(USER_KEY)
  if (savedUser) {
    try {
      user.value = JSON.parse(savedUser)
    } catch {
      localStorage.removeItem(USER_KEY)
    }
  }

  // 是否已登录
  const isLoggedIn = computed(() => !!token.value)

  // 切换侧边栏
  function toggleSidebar() {
    sidebar.value.collapsed = !sidebar.value.collapsed
  }

  // 设置侧边栏状态
  function setSidebarCollapsed(collapsed: boolean) {
    sidebar.value.collapsed = collapsed
  }

  // 登录
  function login(newToken: string, newUser: User) {
    token.value = newToken
    user.value = newUser
    localStorage.setItem(TOKEN_KEY, newToken)
    localStorage.setItem(USER_KEY, JSON.stringify(newUser))
  }

  // 登出
  function logout() {
    token.value = null
    user.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  }

  return {
    sidebar,
    token,
    user,
    isLoggedIn,
    toggleSidebar,
    setSidebarCollapsed,
    login,
    logout,
  }
})

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { serverGroupApi, type ServerGroup } from '@/api/server'

export const useServerStore = defineStore('server', () => {
  // 状态
  const groups = ref<ServerGroup[]>([])
  const loading = ref(false)

  // 计算属性
  const groupOptions = computed(() =>
    groups.value.map((g) => ({ label: g.name, value: g.id }))
  )

  // 方法
  async function fetchGroups() {
    if (groups.value.length > 0) return
    
    loading.value = true
    try {
      const res = await serverGroupApi.list()
      if (res.code === 0) {
        groups.value = res.data
      }
    } finally {
      loading.value = false
    }
  }

  return {
    groups,
    loading,
    groupOptions,
    fetchGroups,
  }
})

import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import NProgress from 'nprogress'

// 布局组件
import Layout from '@/layouts/DefaultLayout.vue'

// 不需要登录的页面
const whiteList = ['/login']

// 路由配置
const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', hidden: true },
  },
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '工作台', icon: 'Odometer' },
      },
    ],
  },
  {
    path: '/servers',
    component: Layout,
    redirect: '/servers/list',
    meta: { title: '服务器管理', icon: 'Monitor' },
    children: [
      {
        path: 'list',
        name: 'ServerList',
        component: () => import('@/views/servers/index.vue'),
        meta: { title: '服务器列表' },
      },
      {
        path: 'groups',
        name: 'ServerGroups',
        component: () => import('@/views/servers/groups.vue'),
        meta: { title: '分组管理' },
      },
      {
        path: 'import',
        name: 'ServerImport',
        component: () => import('@/views/servers/import.vue'),
        meta: { title: '批量导入' },
      },
      {
        path: 'terminal/:id',
        name: 'ServerTerminal',
        component: () => import('@/views/servers/terminal.vue'),
        meta: { title: '服务器终端', hidden: true },
      },
    ],
  },
  {
    path: '/scripts',
    component: Layout,
    redirect: '/scripts/list',
    meta: { title: '脚本中心', icon: 'Document' },
    children: [
      {
        path: 'list',
        name: 'ScriptList',
        component: () => import('@/views/scripts/index.vue'),
        meta: { title: '脚本列表' },
      },
      {
        path: 'create',
        name: 'ScriptCreate',
        component: () => import('@/views/scripts/editor.vue'),
        meta: { title: '创建脚本', hidden: true },
      },
      {
        path: 'edit/:id',
        name: 'ScriptEdit',
        component: () => import('@/views/scripts/editor.vue'),
        meta: { title: '编辑脚本', hidden: true },
      },
    ],
  },
  {
    path: '/resources',
    component: Layout,
    redirect: '/resources/list',
    meta: { title: '资源管理', icon: 'FolderOpened' },
    children: [
      {
        path: 'list',
        name: 'ResourceList',
        component: () => import('@/views/resources/index.vue'),
        meta: { title: '资源文件' },
      },
    ],
  },
  {
    path: '/tasks',
    component: Layout,
    redirect: '/tasks/list',
    meta: { title: '任务管理', icon: 'List' },
    children: [
      {
        path: 'list',
        name: 'TaskList',
        component: () => import('@/views/tasks/index.vue'),
        meta: { title: '任务列表' },
      },
      {
        path: 'create',
        name: 'TaskCreate',
        component: () => import('@/views/tasks/create.vue'),
        meta: { title: '创建任务', hidden: true },
      },
      {
        path: 'detail/:id',
        name: 'TaskDetail',
        component: () => import('@/views/tasks/detail.vue'),
        meta: { title: '任务详情', hidden: true },
      },
      {
        path: 'scheduled',
        name: 'ScheduledTasks',
        component: () => import('@/views/tasks/scheduled.vue'),
        meta: { title: '定时任务' },
      },
    ],
  },
  {
    path: '/results',
    component: Layout,
    redirect: '/results/list',
    meta: { title: '测试结果', icon: 'DataAnalysis' },
    children: [
      {
        path: 'list',
        name: 'ResultList',
        component: () => import('@/views/results/index.vue'),
        meta: { title: '结果列表' },
      },
      {
        path: 'detail/:id',
        name: 'ResultDetail',
        component: () => import('@/views/results/detail.vue'),
        meta: { title: '结果详情', hidden: true },
      },
      {
        path: 'compare',
        name: 'ResultCompare',
        component: () => import('@/views/results/compare.vue'),
        meta: { title: '结果对比' },
      },
      {
        path: 'trend',
        name: 'ResultTrend',
        component: () => import('@/views/results/trend.vue'),
        meta: { title: '趋势分析' },
      },
    ],
  },
  {
    path: '/reports',
    component: Layout,
    redirect: '/reports/list',
    meta: { title: '报告中心', icon: 'Tickets' },
    children: [
      {
        path: 'list',
        name: 'ReportList',
        component: () => import('@/views/reports/index.vue'),
        meta: { title: '报告列表' },
      },
      {
        path: 'detail/:id',
        name: 'ReportDetail',
        component: () => import('@/views/reports/detail.vue'),
        meta: { title: '报告详情', hidden: true },
      },
    ],
  },
  {
    path: '/analysis',
    component: Layout,
    redirect: '/analysis/trend',
    meta: { title: '数据分析', icon: 'TrendCharts' },
    children: [
      {
        path: 'trend',
        name: 'TrendAnalysis',
        component: () => import('@/views/analysis/trend.vue'),
        meta: { title: '趋势分析' },
      },
    ],
  },
  {
    path: '/settings',
    component: Layout,
    redirect: '/settings/config',
    meta: { title: '系统设置', icon: 'Setting' },
    children: [
      {
        path: 'config',
        name: 'SystemConfig',
        component: () => import('@/views/settings/config.vue'),
        meta: { title: '系统配置' },
      },
      {
        path: 'metrics',
        name: 'MetricConfig',
        component: () => import('@/views/settings/metrics.vue'),
        meta: { title: '指标定义' },
      },
      {
        path: 'rules',
        name: 'ResultRules',
        component: () => import('@/views/settings/rules.vue'),
        meta: { title: '判定规则' },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: { title: '404', hidden: true },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 路由守卫
router.beforeEach((to, _from, next) => {
  NProgress.start()
  
  // 设置页面标题
  const title = to.meta?.title as string
  document.title = title ? `${title} - 自动化测试平台` : '自动化测试平台'
  
  // 检查登录状态
  const token = localStorage.getItem('test_platform_token')
  
  if (whiteList.includes(to.path)) {
    // 已登录访问登录页，跳转到首页
    if (token) {
      next('/dashboard')
    } else {
      next()
    }
  } else {
    // 需要登录的页面
    if (token) {
      next()
    } else {
      next('/login')
    }
  }
})

router.afterEach(() => {
  NProgress.done()
})

export default router

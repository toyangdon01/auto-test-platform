# 自动化测试管理平台 - 前端

> Vue 3 + TypeScript + Element Plus 前端项目脚手架

## 技术栈

- **Vue**: 3.4.x
- **TypeScript**: 5.4.x
- **构建工具**: Vite 5.x
- **UI 框架**: Element Plus 2.x
- **状态管理**: Pinia 2.x
- **路由**: Vue Router 4.x
- **HTTP 客户端**: Axios
- **图表**: ECharts 5.x
- **样式**: SCSS

## 项目结构

```
frontend/
├── public/                    # 静态资源
│   └── favicon.svg
├── src/
│   ├── api/                   # API 接口
│   │   ├── index.ts
│   │   ├── server.ts          # 服务器相关 API
│   │   └── script.ts          # 脚本/任务相关 API
│   ├── assets/                # 资源文件
│   │   └── logo.svg
│   ├── layouts/               # 布局组件
│   │   └── DefaultLayout.vue
│   ├── router/                # 路由配置
│   │   └── index.ts
│   ├── stores/                # Pinia 状态管理
│   │   ├── index.ts
│   │   ├── app.ts
│   │   └── server.ts
│   ├── styles/                # 全局样式
│   │   ├── index.scss
│   │   ├── variables.scss
│   │   ├── reset.scss
│   │   └── common.scss
│   ├── utils/                 # 工具函数
│   │   └── request.ts         # Axios 封装
│   ├── views/                 # 页面组件
│   │   ├── dashboard/         # 工作台
│   │   ├── servers/           # 服务器管理
│   │   ├── scripts/           # 脚本中心
│   │   ├── tasks/             # 任务管理
│   │   ├── results/           # 测试结果
│   │   ├── reports/           # 报告中心
│   │   ├── settings/          # 系统设置
│   │   ├── login/             # 登录
│   │   └── error/             # 错误页面
│   ├── App.vue
│   └── main.ts
├── index.html
├── package.json
├── tsconfig.json
├── vite.config.ts
└── README.md
```

## 页面清单

| 模块 | 页面 | 路由 |
|------|------|------|
| 工作台 | Dashboard | `/dashboard` |
| 服务器管理 | 服务器列表 | `/servers/list` |
| | 分组管理 | `/servers/groups` |
| | 批量导入 | `/servers/import` |
| 脚本中心 | 脚本列表 | `/scripts/list` |
| | 脚本编辑器 | `/scripts/create`, `/scripts/edit/:id` |
| 任务管理 | 任务列表 | `/tasks/list` |
| | 创建任务 | `/tasks/create` |
| | 任务详情 | `/tasks/detail/:id` |
| 测试结果 | 结果列表 | `/results/list` |
| | 结果详情 | `/results/detail/:id` |
| | 结果对比 | `/results/compare` |
| | 趋势分析 | `/results/trend` |
| 报告中心 | 报告列表 | `/reports/list` |
| | 报告详情 | `/reports/detail/:id` |
| 系统设置 | 系统配置 | `/settings/config` |
| | 指标定义 | `/settings/metrics` |
| | 判定规则 | `/settings/rules` |
| 登录 | 登录页 | `/login` |
| 错误 | 404页面 | `/:pathMatch(.*)*` |

## 快速开始

### 1. 安装依赖

```bash
cd frontend
npm install
# 或
pnpm install
```

### 2. 开发模式

```bash
npm run dev
```

访问 http://localhost:3000

### 3. 生产构建

```bash
npm run build
```

### 4. 预览生产构建

```bash
npm run preview
```

## 开发规范

### 组件命名

- 页面组件：`index.vue`（放在对应目录下）
- 布局组件：`XxxLayout.vue`
- 公共组件：大驼峰命名

### 样式规范

- 使用 SCSS 预处理器
- 全局变量定义在 `styles/variables.scss`
- 组件内样式使用 `scoped`

### API 调用

```typescript
import { serverApi } from '@/api/server'

// 获取列表
const res = await serverApi.list({ page: 1, size: 20 })
if (res.code === 0) {
  tableData.value = res.data.items
}
```

### 路由跳转

```typescript
import { useRouter } from 'vue-router'

const router = useRouter()

// 编程式导航
router.push('/servers/list')

// 带参数
router.push({ path: '/tasks/detail', params: { id: 1 } })
```

## 环境配置

### 代理配置

开发环境代理配置在 `vite.config.ts`：

```typescript
server: {
  port: 3000,
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true,
    },
  },
}
```

### 环境变量

创建 `.env.development` 和 `.env.production` 文件：

```bash
# .env.development
VITE_API_BASE_URL=/api/v1

# .env.production
VITE_API_BASE_URL=/api/v1
```

## 功能特性

- ✅ 响应式布局（支持侧边栏折叠）
- ✅ 路由懒加载
- ✅ 组件自动导入（Element Plus）
- ✅ TypeScript 类型支持
- ✅ API 统一封装
- ✅ 全局状态管理
- ✅ 请求进度条
- ✅ 统一错误处理
- ✅ 页面切换动画

## 待实现功能

- [ ] 用户登录认证
- [ ] WebSocket 实时推送
- [ ] 代码编辑器（Monaco Editor）
- [ ] 文件上传下载
- [ ] 主题切换
- [ ] 国际化
- [ ] 单元测试

---

**版本**: 1.0.0  
**创建日期**: 2026-03-09

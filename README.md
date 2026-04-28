

# 自动化测试管理平台

一个轻量级的自动化测试管理平台，用于管理和执行远程服务器上的测试脚本，收集测试结果和性能指标。

## 功能特性

- **脚本管理**：支持多版本脚本管理、参数配置、文件上传
- **服务器管理**：支持服务器分组、状态监控、SSH 终端（WebShell）
- **任务执行**：支持多服务器并行/串行执行、实时日志、任务取消
- **结果管理**：测试结果解析、判定规则、报告导出
- **指标采集**：支持自定义性能指标采集、数据存储
- **定时任务**：支持 Cron 表达式定时执行测试任务

## 技术栈

### 后端

| 技术 | 说明 |
|------|------|
| Java 17 | 运行环境 |
| Spring Boot 3.2 | Web 框架 |
| MyBatis-Plus 3.5 | ORM 框架 |
| SQLite | 嵌入式数据库（零配置） |
| JSch | SSH 远程执行 |
| WebSocket | 实时日志推送、终端 |

### 前端

| 技术 | 说明 |
|------|------|
| Vue 3 | 前端框架 |
| Vite 5 | 构建工具 |
| Element Plus | UI 组件库 |
| Pinia | 状态管理 |
| Vue Router | 路由管理 |
| xterm.js | 终端模拟 |

## 环境要求

| 依赖 | 版本 | 说明 |
|------|------|------|
| JDK | 17+ | 必须 |
| Node.js | 18+ | 开发/打包需要 |
| Maven | 3.6+ | 开发/打包需要 |

> **注意**：本项目使用 SQLite 嵌入式数据库，无需安装任何数据库服务。


## 开发指南

### 后端开发

```bash
cd backend

# 编译
mvn compile

# 运行（开发模式）
mvn spring-boot:run

# 运行测试
mvn test

# 打包（跳过测试）
mvn package -DskipTests
```

后端服务地址：`http://localhost:8080/api/v1`

### 前端开发

```bash
cd frontend

# 安装依赖
npm install

# 开发模式运行
npm run dev

# 构建生产版本
npm run build

# 代码检查
npm run lint
```

前端开发服务地址：`http://localhost:3000`

### 开发模式

开发时前后端分离运行：

```bash
# 终端 1 - 启动后端
cd backend && mvn spring-boot:run

# 终端 2 - 启动前端
cd frontend && npm run dev
```

前端会自动代理 `/api` 请求到后端 `http://localhost:8080`

## 打包部署

### 完整打包（前后端合并）

**Windows：**
```powershell
.\build.ps1
```

**Linux/Mac：**
```bash
chmod +x build.sh
./build.sh
```

输出文件：`backend/target/auto-test-platform-1.0.0-SNAPSHOT.jar`

### 单独打包

**后端：**
```bash
cd backend
mvn clean package -DskipTests
```

**前端：**
```bash
cd frontend
npm run build
# 输出在 dist/ 目录
```

### 部署要求

打包后的 JAR 文件已包含前端资源，仅需：

1. **JRE 17+** 运行环境
2. 无需数据库、Nginx 等依赖

## 项目结构

```
auto-test-platform/
├── backend/                    # 后端项目
│   ├── src/main/java/         # Java 源码
│   │   └── com/autotest/
│   │       ├── config/        # 配置类
│   │       ├── controller/    # 控制器
│   │       ├── service/       # 业务逻辑
│   │       ├── mapper/        # 数据访问
│   │       ├── entity/        # 实体类
│   │       └── websocket/     # WebSocket 端点
│   └── src/main/resources/
│       ├── application.yml    # 配置文件
│       └── schema.sql         # 数据库初始化脚本
│
├── frontend/                   # 前端项目
│   ├── src/
│   │   ├── views/             # 页面组件
│   │   ├── components/        # 通用组件
│   │   ├── stores/            # Pinia 状态
│   │   ├── router/            # 路由配置
│   │   └── api/               # API 接口
│   ├── vite.config.ts         # Vite 配置
│   └── package.json
│
├── docs/                       # 文档目录
├── scripts/                    # 脚本目录
├── build.ps1                   # Windows 打包脚本
└── build.sh                    # Linux/Mac 打包脚本
```

## 配置说明

### 应用配置

配置文件：`backend/src/main/resources/application.yml`

```yaml
server:
  port: 8080                    # 服务端口

spring:
  datasource:
    url: jdbc:sqlite:${user.home}/.autotest/test_platform.db  # 数据库路径

# 平台自定义配置
autotest:
  storage:
    scripts-path: ${user.home}/auto-test/scripts     # 脚本存储路径
    reports-path: ${user.home}/auto-test/reports     # 报告存储路径
    temp-path: ${user.home}/auto-test/temp           # 临时文件路径
    results-path: ${user.home}/auto-test/results     # 结果文件路径
  
  ssh:
    connect-timeout: 30000      # SSH 连接超时（毫秒）
    exec-timeout: 3600000       # 命令执行超时（毫秒）
  
  task:
    default-timeout: 3600       # 任务默认超时（秒）
```


### 备份恢复

**备份数据库：**
```bash
cp ~/.autotest/test_platform.db test_platform_backup.db
```

**恢复数据库：**
```bash
cp test_platform_backup.db ~/.autotest/test_platform.db
```

## API 文档

启动后端后访问：

- Swagger UI：`http://localhost:8080/api/v1/swagger-ui.html`
- OpenAPI 文档：`http://localhost:8080/api/v1/docs`


## 许可证

MIT License
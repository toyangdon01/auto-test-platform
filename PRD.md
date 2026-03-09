# 自动化测试管理平台 PRD

> 版本：v1.0.2  
> 更新日期：2026-03-09  
> 文档状态：初稿  
> 更新记录：新增脚本生命周期模式（部署/执行/卸载）

---

## 目录

1. [文档信息](#1-文档信息)
2. [产品概述](#2-产品概述)
3. [功能需求详述](#3-功能需求详述)
4. [非功能需求](#4-非功能需求)
5. [数据库设计](#5-数据库设计)
6. [接口设计](#6-接口设计)
7. [页面清单](#7-页面清单)
8. [技术架构](#8-技术架构)
9. [风险与约束](#9-风险与约束)

---

## 1. 文档信息

| 项目 | 内容 |
|------|------|
| 产品名称 | 自动化测试管理平台 |
| 产品版本 | v1.0.0 |
| 目标用户 | 服务器 POC 测试团队 |
| 技术栈 | Vue 3 + Spring Boot + PostgreSQL + 本地文件存储 |

---

## 2. 产品概述

### 2.1 产品定位

面向服务器 POC 团队的一站式性能测试平台，支持自定义测试脚本上传、自动化测试执行、服务器性能指标采集、测试报告生成，旨在提高测试效率、沉淀测试资产。

### 2.2 核心价值

- **效率提升**：自动化执行替代手工操作，减少重复劳动
- **标准化输出**：统一测试流程，规范化测试报告
- **资产沉淀**：测试脚本可复用、可版本管理
- **数据驱动**：性能指标实时采集，支撑量化分析

### 2.3 产品边界

**V1.0.0 范围内：**

| 功能 | 说明 |
|------|------|
| 服务器资源管理 | 服务器信息录入、分组、状态监控 |
| 自定义测试脚本上传与管理 | **核心功能**，支持脚本版本管理、参数配置、输出解析 |
| 性能指标实时采集 | CPU/内存/磁盘/网络指标采集，支持自定义指标 |
| 测试任务调度执行 | 立即执行/定时执行，串行/并行 |
| 测试报告自动生成 | PDF/HTML 格式导出，历史对比 |
| 预置测试脚本 | 基础性能测试、应用性能测试预设脚本 |

**V1.0.0 范围外（后续迭代）：**

- ⏸ 多服务器并行对比测试
- ⏸ 开放 API 接口
- ⏸ 移动端支持
- ⏸ 用户权限管理
- ⏸ 测试环境隔离

---

## 3. 功能需求详述

### 3.1 功能架构图

```
┌─────────────────────────────────────────────────────────────────────┐
│                        自动化测试管理平台 v1.0                        │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐              │
│  │  服务器管理   │  │  系统配置    │  │  文件管理     │              │
│  └──────────────┘  └──────────────┘  └──────────────┘              │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────┐     │
│  │                     脚本中心（核心）                          │     │
│  │  脚本上传 │ 版本管理 │ 参数配置 │ 输出解析 │ 指标定义          │     │
│  └────────────────────────────────────────────────────────────┘     │
│                                                                      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐              │
│  │  测试任务管理  │  │  测试执行引擎  │  │  性能指标采集  │              │
│  └──────────────┘  └──────────────┘  └──────────────┘              │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────┐     │
│  │                   测试结果管理（核心）                        │     │
│  │  结果存储 │ 结果解析 │ 结果判定 │ 结果对比 │ 趋势分析          │     │
│  └────────────────────────────────────────────────────────────┘     │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────┐     │
│  │                       报告中心                               │     │
│  │  报告生成 │ 报告查看 │ 历史对比 │ 报告导出                     │     │
│  └────────────────────────────────────────────────────────────┘     │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

### 3.2 模块一：服务器管理

#### 3.2.1 功能描述

管理被测服务器信息，支持服务器状态监控和分组管理。

#### 3.2.2 功能清单

| 功能点 | 优先级 | 描述 |
|--------|--------|------|
| 服务器添加 | P0 | 录入服务器连接信息（IP、端口、认证） |
| 服务器编辑 | P0 | 修改服务器信息 |
| 服务器删除 | P0 | 删除服务器（有关联任务时提示） |
| 连接测试 | P0 | 验证 SSH 连接是否可用 |
| 服务器分组 | P1 | 按项目/环境分组管理 |
| 状态监控 | P1 | 定时检测服务器在线/离线状态 |
| 批量导入 | P2 | 通过 CSV 批量导入服务器 |
| 配置自动识别 | P1 | SSH 连接成功后自动识别 CPU、内存、磁盘、操作系统 |

#### 3.2.3 服务器信息字段

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| 名称 | String | ✅ | 服务器名称 |
| 主机 | String | ✅ | IP 地址或域名 |
| 端口 | Integer | ✅ | SSH 端口，默认 22 |
| 用户名 | String | ✅ | SSH 登录用户名 |
| 认证方式 | Enum | ✅ | 密码 / SSH密钥 |
| 认证凭证 | String | ✅ | 密码或密钥内容 |
| 操作系统 | String | 自动 | 系统自动识别 |
| CPU 核心数 | Integer | 自动 | 系统自动识别 |
| 内存大小 | String | 自动 | 系统自动识别（如 "64GB"） |
| 磁盘信息 | JSON | 自动 | 系统自动识别 |
| 分组ID | Long | ❌ | 所属分组 |
| 标签 | Array | ❌ | 多标签 |
| 备注 | Text | ❌ | 备注信息 |
| 状态 | Enum | 自动 | 在线/离线/维护中 |
| 创建时间 | DateTime | 自动 | 创建时间 |
| 更新时间 | DateTime | 自动 | 更新时间 |

#### 3.2.4 服务器自动识别命令

```bash
# 操作系统
cat /etc/os-release | grep PRETTY_NAME

# CPU 核心数
nproc

# 内存大小
free -h | grep Mem | awk '{print $2}'

# 磁盘信息
lsblk -J -o NAME,SIZE,TYPE,MOUNTPOINT
```

---

### 3.3 模块二：脚本中心（核心功能）

#### 3.3.1 功能描述

测试脚本的全生命周期管理，支持多文件脚本、压缩包上传、运行入口指定、版本控制、参数配置、输出解析、脚本导出。这是本产品的核心差异化功能。

#### 3.3.2 功能清单

| 功能点 | 优先级 | 描述 |
|--------|--------|------|
| 脚本上传 | P0 | 支持 Shell / Python 脚本，支持单文件或多文件（压缩包 .zip/.tar.gz） |
| 入口文件指定 | P0 | 多文件脚本时，指定运行入口文件 |
| **生命周期模式** | P0 | 支持简单模式（仅执行）和完整模式（部署→执行→卸载） |
| 脚本编辑 | P1 | 在线编辑脚本内容 |
| 版本管理 | P0 | 每次修改生成新版本，支持版本对比和回退 |
| 参数配置 | P0 | 定义脚本输入参数（名称、类型、默认值、校验规则），支持分组 |
| 输出解析 | P0 | 定义如何解析脚本输出（正则/JSON路径） |
| 脚本分类 | P0 | 按测试类型分类（基础性能/应用性能/自定义） |
| 脚本校验 | P1 | 上传时检查语法（Shell/Python） |
| 脚本复制 | P2 | 复制已有脚本创建新脚本 |
| 脚本删除 | P0 | 删除脚本（软删除，有关联任务时提示） |
| 脚本导出 | P0 | 导出脚本（支持导出为 .zip/.tar.gz 压缩包） |

#### 3.3.3 脚本上传方式

**方式一：单文件上传**
- 直接上传单个 `.sh` 或 `.py` 文件
- 自动作为入口文件

**方式二：多文件上传（压缩包）**
- 上传 `.zip` 或 `.tar.gz` 压缩包
- 系统自动解压，展示文件列表
- 用户从文件列表中选择入口文件

**压缩包结构示例：**
```
mysql_test.zip
├── main.sh              # 主入口脚本
├── lib/
│   ├── common.sh        # 公共函数库
│   └── mysql_utils.sh   # MySQL工具函数
├── config/
│   └── default.conf     # 配置文件
└── data/
    └── test_data.sql    # 测试数据
```

**入口文件要求：**
- Shell 脚本：必须包含 shebang（`#!/bin/bash` 或 `#!/bin/sh`）
- Python 脚本：入口文件可执行，或指定 Python 解释器运行
- 入口文件必须有执行权限

#### 3.3.3 脚本生命周期模式

**生命周期概述：**

脚本从单一"执行"扩展为三段式生命周期：

| 阶段 | 必须/可选 | 说明 |
|------|----------|------|
| **部署（deploy）** | 可选 | 安装被测软件、准备测试环境 |
| **执行（run）** | 必须 | 执行测试、采集结果 |
| **卸载（cleanup）** | 可选 | 清理环境、卸载软件 |

**两种模式：**

| 模式 | 说明 | 适用场景 |
|------|------|----------|
| **简单模式（simple）** | 仅执行测试阶段 | 测试环境已就绪，无需安装/清理 |
| **完整模式（full）** | 部署→执行→卸载 | 需要自动化部署测试环境 |

**脚本结构规范：**

**方式一：单脚本多函数（推荐）**

```
mysql_test.zip
├── main.sh              # 主入口，包含所有生命周期函数
├── lib/
│   └── utils.sh
└── config/
    └── default.conf
```

**main.sh 规范：**

```bash
#!/bin/bash
# 脚本元信息（必需）
# @name mysql_test
# @version 1.0.0
# @description MySQL OLTP 性能测试

set -e

# ============================================
# 部署阶段（可选）
# ============================================
deploy() {
    echo "[Deploy] 开始部署 MySQL..."
    
    # 安装 MySQL
    yum install -y mysql-server
    
    # 配置参数
    cp config/my.cnf /etc/my.cnf
    
    # 启动服务
    systemctl start mysqld
    
    # 准备测试数据
    mysql -e "CREATE DATABASE testdb;"
    
    echo "[Deploy] MySQL 部署完成"
}

# ============================================
# 执行阶段（必需）
# ============================================
run() {
    echo "[Run] 开始执行测试..."
    
    # 获取参数
    THREADS=${THREADS:-4}
    DURATION=${DURATION:-60}
    
    # 执行 sysbench 测试
    sysbench oltp_read_write \
        --threads=$THREADS \
        --time=$DURATION \
        --mysql-host=localhost \
        --mysql-db=testdb \
        run
    
    echo "[Run] 测试执行完成"
}

# ============================================
# 卸载阶段（可选）
# ============================================
cleanup() {
    echo "[Cleanup] 开始清理环境..."
    
    # 停止服务
    systemctl stop mysqld
    
    # 删除数据
    rm -rf /var/lib/mysql/*
    
    # 卸载软件
    yum remove -y mysql-server
    
    echo "[Cleanup] 环境清理完成"
}

# ============================================
# 入口调度（平台调用）
# ============================================
case "$1" in
    deploy)
        deploy
        ;;
    run)
        run
        ;;
    cleanup)
        cleanup
        ;;
    all)
        deploy
        run
        cleanup
        ;;
    *)
        echo "用法: $0 {deploy|run|cleanup|all}"
        exit 1
        ;;
esac
```

**方式二：多文件分离（兼容）**

```
mysql_test.zip
├── deploy.sh            # 部署脚本（可选）
├── run.sh               # 执行脚本（必需）
├── cleanup.sh           # 卸载脚本（可选）
├── lib/
│   └── utils.sh
└── config/
    └── default.conf
```

**入口文件配置：**

| 模式 | 部署入口 | 执行入口 | 卸载入口 |
|------|----------|----------|----------|
| 简单模式 | - | 必填 | - |
| 完整模式 | 可选 | 必填 | 可选 |

#### 3.3.4 脚本信息字段

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| 名称 | String | ✅ | 脚本唯一名称 |
| 脚本类型 | Enum | ✅ | Shell / Python |
| 测试类型 | Enum | ✅ | 基础性能-CPU / 基础性能-内存 / ... / 自定义 |
| 描述 | Text | ❌ | 脚本功能描述 |
| 当前版本 | String | 自动 | 当前版本号 |
| **生命周期模式** | Enum | ✅ | simple（简单模式）/ full（完整模式） |
| **是否包含部署** | Boolean | 自动 | 是否包含部署阶段 |
| **是否包含卸载** | Boolean | 自动 | 是否包含卸载阶段 |
| **部署入口** | String | 条件 | 部署入口文件路径（完整模式可选） |
| 入口文件 | String | ✅ | 执行入口文件路径，如 `main.sh` |
| **卸载入口** | String | 条件 | 卸载入口文件路径（完整模式可选） |
| 文件列表 | JSON | 自动 | 脚本包含的所有文件 |
| 参数定义 | JSON | ❌ | 输入参数定义列表（支持分组） |
| 解析规则 | JSON | ❌ | 输出解析规则 |
| 默认超时 | Integer | ❌ | 默认超时时间（秒），默认 3600 |
| 默认重试 | Integer | ❌ | 默认重试次数，默认 0 |
| 是否预置 | Boolean | 自动 | 是否预置脚本 |
| 状态 | Enum | 自动 | 启用/禁用 |
| 创建时间 | DateTime | 自动 | 创建时间 |
| 更新时间 | DateTime | 自动 | 更新时间 |

#### 3.3.5 文件列表结构

```json
{
  "entryFile": "main.sh",
  "files": [
    {
      "path": "main.sh",
      "size": 2048,
      "type": "shell",
      "isEntry": true
    },
    {
      "path": "lib/common.sh",
      "size": 1024,
      "type": "shell",
      "isEntry": false
    },
    {
      "path": "lib/mysql_utils.sh",
      "size": 1536,
      "type": "shell",
      "isEntry": false
    },
    {
      "path": "config/default.conf",
      "size": 512,
      "type": "config",
      "isEntry": false
    }
  ],
  "totalSize": 5120,
  "fileCount": 4
}
```

#### 3.3.6 脚本导出

**导出格式：**
- `.zip` 格式（默认）
- `.tar.gz` 格式

**导出内容：**
- 脚本所有文件
- 参数配置信息（可选，导出为 `params.json`）
- 输出解析规则（可选，导出为 `parse_rules.json`）

**导出文件结构：**
```
mysql_test_v1.0.0.zip
├── main.sh
├── lib/
│   ├── common.sh
│   └── mysql_utils.sh
├── config/
│   └── default.conf
├── params.json          # 参数定义（可选）
└── parse_rules.json     # 解析规则（可选）
```

#### 3.3.5 参数配置规范

**参数分组设计：**

脚本参数分为三组，分别对应不同生命周期阶段：

| 分组 | 必需/可选 | 说明 | 示例 |
|------|----------|------|------|
| **shared（共享参数）** | 可选 | 部署和执行都需要 | 服务器地址、数据库名 |
| **deploy（部署参数）** | 可选 | 仅部署阶段使用 | 安装路径、版本号、配置项 |
| **run（执行参数）** | 必须 | 仅执行阶段使用 | 线程数、持续时间、测试模式 |

**参数类型定义：**

| 类型 | 说明 | 示例 |
|------|------|------|
| integer | 整数 | 线程数、持续时间 |
| float | 浮点数 | 阈值比例 |
| string | 字符串 | 文件路径 |
| select | 单选下拉 | 测试模式：read/write/readwrite |
| multiselect | 多选 | 测试项目 |
| boolean | 开关 | 是否启用压缩 |
| file | 文件路径 | 数据文件 |

**参数定义结构：**

```json
{
  "name": "thread_count",
  "label": "线程数",
  "type": "integer",
  "required": true,
  "default": 4,
  "min": 1,
  "max": 128,
  "description": "并发测试线程数",
  "placeholder": "请输入1-128之间的整数"
}
```

**完整参数配置示例（分组）：**

```json
{
  "shared": [
    {
      "name": "db_host",
      "label": "数据库地址",
      "type": "string",
      "default": "localhost",
      "description": "MySQL 服务器地址，部署和测试都需要"
    },
    {
      "name": "db_name",
      "label": "数据库名",
      "type": "string",
      "default": "testdb",
      "description": "数据库名称"
    }
  ],
  "deploy": [
    {
      "name": "mysql_version",
      "label": "MySQL 版本",
      "type": "select",
      "required": true,
      "default": "8.0",
      "options": [
        {"value": "5.7", "label": "MySQL 5.7"},
        {"value": "8.0", "label": "MySQL 8.0"},
        {"value": "8.1", "label": "MySQL 8.1"}
      ],
      "description": "要安装的 MySQL 版本"
    },
    {
      "name": "install_dir",
      "label": "安装目录",
      "type": "string",
      "default": "/usr/local/mysql",
      "description": "MySQL 安装目录"
    },
    {
      "name": "innodb_buffer_pool_size",
      "label": "Buffer Pool 大小",
      "type": "string",
      "default": "4G",
      "description": "InnoDB 缓冲池大小"
    }
  ],
  "run": [
    {
      "name": "threads",
      "label": "并发线程数",
      "type": "integer",
      "required": true,
      "default": 8,
      "min": 1,
      "max": 128,
      "description": "sysbench 并发线程数"
    },
    {
      "name": "duration",
      "label": "测试时长(秒)",
      "type": "integer",
      "required": true,
      "default": 300,
      "min": 10,
      "max": 86400
    },
    {
      "name": "test_mode",
      "label": "测试模式",
      "type": "select",
      "required": true,
      "default": "oltp_read_write",
      "options": [
        {"value": "oltp_read_write", "label": "混合读写"},
        {"value": "oltp_read_only", "label": "只读"},
        {"value": "oltp_write_only", "label": "只写"}
      ]
    }
  ]
}
```

**参数注入方式：**

**方式一：环境变量注入（推荐）**

```bash
# 部署阶段执行
export DB_HOST="localhost"
export DB_NAME="testdb"
export MYSQL_VERSION="8.0"
export INSTALL_DIR="/usr/local/mysql"
./main.sh deploy

# 执行阶段执行
export DB_HOST="localhost"
export DB_NAME="testdb"
export THREADS="8"
export DURATION="300"
./main.sh run

# 卸载阶段执行
export DB_HOST="localhost"
export DB_NAME="testdb"
export INSTALL_DIR="/usr/local/mysql"
./main.sh cleanup
```

**方式二：参数文件注入**

```bash
# 平台生成参数文件
cat > /tmp/task_1001_deploy_params.conf << EOF
DB_HOST=localhost
DB_NAME=testdb
MYSQL_VERSION=8.0
INSTALL_DIR=/usr/local/mysql
EOF

# 脚本中读取
source /tmp/task_1001_deploy_params.conf
```

**参数注入规则：**

| 阶段 | 注入参数组 |
|------|----------|
| 部署阶段 | shared_params + deploy_params |
| 执行阶段 | shared_params + run_params |
| 卸载阶段 | shared_params + 部分 deploy_params（安装路径等） |

#### 3.3.6 输出解析规则

**解析方式：**

| 方式 | 说明 | 适用场景 |
|------|------|----------|
| regex | 正则表达式匹配 | 任意文本输出 |
| jsonpath | JSON 路径提取 | JSON 格式输出 |
| line | 按行解析 | 固定格式文本 |
| keyword | 关键字匹配 | 判断成功/失败 |

**正则解析示例：**

```json
{
  "parseType": "regex",
  "patterns": [
    {
      "name": "iops",
      "pattern": "IOPS=([\\d.]+)",
      "unit": "ops/s",
      "description": "IOPS值"
    },
    {
      "name": "throughput",
      "pattern": "Throughput=([\\d.]+)\\s*(KB|MB|GB)/s",
      "unit": "auto",
      "description": "吞吐量"
    },
    {
      "name": "latency_avg",
      "pattern": "latency.*avg=([\\d.]+)",
      "unit": "ms",
      "description": "平均延迟"
    }
  ],
  "successPattern": "Test completed successfully|SUCCESS",
  "failurePattern": "Error:|Failed|FATAL"
}
```

**JSON 解析示例：**

```json
{
  "parseType": "jsonpath",
  "fields": [
    {
      "name": "total_requests",
      "path": "$.results.total_requests",
      "unit": ""
    },
    {
      "name": "requests_per_sec",
      "path": "$.results.requests_per_sec",
      "unit": "req/s"
    }
  ]
}
```

#### 3.3.6 预置脚本清单

**基础性能测试脚本：**

| 脚本名称 | 测试类型 | 描述 | 依赖工具 | 默认参数 |
|----------|----------|------|----------|----------|
| CPU 压力测试 | 基础性能-CPU | 多核 CPU 负载测试 | stress-ng | 线程数、持续时间 |
| CPU 基准测试 | 基础性能-CPU | CPU 计算性能基准 | sysbench cpu | 线程数、事件数 |
| 内存带宽测试 | 基础性能-内存 | 内存读写带宽 | stream | 数组大小 |
| 内存压力测试 | 基础性能-内存 | 内存压力测试 | stress-ng | 内存占用量、持续时间 |
| 磁盘顺序读写 | 基础性能-磁盘 | 磁盘顺序读写性能 | fio | 块大小、文件大小、测试模式 |
| 磁盘随机读写 | 基础性能-磁盘 | 磁盘随机读写性能 | fio | 块大小、队列深度、测试模式 |
| 网络带宽测试 | 基础性能-网络 | TCP/UDP 带宽测试 | iperf3 | 服务端地址、持续时间 |
| 网络延迟测试 | 基础性能-网络 | 网络延迟与丢包 | ping/hping3 | 目标地址、次数 |

**应用性能测试脚本：**

| 脚本名称 | 测试类型 | 描述 | 依赖工具 | 默认参数 |
|----------|----------|------|----------|----------|
| MySQL OLTP | 应用性能-MySQL | 数据库 OLTP 性能 | sysbench | 连接数、表数、持续时间 |
| MySQL 只读测试 | 应用性能-MySQL | 数据库只读性能 | sysbench | 连接数、查询数 |
| PostgreSQL 测试 | 应用性能-PostgreSQL | 数据库性能测试 | pgbench | 连接数、线程数 |
| Redis 性能测试 | 应用性能-Redis | Redis 读写性能 | redis-benchmark | 连接数、请求数、数据大小 |
| Nginx 压力测试 | 应用性能-Nginx | HTTP 服务压力 | wrk/ab | 并发数、持续时间 |
| Java 性能测试 | 应用性能-Java | Java 应用性能 | SPECjbb/自定义 | 配置参数 |
| Hadoop TestDFSIO | 应用性能-Hadoop | HDFS 读写性能 | TestDFSIO | 文件数、文件大小 |

---

### 3.4 模块三：性能指标采集

#### 3.4.1 功能描述

在被测服务器上实时采集性能指标数据，与测试执行过程关联，用于测试报告生成和性能分析。**支持在创建任务时选择是否启用指标采集**。

#### 3.4.2 功能清单

| 功能点 | 优先级 | 描述 |
|--------|--------|------|
| 采集开关 | P0 | 创建任务时可选择启用或禁用指标采集 |
| 实时指标采集 | P0 | 采集 CPU / 内存 / 磁盘 / 网络指标 |
| 采集频率配置 | P0 | 支持 1s / 5s / 10s / 30s / 1min |
| 采集时间窗口 | P0 | 与测试任务绑定，测试开始前启动，结束后停止 |
| 自定义指标 | P1 | 支持配置自定义采集命令 |
| 指标数据存储 | P0 | 存储到数据库，关联测试任务 |
| 历史数据查询 | P0 | 按任务 / 服务器 / 时间范围查询 |
| 数据清理 | P2 | 自动清理过期数据（可配置保留天数） |

#### 3.4.3 采集启用配置

**任务创建时：**
- 默认启用指标采集
- 用户可手动关闭（不采集性能指标）
- 关闭后，测试报告中不包含性能指标分析

**采集配置结构（支持禁用）：**

```json
{
  "enabled": true,
  "frequency": "5s",
  "cpu": {
    "enabled": true,
    "metrics": ["usage_rate", "load_avg", "context_switch"]
  },
  "memory": {
    "enabled": true,
    "metrics": ["usage_rate", "used_mb", "cache_mb", "swap_rate"]
  },
  "disk": {
    "enabled": true,
    "metrics": ["iops", "throughput", "io_wait", "usage_rate"],
    "devices": ["sda", "sdb"]
  },
  "network": {
    "enabled": true,
    "metrics": ["in_bytes", "out_bytes", "tcp_connections"],
    "interfaces": ["eth0", "ens192"]
  },
  "customMetrics": []
}
```

**禁用时配置：**

```json
{
  "enabled": false
}
```

#### 3.4.3 默认采集指标

| 分类 | 指标名称 | 单位 | 采集命令 |
|------|----------|------|----------|
| **CPU** | 总使用率 | % | top -bn1 \| grep "Cpu(s)" \| awk '{print $2}' |
| | 用户态使用率 | % | top 解析 |
| | 系统态使用率 | % | top 解析 |
| | 空闲率 | % | top 解析 |
| | 负载 1m/5m/15m | - | cat /proc/loadavg |
| | 上下文切换 | 次/s | cat /proc/stat |
| **内存** | 总内存 | MB | free -m |
| | 已用内存 | MB | free -m |
| | 内存使用率 | % | 计算 |
| | 缓存 | MB | free -m |
| | Swap 使用率 | % | free -m |
| **磁盘** | 各磁盘读 IOPS | ops/s | cat /proc/diskstats |
| | 各磁盘写 IOPS | ops/s | cat /proc/diskstats |
| | 各磁盘读吞吐 | MB/s | cat /proc/diskstats |
| | 各磁盘写吞吐 | MB/s | cat /proc/diskstats |
| | IO 等待时间 | ms | iostat |
| | 磁盘使用率 | % | df -h |
| **网络** | 网卡入流量 | KB/s | cat /proc/net/dev |
| | 网卡出流量 | KB/s | cat /proc/net/dev |
| | TCP 连接数 | 个 | ss -s |
| | 网络错误数 | 个 | cat /proc/net/dev |

#### 3.4.4 采集配置结构

```json
{
  "frequency": "5s",
  "cpu": {
    "enabled": true,
    "metrics": ["usage_rate", "load_avg", "context_switch"]
  },
  "memory": {
    "enabled": true,
    "metrics": ["usage_rate", "used_mb", "cache_mb", "swap_rate"]
  },
  "disk": {
    "enabled": true,
    "metrics": ["iops", "throughput", "io_wait", "usage_rate"],
    "devices": ["sda", "sdb"]
  },
  "network": {
    "enabled": true,
    "metrics": ["in_bytes", "out_bytes", "tcp_connections"],
    "interfaces": ["eth0", "ens192"]
  },
  "customMetrics": [
    {
      "name": "jvm_heap_used",
      "command": "jstat -gcutil ${PID} | tail -1 | awk '{print $3}'",
      "unit": "%",
      "description": "JVM堆内存使用率"
    }
  ]
}
```

#### 3.4.5 采集实现方案

**方案一：SSH 远程执行（推荐）**
- 优点：无需在目标服务器部署 Agent
- 缺点：高频采集会有性能开销
- 适用：采集频率 ≥ 5s

**方案二：Agent 部署**
- 优点：采集性能好，支持高频采集
- 缺点：需要在目标服务器部署
- 适用：采集频率 < 5s 或大规模采集

**V1.0.0 采用方案一**，后续版本支持 Agent 方式。

---

### 3.5 模块四：测试任务管理

#### 3.5.1 功能描述

创建、调度、管理测试任务，支持立即执行和定时执行。

#### 3.5.2 功能清单

| 功能点 | 优先级 | 描述 |
|--------|--------|------|
| 创建测试任务 | P0 | 配置服务器、脚本、参数、生命周期、指标采集 |
| 任务编辑 | P1 | 编辑未开始的任务 |
| 任务复制 | P1 | 快速创建相似任务 |
| 立即执行 | P0 | 任务创建后立即执行 |
| 定时执行 | P1 | 指定时间执行任务 |
| 任务取消 | P0 | 取消执行中或待执行的任务 |
| 任务重试 | P1 | 失败任务一键重试 |
| 执行进度查看 | P0 | 实时显示执行进度和日志 |
| 任务历史查询 | P0 | 查询历史任务及执行结果 |
| **生命周期控制** | P0 | 可选跳过部署/卸载阶段 |

#### 3.5.3 任务信息字段

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| 任务名称 | String | ✅ | 任务名称 |
| 任务描述 | Text | ❌ | 任务描述 |
| 目标服务器 | Array | ✅ | 选择一台或多台服务器 |
| 测试脚本 | Long | ✅ | 选择测试脚本 |
| 脚本版本 | String | ✅ | 选择脚本版本 |
| **共享参数** | JSON | ❌ | 共享参数配置 |
| **部署参数** | JSON | 条件 | 部署参数配置（完整模式时） |
| **执行参数** | JSON | ✅ | 执行参数配置 |
| **是否跳过部署** | Boolean | ❌ | 是否跳过部署阶段 |
| **是否跳过卸载** | Boolean | ❌ | 是否跳过卸载阶段 |
| **部署超时** | Integer | ❌ | 部署超时时间（秒） |
| **卸载超时** | Integer | ❌ | 卸载超时时间（秒） |
| 指标采集配置 | JSON | ❌ | 性能指标采集配置 |
| 执行方式 | Enum | ✅ | 立即执行 / 定时执行 |
| 定时时间 | DateTime | 条件 | 定时执行时必填 |
| 执行策略 | Enum | ✅ | 串行 / 并行 |
| 并发数 | Integer | 条件 | 并行时最大并发数 |
| 失败策略 | Enum | ❌ | 继续执行 / 停止执行 |
| 状态 | Enum | 自动 | 待执行/部署中/执行中/卸载中/已完成/已取消/失败 |
| 创建时间 | DateTime | 自动 | 创建时间 |
| 开始时间 | DateTime | 自动 | 任务开始执行时间 |
| 结束时间 | DateTime | 自动 | 任务结束时间 |

#### 3.5.4 任务状态机

```
                    ┌─────────────┐
                    │   待执行     │
                    └──────┬──────┘
                           │ 开始执行
                           ▼
              ┌────────────────────────┐
              │      部署中(可选)       │
              └────────────┬───────────┘
                           │ 部署成功/跳过
                           ▼
                    ┌─────────────┐
          ┌────────│   执行中     │────────┐
          │        └──────────────┘        │
          │                                 │
   取消执行                          执行失败
          │                                 │
          ▼                                 ▼
   ┌─────────────┐                 ┌─────────────┐
   │   已取消     │                 │    失败     │
   └─────────────┘                 └──────┬──────┘
                                          │ 重试
                                          ▼
                                   ┌─────────────┐
                                   │   执行中     │
                                   └─────────────┘
          │                                 │
          │                            执行成功
          │                                 │
          └─────────────────────────────────┤
                                            ▼
              ┌────────────────────────────────────────┐
              │            卸载中(可选)                  │
              └────────────────────┬───────────────────┘
                                   │ 卸载完成/跳过
                                   ▼
                            ┌─────────────┐
                            │   已完成     │
                            └─────────────┘
```

**新增状态说明：**

| 状态 | 说明 |
|------|------|
| deploying | 部署中（完整模式下） |
| cleaning | 卸载中（完整模式下） |
| deploy_failed | 部署失败 |
| cleanup_warning | 卸载失败（警告，不阻塞） |

**各阶段失败处理策略：**

| 阶段 | 失败处理 |
|------|----------|
| 部署失败 | 终止任务，不执行后续阶段 |
| 执行失败 | 记录失败，继续卸载阶段（如有） |
| 卸载失败 | 记录警告，不影响测试结果 |

---

### 3.6 模块五：测试执行引擎

#### 3.6.1 功能描述

负责任务的实际执行，包括脚本分发、远程执行、结果收集、日志记录。

#### 3.6.2 功能清单

| 功能点 | 优先级 | 描述 |
|--------|--------|------|
| 脚本分发 | P0 | 将测试脚本上传到目标服务器临时目录 |
| 参数注入 | P0 | 将用户配置的参数注入脚本 |
| 远程执行 | P0 | 通过 SSH 执行测试脚本 |
| 实时日志 | P0 | 实时输出执行日志 |
| 结果收集 | P0 | 收集脚本输出和返回码 |
| 指标采集同步 | P0 | 执行时同步启动/停止指标采集 |
| 超时控制 | P0 | 超过设定时间自动终止 |
| 错误重试 | P1 | 执行失败自动重试 |
| 执行锁 | P1 | 同一服务器同一时间只能执行一个任务 |
| 进度跟踪 | P0 | 实时更新执行进度 |

#### 3.6.3 执行流程

```
┌────────────────────────────────────────────────────────────────────┐
│                    测试执行流程（生命周期版）                         │
├────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ===== 1. 任务初始化 =====                                          │
│  ├── 检查脚本生命周期配置                                            │
│  │   ├── lifecycle_mode = simple → 无部署/卸载                      │
│  │   └── lifecycle_mode = full  → 检查 has_deploy, has_cleanup     │
│  ├── 检查任务配置                                                   │
│  │   ├── skip_deploy 是否跳过部署                                   │
│  │   └── skip_cleanup 是否跳过卸载                                  │
│  ├── 确定各服务器执行计划                                            │
│  └── 更新任务状态 → deploying / running                             │
│                                                                     │
│  ===== 2. 部署阶段 =====                                            │
│  │  （如果 has_deploy=TRUE 且 skip_deploy=FALSE）                   │
│  ├── 2.1 准备部署参数                                               │
│  │   └── 合并：shared_params + deploy_params                        │
│  ├── 2.2 脚本分发                                                   │
│  │   ├── 上传脚本到目标服务器临时目录                                │
│  │   └── 设置执行权限                                               │
│  ├── 2.3 执行部署                                                   │
│  │   ├── 更新 task_servers.deploy_status = running                  │
│  │   ├── 注入参数环境变量                                           │
│  │   ├── 执行：./main.sh deploy                                     │
│  │   ├── 实时收集日志 → task_servers.deploy_output                  │
│  │   └── 记录 deploy_exit_code                                      │
│  ├── 2.4 判断部署结果                                               │
│  │   ├── exit_code = 0 → deploy_status = completed                  │
│  │   └── exit_code ≠ 0 → deploy_status = failed                     │
│  │       └── 任务终止，不再执行后续阶段                              │
│  └── 2.5 更新整体进度                                               │
│       └── 如果跳过：deploy_status = skipped                         │
│                                                                     │
│  ===== 3. 执行阶段 =====                                            │
│  │  （必须执行）                                                     │
│  ├── 3.1 准备执行参数                                               │
│  │   └── 合并：shared_params + run_params                           │
│  ├── 3.2 指标采集启动（如果 collect_enabled=TRUE）                   │
│  │   ├── 启动后台采集进程                                           │
│  │   └── 记录采集开始时间                                           │
│  ├── 3.3 执行测试                                                   │
│  │   ├── 更新 task_servers.run_status = running                     │
│  │   ├── 注入参数环境变量                                           │
│  │   ├── 执行：./main.sh run                                        │
│  │   ├── 实时收集日志 → task_servers.run_output                     │
│  │   └── 记录 run_exit_code                                         │
│  ├── 3.4 结果收集与解析                                             │
│  │   ├── 收集标准输出和错误输出                                      │
│  │   ├── 应用解析规则解析输出                                        │
│  │   ├── 应用判定规则计算结果                                        │
│  │   └── 存储 → test_results 表                                    │
│  ├── 3.5 指标采集停止（如果启用）                                    │
│  │   └── 存储指标数据 → metrics 表                                  │
│  └── 3.6 判断执行结果                                               │
│      ├── exit_code = 0 → run_status = completed                    │
│      └── exit_code ≠ 0 → run_status = failed                       │
│          └── 继续卸载阶段（如有）                                    │
│                                                                     │
│  ===== 4. 卸载阶段 =====                                            │
│  │  （如果 has_cleanup=TRUE 且 skip_cleanup=FALSE）                 │
│  ├── 4.1 准备卸载参数                                               │
│  │   └── 合并：shared_params + deploy_params(部分)                  │
│  ├── 4.2 执行卸载                                                   │
│  │   ├── 更新 task_servers.cleanup_status = running                 │
│  │   ├── 注入参数环境变量                                           │
│  │   ├── 执行：./main.sh cleanup                                    │
│  │   ├── 实时收集日志 → task_servers.cleanup_output                 │
│  │   └── 记录 cleanup_exit_code                                     │
│  ├── 4.3 判断卸载结果                                               │
│  │   ├── exit_code = 0 → cleanup_status = completed                 │
│  │   └── exit_code ≠ 0 → cleanup_status = failed                    │
│  │       └── 记录警告，不影响整体测试结果                            │
│  └── 4.4 清理临时文件                                               │
│       └── 删除目标服务器上的脚本临时目录                              │
│                                                                     │
│  ===== 5. 任务完成 =====                                            │
│  ├── 计算整体状态                                                   │
│  │   ├── 部署失败 → overall_status = failed                         │
│  │   ├── 执行失败 → overall_status = failed                         │
│  │   ├── 卸载失败 → overall_status = completed_with_warning         │
│  │   └── 全部成功 → overall_status = completed                      │
│  ├── 更新任务状态                                                   │
│  ├── 触发报告生成                                                   │
│  └── 释放服务器执行锁                                               │
│                                                                     │
└────────────────────────────────────────────────────────────────────┘
```

#### 3.6.4 参数注入方式

**方式一：环境变量注入**
```bash
# 脚本中使用
export THREAD_COUNT=${THREAD_COUNT:-4}
export DURATION=${DURATION:-60}
```

**方式二：参数文件注入**
```bash
# 生成参数文件
cat > /tmp/params.conf << EOF
THREAD_COUNT=4
DURATION=60
EOF

# 脚本中读取
source /tmp/params.conf
```

**方式三：命令行参数注入**
```bash
# 脚本中使用位置参数
./test_script.sh --threads 4 --duration 60
```

#### 3.6.5 执行日志结构

```json
{
  "taskId": 1001,
  "serverId": 5,
  "logs": [
    {
      "timestamp": "2026-03-08T15:30:00Z",
      "level": "INFO",
      "message": "开始执行测试脚本"
    },
    {
      "timestamp": "2026-03-08T15:30:01Z",
      "level": "INFO",
      "message": "参数配置: threads=4, duration=60s"
    },
    {
      "timestamp": "2026-03-08T15:30:02Z",
      "level": "INFO",
      "message": "启动 fio 测试..."
    },
    {
      "timestamp": "2026-03-08T15:31:02Z",
      "level": "INFO",
      "message": "测试完成，IOPS=50000, Throughput=200MB/s"
    },
    {
      "timestamp": "2026-03-08T15:31:03Z",
      "level": "INFO",
      "message": "脚本执行完成，返回码: 0"
    }
  ]
}
```

---

### 3.7 模块六：测试结果管理（核心功能）

#### 3.7.1 功能描述

测试结果的全生命周期管理，包括结果解析、标准化存储、结果判定、多维度对比分析、趋势追踪。这是实现业务闭环的关键模块。

#### 3.7.2 功能清单

| 功能点 | 优先级 | 描述 |
|--------|--------|------|
| 结果解析 | P0 | 根据脚本解析规则解析测试输出 |
| 结果标准化 | P0 | 将解析结果转换为标准化指标数据 |
| 结果存储 | P0 | 持久化存储测试结果和原始数据 |
| 结果判定 | P0 | 根据判定规则自动判定测试结果（通过/失败/警告） |
| 结果查询 | P0 | 按任务/服务器/时间/结果状态查询 |
| 结果对比 | P0 | 多任务结果对比分析 |
| 趋势分析 | P1 | 同一脚本/服务器的结果趋势追踪 |
| 输出文件收集 | P0 | 收集脚本执行过程中生成的输出文件 |

#### 3.7.3 结果解析与标准化

**解析流程：**

```
脚本执行输出
     │
     ├── 标准输出 (stdout)
     ├── 错误输出 (stderr)
     ├── 返回码 (exit code)
     └── 输出文件 (output files)
            │
            ▼
     ┌──────────────┐
     │  解析规则引擎  │
     │  • 正则匹配    │
     │  • JSON解析   │
     │  • 关键字匹配  │
     │  • 行解析     │
     └──────────────┘
            │
            ▼
     ┌──────────────┐
     │  指标标准化    │
     │  • 名称规范化  │
     │  • 单位转换    │
     │  • 基准线匹配  │
     │  • 评分计算    │
     └──────────────┘
            │
            ▼
     ┌──────────────┐
     │  结果判定      │
     │  • 规则匹配    │
     │  • 阈值判断    │
     │  • 综合评分    │
     └──────────────┘
            │
            ▼
     标准化结果数据
```

**标准化指标数据结构：**

```json
{
  "metrics": [
    {
      "name": "iops",
      "displayName": "IOPS",
      "value": 50000,
      "unit": "ops/s",
      "category": "performance",
      "baseline": {
        "min": 10000,
        "warning": 30000,
        "excellent": 80000
      },
      "score": "excellent",
      "scoreValue": 95,
      "timestamp": "2026-03-09T00:30:00Z"
    },
    {
      "name": "latency_avg",
      "displayName": "平均延迟",
      "value": 2.5,
      "unit": "ms",
      "category": "performance",
      "baseline": {
        "max": 10,
        "warning": 5,
        "excellent": 1
      },
      "score": "warning",
      "scoreValue": 60
    }
  ],
  "summary": {
    "total": 5,
    "excellent": 3,
    "good": 1,
    "warning": 1,
    "fail": 0
  },
  "overallScore": 82,
  "overallResult": "pass"
}
```

#### 3.7.4 结果判定规则

**判定方式：**

| 方式 | 说明 | 适用场景 |
|------|------|----------|
| 阈值判定 | 指标值与阈值比较 | 单指标判定 |
| 规则组合 | 多条件组合判定 | 复杂场景 |
| 脚本返回码 | 根据返回码判定 | 简单场景 |
| 自定义规则 | 用户自定义判定逻辑 | 特殊需求 |

**判定规则配置：**

```json
{
  "ruleName": "磁盘性能测试判定规则",
  "description": "磁盘IO性能测试结果判定",
  "conditions": [
    {
      "metric": "iops",
      "operator": ">=",
      "warningValue": 30000,
      "passValue": 10000,
      "weight": 0.4
    },
    {
      "metric": "latency_avg",
      "operator": "<=",
      "warningValue": 5,
      "passValue": 10,
      "weight": 0.3
    },
    {
      "metric": "throughput",
      "operator": ">=",
      "warningValue": 200,
      "passValue": 100,
      "weight": 0.3
    }
  ],
  "passThreshold": 60,
  "warningThreshold": 80
}
```

**判定结果：**

| 结果 | 说明 | 条件 |
|------|------|------|
| pass | 通过 | 所有必达条件满足，综合分 ≥ 60 |
| warning | 警告 | 有指标接近阈值，综合分 60-80 |
| fail | 失败 | 有必达条件不满足，综合分 < 60 |
| error | 错误 | 执行异常，无有效结果 |

#### 3.7.5 输出文件收集

**配置方式：**

```json
{
  "outputConfig": {
    "collectStdout": true,
    "collectStderr": true,
    "collectFiles": [
      {
        "path": "/tmp/results/*.csv",
        "targetDir": "results",
        "required": false,
        "maxSize": "10MB"
      },
      {
        "path": "/tmp/report.json",
        "targetDir": ".",
        "required": true,
        "parse": true,
        "parseRule": "jsonpath:$.results"
      }
    ],
    "cleanupAfterCollect": true
  }
}
```

**收集流程：**

```
1. 脚本执行完成
2. 收集标准输出和错误输出
3. 根据配置的文件路径模式匹配文件
4. 通过 SFTP 下载文件到平台存储
5. 可选：解析指定文件
6. 可选：清理服务器临时文件
```

#### 3.7.6 结果对比分析

**对比维度：**

| 维度 | 说明 | 应用场景 |
|------|------|----------|
| 时间对比 | 同一脚本不同时间执行的结果 | 性能趋势分析 |
| 服务器对比 | 不同服务器执行相同测试 | 服务器选型 |
| 参数对比 | 不同参数配置的结果 | 参数调优 |
| 版本对比 | 脚本不同版本的结果 | 脚本优化效果 |

**对比结果展示：**

```
┌─────────────────────────────────────────────────────────────────┐
│                     结果对比报告                                  │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  对比任务: 任务A vs 任务B vs 任务C                                │
│  对比维度: 时间 (2026-03-01 / 2026-03-05 / 2026-03-09)           │
│                                                                  │
│  ┌─────────────┬────────┬────────┬────────┬────────┐            │
│  │   指标      │ 任务A  │ 任务B  │ 任务C  │ 变化趋势 │            │
│  ├─────────────┼────────┼────────┼────────┼────────┤            │
│  │ IOPS        │ 45000  │ 48000  │ 52000  │ ↑ 15.6% │            │
│  │ 延迟(ms)    │ 3.2    │ 2.8    │ 2.5    │ ↓ 21.9% │            │
│  │ 吞吐(MB/s)  │ 180    │ 200    │ 220    │ ↑ 22.2% │            │
│  └─────────────┴────────┴────────┴────────┴────────┘            │
│                                                                  │
│  趋势图表: [折线图]                                               │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

#### 3.7.7 趋势分析

**分析类型：**

| 类型 | 说明 | 图表形式 |
|------|------|----------|
| 性能趋势 | 指标随时间的变化 | 折线图 |
| 稳定性趋势 | 结果波动情况 | 控制图 |
| 对比基准 | 与基准线的偏离 | 柱状图 |

**趋势报告：**

```json
{
  "scriptId": 1,
  "serverId": 5,
  "period": "30d",
  "trend": {
    "iops": {
      "trend": "increasing",
      "changeRate": 15.6,
      "stability": "stable",
      "dataPoints": [...]
    },
    "latency_avg": {
      "trend": "decreasing",
      "changeRate": -21.9,
      "stability": "stable",
      "dataPoints": [...]
    }
  },
  "anomalies": [
    {
      "date": "2026-03-05",
      "metric": "iops",
      "value": 25000,
      "expected": 45000,
      "deviation": -44.4
    }
  ]
}
```

---

### 3.8 模块七：报告中心

#### 3.8.1 功能描述

自动生成测试报告，支持查看、对比、导出。

#### 3.8.2 功能清单

| 功能点 | 优先级 | 描述 |
|--------|--------|------|
| 自动生成报告 | P0 | 任务完成后自动生成报告 |
| 报告查看 | P0 | 在线查看报告详情 |
| 报告导出 | P0 | 导出 PDF / HTML 格式 |
| 历史对比 | P1 | 选择多个报告进行对比 |
| 报告列表 | P0 | 按任务/服务器/时间筛选报告 |
| 报告删除 | P1 | 删除过期报告 |

#### 3.8.3 报告内容结构

```
测试报告
│
├── 1. 报告概览
│   ├── 报告名称
│   ├── 生成时间
│   ├── 任务名称
│   ├── 测试服务器列表
│   ├── 测试脚本与版本
│   ├── 执行时长
│   └── 测试结论（通过/失败/警告）
│
├── 2. 测试配置
│   ├── 脚本参数配置
│   └── 指标采集配置
│
├── 3. 执行详情（多服务器时按服务器分组）
│   ├── 执行时间线
│   ├── 脚本标准输出
│   ├── 解析后的指标数据
│   └── 错误信息（如有）
│
├── 4. 性能指标分析
│   ├── CPU 使用率趋势图
│   ├── 内存使用率趋势图
│   ├── 磁盘 IO 趋势图
│   ├── 网络流量趋势图
│   └── 指标统计表（峰值/平均值/最小值）
│
├── 5. 结论与建议
│   ├── 测试结论
│   ├── 性能瓶颈分析
│   └── 优化建议
│
└── 6. 附录
    ├── 完整执行日志
    ├── 原始数据下载
    └── 测试环境信息
```

#### 3.8.4 报告数据结构

```json
{
  "reportId": "RPT-20260308-001",
  "taskId": 1001,
  "title": "CPU压力测试报告",
  "summary": {
    "servers": ["测试服务器-01", "测试服务器-02"],
    "script": "CPU压力测试",
    "scriptVersion": "v1.0.0",
    "duration": "5分钟",
    "conclusion": "pass",
    "conclusionDesc": "测试通过，性能指标符合预期"
  },
  "testConfig": {
    "parameters": {
      "thread_count": 4,
      "duration": 300
    }
  },
  "executionDetails": [
    {
      "serverName": "测试服务器-01",
      "startTime": "2026-03-08T15:30:00Z",
      "endTime": "2026-03-08T15:35:00Z",
      "status": "success",
      "metrics": {
        "cpu_max": "95.2%",
        "cpu_avg": "88.5%",
        "throughput": "15000 ops/s"
      }
    }
  ],
  "performanceMetrics": {
    "cpu": {
      "dataPoints": [...],
      "max": 95.2,
      "avg": 88.5,
      "min": 45.2
    }
  },
  "conclusion": {
    "result": "pass",
    "bottleneck": null,
    "suggestions": []
  }
}
```

---

### 3.9 模块八：系统配置

#### 3.9.1 功能描述

系统级配置管理。

#### 3.9.2 功能清单

| 功能点 | 优先级 | 描述 |
|--------|--------|------|
| 存储路径配置 | P1 | 脚本存储路径、报告存储路径、临时文件路径 |
| 任务执行配置 | P1 | 默认超时时间、默认重试次数、最大并发数 |
| 数据清理配置 | P2 | 指标数据保留天数、报告保留天数 |
| SSH 连接配置 | P1 | 连接超时、执行超时、重连次数 |

---

### 3.10 模块九：文件管理

#### 3.10.1 功能描述

管理脚本文件、报告文件、临时文件。

#### 3.10.2 功能清单

| 功能点 | 优先级 | 描述 |
|--------|--------|------|
| 脚本文件存储 | P0 | 存储上传的测试脚本 |
| 版本文件存储 | P0 | 存储各版本脚本文件 |
| 报告文件存储 | P0 | 存储生成的报告文件 |
| 临时文件清理 | P1 | 定期清理过期临时文件 |
| 存储空间监控 | P2 | 监控存储空间使用情况 |

#### 3.10.3 文件存储结构

```
/data/
├── scripts/                         # 脚本存储目录
│   ├── {script_id}/                # 按脚本ID分组
│   │   ├── v1.0.0/                 # 版本目录（多文件脚本）
│   │   │   ├── main.sh             # 入口文件
│   │   │   ├── lib/                # 子目录
│   │   │   │   ├── common.sh
│   │   │   │   └── utils.sh
│   │   │   └── config/
│   │   │       └── default.conf
│   │   ├── v1.0.1/
│   │   │   └── ...
│   │   └── current -> v1.0.1       # 当前版本软链接
│   └── builtin/                    # 预置脚本目录
│       ├── cpu_stress/
│       │   └── main.sh
│       ├── mem_test/
│       │   └── main.sh
│       └── ...
│
├── reports/                         # 报告存储目录
│   ├── {year}/                     # 按年份分组
│   │   └── {month}/                # 按月份分组
│   │       ├── {task_id}_report.html
│   │       └── {task_id}_report.pdf
│   └── ...
│
├── temp/                            # 临时文件目录
│   ├── uploads/                    # 上传临时目录
│   └── exports/                    # 导出临时目录
│
└── logs/                            # 日志目录
    ├── app.log                     # 应用日志
    └── task_{id}.log               # 任务执行日志
```

**脚本上传临时目录：**
```
/data/temp/uploads/
├── {upload_id}/                    # 上传会话ID
│   ├── mysql_test.zip              # 原始上传文件
│   └── extracted/                  # 解压后的文件
│       ├── main.sh
│       ├── lib/
│       └── config/
```

---

## 4. 非功能需求

### 4.1 性能要求

| 指标 | 要求 |
|------|------|
| 页面响应时间 | < 2s |
| API 响应时间 | < 500ms（普通请求）/ < 3s（复杂查询） |
| 并发用户数 | ≥ 20 |
| 任务并发执行数 | ≥ 10 |
| 单任务支持服务器数 | ≥ 100 |
| 指标数据存储 | 支持千万级数据点 |

### 4.2 可用性要求

| 指标 | 要求 |
|------|------|
| 系统可用性 | ≥ 99% |
| 故障恢复时间 | < 30min |

### 4.3 可扩展性

- 模块化设计，各模块低耦合
- 脚本类型可扩展
- 指标采集可扩展
- 预留分布式架构升级空间

---

## 5. 数据库设计

### 5.1 ER 图概览

```
server_groups ──< servers ──< task_servers >── tasks >── scripts ──< script_versions
                      │              │             │
                      │              └──< metrics   └──< reports
                      │
                      └──< metric_data
```

### 5.2 数据表设计

#### 5.2.1 server_groups（服务器分组表）

```sql
CREATE TABLE server_groups (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_server_groups_name ON server_groups(name);
```

#### 5.2.2 servers（服务器表）

```sql
CREATE TABLE servers (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    host VARCHAR(255) NOT NULL,
    port INTEGER NOT NULL DEFAULT 22,
    username VARCHAR(50) NOT NULL,
    auth_type VARCHAR(20) NOT NULL,  -- password/ssh_key
    auth_secret TEXT,                  -- 加密存储
    os_type VARCHAR(50),
    cpu_cores INTEGER,
    memory_size VARCHAR(20),
    disk_info JSONB,
    group_id BIGINT REFERENCES server_groups(id),
    tags JSONB,
    remark TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'offline',  -- online/offline/maintenance
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_servers_name ON servers(name);
CREATE INDEX idx_servers_host ON servers(host);
CREATE INDEX idx_servers_group_id ON servers(group_id);
CREATE INDEX idx_servers_status ON servers(status);
```

#### 5.2.3 scripts（脚本表）

```sql
CREATE TABLE scripts (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    script_type VARCHAR(20) NOT NULL,  -- shell/python
    test_category VARCHAR(50) NOT NULL,
    description TEXT,
    current_version VARCHAR(20) NOT NULL DEFAULT 'v1.0.0',
    
    -- 生命周期配置
    lifecycle_mode VARCHAR(20) NOT NULL DEFAULT 'simple',  -- simple/full
    has_deploy BOOLEAN DEFAULT FALSE,
    has_cleanup BOOLEAN DEFAULT FALSE,
    deploy_entry VARCHAR(255),
    cleanup_entry VARCHAR(255),
    
    entry_file VARCHAR(255),            -- 执行入口文件路径
    file_list JSONB,                    -- 文件列表
    parameters JSONB,                   -- 参数定义（支持分组：shared/deploy/run）
    parse_rules JSONB,
    default_timeout INTEGER DEFAULT 3600,
    default_retry INTEGER DEFAULT 0,
    is_builtin BOOLEAN DEFAULT FALSE,
    status VARCHAR(20) NOT NULL DEFAULT 'enabled',  -- enabled/disabled
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_scripts_name ON scripts(name);
CREATE INDEX idx_scripts_test_category ON scripts(test_category);
CREATE INDEX idx_scripts_status ON scripts(status);
CREATE INDEX idx_scripts_lifecycle_mode ON scripts(lifecycle_mode);
```

**parameters 字段结构（分组）：**
```json
{
  "shared": [
    {"name": "db_host", "label": "数据库地址", "type": "string", "default": "localhost"}
  ],
  "deploy": [
    {"name": "mysql_version", "label": "MySQL版本", "type": "select", "default": "8.0"}
  ],
  "run": [
    {"name": "threads", "label": "线程数", "type": "integer", "default": 8}
  ]
}
```

**file_list 字段示例：**
```json
[
  {"path": "main.sh", "size": 2048, "isEntry": true},
  {"path": "lib/common.sh", "size": 1024, "isEntry": false},
  {"path": "lib/utils.sh", "size": 1536, "isEntry": false}
]
```

#### 5.2.4 script_versions（脚本版本表）

```sql
CREATE TABLE script_versions (
    id BIGSERIAL PRIMARY KEY,
    script_id BIGINT NOT NULL REFERENCES scripts(id) ON DELETE CASCADE,
    version VARCHAR(20) NOT NULL,
    
    -- 生命周期配置
    lifecycle_mode VARCHAR(20) NOT NULL DEFAULT 'simple',
    has_deploy BOOLEAN DEFAULT FALSE,
    has_cleanup BOOLEAN DEFAULT FALSE,
    deploy_entry VARCHAR(255),
    cleanup_entry VARCHAR(255),
    
    entry_file VARCHAR(255) NOT NULL,   -- 执行入口文件路径
    file_list JSONB NOT NULL,           -- 文件列表
    storage_path VARCHAR(500) NOT NULL, -- 存储目录路径
    total_size BIGINT,                  -- 总文件大小
    file_count INTEGER DEFAULT 1,       -- 文件数量
    checksum VARCHAR(64),               -- 整体校验值
    change_log TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(script_id, version)
);

CREATE INDEX idx_script_versions_script_id ON script_versions(script_id);
```

#### 5.2.5 tasks（任务表）

```sql
CREATE TABLE tasks (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    script_id BIGINT NOT NULL REFERENCES scripts(id),
    script_version VARCHAR(20) NOT NULL,
    
    -- 参数配置（分组）
    shared_params JSONB DEFAULT '{}',   -- 共享参数
    deploy_params JSONB DEFAULT '{}',   -- 部署参数
    run_params JSONB NOT NULL DEFAULT '{}',  -- 执行参数
    
    -- 生命周期控制
    skip_deploy BOOLEAN DEFAULT FALSE,
    skip_cleanup BOOLEAN DEFAULT FALSE,
    deploy_timeout INTEGER DEFAULT 600,
    cleanup_timeout INTEGER DEFAULT 300,
    
    -- 生命周期阶段状态
    deploy_status VARCHAR(20),          -- pending/running/completed/failed/skipped
    cleanup_status VARCHAR(20),
    deploy_started_at TIMESTAMP,
    deploy_finished_at TIMESTAMP,
    cleanup_started_at TIMESTAMP,
    cleanup_finished_at TIMESTAMP,
    
    -- 指标采集配置
    collect_enabled BOOLEAN DEFAULT TRUE,
    collect_config JSONB,
    
    -- 执行配置
    execution_mode VARCHAR(20) NOT NULL,  -- immediate/scheduled
    scheduled_time TIMESTAMP,
    parallel_mode VARCHAR(20) DEFAULT 'sequential',  -- sequential/parallel
    max_parallel INTEGER DEFAULT 1,
    failure_strategy VARCHAR(20) DEFAULT 'continue',  -- continue/stop
    status VARCHAR(20) NOT NULL DEFAULT 'pending',  -- pending/deploying/running/cleaning/completed/cancelled/failed
    progress INTEGER DEFAULT 0,  -- 0-100
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tasks_status ON tasks(status);
CREATE INDEX idx_tasks_script_id ON tasks(script_id);
CREATE INDEX idx_tasks_created_at ON tasks(created_at);
CREATE INDEX idx_tasks_scheduled_time ON tasks(scheduled_time);
CREATE INDEX idx_tasks_deploy_status ON tasks(deploy_status);
CREATE INDEX idx_tasks_cleanup_status ON tasks(cleanup_status);
```

**collect_config 字段说明：**
- `collect_enabled = false` 时，`collect_config` 可为空
- `collect_enabled = true` 时，`collect_config` 包含采集配置

#### 5.2.6 task_servers（任务-服务器关联表）

```sql
CREATE TABLE task_servers (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    server_id BIGINT NOT NULL REFERENCES servers(id) ON DELETE CASCADE,
    
    -- ===== 部署阶段 =====
    deploy_status VARCHAR(20) DEFAULT 'pending',     -- pending/running/completed/failed/skipped
    deploy_started_at TIMESTAMP,
    deploy_finished_at TIMESTAMP,
    deploy_exit_code INTEGER,
    deploy_output TEXT,                              -- 部署日志
    deploy_error TEXT,
    
    -- ===== 执行阶段 =====
    run_status VARCHAR(20) DEFAULT 'pending',        -- pending/running/completed/failed
    started_at TIMESTAMP,                            -- 执行开始时间（兼容旧字段）
    finished_at TIMESTAMP,                           -- 执行结束时间（兼容旧字段）
    exit_code INTEGER,                               -- 执行返回码（兼容旧字段）
    output TEXT,                                     -- 执行日志（兼容旧字段）
    error_message TEXT,
    parsed_result JSONB,                             -- 解析后的结果
    
    -- ===== 卸载阶段 =====
    cleanup_status VARCHAR(20) DEFAULT 'pending',    -- pending/running/completed/failed/skipped
    cleanup_started_at TIMESTAMP,
    cleanup_finished_at TIMESTAMP,
    cleanup_exit_code INTEGER,
    cleanup_output TEXT,                             -- 卸载日志
    cleanup_error TEXT,
    
    -- ===== 综合 =====
    overall_status VARCHAR(20) DEFAULT 'pending',    -- 整体状态
    progress INTEGER DEFAULT 0,                      -- 整体进度 0-100
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(task_id, server_id)
);

CREATE INDEX idx_task_servers_task_id ON task_servers(task_id);
CREATE INDEX idx_task_servers_server_id ON task_servers(server_id);
CREATE INDEX idx_task_servers_overall_status ON task_servers(overall_status);
CREATE INDEX idx_task_servers_deploy_status ON task_servers(deploy_status);
CREATE INDEX idx_task_servers_run_status ON task_servers(run_status);
CREATE INDEX idx_task_servers_cleanup_status ON task_servers(cleanup_status);
```

**各阶段状态说明：**

| 字段 | 状态值 | 说明 |
|------|--------|------|
| deploy_status | pending/running/completed/failed/skipped | 部署阶段状态 |
| run_status | pending/running/completed/failed | 执行阶段状态 |
| cleanup_status | pending/running/completed/failed/skipped | 卸载阶段状态 |
| overall_status | pending/deploying/running/cleaning/completed/failed | 整体状态 |

**各阶段输出字段说明：**

| 字段 | 说明 |
|------|------|
| deploy_output | 部署阶段的完整日志输出 |
| deploy_error | 部署阶段的错误信息 |
| output | 执行阶段的完整日志输出（保留原有字段） |
| cleanup_output | 卸载阶段的完整日志输出 |
| cleanup_error | 卸载阶段的错误信息 |

#### 5.2.7 metrics（性能指标表）

```sql
-- 使用时序表或分区表
CREATE TABLE metrics (
    id BIGSERIAL,
    task_id BIGINT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    server_id BIGINT NOT NULL REFERENCES servers(id) ON DELETE CASCADE,
    timestamp TIMESTAMP NOT NULL,
    metric_type VARCHAR(50) NOT NULL,  -- cpu/memory/disk/network/custom
    metric_name VARCHAR(100) NOT NULL,
    value DOUBLE PRECISION NOT NULL,
    unit VARCHAR(20),
    tags JSONB,
    PRIMARY KEY (id, timestamp)
) PARTITION BY RANGE (timestamp);

-- 按月分区
CREATE TABLE metrics_202603 PARTITION OF metrics
    FOR VALUES FROM ('2026-03-01') TO ('2026-04-01');

CREATE INDEX idx_metrics_task_id ON metrics(task_id);
CREATE INDEX idx_metrics_server_id ON metrics(server_id);
CREATE INDEX idx_metrics_timestamp ON metrics(timestamp);
CREATE INDEX idx_metrics_type_name ON metrics(metric_type, metric_name);
```

#### 5.2.8 reports（报告表）

```sql
CREATE TABLE reports (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    summary TEXT,
    conclusion VARCHAR(50),  -- pass/fail/warning
    report_data JSONB,
    file_path VARCHAR(500),
    file_format VARCHAR(20),  -- pdf/html
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_reports_task_id ON reports(task_id);
CREATE INDEX idx_reports_created_at ON reports(created_at);
```

#### 5.2.9 system_config（系统配置表）

```sql
CREATE TABLE system_config (
    key VARCHAR(100) PRIMARY KEY,
    value TEXT NOT NULL,
    description TEXT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 默认配置
INSERT INTO system_config (key, value, description) VALUES
('script_storage_path', '/data/scripts', '脚本存储路径'),
('report_storage_path', '/data/reports', '报告存储路径'),
('temp_storage_path', '/data/temp', '临时文件存储路径'),
('default_timeout', '3600', '默认超时时间(秒)'),
('default_retry', '0', '默认重试次数'),
('max_concurrent_tasks', '10', '最大并发任务数'),
('metrics_retention_days', '30', '指标数据保留天数'),
('report_retention_days', '90', '报告保留天数');
```

#### 5.2.10 test_results（测试结果表）

```sql
CREATE TABLE test_results (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    server_id BIGINT NOT NULL REFERENCES servers(id) ON DELETE CASCADE,
    task_server_id BIGINT NOT NULL REFERENCES task_servers(id) ON DELETE CASCADE,
    
    -- 结果判定
    result VARCHAR(20) NOT NULL,           -- pass/fail/warning/error
    result_reason TEXT,                     -- 判定原因
    overall_score INTEGER,                  -- 综合评分 0-100
    
    -- 解析后的标准化指标
    metrics JSONB NOT NULL,                 -- 标准化指标数据
    
    -- 原始数据
    raw_output TEXT,                        -- 原始标准输出
    raw_error TEXT,                         -- 原始错误输出
    output_files JSONB,                     -- 输出文件列表
    
    -- 执行信息
    exit_code INTEGER,                      -- 脚本返回码
    duration_ms INTEGER,                    -- 执行时长(毫秒)
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_test_results_task_id ON test_results(task_id);
CREATE INDEX idx_test_results_server_id ON test_results(server_id);
CREATE INDEX idx_test_results_result ON test_results(result);
CREATE INDEX idx_test_results_created_at ON test_results(created_at);
```

**metrics 字段结构：**
```json
{
  "metrics": [
    {
      "name": "iops",
      "displayName": "IOPS",
      "value": 50000,
      "unit": "ops/s",
      "category": "performance",
      "baseline": {"min": 10000, "warning": 30000, "excellent": 80000},
      "score": "excellent",
      "scoreValue": 95
    },
    {
      "name": "latency_avg",
      "displayName": "平均延迟",
      "value": 2.5,
      "unit": "ms",
      "category": "performance",
      "baseline": {"max": 10, "warning": 5, "excellent": 1},
      "score": "warning",
      "scoreValue": 60
    }
  ],
  "summary": {
    "total": 5,
    "excellent": 3,
    "good": 1,
    "warning": 1,
    "fail": 0
  },
  "overallScore": 82
}
```

#### 5.2.11 metric_definitions（指标定义表）

```sql
CREATE TABLE metric_definitions (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,      -- 指标名称
    display_name VARCHAR(200) NOT NULL,     -- 显示名称
    category VARCHAR(50) NOT NULL,          -- 分类：performance/reliability/stability
    unit VARCHAR(50),                        -- 单位
    description TEXT,                        -- 描述
    baseline_config JSONB,                   -- 基准线配置
    comparison_mode VARCHAR(20) DEFAULT 'higher_better',  -- higher_better/lower_better
    applicable_categories JSONB,             -- 适用测试类型
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 预置指标定义
INSERT INTO metric_definitions (name, display_name, category, unit, baseline_config, comparison_mode) VALUES
('iops', 'IOPS', 'performance', 'ops/s', '{"min": 1000, "warning": 10000, "excellent": 50000}', 'higher_better'),
('throughput', '吞吐量', 'performance', 'MB/s', '{"min": 10, "warning": 100, "excellent": 500}', 'higher_better'),
('latency_avg', '平均延迟', 'performance', 'ms', '{"max": 100, "warning": 10, "excellent": 1}', 'lower_better'),
('latency_p99', 'P99延迟', 'performance', 'ms', '{"max": 500, "warning": 50, "excellent": 5}', 'lower_better'),
('latency_p95', 'P95延迟', 'performance', 'ms', '{"max": 200, "warning": 20, "excellent": 2}', 'lower_better'),
('error_rate', '错误率', 'reliability', '%', '{"max": 5, "warning": 1, "excellent": 0.1}', 'lower_better'),
('success_rate', '成功率', 'reliability', '%', '{"min": 95, "warning": 99, "excellent": 99.9}', 'higher_better');

CREATE INDEX idx_metric_definitions_name ON metric_definitions(name);
CREATE INDEX idx_metric_definitions_category ON metric_definitions(category);
```

#### 5.2.12 result_rules（结果判定规则表）

```sql
CREATE TABLE result_rules (
    id BIGSERIAL PRIMARY KEY,
    script_id BIGINT REFERENCES scripts(id),    -- NULL表示全局规则
    name VARCHAR(100) NOT NULL,
    description TEXT,
    rules JSONB NOT NULL,                        -- 判定规则
    priority INTEGER DEFAULT 0,                  -- 优先级
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_result_rules_script_id ON result_rules(script_id);
CREATE INDEX idx_result_rules_enabled ON result_rules(enabled);
```

**rules 字段结构：**
```json
{
  "conditions": [
    {
      "metric": "iops",
      "operator": ">=",
      "warningValue": 30000,
      "passValue": 10000,
      "weight": 0.4
    },
    {
      "metric": "latency_avg",
      "operator": "<=",
      "warningValue": 5,
      "passValue": 10,
      "weight": 0.3
    }
  ],
  "passThreshold": 60,
  "warningThreshold": 80,
  "logic": "weighted"    // all/any/weighted
}
```

### 5.3 数据库索引优化建议

```sql
-- 任务查询优化
CREATE INDEX idx_tasks_status_created ON tasks(status, created_at DESC);

-- 指标查询优化（时序数据）
CREATE INDEX idx_metrics_task_timestamp ON metrics(task_id, timestamp DESC);

-- 报告查询优化
CREATE INDEX idx_reports_conclusion_created ON reports(conclusion, created_at DESC);
```

---

## 6. 接口设计

### 6.1 接口规范

**基础信息：**
- 基础路径：`/api/v1`
- 响应格式：JSON

**统一响应结构：**

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

**错误响应结构：**

```json
{
  "code": 1001,
  "message": "服务器连接失败",
  "data": null
}
```

**错误码定义：**

| 错误码 | 说明 |
|--------|------|
| 0 | 成功 |
| 1001 | 服务器连接失败 |
| 1002 | 脚本执行失败 |
| 1003 | 任务不存在 |
| 2001 | 参数校验失败 |
| 5001 | 系统内部错误 |

### 6.2 接口清单

#### 6.2.1 服务器管理

| 方法 | 路径 | 描述 | 请求参数 |
|------|------|------|----------|
| GET | /servers | 获取服务器列表 | name, status, group_id, page, size |
| GET | /servers/{id} | 获取服务器详情 | - |
| POST | /servers | 添加服务器 | name, host, port, username, auth_type, auth_secret, group_id, tags, remark |
| PUT | /servers/{id} | 更新服务器 | 同上 |
| DELETE | /servers/{id} | 删除服务器 | - |
| POST | /servers/{id}/test | 测试连接 | - |
| GET | /servers/{id}/status | 获取服务器状态 | - |
| POST | /servers/{id}/refresh | 刷新服务器信息 | - |
| GET | /servers/groups | 获取分组列表 | - |
| POST | /servers/groups | 创建分组 | name, description |
| PUT | /servers/groups/{id} | 更新分组 | name, description |
| DELETE | /servers/groups/{id} | 删除分组 | - |
| POST | /servers/import | 批量导入 | file (CSV) |

#### 6.2.2 脚本管理

| 方法 | 路径 | 描述 | 请求参数 |
|------|------|------|----------|
| GET | /scripts | 获取脚本列表 | name, test_category, status, page, size |
| GET | /scripts/{id} | 获取脚本详情 | - |
| POST | /scripts | 上传脚本 | name, script_type, test_category, description, file, entry_file, parameters, parse_rules |
| PUT | /scripts/{id} | 更新脚本 | name, description, parameters, parse_rules, default_timeout, default_retry |
| DELETE | /scripts/{id} | 删除脚本 | - |
| GET | /scripts/{id}/versions | 获取版本列表 | page, size |
| GET | /scripts/{id}/versions/{version} | 获取指定版本 | - |
| POST | /scripts/{id}/versions | 创建新版本 | file, entry_file, change_log |
| POST | /scripts/{id}/rollback/{version} | 回退版本 | - |
| GET | /scripts/{id}/files | 获取脚本文件列表 | version (可选) |
| GET | /scripts/{id}/files/{path} | 获取文件内容 | version (可选) |
| GET | /scripts/{id}/export | 导出脚本 | version, format (zip/tar.gz), include_config |
| GET | /scripts/categories | 获取测试类型列表 | - |
| POST | /scripts/{id}/validate | 校验脚本 | - |
| POST | /scripts/parse-upload | 解析上传文件 | file (返回文件列表) |

**上传脚本接口详情：**

```json
// POST /api/v1/scripts
// Content-Type: multipart/form-data

{
  "name": "MySQL测试脚本",
  "script_type": "shell",
  "test_category": "应用性能-MySQL",
  "description": "MySQL数据库性能测试",
  "file": "<压缩包或单文件>",
  "entry_file": "main.sh",           // 多文件时必填
  "parameters": {...},
  "parse_rules": {...}
}

// 响应
{
  "code": 0,
  "message": "success",
  "data": {
    "id": 1,
    "name": "MySQL测试脚本",
    "current_version": "v1.0.0",
    "entry_file": "main.sh",
    "file_count": 5,
    "files": [
      {"path": "main.sh", "size": 2048, "isEntry": true},
      {"path": "lib/common.sh", "size": 1024, "isEntry": false}
    ]
  }
}
```

**解析上传文件接口详情：**

```json
// POST /api/v1/scripts/parse-upload
// Content-Type: multipart/form-data

{
  "file": "<压缩包>"
}

// 响应
{
  "code": 0,
  "message": "success",
  "data": {
    "fileCount": 5,
    "totalSize": 10240,
    "files": [
      {"path": "main.sh", "size": 2048, "type": "shell"},
      {"path": "lib/common.sh", "size": 1024, "type": "shell"},
      {"path": "lib/utils.py", "size": 1536, "type": "python"},
      {"path": "config/default.conf", "size": 512, "type": "config"},
      {"path": "README.md", "size": 612, "type": "doc"}
    ],
    "suggestedEntry": "main.sh"   // 建议的入口文件
  }
}
```

**导出脚本接口详情：**

```json
// GET /api/v1/scripts/{id}/export?version=v1.0.0&format=zip&include_config=true

// 响应: 文件下载
// 文件名: {script_name}_v1.0.0.zip
```

#### 6.2.3 任务管理

| 方法 | 路径 | 描述 | 请求参数 |
|------|------|------|----------|
| GET | /tasks | 获取任务列表 | name, status, page, size |
| GET | /tasks/{id} | 获取任务详情 | - |
| POST | /tasks | 创建任务 | name, description, script_id, script_version, shared_params, deploy_params, run_params, lifecycle_config, server_ids, collect_config, execution_mode, scheduled_time, parallel_mode |
| PUT | /tasks/{id} | 更新任务 | 同上（仅限 pending 状态） |
| DELETE | /tasks/{id} | 删除任务 | - |
| POST | /tasks/{id}/execute | 执行任务 | - |
| POST | /tasks/{id}/cancel | 取消任务 | - |
| POST | /tasks/{id}/retry | 重试任务 | - |
| GET | /tasks/{id}/logs | 获取执行日志 | server_id, stage (deploy/run/cleanup/all) |
| GET | /tasks/{id}/progress | 获取执行进度 | - |

**创建任务接口详情：**

```json
// POST /api/v1/tasks
{
  "name": "MySQL OLTP 性能测试",
  "description": "测试 MySQL 8.0 在高并发下的性能表现",
  "scriptId": 1,
  "scriptVersion": "v1.0.0",
  "serverIds": [1, 2, 3],
  
  // 参数配置（分组）
  "sharedParams": {
    "db_host": "localhost",
    "db_name": "testdb"
  },
  "deployParams": {
    "mysql_version": "8.0",
    "install_dir": "/usr/local/mysql",
    "data_dir": "/data/mysql",
    "innodb_buffer_pool_size": "4G",
    "max_connections": 500
  },
  "runParams": {
    "threads": 8,
    "duration": 300,
    "test_mode": "oltp_read_write",
    "table_size": 100000
  },
  
  // 生命周期控制
  "lifecycleConfig": {
    "skipDeploy": false,        // 是否跳过部署
    "skipCleanup": false,       // 是否跳过卸载
    "deployTimeout": 600,       // 部署超时（秒）
    "cleanupTimeout": 300       // 卸载超时（秒）
  },
  
  // 指标采集配置
  "collectEnabled": true,
  "collectConfig": {
    "frequency": "5s",
    "cpu": {"enabled": true},
    "memory": {"enabled": true},
    "disk": {"enabled": true},
    "network": {"enabled": true}
  },
  
  // 执行配置
  "executionMode": "immediate",
  "scheduledTime": null,
  "parallelMode": "parallel",
  "maxParallel": 3,
  "failureStrategy": "continue"
}
```

**任务详情接口响应：**

```json
// GET /api/v1/tasks/{id}
{
  "code": 0,
  "message": "success",
  "data": {
    "id": 1001,
    "name": "MySQL OLTP 性能测试",
    "status": "completed",
    
    "script": {
      "id": 1,
      "name": "MySQL OLTP 测试",
      "version": "v1.0.0",
      "lifecycleMode": "full",
      "hasDeploy": true,
      "hasCleanup": true
    },
    
    // 生命周期配置
    "lifecycleConfig": {
      "skipDeploy": false,
      "skipCleanup": false,
      "deployTimeout": 600,
      "cleanupTimeout": 300
    },
    
    // 各阶段汇总状态
    "lifecycleSummary": {
      "deploy": {
        "status": "completed",
        "startedAt": "2026-03-09T10:00:00Z",
        "finishedAt": "2026-03-09T10:05:00Z",
        "duration": 300,
        "successCount": 3,
        "failedCount": 0
      },
      "run": {
        "status": "completed",
        "startedAt": "2026-03-09T10:05:00Z",
        "finishedAt": "2026-03-09T10:15:00Z",
        "duration": 600,
        "successCount": 3,
        "failedCount": 0
      },
      "cleanup": {
        "status": "completed",
        "startedAt": "2026-03-09T10:15:00Z",
        "finishedAt": "2026-03-09T10:18:00Z",
        "duration": 180,
        "successCount": 3,
        "failedCount": 0
      }
    },
    
    // 参数配置
    "sharedParams": {...},
    "deployParams": {...},
    "runParams": {...},
    
    // 服务器执行详情
    "servers": [
      {
        "serverId": 1,
        "serverName": "测试服务器-01",
        "overallStatus": "completed",
        "deploy": {
          "status": "completed",
          "startedAt": "2026-03-09T10:00:00Z",
          "finishedAt": "2026-03-09T10:05:00Z",
          "duration": 300,
          "exitCode": 0
        },
        "run": {
          "status": "completed",
          "startedAt": "2026-03-09T10:05:00Z",
          "finishedAt": "2026-03-09T10:15:00Z",
          "duration": 600,
          "exitCode": 0
        },
        "cleanup": {
          "status": "completed",
          "startedAt": "2026-03-09T10:15:00Z",
          "finishedAt": "2026-03-09T10:18:00Z",
          "duration": 180,
          "exitCode": 0
        }
      }
    ]
  }
}
```

**日志查询接口：**

```json
// GET /api/v1/tasks/{id}/logs?server_id=1&stage=deploy

// 请求参数
// - server_id: 服务器ID（可选，不传则返回所有服务器日志）
// - stage: 阶段类型 deploy/run/cleanup/all（默认 all）

// 响应
{
  "code": 0,
  "message": "success",
  "data": {
    "taskId": 1001,
    "serverId": 1,
    "stage": "deploy",
    "logs": [
      {
        "timestamp": "2026-03-09T10:00:01Z",
        "level": "INFO",
        "message": "开始部署 MySQL..."
      },
      {
        "timestamp": "2026-03-09T10:00:02Z",
        "level": "INFO",
        "message": "安装 MySQL 8.0..."
      }
    ],
    "exitCode": 0,
    "duration": 300
  }
}
```

#### 6.2.4 指标数据

| 方法 | 路径 | 描述 | 请求参数 |
|------|------|------|----------|
| GET | /metrics | 查询指标数据 | task_id, server_id, metric_type, metric_name, start_time, end_time |
| GET | /metrics/summary | 指标统计 | task_id, server_id, metric_type |
| GET | /metrics/export | 导出指标数据 | task_id, format (csv/json) |

#### 6.2.5 报告管理

| 方法 | 路径 | 描述 | 请求参数 |
|------|------|------|----------|
| GET | /reports | 获取报告列表 | task_id, conclusion, start_date, end_date, page, size |
| GET | /reports/{id} | 获取报告详情 | - |
| GET | /reports/{id}/download | 下载报告 | format (pdf/html) |
| DELETE | /reports/{id} | 删除报告 | - |
| POST | /reports/compare | 报告对比 | report_ids[] |
| POST | /reports/regenerate/{task_id} | 重新生成报告 | - |

#### 6.2.6 测试结果管理

| 方法 | 路径 | 描述 | 请求参数 |
|------|------|------|----------|
| GET | /results | 获取结果列表 | task_id, server_id, result, start_date, end_date, page, size |
| GET | /results/{id} | 获取结果详情 | - |
| GET | /results/task/{taskId} | 获取任务所有结果 | server_id (可选) |
| GET | /results/{id}/raw | 获取原始输出 | type (stdout/stderr/all) |
| GET | /results/{id}/files | 获取输出文件列表 | - |
| GET | /results/{id}/files/{fileId} | 下载输出文件 | - |
| POST | /results/compare | 结果对比 | result_ids[], compare_type |
| GET | /results/trend | 趋势分析 | script_id, server_id, metric_name, period |
| GET | /results/export | 导出结果数据 | task_id, format (csv/json/excel) |

**结果对比接口详情：**

```json
// POST /api/v1/results/compare
{
  "resultIds": [1, 2, 3],
  "compareType": "time"  // time/server/parameter/version
}

// 响应
{
  "code": 0,
  "message": "success",
  "data": {
    "compareType": "time",
    "dimensions": ["2026-03-01", "2026-03-05", "2026-03-09"],
    "metrics": [
      {
        "name": "iops",
        "displayName": "IOPS",
        "unit": "ops/s",
        "values": [45000, 48000, 52000],
        "changeRates": ["+6.7%", "+8.3%"],
        "trend": "increasing"
      },
      {
        "name": "latency_avg",
        "displayName": "平均延迟",
        "unit": "ms",
        "values": [3.2, 2.8, 2.5],
        "changeRates": ["-12.5%", "-10.7%"],
        "trend": "decreasing"
      }
    ]
  }
}
```

**趋势分析接口详情：**

```json
// GET /api/v1/results/trend?script_id=1&server_id=5&metric_name=iops&period=30d

// 响应
{
  "code": 0,
  "message": "success",
  "data": {
    "metric": {
      "name": "iops",
      "displayName": "IOPS",
      "unit": "ops/s"
    },
    "period": "30d",
    "dataPoints": [
      {"date": "2026-03-01", "value": 45000, "taskId": 100},
      {"date": "2026-03-05", "value": 48000, "taskId": 101},
      {"date": "2026-03-09", "value": 52000, "taskId": 102}
    ],
    "statistics": {
      "min": 45000,
      "max": 52000,
      "avg": 48333,
      "stddev": 3512
    },
    "trend": {
      "direction": "increasing",
      "changeRate": 15.6
    },
    "anomalies": []
  }
}
```

#### 6.2.7 指标定义管理

| 方法 | 路径 | 描述 | 请求参数 |
|------|------|------|----------|
| GET | /metric-definitions | 获取指标定义列表 | category, page, size |
| GET | /metric-definitions/{id} | 获取指标定义详情 | - |
| POST | /metric-definitions | 创建指标定义 | name, display_name, category, unit, baseline_config, comparison_mode |
| PUT | /metric-definitions/{id} | 更新指标定义 | 同上 |
| DELETE | /metric-definitions/{id} | 删除指标定义 | - |

#### 6.2.8 结果判定规则管理

| 方法 | 路径 | 描述 | 请求参数 |
|------|------|------|----------|
| GET | /result-rules | 获取规则列表 | script_id, page, size |
| GET | /result-rules/{id} | 获取规则详情 | - |
| POST | /result-rules | 创建规则 | script_id, name, description, rules, priority |
| PUT | /result-rules/{id} | 更新规则 | 同上 |
| DELETE | /result-rules/{id} | 删除规则 | - |
| POST | /result-rules/{id}/test | 测试规则 | result_id (用已有结果测试规则) |

#### 6.2.9 系统配置

| 方法 | 路径 | 描述 | 请求参数 |
|------|------|------|----------|
| GET | /system/config | 获取系统配置 | - |
| PUT | /system/config | 更新系统配置 | key, value |
| GET | /system/storage | 获取存储信息 | - |

---

## 7. 页面清单

### 7.1 页面结构

```
自动化测试管理平台
│
├── 首页（仪表盘）
│   ├── 统计概览（服务器数、脚本数、任务数、今日任务数、测试通过率）
│   ├── 最近任务列表
│   ├── 最近测试结果概览
│   └── 服务器状态概览
│
├── 服务器管理
│   ├── 服务器列表
│   ├── 添加/编辑服务器
│   ├── 服务器详情
│   └── 分组管理
│
├── 脚本中心
│   ├── 脚本列表
│   ├── 上传脚本
│   ├── 脚本详情
│   ├── 编辑脚本
│   ├── 版本管理
│   └── 判定规则配置
│
├── 测试任务
│   ├── 任务列表
│   ├── 创建任务
│   ├── 任务详情
│   └── 执行监控
│
├── 测试结果（新增）
│   ├── 结果列表
│   ├── 结果详情
│   ├── 结果对比
│   └── 趋势分析
│
├── 报告中心
│   ├── 报告列表
│   ├── 报告详情
│   └── 报告对比
│
└── 系统设置
    ├── 系统配置
    └── 指标定义管理
```

### 7.2 核心页面详情

#### 7.2.1 首页（仪表盘）

**页面要素：**
- 统计卡片：服务器数量、脚本数量、任务总数、今日任务数
- 最近任务列表：任务名称、状态、执行时间、操作（查看详情）
- 服务器状态概览：在线/离线/维护中数量，饼图展示

#### 7.2.2 服务器列表页

**页面要素：**
- 筛选区：名称搜索、状态筛选、分组筛选
- 操作按钮：添加服务器、批量导入
- 列表区：名称、主机、状态、操作系统、CPU/内存、分组、操作（测试连接/编辑/删除）
- 批量操作：批量测试连接、批量删除

#### 7.2.3 添加/编辑服务器页

**页面要素：**
- 基本信息：名称、主机、端口、分组、标签、备注
- 连接配置：用户名、认证方式、密码/密钥
- 操作按钮：测试连接、保存

#### 7.2.4 脚本列表页

**页面要素：**
- 筛选区：名称搜索、测试类型筛选、状态筛选
- 操作按钮：上传脚本
- 列表区：名称、测试类型、脚本类型、当前版本、状态、操作（查看/编辑/删除）
- 预置脚本标识

#### 7.2.5 上传脚本页

**页面要素：**

**步骤一：基本信息**
- 脚本名称（必填）
- 脚本类型：Shell / Python
- 测试类型：下拉选择
- 描述

**步骤二：生命周期模式**
- ○ 简单模式（仅执行测试）
- ● 完整模式（部署 → 执行 → 卸载）

**步骤三：上传脚本文件**
- 文件上传区域（支持拖拽）
- 支持格式：.sh / .py / .zip / .tar.gz
- 上传压缩包后：
  - 显示文件列表（树形结构）
  - 入口文件选择（下拉或点击文件选择）
  - 文件预览（点击文件查看内容）
- 文件大小限制提示

**步骤四：入口文件配置**
- 执行入口：[下拉选择] （必需）
- 部署入口：[下拉选择] （完整模式时可选）
- 卸载入口：[下拉选择] （完整模式时可选）

**步骤五：参数配置**
- **共享参数**（Tab）
  - 动态参数列表（添加/删除参数）
  - 每个参数：名称、标签、类型、是否必填、默认值、校验规则、描述
- **部署参数**（Tab，完整模式时显示）
  - 动态参数列表（添加/删除参数）
  - 每个参数：名称、标签、类型、是否必填、默认值、校验规则、描述
- **测试参数**（Tab）
  - 动态参数列表（添加/删除参数）
  - 每个参数：名称、标签、类型、是否必填、默认值、校验规则、描述
- 参数预览

**步骤六：输出解析**
- 解析方式选择（正则/JSON/关键字）
- 解析规则配置
- 成功/失败判断规则
- 测试解析（输入示例输出测试解析结果）

**步骤七：执行配置**
- 默认超时时间
- 默认重试次数

**操作按钮：** 保存、取消、保存并继续编辑

#### 7.2.6 脚本详情页

**页面要素：**
- 基本信息：名称、类型、测试类型、描述
- 当前版本信息
- 文件列表（树形结构展示）
  - 支持点击查看文件内容
  - 入口文件标识
- 参数配置预览
- 输出解析规则预览
- 操作按钮：编辑、导出、删除、版本管理

#### 7.2.7 版本管理页

**页面要素：**
- 版本列表
  - 版本号、上传时间、文件数量、总大小、入口文件、变更说明
  - 当前版本标识
- 操作按钮
  - 查看文件：展开查看该版本所有文件
  - 设为当前版本
  - 导出该版本
  - 删除版本（仅非当前版本）
- 版本对比
  - 选择两个版本
  - 文件差异对比（新增/删除/修改）
  - 文件内容差异（Diff 视图）

#### 7.2.8 创建任务页

**页面要素：**
- 步骤一：基本信息
  - 任务名称、描述
- 步骤二：选择服务器
  - 服务器列表（支持多选、分组筛选）
- 步骤三：选择脚本
  - 脚本选择、版本选择
- 步骤四：参数配置
  - **共享参数**（如果脚本有定义）
    - 根据脚本参数定义动态渲染表单
  - **部署参数**（如果脚本有定义且为完整模式）
    - 根据脚本参数定义动态渲染表单
  - **测试参数**
    - 根据脚本参数定义动态渲染表单
- 步骤五：生命周期配置（如果脚本为完整模式）
  - **部署阶段**
    - ☑ 执行部署（默认勾选）
    - 超时时间设置
    - 失败策略：终止任务 / 继续执行
  - **卸载阶段**
    - ☑ 执行卸载（默认勾选）
    - 超时时间设置
    - 失败处理：终止任务 / 记录警告继续完成
- 步骤六：指标采集配置
  - **采集开关**（启用/禁用）
  - 禁用时提示：将不采集服务器性能指标
  - 启用时显示：
    - 采集频率选择
    - 指标项选择（CPU/内存/磁盘/网络）
    - 自定义指标配置（可选）
- 步骤七：执行设置
  - 执行方式（立即/定时）
  - 并行模式（串行/并行）
  - 失败策略
- 步骤八：预览确认
  - 配置预览
  - 操作按钮：立即创建、创建并执行

#### 7.2.8 任务执行监控页

**页面要素：**
- 任务信息卡片：任务名称、状态、执行进度、开始时间
- **生命周期时间线**（如果为完整模式）
  - 部署阶段：状态、耗时、成功/失败数量
  - 执行阶段：状态、耗时、成功/失败数量
  - 卸载阶段：状态、耗时、成功/失败数量
- 服务器执行状态列表
  - 服务器名称、部署状态、执行状态、卸载状态、综合状态、操作（查看日志）
- 实时日志区域
  - 服务器选择
  - **阶段选择**：部署日志 / 执行日志 / 卸载日志
  - 日志滚动显示
  - 日志级别筛选
- 实时性能指标图表（如果启用采集）
  - CPU 使用率趋势
  - 内存使用率趋势
  - 磁盘 IO 趋势
  - 网络流量趋势
- 操作按钮：取消任务

#### 7.2.9 报告详情页

**页面要素：**
- 报告概览：测试结论、执行时长、关键指标
- **生命周期时间线**（如果为完整模式）
  - 部署阶段：开始时间、结束时间、耗时
  - 执行阶段：开始时间、结束时间、耗时
  - 卸载阶段：开始时间、结束时间、耗时
- 测试配置：脚本信息、参数配置
- 执行详情：各服务器执行结果
  - 显示各服务器的部署、执行、卸载状态
- 性能指标图表：各指标趋势图（交互式图表，支持缩放、数据点查看）
- 统计表格：峰值/平均值/最小值
- 结论建议：测试结论、性能瓶颈分析、优化建议
- 操作按钮：导出 PDF、导出 HTML

---

### 7.3 测试结果页面详情

#### 7.3.1 结果列表页

**页面要素：**
- 筛选区
  - 任务名称搜索
  - 服务器选择
  - 结果状态筛选（pass/fail/warning/error）
  - 时间范围选择
- 列表区
  - 任务名称、服务器名称、脚本名称、结果状态、综合评分、执行时间
  - 关键指标预览（前3个核心指标）
- 操作
  - 查看详情、查看原始输出、导出结果
- 批量操作
  - 批量导出、批量对比

#### 7.3.2 结果详情页

**页面要素：**

**一、结果概览卡片**
- 测试结果：pass/fail/warning/error（颜色标识）
- 综合评分：0-100分（进度条展示）
- 执行时长
- 脚本名称和版本
- 服务器信息

**二、指标详情表格**
| 指标名称 | 数值 | 单位 | 基准线 | 评分 | 趋势 |
|----------|------|------|--------|------|------|
| IOPS | 50000 | ops/s | ↑ 优秀 | 95 | ↑ |
| 平均延迟 | 2.5 | ms | ⚠ 警告 | 60 | ↓ |

- 支持点击指标查看历史趋势
- 支持指标排序和筛选

**三、原始输出区域**
- Tab切换：标准输出 / 错误输出
- 输出内容展示（带语法高亮）
- 支持搜索、复制、下载

**四、输出文件区域**（如有）
- 文件列表：文件名、大小、类型
- 支持预览（文本文件）和下载

**五、执行时间线**
- 开始时间、各阶段耗时、结束时间
- 时间线可视化展示

**操作按钮：**
- 导出结果、加入对比、查看关联报告

#### 7.3.3 结果对比页

**页面要素：**

**一、对比选择区**
- 对比类型：时间对比 / 服务器对比 / 参数对比 / 版本对比
- 结果选择：多选结果（2-5个）
- 快速选择：最近3次、最近7天、同服务器对比

**二、对比结果表格**
| 指标 | 结果1 | 结果2 | 结果3 | 变化趋势 | 变化率 |
|------|-------|-------|-------|----------|--------|
| IOPS | 45000 | 48000 | 52000 | ↑ 持续上升 | +15.6% |
| 延迟 | 3.2 | 2.8 | 2.5 | ↓ 持续下降 | -21.9% |

- 颜色标识：绿色(改善)、红色(恶化)、灰色(持平)
- 支持导出对比报告

**三、趋势图表**
- 多结果指标趋势折线图
- 支持选择展示的指标
- 支持缩放和数据点查看

**四、差异分析**
- 自动识别显著变化的指标
- 异常点标注
- 变化原因分析建议

#### 7.3.4 趋势分析页

**页面要素：**

**一、分析配置区**
- 脚本选择
- 服务器选择（可多选对比）
- 指标选择
- 时间范围：7天 / 30天 / 90天 / 自定义

**二、趋势图表**
- 主图表：指标随时间变化折线图
- 辅助线：基准线、平均线
- 支持多指标叠加对比
- 支持多服务器叠加对比
- 交互功能：缩放、数据点悬停查看、区间选择

**三、统计信息**
| 统计项 | 数值 |
|--------|------|
| 最小值 | 45000 |
| 最大值 | 52000 |
| 平均值 | 48333 |
| 标准差 | 3512 |
| 变化趋势 | 上升 (+15.6%) |

**四、异常检测**
- 自动检测异常数据点
- 异常时间点标注
- 可能原因提示

**五、趋势预测**（可选）
- 基于历史数据预测未来趋势
- 预测置信区间

---

## 8. 技术架构

### 8.1 架构图

```
┌─────────────────────────────────────────────────────────────────────┐
│                           前端层 (Vue 3)                             │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐              │
│  │  路由    │ │  状态管理 │ │  组件库   │ │  HTTP库   │              │
│  │  Router  │ │  Pinia   │ │  Element+ │ │  Axios   │              │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘              │
└─────────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────┐
│                         后端层 (Spring Boot)                         │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐              │
│  │  API层   │ │  业务层   │ │  SSH执行  │ │  报告生成 │              │
│  │Controller│ │ Service  │ │  Engine   │ │Generator │              │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘              │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐                           │
│  │  任务调度 │ │  指标采集 │ │  文件管理 │                           │
│  │Scheduler │ │ Collector │ │StorageSvc│                           │
│  └──────────┘ └──────────┘ └──────────┘                           │
└─────────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────┐
│                           数据层                                     │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐                           │
│  │PostgreSQL│ │  Redis   │ │ 本地存储  │                           │
│  │ 数据库   │ │  缓存    │ │ 文件系统  │                           │
│  └──────────┘ └──────────┘ └──────────┘                           │
└─────────────────────────────────────────────────────────────────────┘
                                │
                                ▼
              ┌─────────────────────────────────┐
              │        被测服务器集群             │
              │      (SSH / Agent 连接)          │
              └─────────────────────────────────┘
```

### 8.2 技术选型详情

#### 前端技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Vue | 3.x | 前端框架 |
| Vue Router | 4.x | 路由管理 |
| Pinia | 2.x | 状态管理 |
| Element Plus | 2.x | UI 组件库 |
| Axios | 1.x | HTTP 客户端 |
| ECharts | 5.x | 图表库 |
| Monaco Editor | - | 代码编辑器 |
| Day.js | - | 日期处理 |

#### 后端技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.x | 后端框架 |
| Spring Scheduler | - | 定时任务 |
| MyBatis Plus | 3.x | ORM 框架 |
| PostgreSQL | 15.x | 主数据库 |
| Redis | 7.x | 缓存、任务队列 |
| JSch / Apache SSHD | - | SSH 连接 |
| iText / Flying Saucer | - | PDF 生成 |
| Thymeleaf | - | HTML 模板引擎 |

#### 部署方案

```yaml
# docker-compose.yml
version: '3.8'
services:
  app:
    build: .
    ports:
      - "8080:8080"
    volumes:
      - ./data:/data
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - DB_HOST=postgres
      - REDIS_HOST=redis
    depends_on:
      - postgres
      - redis
  
  postgres:
    image: postgres:15
    volumes:
      - postgres_data:/var/lib/postgresql/data
    environment:
      - POSTGRES_DB=test_platform
      - POSTGRES_USER=admin
      - POSTGRES_PASSWORD=admin123
  
  redis:
    image: redis:7
    volumes:
      - redis_data:/data

volumes:
  postgres_data:
  redis_data:
```

---

## 9. 风险与约束

### 9.1 技术风险

| 风险 | 影响 | 应对措施 | 优先级 |
|------|------|----------|--------|
| SSH 连接不稳定 | 任务执行失败 | 实现重连机制、连接池管理、超时控制 | 高 |
| 脚本执行卡死 | 资源占用 | 超时强制终止、进程监控 | 高 |
| 大量指标数据 | 数据库性能 | 按月分区、定期归档、索引优化 | 中 |
| 并发任务过多 | 系统负载 | 任务队列限流、资源隔离 | 中 |
| 磁盘空间不足 | 文件存储失败 | 存储监控、定期清理 | 低 |

### 9.2 约束条件

| 约束 | 说明 |
|------|------|
| 单体架构 | V1 版本采用单体架构 |
| 本地存储 | 文件存储使用本地文件系统 |
| 网络要求 | 平台需能 SSH 访问被测服务器 |
| 浏览器支持 | Chrome 90+、Edge 90+、Firefox 88+ |
| 无用户认证 | V1 版本暂不实现用户认证功能 |

---

## 附录

### A. 名词解释

| 术语 | 解释 |
|------|------|
| POC | Proof of Concept，概念验证，此处指服务器售前测试验证 |
| IOPS | Input/Output Operations Per Second，每秒读写次数 |
| OLTP | Online Transaction Processing，在线事务处理 |

### B. 参考文档

- fio 官方文档：https://fio.readthedocs.io/
- sysbench 文档：https://github.com/akopytov/sysbench
- iperf3 文档：https://software.es.net/iperf/
- PostgreSQL 分区表：https://www.postgresql.org/docs/current/ddl-partitioning.html

---

**文档结束**

# autotest.yaml 配置文件功能

## 功能概述

autotest.yaml 是自动化测试平台的脚本配置文件，用于定义脚本的基本信息、参数配置和步骤配置。

**核心特性：**
- 📄 脚本包上传时自动解析配置
- 🔄 配置变更时自动同步到 YAML 文件
- 📦 支持版本控制和配置迁移
- 🔧 与现有数据库结构 100% 兼容

---

## YAML 文件格式

### 完整示例

```yaml
# autotest.yaml - 自动化测试脚本配置文件

# ==================== 基本信息 ====================
name: fio-disk-test
description: FIO 磁盘性能测试脚本

type: shell                    # shell | python
category: disk                 # cpu | memory | disk | network | database | ...
timeout: 1800                  # 默认超时时间（秒）

# ==================== 参数配置 ====================
parameters:
  - name: RUNTIME
    type: string
    default: "60"
    description: 测试时长（秒）
    
  - name: SIZE
    type: string
    default: "5G"
    description: 测试文件大小
    
  - name: IODEPTH
    type: string
    default: "32"
    description: IO 深度
    
  - name: FILENAME
    type: string
    default: "/dev/vdb"
    description: 测试设备或文件路径

# ==================== 资源配置（引用已存在的资源文件 ID） ====================
resources:
  - resourceId: 100
    targetPath: /etc/fio.conf
    permissions: "644"
    order: 1
    
  - resourceId: 101
    targetPath: /opt/fio-patterns.dat
    permissions: "644"
    order: 2

# ==================== 步骤配置 ====================
steps:
  # 步骤 1：准备环境
  prepare:
    displayName: 准备环境
    script: scripts/prepare.sh
    dependsOn: []
    params:
      - name: CHECK_DISK
        defaultValue: "true"
        description: 检查磁盘是否存在
    resultParser: false
    resultCollector: false
    startupProbe: null
    fileCollectEnabled: false
    fileCollects: []
    resources: []
    
  # 步骤 2：执行 FIO 测试
  run_test:
    displayName: 执行 FIO 测试
    script: main.sh
    dependsOn:
      - prepare
    params:
      - name: TEST_MODE
        defaultValue: "randrw"
        description: 测试模式（read/write/randrw）
    resultParser: true
    resultCollector: true
    startupProbe: null
    parseRule:
      parserType: builtin
      builtinFormat: json
      inputSource: stdout
      filePattern: ""
      scriptSource: inline
      scriptContent: ""
      scriptLanguage: python
    fileCollectEnabled: true
    fileCollects:
      - name: FIO 结果 JSON
        path: /tmp/fio_results.json
        type: file
        required: true
        
      - name: FIO 日志目录
        path: /tmp/fio_logs
        type: directory
        required: false
        
      - name: 错误日志
        path: /tmp/fio_error.log
        type: pattern
        required: false
    resources:
      - resourceId: 102
        targetPath: /tmp/custom_pattern.dat
        permissions: "644"
        order: 1


### 字段说明

#### 基本信息

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | string | ✅ | 脚本名称（唯一） |
| `description` | string | ❌ | 脚本描述 |
| `type` | string | ❌ | 脚本类型：shell / python |
| `category` | string | ❌ | 测试分类 |
| `timeout` | integer | ❌ | 默认超时时间（秒） |

#### 参数配置

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | string | ✅ | 参数名称（环境变量名） |
| `type` | string | ❌ | 参数类型：string / number |
| `default` | any | ❌ | 默认值 |
| `description` | string | ❌ | 参数描述 |

#### 步骤配置

`steps` 字段支持两种格式：**对象格式**（推荐）和 **数组格式**（兼容）。

#### 格式一：对象格式（推荐）

```yaml
steps:
  prepare:
    displayName: 准备环境
    script: scripts/prepare.sh
    dependsOn: []
    
  run_test:
    displayName: 执行测试
    script: main.sh
    dependsOn:
      - prepare
```

#### 格式二：数组格式（兼容）

如果用户习惯数组格式，也支持自动转换：

```yaml
steps:
  - name: prepare
    displayName: 准备环境
    script: scripts/prepare.sh
    dependsOn: []
    
  - name: run_test
    displayName: 执行测试
    script: main.sh
    dependsOn:
      - prepare
```

> 💡 **说明**：
> - 对象格式是推荐的标准格式，步骤名称作为 key，更清晰
> - 数组格式会被自动转换为对象格式
> - 如果数组格式的步骤没有 `name` 字段，会自动生成 `step_1`、`step_2` 等名称
> - 两种格式在保存时都会转换为对象格式

#### 步骤字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| `name` | string | 步骤名称（数组格式必填，对象格式作为 key） |
| `displayName` | string | 步骤显示名称 |
| `script` | string | 要执行的脚本文件路径 |
| `dependsOn` | string[] | 依赖的步骤列表 |
| `params` | array | 步骤参数定义 |
| `resultParser` | boolean | 是否启用结果解析 |
| `resultCollector` | boolean | 是否启用结果收集 |
| `startupProbe` | object | 启动探测配置 |
| `parseRule` | object | 解析规则配置 |
| `fileCollectEnabled` | boolean | 是否启用文件收集 |
| `fileCollects` | array | 文件收集配置 |
| `resources` | array | 步骤专属资源 |

#### 文件收集配置 (fileCollects)

```yaml
fileCollectEnabled: true
fileCollects:
  - name: FIO 结果 JSON
    path: /tmp/fio_results.json
    type: file           # file | directory | pattern
    required: true
    
  - name: FIO 日志目录
    path: /tmp/fio_logs
    type: directory
    required: false
    
  - name: 错误日志
    path: /tmp/fio_error.log
    type: pattern        # 支持通配符
    required: false
```

**字段说明**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `name` | string | 收集文件的名称（用于展示） |
| `path` | string | 文件路径或模式 |
| `type` | string | 类型：`file`（单文件）\|`directory`（目录）\|`pattern`（通配符） |
| `required` | boolean | 是否必需，失败是否影响任务 |

**收集时机**：
- 步骤执行完成后自动收集
- 收集的文件可以在任务详情页查看和下载

#### 资源配置 (resources)

**脚本级资源配置**：

```yaml
# 在脚本根级别
resources:
  - resourceId: 100
    targetPath: /etc/config.conf
    permissions: "644"
    order: 1
```

**步骤级资源配置**：

```yaml
steps:
  run_test:
    displayName: 执行测试
    script: main.sh
    resources:
      - resourceId: 102
        targetPath: /tmp/custom_pattern.dat
        permissions: "644"
        order: 1
```

**字段说明**：

| 字段 | 类型 | 说明 |
|------|------|------|
| `resourceId` | integer | 平台资源文件 ID（必填） |
| `targetPath` | string | 目标路径（默认：/tmp） |
| `permissions` | string | 文件权限（默认：644） |
| `order` | integer | 上传顺序（默认：0） |

**工作流程**：
1. 上传脚本包时，平台解析 `resources` 配置
2. 验证 `resourceId` 对应的资源文件是否存在
3. 创建脚本与资源的关联关系
4. 任务执行时，资源文件自动上传到服务器的指定路径

#### 启动探测 (startupProbe)

```yaml
startupProbe:
  type: tcp                  # tcp | http | command
  port: 22                   # 端口号（tcp/http）
  url: /health               # URL 路径（http）
  command: "pgrep nginx"     # 自定义命令（command）
  timeoutSeconds: 60         # 超时时间
```

#### 解析规则 (parseRule)

```yaml
parseRule:
  parserType: builtin        # builtin | script
  builtinFormat: json        # json | key-value
  inputSource: stdout        # stdout | file
  filePattern: ""            # 文件路径模式
  scriptSource: inline       # inline | file
  scriptContent: ""          # 内联脚本内容
  scriptLanguage: python     # 脚本语言
```

#### 输出配置（已废弃）

> ⚠️ **注意**：`outputConfig` 字段**已废弃**，不再使用。
>
> **替代方案**：使用步骤级的 `fileCollectEnabled` 和 `fileCollects` 配置。

**废弃原因**：
- 功能已迁移到步骤级别，更灵活
- 支持每个步骤独立配置文件收集
- 更好的控制和错误处理

**迁移示例**：

```yaml
# ❌ 旧方式（已废弃）
outputConfig:
  collectEnabled: true
  collectRules:
    - pattern: "/tmp/result_*.json"
      description: "结果文件"

# ✅ 新方式（推荐）
steps:
  run_test:
    displayName: 执行测试
    script: main.sh
    fileCollectEnabled: true
    fileCollects:
      - name: 结果文件
        path: /tmp/result.json
        type: file
        required: true
      - name: 所有结果
        path: /tmp/result_*.json
        type: pattern
        required: false
```

---

## 使用方式

### 1. 创建新脚本

**方式一：上传包含 autotest.yaml 的脚本包**

```
脚本包结构：
├── autotest.yaml     # 配置文件
├── main.sh           # 主脚本
├── lib/              # 依赖文件
│   └── utils.sh
└── README.md
```

平台会自动解析 autotest.yaml 并应用配置。

**方式二：上传脚本包后手动配置**

1. 上传脚本包（不含 autotest.yaml）
2. 在前端界面配置参数和步骤
3. 保存后自动生成 autotest.yaml

### 2. 更新脚本配置

在前端修改脚本配置后，autotest.yaml 会自动同步更新。

### 3. 导出脚本包

下载脚本包时，包含最新的 autotest.yaml 文件，可用于：
- 版本控制
- 脚本迁移
- 配置备份

---

## 实现细节

### 架构设计

```
┌─────────────────────────────────────────────────────────────┐
│                      ScriptController                        │
│  - createScript() 上传时解析 YAML                            │
│  - updateScript() 更新时同步 YAML                            │
└───────────────────────────┬─────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                    ScriptConfigService                       │
│  - parseConfig() 解析 YAML 文件                              │
│  - saveConfig() 保存 YAML 文件                               │
│  - applyConfigToScript() 应用配置到实体                       │
│  - syncConfig() 同步更新 YAML                                │
│  - generateConfigFromDatabase() 从数据库生成配置              │
└───────────────────────────┬─────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                      ScriptConfig                            │
│  - name, description, type, category, timeout               │
│  - parameters: List<ParameterConfig>                        │
│  - steps: Map<String, Object>                               │
│  - outputConfig: Map<String, Object>                        │
│  - resources: List<ResourceConfig>                          │
└─────────────────────────────────────────────────────────────┘
```

### 数据库映射

| YAML 字段 | 数据库字段 | 表 |
|-----------|-----------|-----|
| name | name | scripts |
| description | description | scripts |
| type | script_type | scripts |
| category | test_category | scripts |
| timeout | default_timeout | scripts |
| parameters | parameters | script_versions |
| steps | steps | script_versions |
| outputConfig | output_config | script_versions |

### 核心代码位置

| 文件 | 说明 |
|------|------|
| `backend/src/main/java/com/autotest/config/ScriptConfig.java` | 配置实体类 |
| `backend/src/main/java/com/autotest/service/ScriptConfigService.java` | 配置服务类 |
| `backend/src/main/java/com/autotest/controller/ScriptController.java` | 控制器集成 |

---

## API 接口

### 创建脚本（支持 autotest.yaml）

```http
POST /api/v1/scripts
Content-Type: application/json

{
  "name": "sol-network-config",
  "tempFilePath": "/tmp/upload/xxx",
  "fileList": [...]
}
```

如果 tempFilePath 目录包含 autotest.yaml，系统会自动解析并应用配置。

### 更新脚本（自动同步 YAML）

```http
PUT /api/v1/scripts/{id}
Content-Type: application/json

{
  "description": "更新后的描述",
  "parameters": [...],
  "steps": {...}
}
```

更新后 autotest.yaml 会自动同步。

---

## 测试方法

### 1. 准备测试脚本包

创建目录结构：
```
test-script/
├── autotest.yaml
└── main.sh
```

autotest.yaml 内容：
```yaml
name: test-script
description: 测试脚本
type: shell
category: cpu
timeout: 300

parameters:
  - name: TEST_PARAM
    type: string
    default: "hello"
    description: 测试参数

steps:
  step_1:
    displayName: 执行测试
    script: main.sh
    dependsOn: []
```

main.sh 内容：
```bash
#!/bin/bash
echo "TEST_PARAM = $TEST_PARAM"
echo "Hello from test script!"
```

### 2. 打包并上传

```bash
cd test-script
zip -r ../test-script.zip .
```

通过前端上传 test-script.zip。

### 3. 验证

1. 检查脚本名称、描述是否正确
2. 检查参数配置是否应用
3. 检查步骤配置是否正确
4. 检查脚本目录下是否有 autotest.yaml

---

## 常见问题

### Q: YAML 文件名必须是 autotest.yaml 吗？
A: 是的，固定为 `autotest.yaml`。

### Q: 如果不提供 autotest.yaml 会怎样？
A: 系统会使用默认配置，并在保存后自动生成 autotest.yaml。

### Q: 修改配置后 YAML 文件会自动更新吗？
A: 是的，通过 API 更新脚本配置后会自动同步到 autotest.yaml。

### Q: YAML 支持中文吗？
A: 支持，UTF-8 编码。

### Q: 参数类型支持哪些？
A: 目前支持 `string` 。

---

## 版本历史

| 版本 | 日期 | 说明 |
|------|------|------|
| 1.0.0 | 2026-04-08 | 初始实现 |

---

## 相关文档

- [脚本管理 API](./api-scripts.md)
- [步骤配置说明](./steps-configuration.md)
- [参数配置说明](./parameters-configuration.md)

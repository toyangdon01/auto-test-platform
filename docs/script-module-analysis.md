# 脚本模块分析报告

## 一、当前功能模块

### 1. 基本信息配置
| 字段 | 说明 | 是否使用 |
|------|------|---------|
| name | 脚本名称 | ✅ 使用中 |
| description | 脚本描述 | ✅ 使用中 |
| testCategory | 测试类型 | ✅ 使用中 |

### 2. 文件上传
| 字段 | 说明 | 是否使用 |
|------|------|---------|
| fileList | 文件列表 | ✅ 使用中 |
| storagePath | 存储路径 | ✅ 使用中 |
| entryFile | 入口文件 | ✅ 使用中 |

### 3. 参数配置 (parameters)
| 字段 | 说明 | 是否使用 |
|------|------|---------|
| name | 参数名称 | ✅ 使用中 |
| type | 参数类型 (string/number/boolean/select) | ✅ 使用中 |
| default | 默认值 | ✅ 使用中 |
| description | 参数描述 | ✅ 使用中 |
| required | 是否必填 | ✅ 使用中 |
| options | 选项列表 (select 类型) | ✅ 使用中 |

### 4. 执行步骤配置 (steps)
| 字段 | 说明 | 是否使用 |
|------|------|---------|
| name | 步骤标识 | ✅ 使用中 |
| displayName | 步骤名称 | ✅ 使用中 |
| script | 执行脚本 | ✅ 使用中 |
| dependsOn | 依赖步骤 | ✅ 使用中 |
| startupProbe | 启动探测 | ✅ 使用中 |
| resultCollector | 是否收集结果 | ✅ 使用中 |
| params | 步骤参数 | ✅ 使用中 |
| isLocal | 本地执行 | ✅ 使用中 |
| parseRule | 解析规则 | ✅ 使用中 |

### 5. 共享资源 (resources)
| 字段 | 说明 | 是否使用 |
|------|------|---------|
| scriptId | 脚本ID | ✅ 使用中 |
| resourceFileId | 资源文件ID | ✅ 使用中 |
| targetPath | 目标路径 | ✅ 使用中 |

---

## 二、废弃字段分析

### Script 表废弃字段

| 字段 | 说明 | 原因 | 建议 |
|------|------|------|------|
| lifecycleMode | 生命周期模式 | 功能在 steps 中配置 | ⚠️ 保留兼容，不再使用 |
| hasDeploy | 是否包含部署阶段 | 由 steps 配置决定 | ❌ 可删除 |
| hasCleanup | 是否包含卸载阶段 | 由 steps 配置决定 | ❌ 可删除 |
| deployEntry | 部署入口文件 | 不再使用 | ❌ 可删除 |
| cleanupEntry | 卸载入口文件 | 不再使用 | ❌ 可删除 |
| defaultTimeout | 默认超时 | 任务级别配置 | ⚠️ 可保留作为默认值 |
| defaultRetry | 默认重试次数 | 未实现 | ❌ 可删除 |
| isBuiltin | 是否内置 | 系统预置脚本标识 | ✅ 保留 |

### ScriptVersion 表废弃字段

| 字段 | 说明 | 原因 | 建议 |
|------|------|------|------|
| lifecycleMode | 同上 | 功能在 steps 中 | ⚠️ 保留兼容 |
| hasDeploy | 同上 | 由 steps 决定 | ❌ 可删除 |
| hasCleanup | 同上 | 由 steps 决定 | ❌ 可删除 |
| deployEntry | 同上 | 不再使用 | ❌ 可删除 |
| cleanupEntry | 同上 | 不再使用 | ❌ 可删除 |
| content | 脚本内容 | 文件存储替代 | ❌ 可删除 |
| checksum | 文件校验和 | 未使用 | ⚠️ 可保留用于验证 |

---

## 三、代码清理建议

### 3.1 可删除的实体字段

**Script.java:**
```java
// 可删除
private Boolean hasDeploy;
private Boolean hasCleanup;
private String deployEntry;
private String cleanupEntry;
private Integer defaultRetry;

// 非持久化字段可删除
private String runContent;
private String deployContent;
private String cleanupContent;
```

**ScriptVersion.java:**
```java
// 可删除
private String lifecycleMode;
private Boolean hasDeploy;
private Boolean hasCleanup;
private String deployEntry;
private String cleanupEntry;
private String content;
```

### 3.2 可删除的数据库字段

```sql
-- scripts 表
ALTER TABLE scripts DROP COLUMN IF EXISTS has_deploy;
ALTER TABLE scripts DROP COLUMN IF EXISTS has_cleanup;
ALTER TABLE scripts DROP COLUMN IF EXISTS deploy_entry;
ALTER TABLE scripts DROP COLUMN IF EXISTS cleanup_entry;
ALTER TABLE scripts DROP COLUMN IF EXISTS default_retry;

-- script_versions 表
ALTER TABLE script_versions DROP COLUMN IF EXISTS lifecycle_mode;
ALTER TABLE script_versions DROP COLUMN IF EXISTS has_deploy;
ALTER TABLE script_versions DROP COLUMN IF EXISTS has_cleanup;
ALTER TABLE script_versions DROP COLUMN IF EXISTS deploy_entry;
ALTER TABLE script_versions DROP COLUMN IF EXISTS cleanup_entry;
ALTER TABLE script_versions DROP COLUMN IF EXISTS content;
```

---

## 四、脚本模块使用说明

### 4.1 创建脚本流程

#### 步骤 1：基本信息

| 参数 | 必填 | 说明 | 示例 |
|------|------|------|------|
| 脚本名称 | ✅ | 唯一标识，建议英文+数字 | `mysql_stress_test` |
| 测试类型 | ✅ | 选择测试类型 | CPU测试、内存测试、磁盘测试、网络测试、综合测试 |
| 脚本描述 | ❌ | 功能说明 | MySQL 压力测试脚本 |

#### 步骤 2：上传文件

| 支持格式 | 说明 |
|---------|------|
| `.sh` | Shell 脚本 |
| `.py` | Python 脚本 |
| `.zip` | 压缩包（自动解压） |
| `.tar.gz` | 压缩包（自动解压） |

**操作说明：**
1. 拖拽或点击上传
2. 压缩包会自动解压，保持目录结构
3. 可在线编辑文本文件
4. 可删除不需要的文件

#### 步骤 3：参数配置

| 参数属性 | 说明 |
|---------|------|
| 参数名 | 环境变量名，如 `DURATION` |
| 类型 | string / number / boolean / select |
| 默认值 | 未填写时使用的默认值 |
| 描述 | 参数用途说明 |

**示例参数：**
```
参数名: DURATION
类型: number
默认值: 60
描述: 测试持续时间（秒）
```

#### 步骤 4：执行计划

**步骤配置项：**

| 配置项 | 说明 |
|--------|------|
| 步骤名称 | 显示名称，如"部署服务" |
| 执行脚本 | 从文件列表选择入口脚本 |
| 依赖步骤 | 选择需要先完成的步骤 |
| 启动探测 | 配置服务就绪检测（TCP/HTTP） |
| 结果解析 | 启用后解析输出结果 |

**启动探测配置：**
| 类型 | 参数 |
|------|------|
| TCP | 端口号 |
| HTTP | 端口号 + 路径 |

**结果解析配置：**
| 解析方式 | 说明 |
|---------|------|
| 内置规则 | Key-Value 或 JSON 格式 |
| 自定义脚本 | Python/Shell 解析脚本 |

**本地执行：**
- 勾选后脚本在平台服务器本地执行
- 通过参数传入目标服务器连接信息
- 适用于 SOL/SSH 等需要本地发起连接的场景

#### 步骤 5：共享资源

配置所有步骤共用的资源文件（如配置文件、证书）。

---

### 4.2 参数类型详解

| 类型 | 输入方式 | 示例 |
|------|---------|------|
| string | 文本框 | 任意字符串 |
| number | 数字框 | 60, 100, 3600 |
| boolean | 开关 | true / false |
| select | 下拉选择 | 从预定义选项选择 |

**Select 类型扩展配置：**
```json
{
  "type": "select",
  "options": ["option1", "option2", "option3"]
}
```

---

### 4.3 步骤依赖关系

- 支持多步骤 DAG 执行
- 步骤可配置多个依赖
- 依赖步骤完成后才执行当前步骤
- 支持并行执行无依赖的步骤

---

### 4.4 本地执行说明

**适用场景：**
- 需要 SOL 连接目标服务器
- 脚本本地发起 SSH 连接
- 需要访问本地工具（如 ipmitool）

**参数传递：**
- 通过环境变量传递
- 脚本通过 `os.environ.get('PARAM_NAME')` 获取

**示例：**
```python
import os

bmc_ip = os.environ.get('TARGET_BMC_IP')
bmc_user = os.environ.get('TARGET_BMC_USER')
```

---

## 五、数据结构参考

### 5.1 步骤配置示例

```json
{
  "deploy": {
    "displayName": "部署服务",
    "script": "deploy.sh",
    "dependsOn": [],
    "startupProbe": {
      "type": "tcp",
      "port": 8080
    },
    "resultCollector": false,
    "isLocal": false,
    "params": [
      {"name": "SERVICE_PORT", "default": "8080"}
    ]
  },
  "test": {
    "displayName": "执行测试",
    "script": "main.sh",
    "dependsOn": ["deploy"],
    "resultCollector": true,
    "parseRule": {
      "parserType": "builtin",
      "builtinFormat": "key_value"
    }
  }
}
```

### 5.2 参数配置示例

```json
[
  {
    "name": "DURATION",
    "displayName": "测试时长",
    "type": "number",
    "default": 60,
    "required": true,
    "description": "测试持续时间（秒）"
  },
  {
    "name": "CONCURRENCY",
    "displayName": "并发数",
    "type": "select",
    "default": "1",
    "options": ["1", "2", "4", "8", "16"],
    "description": "并发线程数"
  }
]
```

---

## 六、建议的代码清理优先级

### 高优先级（立即清理）
1. `hasDeploy`, `hasCleanup` 字段 - 逻辑已在 steps 中
2. `deployEntry`, `cleanupEntry` 字段 - 不再使用
3. `defaultRetry` 字段 - 未实现

### 中优先级（保留兼容）
1. `lifecycleMode` - 保留默认值 'simple'
2. `defaultTimeout` - 可作为默认值

### 低优先级（暂不清理）
1. `isBuiltin` - 系统脚本标识
2. `checksum` - 可用于文件验证
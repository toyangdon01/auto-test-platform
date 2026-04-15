# 自动化测试平台脚本开发指南

> 本文档面向需要编写可在自动化测试平台上运行的测试脚本的开发者，以及需要理解平台脚本机制的 AI 大模型工具。

---

## 目录

1. [概述](#1-概述)
2. [脚本包结构](#2-脚本包结构)
3. [autotest.yaml 配置详解](#3-autotestyaml-配置详解)
4. [脚本编写规范](#4-脚本编写规范)
5. [参数传递机制](#5-参数传递机制)
6. [结果输出格式](#6-结果输出格式)
7. [多步骤测试场景](#7-多步骤测试场景)
8. [最佳实践](#8-最佳实践)
9. [完整示例：iperf2 网络带宽测试](#9-完整示例iperf2-网络带宽测试)
10. [AI 工具集成指南](#10-ai 工具集成指南)

---

## 1. 概述

### 1.1 平台定位

自动化测试管理平台是一个用于管理和执行各类测试脚本的系统，支持：

- **脚本版本管理**：维护脚本的不同版本
- **参数化配置**：通过配置文件定义可调参数
- **多步骤编排**：支持复杂的测试流程
- **结果解析**：自动解析测试输出，标准化结果
- **分布式执行**：在多台服务器上并行执行测试
- **资源管理**：大文件依赖统一管理，自动分发

### 1.2 核心概念

| 概念 | 说明 |
|------|------|
| **脚本包** | 包含测试脚本、配置文件、依赖资源的完整目录 |
| **脚本** | 可执行的测试程序（Shell、Python 等） |
| **步骤** | 测试流程中的一个执行单元，可以有依赖关系 |
| **任务** | 脚本的一次执行实例，分配到特定服务器 |
| **资源文件** | 独立管理的大文件，执行时自动上传到服务器 |

---

## 2. 脚本包结构

### 2.1 目录结构

```
my_test_script/
├── autotest.yaml        # 必需：脚本配置文件
├── main.sh              # 主执行脚本
├── lib/                 # 依赖库目录
│   └── common.sh
├── data/                # 数据文件目录（小文件）
│   └── test_data.txt
├── README.md            # 使用说明
└── resources/           # 资源文件（可选，大文件建议用资源模块）
    └── config.json
```

### 2.2 文件说明

| 文件 | 必需 | 说明 |
|------|------|------|
| `autotest.yaml` | ✅ | 脚本配置文件，定义元数据、参数、步骤 |
| `main.sh` / `main.py` | ✅ | 主执行脚本入口 |
| `lib/` | ❌ | 公共函数库，可被主脚本引用 |
| `README.md` | ❌ | 使用说明文档 |
| 其他文件 | ❌ | 测试数据、配置文件、依赖包等 |

---

## 3. autotest.yaml 配置详解

### 3.1 完整配置模板

```yaml
# ========== 基本信息 ==========
name: my_script                    # 脚本唯一标识
description: 脚本功能描述          # 详细说明
type: shell                        # 脚本类型：shell | python
category: network                  # 测试分类
timeout: 600                       # 默认超时时间（秒）

# ========== 共享参数定义 ==========
parameters:
  - name: PARAM_NAME
    type: string
    default: "default_value"
    description: 参数说明

# ========== 资源配置（可选） ==========
resources:
  - resourceId: 100
    targetPath: /etc/config.conf
    permissions: "644"
    order: 1

# ========== 执行步骤定义 ==========
steps:
  - name: step_1
    displayName: 步骤显示名称
    script: main.sh
    dependsOn: []
    params:
      - name: STEP_PARAM
        defaultValue: "value"
        description: 说明
    resultParser: true
    resultCollector: true
    parseRule:
      parserType: builtin
      builtinFormat: json
      inputSource: stdout
```

### 3.2 配置字段详解

#### 3.2.1 基本信息

| 字段 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `name` | string | ✅ | 脚本唯一标识 |
| `description` | string | ❌ | 脚本功能描述 |
| `type` | string | ✅ | 脚本类型：`shell` 或 `python` |
| `category` | string | ✅ | 测试分类 |
| `timeout` | integer | ❌ | 默认超时时间（秒） |

**测试分类（category）可选值**：
- `cpu` - CPU 性能测试
- `memory` - 内存性能测试
- `disk` - 磁盘 I/O 测试
- `network` - 网络性能测试
- `database` - 数据库测试
- `middleware` - 中间件测试
- `java` - Java 应用测试
- `storage` - 存储系统测试
- `bigdata` - 大数据组件测试

#### 3.2.2 共享参数（parameters）

```yaml
parameters:
  - name: SERVER_PORT
    type: string
    default: "5001"
    description: 服务端口
```

#### 3.2.3 步骤定义（steps）

```yaml
steps:
  - name: step_1
    displayName: 步骤显示名称
    script: main.sh
    dependsOn: []
    params:
      - name: MODE
        defaultValue: "server"
    resultParser: false
    resultCollector: false
    
  - name: step_2
    displayName: 执行测试
    script: main.sh
    dependsOn: [step_1]
    resultParser: true
    resultCollector: true
    parseRule:
      parserType: builtin
      builtinFormat: json
      inputSource: stdout
```

**resultParser 和 resultCollector 说明**：

| resultParser | resultCollector | 行为 |
|--------------|-----------------|------|
| false | false | 不解析、不收集结果 |
| false | true | 收集原始输出，不解析 |
| true | false | 解析结果，自动设 resultCollector=true |
| true | true | 解析并收集结果 ✅ 推荐 |

---

## 4. 脚本编写规范

### 4.1 Shell 脚本模板

```bash
#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/lib/common.sh"

# 参数默认值（平台通过环境变量传递）
SERVER_PORT="${SERVER_PORT:-5001}"
DURATION="${DURATION:-10}"

run_test() {
    log_info "开始测试..."
    
    # 执行测试
    # ...
    
    # 输出 JSON 结果
    cat << EOF
{
  "testName": "my_test",
  "status": "pass",
  "metric1": 123.45
}
EOF
    
    log_success "测试完成"
}

main "$@"
```

### 4.2 Python 脚本模板

```python
#!/usr/bin/env python3
import os
import json

SERVER_PORT = os.environ.get('SERVER_PORT', '5001')

def run_test():
    print(f"开始测试...")
    
    result = {
        "testName": "my_test",
        "status": "pass",
        "metric1": 123.45
    }
    print(json.dumps(result, indent=2))

if __name__ == "__main__":
    run_test()
```

---

## 5. 参数传递机制

### 5.1 参数优先级

```
步骤参数 > 共享参数 > 默认值
```

### 5.2 内置参数

平台自动注入以下内置参数：

| 参数名 | 说明 |
|--------|------|
| `TASK_ID` | 任务 ID |
| `SCRIPT_ID` | 脚本 ID |
| `TASK_NAME` | 任务名称 |
| `SCRIPT_VERSION` | 脚本版本 |

---

## 6. 结果输出格式

### 6.1 JSON 格式（推荐）

```json
{
  "testName": "iperf2",
  "status": "pass",
  "protocol": "tcp",
  "bandwidth": "1.07 Gbits/sec",
  "bandwidthValue": 1.07,
  "bandwidthUnit": "Gbits"
}
```

### 6.2 Key-Value 格式

```
testName=iperf2
status=pass
bandwidth=1.07 Gbits/sec
```

---

## 7. 多步骤测试场景

### 7.1 服务端/客户端模式

```yaml
steps:
  - name: server
    displayName: 启动服务端
    script: main.sh
    params:
      - name: MODE
        defaultValue: "server"
    resultParser: false
    resultCollector: false
    
  - name: client
    displayName: 运行客户端测试
    script: main.sh
    dependsOn: [server]
    params:
      - name: MODE
        defaultValue: "client"
    resultParser: true
    resultCollector: true
```

---

## 8. 最佳实践

### 8.1 脚本设计原则

1. **幂等性**：多次执行结果一致
2. **自包含**：脚本包包含所有依赖
3. **参数化**：关键参数通过配置传入
4. **错误处理**：完善的错误检测和提示
5. **日志输出**：关键步骤输出日志

### 8.2 命名规范

| 类型 | 规范 | 示例 |
|------|------|------|
| 脚本名称 | 小写字母+下划线 | `iperf2`, `fio_test` |
| 参数名称 | 大写字母+下划线 | `SERVER_PORT`, `DURATION` |
| 步骤名称 | 小写字母+下划线 | `run_server`, `execute_test` |

### 8.3 大文件依赖处理

当脚本依赖较大的文件（如安装包、数据集、二进制工具）时，建议使用平台的**资源模块**功能。

#### 8.3.1 资源模块优势

| 方式 | 脚本包内嵌 | 资源模块 |
|------|-----------|----------|
| 文件大小限制 | 建议小于 100MB | 最大 20GB |
| 重复存储 | 每个脚本独立存储 | MD5 去重 |
| 版本管理 | 随脚本版本 | 独立管理 |

#### 8.3.2 使用流程

**步骤 1：上传资源文件**
```bash
curl -X POST "http://host:8080/api/v1/resources/upload" \
  -F "file=@large_package.tar.gz" \
  -F "category=package"
```

**步骤 2：在 autotest.yaml 中声明资源配置**
```yaml
name: my_test
description: 使用资源文件的测试脚本
type: shell

# 资源配置（推荐使用 resourceMd5，支持跨项目共享）
resources:
  # 方式一：使用 resourceMd5（推荐，跨项目一致性更好）
  - resourceMd5: e142c2058313b4646c36fa9bb1b38493
    targetPath: /etc/config.conf
    permissions: "644"
    order: 1
    
  # 方式二：使用 resourceId（仅适用于同一项目内）
  - resourceId: 101
    targetPath: /opt/data.tar.gz
    permissions: "755"
    order: 2
    
  # 方式三：同时提供（导入时优先使用 resourceMd5 匹配）
  - resourceId: 102
    resourceMd5: abc123def456789
    targetPath: /tmp/tool.tar.gz
    permissions: "755"
    order: 3

steps:
  - name: run_test
    script: main.sh
    resultParser: true
```

**导入逻辑说明**：
- 导入脚本时，优先使用 `resourceMd5` 查找匹配的资源文件
- 如果 MD5 匹配失败，再尝试使用 `resourceId`
- 如果都失败，记录警告并跳过该资源配置

**步骤 3：创建脚本时自动应用配置**
```bash
curl -X POST "http://host:8080/api/v1/scripts" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "my_test",
    "scriptType": "shell",
    "testCategory": "network",
    "tempFilePath": "/tmp/upload/xxx"
  }'
```

平台会自动：
1. 解析 `autotest.yaml` 中的 `resources` 配置
2. 验证 `resourceId` 对应的资源文件是否存在
3. 创建脚本与资源的关联关系
4. 任务执行时自动上传资源文件到服务器的指定路径

#### 8.3.3 脚本中使用资源文件

```bash
#!/bin/bash
# 资源文件已自动上传到工作目录
CONFIG_FILE="/tmp/test_platform/task_${TASK_ID}/etc/config.conf"
DATA_FILE="/tmp/test_platform/task_${TASK_ID}/opt/data.tar.gz"

# 直接使用
if [ -f "${CONFIG_FILE}" ]; then
    source "${CONFIG_FILE}"
    log_info "配置文件已加载"
fi

if [ -f "${DATA_FILE}" ]; then
    tar -xzf "${DATA_FILE}" -C /tmp/test_data
    log_info "数据文件已解压"
fi
```

#### 8.3.4 Python 示例代码

```python
import requests

BASE_URL = "http://host:8080/api/v1"

# 1. 上传资源文件
with open('large_package.tar.gz', 'rb') as f:
    files = {'file': ('large_package.tar.gz', f)}
    response = requests.post(f"{BASE_URL}/resources/upload", files=files)
    resource_id = response.json()['data']['id']
    print(f"资源文件已上传，ID: {resource_id}")

# 2. 创建 autotest.yaml
autotest_yaml = f"""
name: my_test
description: 使用资源文件的测试脚本
type: shell

resources:
  - resourceId: {resource_id}
    targetPath: /opt/package.tar.gz
    permissions: "755"
    order: 1

steps:
  - name: run_test
    script: main.sh
    resultParser: true
"""

# 3. 打包并上传脚本
import zipfile
import io

zip_buffer = io.BytesIO()
with zipfile.ZipFile(zip_buffer, 'w', zipfile.ZIP_DEFLATED) as zip_file:
    zip_file.writestr("autotest.yaml", autotest_yaml)
    zip_file.writestr("main.sh", "#!/bin/bash\necho 'Hello'")

zip_buffer.seek(0)
files = {'file': ('script.zip', zip_buffer, 'application/zip')}
response = requests.post(f"{BASE_URL}/scripts/upload", files=files)
temp_path = response.json()['data']['tempFilePath']

# 4. 创建脚本
script_data = {
    "name": "my_test",
    "scriptType": "shell",
    "tempFilePath": temp_path
}
response = requests.post(f"{BASE_URL}/scripts", json=script_data)
script_id = response.json()['data']['script']['id']
print(f"脚本创建成功，ID: {script_id}")
```

---

## 9. 完整示例：iperf2 网络带宽测试

### 9.1 autotest.yaml

```yaml
name: iperf2
description: iperf2 网络带宽测试
type: shell
category: network
timeout: 600

parameters:
  - name: PROTOCOL
    default: "tcp"
  - name: SERVER_IP
    default: ""
  - name: SERVER_PORT
    default: "5001"
  - name: DURATION
    default: "10"

steps:
  - name: run_iperf2
    displayName: 执行 iperf2 服务端
    script: main.sh
    params:
      - name: MODE
        defaultValue: "server"
    resultParser: false
    resultCollector: false
    
  - name: step_2
    displayName: 执行 iperf2 客户端
    script: main.sh
    dependsOn: [run_iperf2]
    params:
      - name: MODE
        defaultValue: "client"
    resultParser: true
    resultCollector: true
    parseRule:
      parserType: builtin
      builtinFormat: json
      inputSource: stdout
```

---

## 10. AI 工具集成指南

本节说明 AI 大模型工具如何通过 API 直接创建和管理脚本。

### 10.1 创建脚本的完整流程

```
1. 生成 autotest.yaml 内容
   ↓
2. 打包脚本文件（zip/tar.gz）
   ↓
3. 上传脚本包到临时目录
   ↓
4. 调用创建脚本 API
   ↓
5. 获取脚本 ID 和版本信息
```

### 10.2 API 调用示例（Python）

```python
import requests
import zipfile
import io

BASE_URL = "http://host:8080/api/v1"

# ========== 步骤 1：准备 autotest.yaml ==========
autotest_yaml = """
name: ai_generated_test
description: AI 自动生成的测试脚本
type: shell
category: network
timeout: 600

parameters:
  - name: DURATION
    type: string
    default: "60"
    description: 测试时长

steps:
  - name: run_test
    displayName: 执行测试
    script: main.sh
    resultParser: true
    resultCollector: true
    parseRule:
      parserType: builtin
      builtinFormat: json
      inputSource: stdout
"""

# ========== 步骤 2：准备 main.sh ==========
main_sh = """#!/bin/bash
set -e
source "$(dirname "${BASH_SOURCE[0]}")/lib/common.sh"

DURATION="${DURATION:-60}"
log_info "开始测试，时长：${DURATION}秒"

# 执行测试逻辑
# ...

# 输出 JSON 结果
cat << EOF
{
  "testName": "ai_generated_test",
  "status": "pass",
  "duration": ${DURATION}
}
EOF

log_success "测试完成"
"""

# ========== 步骤 3：打包脚本文件 ==========
zip_buffer = io.BytesIO()
with zipfile.ZipFile(zip_buffer, 'w', zipfile.ZIP_DEFLATED) as zip_file:
    zip_file.writestr("autotest.yaml", autotest_yaml)
    zip_file.writestr("main.sh", main_sh)
    zip_file.writestr("lib/common.sh", "#!/bin/bash\n# 公共函数库")

zip_buffer.seek(0)

# ========== 步骤 4：上传脚本包 ==========
files = {'file': ('script.zip', zip_buffer, 'application/zip')}
response = requests.post(f"{BASE_URL}/scripts/upload", files=files)
upload_result = response.json()

if upload_result['code'] != 0:
    print(f"上传失败：{upload_result['message']}")
    exit(1)

temp_file_path = upload_result['data']['tempFilePath']
print(f"脚本包已上传到临时目录：{temp_file_path}")

# ========== 步骤 5：创建脚本 ==========
script_data = {
    "name": "ai_generated_test",
    "scriptType": "shell",
    "testCategory": "network",
    "tempFilePath": temp_file_path,
    "description": "AI 自动生成的测试脚本"
}

response = requests.post(f"{BASE_URL}/scripts", json=script_data)
result = response.json()

if result['code'] == 0:
    script_id = result['data']['script']['id']
    print(f"脚本创建成功！ID: {script_id}")
    
    # 检查是否有警告
    if result['data'].get('configStatus') == 'success_with_warnings':
        print("警告信息：")
        for warning in result['data'].get('warnings', []):
            print(f"  - {warning}")
    elif result['data'].get('configStatus') == 'error':
        print(f"配置错误：{result['data'].get('error')}")
        print(f"错误位置：{result['data'].get('errorLocation')}")
else:
    print(f"创建失败：{result['message']}")
```

### 10.3 创建任务并执行

```python
# ========== 步骤 6：创建任务 ==========
task_data = {
    "name": "AI 自动测试任务",
    "scriptId": script_id,
    "scriptVersion": "v1.0.0",
    "serverIds": [1, 2],  # 执行服务器 ID 列表
    "sharedParams": {
        "DURATION": "120"  # 覆盖默认值
    }
}

response = requests.post(f"{BASE_URL}/tasks", json=task_data)
task_result = response.json()

if task_result['code'] == 0:
    task_id = task_result['data']['id']
    print(f"任务创建成功！ID: {task_id}")
    
    # ========== 步骤 7：执行任务 ==========
    response = requests.post(f"{BASE_URL}/tasks/{task_id}/execute")
    print("任务已开始执行")
    
    # ========== 步骤 8：获取结果 ==========
    # 等待执行完成后查询结果
    import time
    time.sleep(10)  # 等待执行
    
    response = requests.get(f"{BASE_URL}/results?taskId={task_id}")
    results = response.json()['data']['records']
    
    for result in results:
        print(f"服务器 {result['serverId']}: {result['result']}")
        if result.get('parsedData'):
            print(f"  带宽：{result['parsedData'].get('bandwidth')}")
else:
    print(f"任务创建失败：{task_result['message']}")
```

### 10.4 常用 API 接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/scripts/upload` | POST | 上传脚本包（zip 格式） |
| `/scripts` | POST | 创建脚本 |
| `/scripts` | GET | 获取脚本列表 |
| `/scripts/{id}` | GET | 获取脚本详情 |
| `/scripts/{id}` | PUT | 更新脚本 |
| `/scripts/{id}` | DELETE | 删除脚本 |
| `/tasks` | POST | 创建任务 |
| `/tasks/{id}/execute` | POST | 执行任务 |
| `/tasks/{id}/cancel` | POST | 取消任务 |
| `/results` | GET | 获取结果列表 |
| `/resources/upload` | POST | 上传资源文件 |

### 10.5 错误处理

```python
def create_script_with_retry(script_data, max_retries=3):
    """带重试的脚本创建"""
    for attempt in range(max_retries):
        response = requests.post(f"{BASE_URL}/scripts", json=script_data)
        result = response.json()
        
        if result['code'] == 0:
            return result['data']
        
        # 可重试的错误
        if 'timeout' in result['message'].lower() and attempt < max_retries - 1:
            time.sleep(2 ** attempt)  # 指数退避
            continue
        
        # 不可重试的错误
        raise Exception(f"创建失败：{result['message']}")
    
    raise Exception("重试次数已用尽")
```

---

> 文档版本：v1.2.0  
> 最后更新：2026-04-09  
> 新增：AI 工具集成指南、修正资源模块说明

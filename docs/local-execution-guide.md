# 本地执行功能使用指南

## 一、功能概述

### 1.1 什么是本地执行？

**本地执行**是指在运行测试平台的服务器上直接执行脚本，而不是通过 SSH 远程连接到其他服务器执行。

| 执行方式 | 执行位置 | serverId | 适用场景 |
|----------|----------|----------|----------|
| 远程执行 | 目标服务器 | 正整数 (1, 2, 3...) | 直接在目标服务器执行命令 |
| 本地执行 | 平台服务器 | **-1** | 需要本地工具连接目标服务器 |

### 1.2 典型使用场景

| 场景 | 说明 |
|------|------|
| SOL 连接 | 通过 SOL (Serial Over LAN) 连接目标服务器 BMC |
| IPMI 管理 | 使用本地 ipmitool 管理远程设备 |
| 本地 SSH | 从平台发起 SSH 连接到目标服务器 |
| API 测试 | 从平台调用远程 API 接口 |
| 使用本地工具 | 依赖平台服务器上的特定工具或环境 |

### 1.3 架构说明

```
┌─────────────────────────────────────────────────────────┐
│                    平台服务器                            │
│  ┌─────────────────────────────────────────────────┐   │
│  │              测试平台后端                         │   │
│  │  ┌─────────────────────────────────────────┐    │   │
│  │  │        LocalExecutorFactory             │    │   │
│  │  │    ┌─────────────┬─────────────┐        │    │   │
│  │  │    │ Windows     │ Linux/Unix  │        │    │   │
│  │  │    │ PowerShell  │ Shell/Bash  │        │    │   │
│  │  │    └─────────────┴─────────────┘        │    │   │
│  │  └─────────────────────────────────────────┘    │   │
│  └─────────────────────────────────────────────────┘   │
│                           │                            │
│                           ▼                            │
│  ┌─────────────────────────────────────────────────┐   │
│  │              执行的脚本                          │   │
│  │   - 解析参数（目标 IP、用户名、密码）            │   │
│  │   - 连接目标服务器（SSH/SOL/IPMI）              │   │
│  │   - 执行测试操作                                │   │
│  │   - 返回结果                                    │   │
│  └─────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
                            │
                            ▼
              ┌─────────────────────────┐
              │      目标服务器          │
              └─────────────────────────┘
```

---

## 二、执行器说明

### 2.1 执行器类型

平台根据操作系统自动选择合适的执行器：

| 操作系统 | 执行器 | 说明 |
|----------|--------|------|
| Windows | PowerShellExecutor | 使用 PowerShell 执行脚本 |
| Linux/Unix | ShellExecutor | 使用 Bash 执行脚本 |

### 2.2 脚本类型支持

根据脚本文件扩展名自动选择执行方式：

| 扩展名 | 执行命令 | 说明 |
|--------|----------|------|
| `.py` | `python script.py` | Python 脚本 |
| `.ps1` | `powershell -ExecutionPolicy Bypass -File script.ps1` | PowerShell 脚本 |
| `.sh` | `bash script.sh` | Shell 脚本 |
| 其他 | `python script` | 默认使用 Python |

### 2.3 关键配置

配置文件路径：`application.yml`

```yaml
autotest:
  storage:
    scripts-path: C:/data/auto-test/scripts    # 脚本存储路径
  local-executor:
    timeout: 300                                # 执行超时时间（秒），默认 5 分钟
    powershell-path: powershell.exe             # PowerShell 路径（Windows）
    shell-path: bash                            # Shell 路径（Linux）
```

---

## 三、使用方法

### 3.1 创建任务时选择本地执行

在任务编辑页面，为步骤选择服务器时：

1. 在服务器下拉列表中选择 **「本地环境」**
2. 勾选 **「本地执行」** 复选框
3. 配置步骤参数（目标服务器 IP、认证信息等）

```
服务器选择示例：
┌─────────────────────────────────────┐
│ ▼ 选择服务器                         │
├─────────────────────────────────────┤
│ ─── 服务器 ───                       │
│   192.168.1.100                     │
│   192.168.1.101                     │
│ ─── 本地执行 ───                     │
│  ✓ 本地环境 (平台直接执行)           │
└─────────────────────────────────────┘

勾选后：
☑ 本地执行
```

### 3.2 数据库记录

本地执行的任务在数据库中的特征：

**task_servers 表**：
```sql
server_id = -1       -- 本地执行标识
is_local = true      -- 本地执行标志
```

**task_steps 表**：
```sql
server_id = -1       -- 本地执行标识
```

### 3.3 参数传递方式

参数通过**环境变量**传递给脚本：

```java
// 后端代码 (PowerShellExecutor.java)
Map<String, String> env = pb.environment();
if (params != null) {
    params.forEach((k, v) -> {
        if (v != null && !String.valueOf(v).trim().isEmpty()) {
            env.put(k, String.valueOf(v));
        }
    });
}
```

**脚本中获取参数**：

```bash
# Shell 脚本
TARGET_IP=${TARGET_IP:-"192.168.1.100"}
TARGET_USER=${TARGET_USER:-"admin"}
TARGET_PASS=${TARGET_PASS:-"password"}

echo "Connecting to $TARGET_IP..."
```

```python
# Python 脚本
import os

target_ip = os.environ.get('TARGET_IP', '192.168.1.100')
target_user = os.environ.get('TARGET_USER', 'admin')
target_pass = os.environ.get('TARGET_PASS', 'password')

print(f"Connecting to {target_ip}...")
```

```powershell
# PowerShell 脚本
$targetIp = $env:TARGET_IP
$targetUser = $env:TARGET_USER
$targetPass = $env:TARGET_PASS

Write-Host "Connecting to $targetIp..."
```

### 3.4 内置参数

本地执行时自动注入的内置参数：

| 参数名 | 说明 | 示例值 |
|--------|------|--------|
| `TASK_ID` | 任务 ID | `123` |
| `SCRIPT_ID` | 脚本 ID | `456` |
| `TASK_NAME` | 任务名称 | `MySQL压力测试` |
| `SCRIPT_VERSION` | 脚本版本 | `v1.0` |

---

## 四、执行流程

### 4.1 完整执行流程

```
1. 任务创建
   └── 用户选择"本地环境"
   └── serverId 设为 -1
   └── isLocal = true

2. 任务执行
   └── TaskExecutionService.executeStepOnServer()
   └── 检测 isLocal = true
   └── 调用 executeStepLocally()

3. 本地执行
   └── LocalExecutorFactory.getDefaultExecutor()
   └── 根据操作系统获取执行器
   └── 设置环境变量
   └── 启动进程执行脚本

4. 结果收集
   └── 收集标准输出和错误输出
   └── 解析结果（如启用）
   └── 更新步骤状态
```

### 4.2 代码调用链

```java
// TaskExecutionService.java
private boolean executeStepOnServer(..., boolean isLocal) {
    if (isLocal) {
        return executeStepLocally(context, task, scriptVersion, stepConfig, taskStep, taskServer);
    }
    // ... 远程执行逻辑
}

private boolean executeStepLocally(...) {
    LocalExecutor executor = localExecutorFactory.getDefaultExecutor();
    
    // 构建参数
    Map<String, Object> params = new HashMap<>();
    params.put("TASK_ID", task.getId());
    params.putAll(task.getSharedParams());
    // ...
    
    // 执行脚本
    ExecutionResult result = executor.execute(scriptVersion, scriptFile, params, taskServer, server, logConsumer);
    
    return result.isSuccess();
}
```

---

## 五、脚本开发指南

### 5.1 本地执行脚本示例

#### Python 脚本（SOL 连接示例）

```python
#!/usr/bin/env python3
"""
SOL 连接测试脚本
通过 SOL (Serial Over LAN) 连接目标服务器 BMC
"""
import os
import subprocess
import sys
import time

def main():
    # 获取参数
    target_ip = os.environ.get('TARGET_BMC_IP')
    target_user = os.environ.get('TARGET_BMC_USER', 'admin')
    target_pass = os.environ.get('TARGET_BMC_PASS')
    command = os.environ.get('TARGET_COMMAND', 'ipmitool shell')
    
    if not target_ip or not target_pass:
        print("ERROR: 缺少必要参数 TARGET_BMC_IP 或 TARGET_BMC_PASS")
        sys.exit(1)
    
    print(f"[INFO] 连接目标 BMC: {target_ip}")
    print(f"[INFO] 执行命令: {command}")
    
    # 使用 ipmitool 通过 SOL 连接
    sol_cmd = [
        'ipmitool',
        '-I', 'lanplus',
        '-H', target_ip,
        '-U', target_user,
        '-P', target_pass,
        'sol', 'activate'
    ]
    
    try:
        result = subprocess.run(sol_cmd, capture_output=True, text=True, timeout=60)
        print(result.stdout)
        
        if result.returncode == 0:
            print("RESULT: status=success")
            print("RESULT: connected=true")
        else:
            print(f"ERROR: {result.stderr}")
            sys.exit(1)
            
    except subprocess.TimeoutExpired:
        print("ERROR: 连接超时")
        sys.exit(1)
    except Exception as e:
        print(f"ERROR: {str(e)}")
        sys.exit(1)

if __name__ == '__main__':
    main()
```

#### Shell 脚本（本地 SSH 示例）

```bash
#!/bin/bash
# 本地 SSH 连接测试脚本
# 从平台服务器发起 SSH 连接到目标服务器

set -e

# 获取参数
TARGET_HOST=${TARGET_HOST:-""}
TARGET_USER=${TARGET_USER:-"root"}
TARGET_PORT=${TARGET_PORT:-"22"}
SSH_KEY=${SSH_KEY:-""}
COMMAND=${COMMAND:-"hostname"}

# 参数校验
if [ -z "$TARGET_HOST" ]; then
    echo "ERROR: TARGET_HOST 参数未设置"
    exit 1
fi

echo "[INFO] 目标主机: $TARGET_HOST:$TARGET_PORT"
echo "[INFO] 执行命令: $COMMAND"

# 执行 SSH 命令
if [ -n "$SSH_KEY" ]; then
    # 使用密钥认证
    ssh -i "$SSH_KEY" -p "$TARGET_PORT" -o StrictHostKeyChecking=no "$TARGET_USER@$TARGET_HOST" "$COMMAND"
else
    # 使用密码认证（需要 sshpass）
    if ! command -v sshpass &> /dev/null; then
        echo "ERROR: sshpass 未安装"
        exit 1
    fi
    TARGET_PASS=${TARGET_PASS:-""}
    if [ -z "$TARGET_PASS" ]; then
        echo "ERROR: TARGET_PASS 参数未设置"
        exit 1
    fi
    sshpass -p "$TARGET_PASS" ssh -p "$TARGET_PORT" -o StrictHostKeyChecking=no "$TARGET_USER@$TARGET_HOST" "$COMMAND"
fi

echo "RESULT: status=success"
```

#### PowerShell 脚本（Windows 示例）

```powershell
# Windows 本地执行脚本示例
# 通过 PowerShell 连接远程服务器

param()

# 获取环境变量参数
$targetIp = $env:TARGET_IP
$targetUser = $env:TARGET_USER
$targetPass = $env:TARGET_PASS

if (-not $targetIp -or -not $targetPass) {
    Write-Host "ERROR: 缺少必要参数"
    exit 1
}

Write-Host "[INFO] 连接目标: $targetIp"

# 使用 PowerShell 远程会话
$password = ConvertTo-SecureString $targetPass -AsPlainText -Force
$credential = New-Object System.Management.Automation.PSCredential($targetUser, $password)

try {
    $session = New-PSSession -ComputerName $targetIp -Credential $credential -ErrorAction Stop
    
    Invoke-Command -Session $session -ScriptBlock {
        hostname
        Get-Service | Where-Object { $_.Status -eq 'Running' } | Select-Object -First 5
    }
    
    Remove-PSSession -Session $session
    
    Write-Host "RESULT: status=success"
} catch {
    Write-Host "ERROR: $($_.Exception.Message)"
    exit 1
}
```

### 5.2 结果输出规范

为了便于平台解析结果，建议使用以下格式：

**Key-Value 格式**：
```bash
echo "RESULT: key=value"
echo "RESULT: total_requests=10000"
echo "RESULT: success_rate=99.5"
echo "RESULT: avg_latency=50"
```

**JSON 格式**：
```bash
echo "RESULT_JSON: {\"status\":\"success\",\"value\":100}"
```

---

## 六、常见问题

### Q1: 本地执行和远程执行如何选择？

| 场景 | 推荐方式 | 原因 |
|------|----------|------|
| 在目标服务器执行命令 | 远程执行 | 直接、简单 |
| 需要使用本地工具（ipmitool 等） | 本地执行 | 工具依赖本地环境 |
| SOL 连接 BMC | 本地执行 | SOL 需要本地发起 |
| 批量 SSH 操作 | 本地执行 | 从平台并发连接多台服务器 |
| 使用本地配置文件/证书 | 本地执行 | 资源在本地可用 |

### Q2: 本地执行脚本找不到命令？

**问题**：脚本执行报错 `command not found`

**原因**：本地执行时环境变量可能与登录环境不同

**解决方案**：
```bash
# 方案 1：使用完整路径
/opt/tool/bin/mycommand

# 方案 2：在脚本中设置 PATH
export PATH=$PATH:/opt/tool/bin

# 方案 3：在平台配置中添加环境变量
# application.yml 或任务参数中配置
```

### Q3: 本地执行超时怎么办？

**默认超时**：300 秒（5 分钟）

**调整方法**：
```yaml
# application.yml
autotest:
  local-executor:
    timeout: 600  # 改为 10 分钟
```

### Q4: 如何在本地执行中使用敏感信息？

**推荐方案**：
1. 使用平台参数管理功能存储密码等敏感信息
2. 参数通过环境变量传递给脚本，不会明文存储
3. 脚本执行完毕后，环境变量自动清除

```bash
# 脚本中使用
PASSWORD=${TARGET_PASS}
# 不要打印密码
echo "[INFO] 连接中..."
```

### Q5: 本地执行的日志在哪里？

日志存储位置：
- **实时日志**：通过 WebSocket 推送到前端显示
- **持久化日志**：存储在 `task_steps.output` 字段

### Q6: 如何调试本地执行脚本？

**推荐方法**：
1. 在脚本中添加详细日志
2. 检查 `/tmp/test_platform/task_{taskId}/` 目录
3. 查看任务详情页的执行日志

```bash
# 调试技巧
echo "[DEBUG] 参数值: $TARGET_IP"
echo "[DEBUG] 当前目录: $(pwd)"
echo "[DEBUG] 环境变量:"
env | grep TARGET
```

---

## 七、最佳实践

### 7.1 脚本设计原则

1. **参数化设计**：所有配置通过参数传入，避免硬编码
2. **错误处理**：完善的错误检测和提示
3. **日志规范**：清晰的日志输出，便于问题定位
4. **结果输出**：使用标准格式输出关键指标

### 7.2 安全建议

1. 敏感信息使用参数传递，不要写入脚本文件
2. 使用最小权限原则，限制脚本可访问的资源
3. 避免在日志中输出密码等敏感信息
4. 定期审查和更新认证凭据

### 7.3 性能优化

1. 合理设置超时时间
2. 避免在脚本中进行不必要的等待
3. 使用并发执行提高效率
4. 及时清理临时文件和资源

---

## 八、相关代码位置

| 文件 | 说明 |
|------|------|
| `backend/src/main/java/com/autotest/service/LocalExecutor.java` | 本地执行器接口 |
| `backend/src/main/java/com/autotest/service/PowerShellExecutor.java` | Windows 执行器实现 |
| `backend/src/main/java/com/autotest/service/ShellExecutor.java` | Linux/Unix 执行器实现 |
| `backend/src/main/java/com/autotest/service/LocalExecutorFactory.java` | 执行器工厂 |
| `backend/src/main/java/com/autotest/service/TaskExecutionService.java` | 任务执行服务（`executeStepLocally` 方法） |
| `backend/src/main/java/com/autotest/entity/TaskServer.java` | 任务服务器实体（`isLocal` 字段） |
| `frontend/src/views/tasks/editor.vue` | 任务编辑器前端（本地执行选项） |

---

## 九、版本历史

| 版本 | 日期 | 变更说明 |
|------|------|---------|
| v1.0 | 2026-04-26 | 初始版本，记录本地执行功能使用方法 |

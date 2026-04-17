# 自动化测试平台 API 文档

> 本文档供 AI 助手理解和使用自动化测试平台的 REST API 接口。

## 基本信息

- **Base URL**: `http://localhost:8080/api/v1`
- **认证方式**: 无（可扩展添加 API Key）
- **响应格式**: JSON

### 通用响应结构

```json
{
  "code": 0,           // 0=成功，非0=失败
  "message": "success",
  "data": { ... }      // 实际数据
}
```

---

## 一、脚本管理 `/scripts`

### 1.1 获取脚本列表

```
GET /scripts
```

**查询参数**:
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| page | int | 否 | 页码，默认 1 |
| size | int | 否 | 每页数量，默认 20 |
| name | string | 否 | 脚本名称（模糊搜索） |
| testCategory | string | 否 | 测试分类 |
| status | string | 否 | 状态：enabled/disabled |

**响应示例**:
```json
{
  "code": 0,
  "data": {
    "total": 100,
    "page": 1,
    "size": 20,
    "items": [
      {
        "id": 1,
        "name": "mysql_test",
        "scriptType": "shell",
        "testCategory": "database",
        "description": "MySQL 性能测试",
        "currentVersion": "v1.0.0",
        "status": "enabled",
        "createdAt": "2026-04-01T10:00:00"
      }
    ]
  }
}
```

---

### 1.2 获取脚本详情

```
GET /scripts/{id}
```

**路径参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| id | long | 脚本ID |

**响应**: 返回脚本完整信息，包括 `steps`（步骤配置）和 `parameters`（参数配置）。

---

### 1.3 创建脚本（推荐流程）

创建脚本需要两步：
1. 上传脚本文件（POST /scripts/upload）
2. 创建脚本记录（POST /scripts）

#### 步骤1：上传脚本文件

```
POST /scripts/upload
Content-Type: multipart/form-data
```

**表单字段**:
| 字段 | 类型 | 说明 |
|------|------|------|
| file | File | 脚本文件（.zip 或单文件） |

**响应示例**:
```json
{
  "code": 0,
  "data": {
    "originalName": "test_script.zip",
    "fileName": "test_script.zip",
    "tempPath": "C:\\data\\auto-test\\temp\\upload_xxx\\test_script.zip",
    "isArchive": true,
    "fileList": [
      { "path": "main.sh", "size": 1024 },
      { "path": "autotest.yaml", "size": 512 }
    ]
  }
}
```

#### 步骤2：创建脚本记录

```
POST /scripts
Content-Type: application/json
```

**请求体**:
```json
{
  "name": "my_test_script",
  "description": "测试脚本描述",
  "testCategory": "performance",
  "scriptType": "shell",
  "tempFilePath": "C:\\data\\auto-test\\temp\\upload_xxx\\test_script.zip"
}
```

**字段说明**:
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| name | string | 是 | 脚本名称，唯一 |
| description | string | 否 | 描述 |
| testCategory | string | 否 | 测试分类：system/database/network/performance/functional |
| scriptType | string | 否 | 脚本类型：shell/python（默认 shell） |
| tempFilePath | string | 否 | 上传返回的临时路径 |
| steps | array | 否 | 执行步骤配置（若 autotest.yaml 中有则自动解析） |
| parameters | object | 否 | 参数定义（若 autotest.yaml 中有则自动解析） |
| fileList | array | 否 | 文件列表 |

**响应**: 返回创建的脚本信息，如果包含 `autotest.yaml` 会自动解析配置。

**自动解析规则**:
- 如果 `tempFilePath` 指向的目录包含 `autotest.yaml`，系统会自动：
  1. 解析 steps（执行步骤）
  2. 解析 parameters（参数配置）
  3. 解析 retryConfig（重试配置）

---

### 1.4 更新脚本

```
PUT /scripts/{id}
Content-Type: application/json
```

**请求体**: 同创建脚本

---

### 1.5 删除脚本

```
DELETE /scripts/{id}
```

**注意**: 会级联删除相关的任务记录。

---

### 1.6 导出脚本

```
GET /scripts/{id}/export?format=zip
```

---

### 1.7 获取脚本文件列表

```
GET /scripts/{id}/file-list
```

---

### 1.8 获取脚本文件内容

```
GET /scripts/{id}/files/{filePath}
```

---

## 二、任务管理 `/tasks`

### 2.1 获取任务列表

```
GET /tasks
```

**查询参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| page | int | 页码 |
| size | int | 每页数量 |
| name | string | 任务名称（模糊搜索） |
| status | string | 状态：pending/running/success/failed/cancelled |
| scriptId | long | 脚本ID |

---

### 2.2 获取任务详情

```
GET /tasks/{id}
```

**响应字段**:
| 字段 | 说明 |
|------|------|
| id | 任务ID |
| name | 任务名称 |
| scriptId | 关联的脚本ID |
| status | 状态 |
| successCount | 成功服务器数 |
| failedCount | 失败服务器数 |
| createdAt | 创建时间 |
| startedAt | 开始时间 |
| finishedAt | 完成时间 |

---

### 2.3 创建任务

```
POST /tasks
Content-Type: application/json
```

**请求体**:
```json
{
  "name": "test_task_001",
  "description": "测试任务描述",
  "scriptId": 1,
  "scriptVersion": "v1.0.0",
  "executionMode": "immediate",
  "serverIds": [1, 2, 3]
}
```

**字段说明**:
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| name | string | 是 | 任务名称 |
| description | string | 否 | 描述 |
| scriptId | long | 是 | 脚本ID |
| scriptVersion | string | 是 | 脚本版本，通常为 "v1.0.0" |
| executionMode | string | 是 | 执行模式：immediate（立即）/scheduled（定时） |
| serverIds | array | 是 | 目标服务器ID列表 |

---

### 2.4 执行任务

```
POST /tasks/{id}/execute
```

**说明**: 触发任务开始执行。

---

### 2.5 取消任务

```
POST /tasks/{id}/cancel
```

---

### 2.6 重试任务

```
POST /tasks/{id}/retry
```

**说明**: 重新执行失败的任务。

---

### 2.7 获取执行日志

```
GET /tasks/{id}/logs
```

**响应**: 返回每个服务器的执行状态和日志信息。

---

### 2.8 获取任务步骤

```
GET /tasks/{id}/steps
```

**响应**: 返回任务的所有执行步骤及其状态。

---

## 三、服务器管理 `/servers`

### 3.1 获取服务器列表

```
GET /servers
```

**查询参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| page | int | 页码 |
| size | int | 每页数量 |
| name | string | 服务器名称（模糊搜索） |
| status | string | 状态：online/offline |
| groupId | long | 分组ID |

---

### 3.2 获取服务器详情

```
GET /servers/{id}
```

---

### 3.3 添加服务器

```
POST /servers
Content-Type: application/json
```

**请求体**:
```json
{
  "name": "test-server-01",
  "host": "192.168.1.100",
  "port": 22,
  "username": "root",
  "password": "password",
  "authType": "password",
  "groupId": 1
}
```

**字段说明**:
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| name | string | 是 | 服务器名称 |
| host | string | 是 | 主机地址 |
| port | int | 否 | SSH端口，默认22 |
| username | string | 是 | SSH用户名 |
| password | string | 条件 | SSH密码（authType=password时必填） |
| privateKey | string | 条件 | SSH私钥（authType=key时必填） |
| authType | string | 是 | 认证类型：password/key |
| groupId | long | 否 | 分组ID |

---

### 3.4 测试连接

```
POST /servers/{id}/test
```

**响应**:
```json
{
  "code": 0,
  "data": {
    "connected": true
  }
}
```

---

### 3.5 删除服务器

```
DELETE /servers/{id}
```

---

### 3.6 服务器分组管理

```
GET    /servers/groups           # 获取分组列表
POST   /servers/groups           # 创建分组
PUT    /servers/groups/{id}      # 更新分组
DELETE /servers/groups/{id}      # 删除分组
```

---

## 四、测试结果 `/results`

### 4.1 获取结果列表

```
GET /results
```

**查询参数**:
| 参数 | 类型 | 说明 |
|------|------|------|
| page | int | 页码 |
| pageSize | int | 每页数量 |
| taskId | long | 任务ID |
| serverId | long | 服务器ID |
| result | string | 结果：pass/fail/warning/error |

---

### 4.2 获取结果详情

```
GET /results/{id}
```

---

### 4.3 获取任务的测试结果

```
GET /results/task/{taskId}
```

---

### 4.4 获取结果统计

```
GET /results/statistics?taskId={taskId}
```

**响应**:
```json
{
  "code": 0,
  "data": {
    "total": 100,
    "pass": 80,
    "fail": 15,
    "warning": 3,
    "error": 2,
    "passRate": 80.0
  }
}
```

---

### 4.5 结果对比

```
POST /results/compare
Content-Type: application/json
```

**请求体**:
```json
{
  "resultIds": [1, 2, 3]
}
```

---

### 4.6 导出结果

```
GET /results/export?taskId={taskId}&format=csv
```

---

## 五、常用操作流程

### 5.1 创建并执行测试任务

```
1. GET /servers                     # 获取可用服务器
2. GET /scripts?name=xxx            # 查找脚本
3. POST /tasks                      # 创建任务
4. POST /tasks/{id}/execute         # 执行任务
5. GET /tasks/{id}                  # 查询状态
6. GET /tasks/{id}/logs             # 查看日志
7. GET /results/task/{taskId}       # 查看结果
```

### 5.2 上传新脚本并执行

```
1. POST /scripts/upload             # 上传脚本包
2. POST /scripts                    # 创建脚本记录
3. GET /scripts/{id}                # 确认配置正确
4. POST /tasks                      # 创建任务
5. POST /tasks/{id}/execute         # 执行任务
```

---

## 六、AI 助手使用指南

### 6.1 用户意图识别

当用户表达以下意图时，应调用对应 API：

| 用户意图 | 操作 | API |
|---------|------|-----|
| "查看所有脚本" | 获取脚本列表 | GET /scripts |
| "创建一个xxx测试任务" | 创建任务 | POST /tasks |
| "在服务器上执行测试" | 执行任务 | POST /tasks/{id}/execute |
| "查看任务状态/进度" | 查询任务 | GET /tasks/{id} |
| "测试服务器连接" | 测试连接 | POST /servers/{id}/test |
| "添加一个服务器" | 添加服务器 | POST /servers |
| "查看测试结果" | 获取结果 | GET /results |

### 6.2 响应格式建议

**创建任务成功后**:
```
✅ 任务创建成功！

- 任务ID: 123
- 名称: mysql_test_001
- 目标服务器: 3台
- 状态: pending

需要我立即执行吗？
```

**任务执行中**:
```
⏳ 任务正在执行中...

- 进度: 2/3 服务器完成
- 成功: 1
- 失败: 1
- 当前步骤: step_2_run_test

预计还需要 2 分钟...
```

**任务完成**:
```
✅ 任务执行完成！

- 总耗时: 5分32秒
- 成功: 2台服务器
- 失败: 1台服务器
- 通过率: 95%

是否需要查看详细结果？
```

### 6.3 错误处理

当 API 返回错误时，应向用户展示友好信息：

```json
{
  "code": 500,
  "message": "脚本不存在"
}
```

应转换为：
```
❌ 操作失败：脚本不存在

请先创建脚本，或检查脚本ID是否正确。
```

---

## 七、状态码说明

| 状态 | 说明 |
|------|------|
| pending | 等待执行 |
| running | 执行中 |
| success | 执行成功 |
| failed | 执行失败 |
| cancelled | 已取消 |
| timeout | 执行超时 |

---

## 八、Webhook 事件（待实现）

未来可实现以下事件推送：

```json
{
  "event": "task.completed",
  "data": {
    "taskId": 123,
    "status": "success",
    "successCount": 3,
    "failedCount": 0,
    "duration": 320
  },
  "timestamp": "2026-04-01T10:30:00Z"
}
```

**事件类型**:
- `task.created` - 任务创建
- `task.started` - 任务开始执行
- `task.completed` - 任务完成
- `task.failed` - 任务失败
- `server.offline` - 服务器离线

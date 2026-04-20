# 任务编排功能说明文档

## 功能概述

任务编排功能允许用户将多个测试脚本组合成一个编排，按照依赖关系自动执行。支持 DAG（有向无环图）执行模式，可控制最大并行数。

## 核心概念

### 编排（Pipeline）
编排是一个或多个任务的集合，定义了任务之间的依赖关系和执行策略。

### 任务（PipelineTask）
编排中的每个任务对应一个测试脚本，可以配置：
- 选择的服务器
- 步骤参数
- 共享参数
- 依赖的其他任务

### 执行记录（PipelineRun）
每次执行编排都会生成一条执行记录，包含：
- 执行状态
- 开始/结束时间
- 触发者
- 关联的任务执行列表

---

## 数据库设计

### 表结构

#### 1. pipelines - 编排定义表

```sql
CREATE TABLE IF NOT EXISTS pipelines (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name VARCHAR(100) NOT NULL,           -- 编排名称
    description TEXT,                      -- 描述
    max_parallel INTEGER DEFAULT 5,        -- 最大并行数
    enabled BOOLEAN DEFAULT 1,             -- 是否启用
    created_by VARCHAR(100),               -- 创建者
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_pipelines_name ON pipelines(name);
CREATE INDEX IF NOT EXISTS idx_pipelines_enabled ON pipelines(enabled);
```

#### 2. pipeline_tasks - 编排任务配置表

```sql
CREATE TABLE IF NOT EXISTS pipeline_tasks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    pipeline_id BIGINT NOT NULL REFERENCES pipelines(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,            -- 任务名称
    script_id BIGINT NOT NULL REFERENCES scripts(id),  -- 脚本ID
    order_num INTEGER DEFAULT 0,           -- 排序号
    server_ids TEXT,                       -- 服务器ID列表（JSON数组）
    step_server_mapping TEXT,              -- 步骤服务器映射（JSON对象）
    step_params TEXT,                      -- 步骤参数（JSON对象）
    shared_params TEXT,                    -- 共享参数（JSON对象）
    timeout BIGINT,                        -- 超时时间（秒）
    enabled BOOLEAN DEFAULT 1,             -- 是否启用
    depends_on TEXT,                       -- 依赖任务列表（JSON数组）
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_pipeline_tasks_pipeline_id ON pipeline_tasks(pipeline_id);
CREATE INDEX IF NOT EXISTS idx_pipeline_tasks_order ON pipeline_tasks(pipeline_id, order_num);
```

**JSON 字段格式说明**：

| 字段 | 格式 | 示例 |
|------|------|------|
| `server_ids` | `[1, 2, 3]` | `[1, 2]` |
| `step_server_mapping` | `{"stepName": [serverId]}` | `{"step_1": [1], "step_2": [2]}` |
| `step_params` | `{"stepName": {"param": "value"}}` | `{"step_1": {"SIZE": "10G"}}` |
| `shared_params` | `{"param": "value"}` | `{"RUNTIME": 60}` |
| `depends_on` | `["taskName1", "taskName2"]` | `["FIO", "iperf3"]` |

#### 3. pipeline_runs - 编排执行记录表

```sql
CREATE TABLE IF NOT EXISTS pipeline_runs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    pipeline_id BIGINT NOT NULL REFERENCES pipelines(id) ON DELETE CASCADE,
    pipeline_name VARCHAR(100),            -- 编排名称（快照）
    status VARCHAR(20) DEFAULT 'pending',  -- 状态
    started_at TIMESTAMP,                  -- 开始时间
    finished_at TIMESTAMP,                 -- 结束时间
    triggered_by VARCHAR(100),             -- 触发者
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_pipeline_runs_pipeline_id ON pipeline_runs(pipeline_id);
CREATE INDEX IF NOT EXISTS idx_pipeline_runs_status ON pipeline_runs(status);
CREATE INDEX IF NOT EXISTS idx_pipeline_runs_created_at ON pipeline_runs(created_at);
```

**状态说明**：

| 状态 | 说明 |
|------|------|
| `pending` | 等待执行 |
| `running` | 执行中 |
| `completed` | 执行完成（所有任务成功） |
| `failed` | 执行失败（有任务失败） |
| `cancelled` | 已取消 |

#### 4. pipeline_run_tasks - 编排执行任务关联表

```sql
CREATE TABLE IF NOT EXISTS pipeline_run_tasks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    pipeline_run_id BIGINT NOT NULL REFERENCES pipeline_runs(id) ON DELETE CASCADE,
    task_id BIGINT NOT NULL REFERENCES tasks(id),  -- 关联的真实任务ID
    task_name VARCHAR(100),               -- 任务名称（快照）
    status VARCHAR(20) DEFAULT 'pending', -- 状态
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_pipeline_run_tasks_run_id ON pipeline_run_tasks(pipeline_run_id);
CREATE INDEX IF NOT EXISTS idx_pipeline_run_tasks_task_id ON pipeline_run_tasks(task_id);
```

### 表关系图

```
pipelines (编排定义)
    │
    ├──< pipeline_tasks (编排任务配置)
    │       │
    │       └── script_id → scripts.id
    │
    └──< pipeline_runs (执行记录)
            │
            └──< pipeline_run_tasks (执行任务关联)
                    │
                    └── task_id → tasks.id
```

---

## API 接口

### 编排管理

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/v1/pipelines` | 创建编排 |
| `GET` | `/api/v1/pipelines` | 编排列表 |
| `GET` | `/api/v1/pipelines/{id}` | 编排详情 |
| `PUT` | `/api/v1/pipelines/{id}` | 更新编排 |
| `DELETE` | `/api/v1/pipelines/{id}` | 删除编排 |

### 任务管理

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/v1/pipelines/{id}/tasks` | 获取编排任务列表 |
| `POST` | `/api/v1/pipelines/{id}/tasks` | 添加编排任务 |
| `PUT` | `/api/v1/pipelines/{id}/tasks/{taskId}` | 更新编排任务 |
| `DELETE` | `/api/v1/pipelines/{id}/tasks/{taskId}` | 删除编排任务 |

### YAML 导入导出

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/v1/pipelines/import` | 导入 YAML 创建编排 |
| `GET` | `/api/v1/pipelines/{id}/export` | 导出编排为 YAML |

### 执行管理

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/v1/pipelines/{id}/execute` | 执行编排 |
| `GET` | `/api/v1/pipelines/runs` | 执行记录列表 |
| `GET` | `/api/v1/pipelines/runs/{runId}` | 执行详情 |
| `GET` | `/api/v1/pipelines/runs/{runId}/tasks` | 执行任务列表 |
| `POST` | `/api/v1/pipelines/runs/{runId}/cancel` | 取消执行 |

---

## 前端页面

### 路由配置

| 路径 | 页面 | 说明 |
|------|------|------|
| `/pipelines/list` | 编排列表 | 显示所有编排，支持执行、编辑、删除 |
| `/pipelines/create` | 创建编排 | 新建编排和任务配置 |
| `/pipelines/edit/:id` | 编辑编排 | 修改编排和任务配置 |
| `/pipelines/runs` | 执行记录 | 显示执行历史记录 |
| `/pipelines/runs/:runId` | 执行详情 | 显示单次执行的详细信息 |

### 关键文件

**后端**：
```
backend/src/main/java/com/autotest/
├── entity/
│   ├── Pipeline.java
│   ├── PipelineTask.java
│   ├── PipelineRun.java
│   └── PipelineRunTask.java
├── mapper/
│   ├── PipelineMapper.java
│   ├── PipelineTaskMapper.java
│   ├── PipelineRunMapper.java
│   └── PipelineRunTaskMapper.java
├── service/
│   ├── PipelineService.java
│   └── impl/PipelineServiceImpl.java
└── controller/
    └── PipelineController.java
```

**前端**：
```
frontend/src/
├── api/
│   └── pipeline.ts
├── components/
│   └── PipelineImportDialog.vue   # YAML 导入对话框
└── views/pipelines/
    ├── index.vue        # 编排列表
    ├── editor.vue       # 创建/编辑编排
    ├── runs.vue         # 执行记录列表
    └── run-detail.vue   # 执行详情
```

---

## 执行流程

### DAG 执行逻辑

```
1. 创建执行记录（pipeline_runs）
2. 遍历编排任务，创建真实任务（tasks）
3. 创建任务关联记录（pipeline_run_tasks）
4. 构建任务依赖图
5. 异步执行 DAG：
   a. 找出可执行任务（依赖已完成）
   b. 控制并行数 <= maxParallel
   c. 执行任务
   d. 轮询检查任务状态
   e. 更新编排执行状态
```

### 任务创建配置

执行编排创建任务时，自动配置：
- `executionMode` = `immediate`（立即执行）
- `collectEnabled` = `false`（关闭指标采集）
- `scriptVersion` = 脚本当前版本

---

## 注意事项

1. **任务名称唯一性**
   - 同一编排内任务名称不能重复
   - 前端选择脚本时自动命名，重复时添加序号后缀

2. **依赖关系**
   - 依赖任务必须先于当前任务定义
   - 不支持循环依赖

3. **删除编排**
   - 会级联删除关联的任务配置、执行记录、执行任务关联

4. **数据库连接**
   - DAG 执行采用异步方式，避免长时间占用数据库连接

---

## YAML 导入导出

### 功能说明

支持通过 YAML 文件导入/导出编排配置，方便编排的分享和版本管理。

### 导入 YAML

点击编排列表页面的「导入 YAML」按钮，上传 YAML 文件即可创建编排。

### 导出 YAML

在编排列表页面点击任务的「导出」按钮，下载 YAML 配置文件。

### 导入模板

点击「导入模板」按钮下载配置模板，包含详细注释说明。

### YAML 格式示例

```yaml
# 基本信息
name: 示例流水线

description: 这是一个示例流水线

maxParallel: 3

# 服务器定义（可选，已存在则更新）
servers:
  - name: test-server-1
    host: 192.168.1.100
    username: root
    authSecret: your-password

# 任务列表
tasks:
  - name: 环境准备
    script: env-check
    timeout: 300
    stepServerMapping:
      check: [test-server-1]

  - name: 执行测试
    script: performance-test
    dependsOn: [环境准备]
    stepServerMapping:
      prepare: [test-server-1]
      run_test: [test-server-2]
    sharedParams:
      RUNTIME: 60
    stepParams:
      run_test:
        TEST_MODE: randrw
```

### 字段说明

| 字段 | 必填 | 说明 |
|------|------|------|
| `name` | 是 | 编排名称 |
| `description` | 否 | 描述 |
| `maxParallel` | 否 | 最大并行数，默认 5 |
| `servers` | 否 | 服务器定义列表 |
| `tasks` | 是 | 任务配置列表 |

### 任务字段说明

| 字段 | 必填 | 说明 |
|------|------|------|
| `name` | 是 | 任务名称 |
| `script` | 是 | 脚本名称（需存在于平台） |
| `timeout` | 否 | 超时时间（秒） |
| `dependsOn` | 否 | 依赖任务名称列表 |
| `stepServerMapping` | 是 | 步骤服务器映射 |
| `sharedParams` | 否 | 共享参数 |
| `stepParams` | 否 | 步骤参数 |

### stepServerMapping 格式

```yaml
stepServerMapping:
  step_1: [server-1]           # 单台服务器
  step_2: [server-1, server-2] # 多台服务器并行执行
```

- **key**: 步骤名称（必须与脚本定义一致）
- **value**: 服务器列表
  - **导入时**：支持服务器名称或 ID
  - **导出时**：输出服务器名称（更易读）

### 关键点

1. **只使用 `stepServerMapping`**：不再使用 `serverIds` 字段
2. **步骤支持多服务器**：一个步骤可以绑定多台服务器并行执行
3. **导出过滤空值**：空对象、空数组、`default` 映射不会导出
4. **脚本必须定义步骤**：无步骤的脚本无法创建任务

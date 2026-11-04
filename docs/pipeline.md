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

---

## 智能体接入（MCP）

后端内置的 MCP endpoint（`POST /mcp`）把编排开放成 12 个工具，实现在
`backend/src/main/java/com/autotest/agent/PipelineTools.java`。建、改都收 **YAML 文本**，
与界面「导入 YAML」共用 `PipelineImportService` 那一条路径 —— 智能体只需学一种格式，
也避免把「任务列表 + 步骤映射 + 两层参数」这种三层嵌套塞进工具参数。

### 工具

| 工具 | 作用 | 关键返回 |
|------|------|----------|
| `list_pipelines` | 按名称找编排 | `id`、`taskCount`、`lastRun` |
| `get_pipeline` | 看完整定义 | `tasks[]`（含 `pipelineTaskId`）、**`problems`**（体检结果） |
| `create_pipeline` | YAML 新建 | 新建后的 `get_pipeline` 视图，`created: true` |
| `update_pipeline` | YAML 整体替换 | 同上 + `notes`（提示 `pipelineTaskId` 已重建） |
| `delete_pipeline` | 删除编排及其任务定义、全部执行记录 | `deletedTaskCount`、`deletedRunCount` |
| `run_pipeline` | 触发执行，可选临时改主机 | `runId`、`tasks[]`（子任务 `taskId`） |
| `wait_for_pipeline_run` | 阻塞等终态，默认 30s、上限 110s | `finished`、`status`、`taskSummary` |
| `get_pipeline_run` | 看一次执行 | `tasks[].taskId` → 转 `get_task_result` |
| `list_pipeline_runs` | 列执行记录 | `runId`、`status` |
| `cancel_pipeline_run` | 取消执行 | `status`、`taskSummary` |
| `export_pipeline` | 导出 YAML（改编排的起点） | `yaml`、`topLevelKeys`、`taskKeys` |
| `describe_pipeline_spec` | 库里没有可抄的编排时，自述这份 YAML 契约 | `topLevelKeys`/`taskKeys`/`serverKeys`（带每个键的用途）、`rules`（12 条）、`exampleYaml`、`commonMistakes`、`howToAuthor` |

典型链路：`list_pipelines` → `get_pipeline`（读 `problems`）→ `run_pipeline` →
`wait_for_pipeline_run` → `get_pipeline_run` 拿子任务 `taskId` → `get_task_result` / `get_step_log`。
新建优先走 `export_pipeline` 抄现成的改；库里没有相近的编排就先 `describe_pipeline_spec`，
它把键表、硬规则、一份带注释的最小示例和常见错法一起返回（示例里的 `<...>` 必须换成
`get_script_contract` 与 `list_servers` 查到的真实值，猜出来的名字会撞上精确的错误码）。

### 键契约

合法键集只有一处真源：`PipelineTools` 的 `TOP_DOCS` / `TASK_DOCS` / `SERVER_DOCS` 三张「键 -> 用途」表，
校验用的 `YAML_TOP` / `YAML_TASK` / `YAML_SERVER` 就是它们的 keySet，`describe_pipeline_spec` 返回的也是它们。
**加字段只改这张表**，否则校验与自述会立刻漂移。**全驼峰**：

- 顶层：`name`、`description`、`maxParallel`、`servers`、`tasks`。`max_parallel` 会被拒并提示「是不是想写 maxParallel？」
- 任务：`name`、`script`、`scriptId`、`dependsOn`、`timeout`、`stepServerMapping`、`sharedParams`、`stepParams`。
  **任务级没有 `servers` 键**，主机只在 `stepServerMapping` 里按 `{步骤名: [服务器名或 id]}` 给。
- `timeout` 在 YAML 里是**秒**，导入时 ×1000 存进 `pipeline_tasks.timeout`（毫秒）；导出时 ÷1000 回到秒。
- `servers` 段的 `authType` 只认 `password` / `key`。曾经各处注释与示例写的 `ssh_key` 是错的（已改正）：
  `SshService` 的两个分支只匹配 `password` 和 `key`，写别的值既不设密码也不加载私钥，一路带到连接失败才知道原因。
  YAML 导入留空按 `password`（`ServerYamlConfig.toCreateRequest`），MCP 的 `create_server` 则必填且不猜。

写坏的键不会被静默丢掉：未知键 → `UNKNOWN_KEY` 并列出该层合法键 + 拼写候选；类型不对（如 `timeout: abc`）
→ `YAML_TYPE_INVALID` 并带上 Jackson 的原文。校验用的是独立的 `YAML_READER`，不共用 Spring 那个
`fail-on-unknown-properties: false` 的 mapper —— 否则未知键在反序列化阶段就消失了，报不出来。

### 结构化拒绝

不合格不提交，返回逐条问题而不是「解析失败」：

```json
{
  "ok": false,
  "reason_code": "PIPELINE_INVALID",
  "error": "编排有 2 处问题，未提交；按 errors 逐条改完再试",
  "errors": [
    { "code": "DEPENDS_ON_CYCLE", "where": "a",
      "message": "依赖成环，调度永远结束不了: a -> b -> a" },
    { "code": "NO_SERVERS", "where": "tasks[1]",
      "message": "没有可用主机：stepServerMapping 至少要给一个步骤指定存在的服务器名或 id" }
  ],
  "next": "get_pipeline 或 export_pipeline"
}
```

`where` 在 `create_pipeline` / `update_pipeline` 里是 YAML 路径（`tasks[1].dependsOn`），
在 `get_pipeline.problems` 里是任务名 —— 后者是给「库里的存量编排体检」用的，同一套校验规则。

`run_pipeline` 的守卫按顺序：`PIPELINE_NOT_FOUND` → `PIPELINE_DISABLED` → `NO_TASKS` →
`ALREADY_RUNNING`（独立 reason_code，不和校验混在一坨 errors 里，避免智能体把「上一条还在跑」当成
「YAML 写错了」去改配置）→ 校验 → `BUSY`（同一批主机上已有 pending/running 任务，SSH 池只有 2 条连接）。

### 服务层的坑，以及 MCP 侧怎么挡

`PipelineServiceImpl` 有几种失败方式不报错，只让结果不对。这些在 MCP 边界上全部提前拦掉：

| 情形 | 服务层原本的行为 | MCP 侧 |
|------|------------------|--------|
| 任务没有可用主机 | `continue` 静默跳过，不建子任务；依赖它的任务永远等 | `NO_SERVERS` / `SERVER_NOT_FOUND`，提交前拒 |
| `dependsOn` 成环 | `executeTasksDAG` 无 sleep 忙等，CPU 打满 | `DEPENDS_ON_CYCLE`，DFS 三色标记并给出环路径 |
| `dependsOn` 指向不存在的任务 | 该依赖被直接丢弃，**顺序不生效**且无提示 | `DEPENDS_ON_UNKNOWN`（`get_pipeline.problems` 同样报） |
| 任务重名 | `Collectors.toMap` 抛 `IllegalStateException`，整条编排点不动 | `TASK_NAME_DUPLICATE` |
| 编排 `enabled=false` | `executePipeline` 不看这个标记，照跑 | `PIPELINE_DISABLED` 拒绝执行；任务级 `enabled=false` 服务层也不看，只能在返回的 `warnings` 里说清楚 |
| 删除正在跑的编排 | 无守卫，直接连带删掉执行记录 | `PIPELINE_RUNNING` 拒绝，要求先 `cancel_pipeline_run`；删除还要求显式 `confirm=true` |
| `servers` 段同名 | 按名称更新，**凭据一起被覆盖** | `SERVER_EXISTS_WOULD_OVERWRITE`，确需覆盖才传 `allowExistingServerUpdates=true` |

`update_pipeline` 继承服务层「先删后插」的实现，`pipelineTaskId` 每次都是新 id，历史执行记录不受影响；
返回值里的 `notes` 会提示 `run_pipeline` 的 `serverMapping` 键要按本次返回的 id 或任务名重新取。

### 取消语义（本次改动，界面同样受益）

`cancel_pipeline_run` 只写库，不改那个正在调度的线程。原来有两个真问题：

1. 取消后调度循环继续把剩余任务发出去 —— 现在 `executeTasksDAG` 每轮读回 run 状态，`cancelled` 立即 break；
2. 结束时用 `failed.isEmpty()` 覆写状态，把 `cancelled` 改成 `completed` / `failed` —— 现在终态写入跳过 `cancelled`。

因此取消后 run 会停在 `cancelled`，未启动的子任务保持 `cancelled` 且 `startedAt` 为空，再次取消返回
`ALREADY_TERMINAL`。

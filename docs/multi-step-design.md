# 多步骤脚本测试流程设计

> 版本：v1.0  
> 日期：2026-03-14  
> 状态：设计稿

---

## 1. 概述

### 1.1 背景

复杂的测试场景往往需要多个步骤按顺序执行，例如：
- 先部署数据库 → 再初始化数据 → 执行压测 → 清理环境
- 服务端启动 → 客户端连接 → 执行测试 → 收集结果

### 1.2 设计目标

1. **灵活编排**：支持多步骤按依赖关系执行
2. **并行优化**：无依赖的步骤可并行执行
3. **状态可见**：实时展示每个步骤的执行状态
4. **容错机制**：支持步骤失败后的处理策略
5. **结果关联**：每个步骤的结果独立存储，支持关联分析

---

## 2. 数据模型

### 2.1 步骤定义（ScriptVersion.steps）

```json
{
  "step_1": {
    "displayName": "部署 MySQL",
    "script": "deploy.sh",
    "dependsOn": [],
    "resultCollector": false,
    "startupProbe": {
      "type": "tcp",
      "port": 3306,
      "timeoutSeconds": 60
    },
    "params": [
      { "name": "version", "defaultValue": "8.0", "description": "MySQL版本" }
    ]
  },
  "step_2": {
    "displayName": "初始化数据",
    "script": "init_data.sh",
    "dependsOn": ["step_1"],
    "resultCollector": false,
    "params": []
  },
  "step_3": {
    "displayName": "执行压测",
    "script": "benchmark.sh",
    "dependsOn": ["step_2"],
    "resultCollector": true,
    "params": [
      { "name": "threads", "defaultValue": "16", "description": "并发线程数" },
      { "name": "duration", "defaultValue": "300", "description": "持续时间(秒)" }
    ]
  },
  "step_4": {
    "displayName": "收集结果",
    "script": "collect.sh",
    "dependsOn": ["step_3"],
    "resultCollector": true,
    "params": []
  }
}
```

### 2.2 任务步骤状态（新增 task_steps 表）

```sql
CREATE TABLE task_steps (
    id BIGSERIAL PRIMARY KEY,
    task_id BIGINT NOT NULL REFERENCES tasks(id),
    server_id BIGINT NOT NULL REFERENCES servers(id),
    step_name VARCHAR(50) NOT NULL,           -- 步骤标识，如 step_1
    display_name VARCHAR(200),                 -- 步骤显示名称
    
    -- 执行配置
    script VARCHAR(500),                       -- 执行的脚本文件
    depends_on TEXT[],                         -- 依赖的步骤
    params JSONB,                              -- 步骤参数
    
    -- 执行状态
    status VARCHAR(20) DEFAULT 'pending',      -- pending/waiting/running/success/failed/skipped
    wait_reason VARCHAR(200),                  -- 等待原因（如：等待 step_1 完成）
    
    -- 执行信息
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    exit_code INTEGER,
    output TEXT,
    error_message TEXT,
    
    -- 结果收集
    result_collector BOOLEAN DEFAULT false,
    parsed_result JSONB,                       -- 解析后的结果
    
    -- 启动探测
    startup_probe JSONB,                       -- 探测配置
    probe_status VARCHAR(20),                  -- 探测状态
    probe_started_at TIMESTAMP,
    probe_finished_at TIMESTAMP,
    
    -- 时间戳
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(task_id, server_id, step_name)
);

CREATE INDEX idx_task_steps_task ON task_steps(task_id);
CREATE INDEX idx_task_steps_status ON task_steps(status);
```

### 2.3 步骤状态流转

```
                    ┌─────────────┐
                    │   pending   │ 初始状态
                    └──────┬──────┘
                           │ 检查依赖
              ┌────────────┴────────────┐
              ▼                         ▼
      ┌─────────────┐          ┌─────────────┐
      │   waiting   │          │   running   │ 无依赖或依赖已完成
      │  (等待依赖)  │          └──────┬──────┘
      └──────┬──────┘                 │
             │ 依赖完成               │ 执行完成
             ▼                        │
      ┌─────────────┐                 │
      │   running   │                 │
      └──────┬──────┘                 │
             │                        │
    ┌────────┴────────┐               │
    ▼                 ▼               ▼
┌─────────┐     ┌─────────┐     ┌─────────┐
│ success │     │ failed  │     │ skipped │ 依赖失败时跳过
└─────────┘     └─────────┘     └─────────┘
```

---

## 3. 执行流程

### 3.1 任务创建流程

```
┌─────────────────────────────────────────────────────────────────┐
│                       任务创建页面                               │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  1. 选择脚本                                                     │
│     └─► 加载脚本的步骤定义                                       │
│                                                                  │
│  2. 选择服务器                                                   │
│     └─► 显示步骤分配界面                                         │
│                                                                  │
│  3. 步骤分配（新增）                                             │
│     ┌─────────────────────────────────────────────────────┐    │
│     │  步骤          │  执行服务器   │  参数覆盖           │    │
│     ├─────────────────────────────────────────────────────┤    │
│     │  step_1 部署   │  [选择服务器] │  version=8.0       │    │
│     │  step_2 初始化 │  [同上]      │  -                  │    │
│     │  step_3 压测   │  [选择服务器] │  threads=32        │    │
│     │  step_4 收集   │  [同上]      │  -                  │    │
│     └─────────────────────────────────────────────────────┘    │
│                                                                  │
│  4. 执行参数覆盖                                                 │
│                                                                  │
│  5. 创建任务                                                     │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### 3.2 执行引擎调度流程

```
┌─────────────────────────────────────────────────────────────────┐
│                     TaskExecutionService                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  executeTask(taskId)                                             │
│       │                                                          │
│       ▼                                                          │
│  ┌─────────────────────────────────────┐                        │
│  │ 1. 加载任务、脚本、步骤定义          │                        │
│  │ 2. 初始化所有 task_steps 记录       │                        │
│  │ 3. 构建 DAG 依赖图                  │                        │
│  └─────────────────────────────────────┘                        │
│       │                                                          │
│       ▼                                                          │
│  ┌─────────────────────────────────────┐                        │
│  │     while (有未完成的步骤)           │◄─────────┐            │
│  │           │                          │          │            │
│  │           ▼                          │          │            │
│  │  ┌─────────────────────────────┐    │          │            │
│  │  │ 找出可执行的步骤：           │    │          │            │
│  │  │ - status = pending          │    │          │            │
│  │  │ - 所有依赖都 success         │    │          │            │
│  │  └─────────────────────────────┘    │          │            │
│  │           │                          │          │            │
│  │           ▼                          │          │            │
│  │  ┌─────────────────────────────┐    │          │            │
│  │  │ 并行执行可执行步骤           │    │          │            │
│  │  │ (每个服务器一个线程)         │    │          │            │
│  │  └─────────────────────────────┘    │          │            │
│  │           │                          │          │            │
│  │           ▼                          │          │            │
│  │  ┌─────────────────────────────┐    │          │            │
│  │  │ 等待任一步骤完成             │    │          │            │
│  │  │ 更新状态，触发下游检查       │────┘          │            │
│  │  └─────────────────────────────┘               │            │
│  └─────────────────────────────────────┘            │            │
│       │                                              │            │
│       ▼                                              │            │
│  ┌─────────────────────────────────────┐            │            │
│  │ 所有步骤完成或失败                   │            │            │
│  │ 更新任务最终状态                     │            │            │
│  └─────────────────────────────────────┘            │            │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### 3.3 单步骤执行流程

```
┌─────────────────────────────────────────────────────────────────┐
│                    executeStep(taskStep)                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  1. 上传脚本文件到服务器                                          │
│     └─► 包括主脚本和依赖文件                                      │
│                                                                  │
│  2. 执行脚本                                                      │
│     - chmod +x script.sh                                          │
│     - ./script.sh [params...]                                     │
│                                                                  │
│  3. 实时输出收集                                                  │
│     - 更新 output 字段                                            │
│     - WebSocket 推送进度                                          │
│                                                                  │
│  4. 启动探测（如果有）                                            │
│     - TCP 探测：检测端口是否可连接                                │
│     - HTTP 探测：检测 HTTP 端点是否返回 200                       │
│     - 超时则标记失败                                              │
│                                                                  │
│  5. 结果收集（如果 resultCollector=true）                         │
│     - 解析输出                                                    │
│     - 存储到 parsed_result                                        │
│                                                                  │
│  6. 更新步骤状态                                                  │
│     - success / failed                                            │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 4. 前端设计

### 4.1 任务创建页面 - 步骤分配

```vue
<template>
  <div class="task-steps-assign">
    <el-divider content-position="left">步骤分配</el-divider>
    
    <el-table :data="stepAssignments" border>
      <el-table-column label="步骤" width="200">
        <template #default="{ row }">
          <div class="step-info">
            <el-tag type="info" size="small">{{ row.stepName }}</el-tag>
            <span class="step-name">{{ row.displayName }}</span>
          </div>
        </template>
      </el-table-column>
      
      <el-table-column label="依赖" width="150">
        <template #default="{ row }">
          <el-tag v-for="dep in row.dependsOn" :key="dep" size="small" type="warning">
            {{ dep }}
          </el-tag>
          <span v-if="!row.dependsOn?.length" class="text-muted">无</span>
        </template>
      </el-table-column>
      
      <el-table-column label="执行服务器" min-width="200">
        <template #default="{ row }">
          <el-select v-model="row.serverId" placeholder="选择服务器" style="width: 100%">
            <el-option 
              v-for="server in availableServers" 
              :key="server.id"
              :label="server.name"
              :value="server.id"
            />
          </el-select>
        </template>
      </el-table-column>
      
      <el-table-column label="参数覆盖" min-width="250">
        <template #default="{ row }">
          <div v-for="param in row.params" :key="param.name" class="param-row">
            <span class="param-label">{{ param.name }}:</span>
            <el-input v-model="param.value" :placeholder="param.defaultValue" size="small" />
          </div>
        </template>
      </el-table-column>
      
      <el-table-column label="结果收集" width="100">
        <template #default="{ row }">
          <el-tag :type="row.resultCollector ? 'success' : 'info'" size="small">
            {{ row.resultCollector ? '是' : '否' }}
          </el-tag>
        </template>
      </el-table-column>
    </el-table>
    
    <!-- 快捷操作 -->
    <div class="quick-actions">
      <el-button @click="assignAllToFirst">所有步骤分配到第一个服务器</el-button>
      <el-button @click="copyServerDown">复制服务器到下游步骤</el-button>
    </div>
  </div>
</template>
```

### 4.2 任务详情页面 - 步骤进度

```vue
<template>
  <div class="task-steps-progress">
    <el-divider content-position="left">执行进度</el-divider>
    
    <!-- 步骤拓扑图 -->
    <div class="steps-dag">
      <div v-for="step in steps" :key="step.stepName" 
           :class="['step-node', step.status]">
        <div class="step-header">
          <el-icon v-if="step.status === 'running'" class="is-loading">
            <Loading />
          </el-icon>
          <el-icon v-else-if="step.status === 'success'" style="color: #67c23a">
            <CircleCheck />
          </el-icon>
          <el-icon v-else-if="step.status === 'failed'" style="color: #f56c6c">
            <CircleClose />
          </el-icon>
          <el-icon v-else-if="step.status === 'waiting'" style="color: #e6a23c">
            <Clock />
          </el-icon>
          <span class="step-name">{{ step.displayName }}</span>
        </div>
        <div class="step-meta">
          <span v-if="step.startedAt">{{ formatDuration(step) }}</span>
          <span v-if="step.serverName" class="server-tag">{{ step.serverName }}</span>
        </div>
      </div>
      
      <!-- 依赖连线 -->
      <svg class="dag-lines">
        <!-- 根据 dependsOn 绘制连线 -->
      </svg>
    </div>
    
    <!-- 详细表格 -->
    <el-table :data="stepsWithDetails" border>
      <el-table-column label="步骤" prop="displayName" />
      <el-table-column label="服务器" prop="serverName" />
      <el-table-column label="状态">
        <template #default="{ row }">
          <el-tag :type="getStatusType(row.status)">{{ getStatusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="开始时间" prop="startedAt" />
      <el-table-column label="耗时" prop="duration" />
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button link @click="viewOutput(row)">日志</el-button>
          <el-button link @click="viewResult(row)">结果</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>
```

---

## 5. 后端实现

### 5.1 TaskExecutionService 改造

```java
/**
 * 执行多步骤任务
 */
public void executeMultiStepTask(Long taskId) {
    Task task = taskMapper.selectById(taskId);
    ScriptVersion version = getScriptVersion(task);
    Map<String, Object> steps = version.getSteps();
    
    // 1. 初始化所有步骤状态
    initTaskSteps(task, steps);
    
    // 2. 构建依赖图
    StepDAG dag = buildDAG(steps);
    
    // 3. 执行循环
    while (dag.hasPendingSteps()) {
        // 找出可执行的步骤
        List<String> readySteps = dag.getReadySteps();
        
        if (readySteps.isEmpty()) {
            // 检查是否有失败的步骤导致死锁
            if (dag.hasFailedSteps()) {
                markBlockedStepsAsSkipped(dag);
                break;
            }
            // 等待正在执行的步骤完成
            waitForAnyStepComplete();
            continue;
        }
        
        // 并行执行可执行的步骤
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (String stepName : readySteps) {
            dag.markAsRunning(stepName);
            futures.add(executeStepAsync(taskId, stepName));
        }
        
        // 等待任一完成
        CompletableFuture.anyOf(futures.toArray(new CompletableFuture[0])).join();
    }
    
    // 4. 更新任务最终状态
    updateTaskFinalStatus(taskId);
}

/**
 * 异步执行单个步骤
 */
private CompletableFuture<Void> executeStepAsync(Long taskId, String stepName) {
    return CompletableFuture.runAsync(() -> {
        TaskStep step = taskStepMapper.selectOne(
            new LambdaQueryWrapper<TaskStep>()
                .eq(TaskStep::getTaskId, taskId)
                .eq(TaskStep::getStepName, stepName)
        );
        
        try {
            // 执行脚本
            StepResult result = executeScript(step);
            
            // 启动探测
            if (result.isSuccess() && step.getStartupProbe() != null) {
                result = waitForStartupProbe(step);
            }
            
            // 更新状态
            step.setStatus(result.isSuccess() ? "success" : "failed");
            step.setExitCode(result.getExitCode());
            step.setOutput(result.getOutput());
            
        } catch (Exception e) {
            step.setStatus("failed");
            step.setErrorMessage(e.getMessage());
        } finally {
            step.setFinishedAt(LocalDateTime.now());
            taskStepMapper.updateById(step);
        }
    }, executorService);
}

/**
 * 构建步骤依赖图
 */
private StepDAG buildDAG(Map<String, Object> steps) {
    StepDAG dag = new StepDAG();
    
    for (Map.Entry<String, Object> entry : steps.entrySet()) {
        String name = entry.getKey();
        Map<String, Object> config = (Map<String, Object>) entry.getValue();
        List<String> dependsOn = (List<String>) config.getOrDefault("dependsOn", List.of());
        
        dag.addStep(name, dependsOn);
    }
    
    return dag;
}
```

### 5.2 StepDAG 依赖图实现

```java
/**
 * 步骤依赖有向无环图
 */
public class StepDAG {
    private final Map<String, List<String>> dependencies = new HashMap<>();
    private final Map<String, String> status = new HashMap<>();
    
    public void addStep(String name, List<String> dependsOn) {
        dependencies.put(name, dependsOn);
        status.put(name, "pending");
    }
    
    public List<String> getReadySteps() {
        return dependencies.entrySet().stream()
            .filter(e -> "pending".equals(status.get(e.getKey())))
            .filter(e -> e.getValue().stream()
                .allMatch(dep -> "success".equals(status.get(dep))))
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }
    
    public void markAsRunning(String stepName) {
        status.put(stepName, "running");
    }
    
    public void markAsComplete(String stepName, boolean success) {
        status.put(stepName, success ? "success" : "failed");
    }
    
    public boolean hasPendingSteps() {
        return status.values().stream()
            .anyMatch(s -> "pending".equals(s) || "running".equals(s));
    }
    
    public boolean hasFailedSteps() {
        return status.containsValue("failed");
    }
}
```

---

## 6. 实现计划

### Phase 1：数据层（1天）
- [ ] 创建 task_steps 表
- [ ] 添加 TaskStep 实体和 Mapper
- [ ] 更新 ScriptVersion 实体支持 steps 字段

### Phase 2：后端执行引擎（2天）
- [ ] 实现 StepDAG 依赖图
- [ ] 改造 TaskExecutionService 支持多步骤
- [ ] 实现步骤并行执行
- [ ] 实现启动探测

### Phase 3：前端任务创建（1天）
- [ ] 步骤分配组件
- [ ] 参数覆盖功能
- [ ] 快捷操作

### Phase 4：前端任务详情（1天）
- [ ] 步骤进度展示
- [ ] DAG 可视化
- [ ] 日志/结果查看

### Phase 5：测试和优化（1天）
- [ ] 端到端测试
- [ ] 性能优化
- [ ] Bug 修复

---

## 7. 示例场景

### 7.1 MySQL 性能测试

```
step_1: 部署 MySQL
  ├─ script: deploy_mysql.sh
  ├─ dependsOn: []
  ├─ startupProbe: tcp:3306
  └─ resultCollector: false

step_2: 初始化数据
  ├─ script: init_data.sh
  ├─ dependsOn: [step_1]
  └─ resultCollector: false

step_3: 执行压测
  ├─ script: sysbench.sh
  ├─ dependsOn: [step_2]
  ├─ resultCollector: true
  └─ params: threads=16, duration=300

step_4: 清理环境
  ├─ script: cleanup.sh
  ├─ dependsOn: [step_3]
  └─ resultCollector: false
```

### 7.2 Client-Server 测试

```
step_1: 启动服务端
  ├─ script: start_server.sh
  ├─ server: server-01
  ├─ startupProbe: tcp:8080
  └─ resultCollector: false

step_2: 启动客户端
  ├─ script: start_client.sh
  ├─ server: server-02
  ├─ dependsOn: [step_1]
  └─ resultCollector: false

step_3: 执行测试
  ├─ script: run_test.sh
  ├─ server: server-02
  ├─ dependsOn: [step_2]
  └─ resultCollector: true

step_4: 收集服务端日志
  ├─ script: collect_logs.sh
  ├─ server: server-01
  ├─ dependsOn: [step_3]
  └─ resultCollector: true
```

---

## 8. 风险与约束

1. **循环依赖检测**：需要在保存脚本时验证 steps 不存在循环依赖
2. **超时处理**：每个步骤需要设置超时时间
3. **并发控制**：大量并行步骤可能占用过多资源
4. **状态一致性**：任务取消时需要正确处理所有执行中的步骤

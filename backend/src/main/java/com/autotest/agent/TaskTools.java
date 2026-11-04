package com.autotest.agent;

import com.autotest.dto.request.TaskCreateRequest;
import com.autotest.entity.Script;
import com.autotest.entity.Task;
import com.autotest.entity.TaskServer;
import com.autotest.entity.TaskStep;
import com.autotest.mapper.ScriptMapper;
import com.autotest.mapper.TaskMapper;
import com.autotest.mapper.TaskServerMapper;
import com.autotest.mapper.TaskStepMapper;
import com.autotest.service.TaskService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.autotest.agent.CatalogTools.error;
import static com.autotest.agent.CatalogTools.result;

/**
 * 面向智能体的任务提交、等待与结果读取工具。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskTools {

    private static final Set<String> TERMINAL_TASK = Set.of(
            "completed", "completed_with_errors", "failed", "cancelled");
    private static final int DEFAULT_LOG_CHARS = 8000;

    private final TaskService taskService;
    private final TaskMapper taskMapper;
    private final TaskStepMapper taskStepMapper;
    private final TaskServerMapper taskServerMapper;
    private final ScriptMapper scriptMapper;

    @McpTool(name = "run_test", description = "创建并立即执行一个测试任务（等价于先 POST /tasks 再 POST /tasks/{id}/execute）。"
            + "返回 taskId，执行是异步的，用 wait_for_task 等待、get_task_result 取结果。"
            + "参数必须先经 get_script_contract 获取：sharedParams 是所有步骤共享的参数，stepParams 按步骤名覆盖同名共享参数。"
            + "共享参数和步骤参数都受契约校验：required=true 没传值返回 PARAM_REQUIRED（共享参数声明的 default 不会自动注入，"
            + "步骤声明的 defaultValue/default 会注入），取值不在 options 内返回 PARAM_NOT_IN_OPTIONS，"
            + "步骤维度的问题项带 step 字段，两种都带 problems 明细，不会创建任务。",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = false,
                    idempotentHint = false, title = "提交并执行测试任务"))
    public Map<String, Object> runTest(
            @McpToolParam(description = "脚本包 id") Long scriptId,
            @McpToolParam(description = "目标服务器 id 列表；-1 表示在平台本机执行") List<Long> serverIds,
            @McpToolParam(required = false, description = "共享参数，键为参数名") Map<String, Object> sharedParams,
            @McpToolParam(required = false, description = "步骤参数，形如 {\"step_1\": {\"MODE\": \"server\"}}，优先级高于共享参数与步骤默认值；按 steps.<步骤名>.params 的 required/options 校验，_BACKGROUND 除外")
            Map<String, Map<String, Object>> stepParams,
            @McpToolParam(required = false, description = "任务名称，默认 agent-<scriptId>-<时间戳>") String name,
            @McpToolParam(required = false, description = "超时毫秒；不传则按脚本包 defaultTimeoutSeconds 换算，最终默认 24 小时") Integer timeout,
            @McpToolParam(required = false, description = "失败策略：continue 或 stop，默认 continue") String failureStrategy,
            @McpToolParam(required = false, description = "是否采集指标，默认 false") Boolean collectEnabled,
            @McpToolParam(required = false, description = "幂等键；同名任务仍处于 pending/running 时直接复用，不重复提交") String idempotencyKey) {

        if (serverIds == null || serverIds.isEmpty()) {
            return error("PARAM_INVALID", "serverIds 不能为空（-1 表示平台本机执行）");
        }
        Script script = scriptMapper.selectById(scriptId);
        if (script == null) {
            return error("SCRIPT_NOT_FOUND", "脚本不存在: " + scriptId);
        }

        // 先按脚本契约校验，避免创建出必然失败的半成品任务
        List<Map<String, Object>> paramProblems =
                taskService.checkParams(scriptId, script.getCurrentVersion(), sharedParams, stepParams);
        if (!paramProblems.isEmpty()) {
            Map<String, Object> first = paramProblems.get(0);
            Map<String, Object> out = error(String.valueOf(first.get("code")),
                    String.valueOf(first.get("message")));
            out.put("problems", paramProblems);
            out.put("next", "get_script_contract");
            return out;
        }

        String taskName = resolveName(name, scriptId, idempotencyKey);

        Task reused = findActiveByName(taskName);
        if (reused != null) {
            return result("ok", true, "reused", true, "taskId", reused.getId(),
                    "name", taskName, "status", reused.getStatus(),
                    "next", "wait_for_task");
        }

        List<Long> conflicting = findTasksUsingServers(serverIds);
        if (!conflicting.isEmpty()) {
            return error("BUSY", "这些服务器上已有 pending/running 任务，SQLite 连接池与 SSH 池不足以并行: "
                    + conflicting);
        }

        TaskCreateRequest req = new TaskCreateRequest();
        req.setName(taskName);
        req.setDescription("由智能体通过 MCP 提交");
        req.setScriptId(scriptId);
        req.setScriptVersion(script.getCurrentVersion());
        req.setServerIds(serverIds);
        req.setSharedParams(sharedParams == null ? new LinkedHashMap<>() : sharedParams);
        req.setStepParams(stepParams == null ? new LinkedHashMap<>() : stepParams);
        req.setExecutionMode("immediate");
        req.setParallelMode("sequential");
        req.setMaxParallel(1);
        req.setFailureStrategy(failureStrategy == null ? "continue" : failureStrategy);
        req.setCollectEnabled(Boolean.TRUE.equals(collectEnabled));
        req.setTimeout(timeout != null ? timeout : defaultTimeoutMs(script));

        Task created = taskService.createTask(req);
        try {
            taskService.executeTask(created.getId());
        } catch (Exception e) {
            log.warn("任务 {} 触发执行失败: {}", created.getId(), e.getMessage());
            return error("INTERNAL", "任务已创建但未能启动: taskId=" + created.getId() + " - " + e.getMessage());
        }
        return result("ok", true, "reused", false, "taskId", created.getId(), "name", taskName,
                "scriptId", scriptId, "scriptVersion", script.getCurrentVersion(),
                "serverIds", serverIds, "status", "running", "next", "wait_for_task");
    }

    @McpTool(name = "wait_for_task", description = "轮询等待任务进入终态，最长阻塞 maxWaitSeconds（默认 30，上限 110）。"
            + "未到终态时返回 status=running 与已完成的步骤概览，继续调用即可。",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true,
                    title = "等待任务结束"))
    public Map<String, Object> waitForTask(
            @McpToolParam(description = "任务 id") Long taskId,
            @McpToolParam(required = false, description = "最多等待秒数，默认 30，上限 110") Integer maxWaitSeconds) {

        int budget = maxWaitSeconds == null || maxWaitSeconds <= 0 ? 30 : Math.min(maxWaitSeconds, 110);
        long deadline = System.currentTimeMillis() + budget * 1000L;

        Task task = loadTask(taskId);
        if (task == null) {
            return error("TASK_NOT_FOUND", "任务不存在: " + taskId);
        }
        while (!TERMINAL_TASK.contains(nz(task.getStatus())) && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            task = loadTask(taskId);
            if (task == null) {
                return error("TASK_NOT_FOUND", "任务在等待期间消失: " + taskId);
            }
        }

        boolean finished = TERMINAL_TASK.contains(nz(task.getStatus()));
        Map<String, Object> out = summarize(task);
        out.put("finished", finished);
        out.put("waitedSeconds", budget - (int) Math.max(0, (deadline - System.currentTimeMillis()) / 1000));
        out.put("next", finished ? "get_task_result" : "wait_for_task");
        return out;
    }

    @McpTool(name = "get_task_result", description = "取任务的结构化结果：每个步骤的状态、退出码、失败归因"
            + "（reason_code / failure_phase / is_retryable / retry_after_seconds）、产物清单与日志长度。"
            + "分析结果从这里开始，需要看日志再用 get_step_log。",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true,
                    title = "取任务结果"))
    public Map<String, Object> getTaskResult(
            @McpToolParam(description = "任务 id") Long taskId) {

        Task task = loadTask(taskId);
        if (task == null) {
            return error("TASK_NOT_FOUND", "任务不存在: " + taskId);
        }
        Map<String, Object> out = summarize(task);
        out.put("sharedParams", task.getSharedParams());
        return out;
    }

    @McpTool(name = "get_step_log", description = "分段读取步骤日志，避免一次返回超大文本。"
            + "不传 fromChar 时返回结尾 maxChars 个字符；传了则从该位置往后读。返回 nextFromChar 用于续读。"
            + "stepId/stepName/serverId 都不传时，优先取第一个失败步骤，否则取第一步。",
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false,
                    idempotentHint = true,
                    title = "读取步骤日志"))
    public Map<String, Object> getStepLog(
            @McpToolParam(description = "任务 id") Long taskId,
            @McpToolParam(required = false, description = "步骤 id；不传则用 stepName 定位") Long stepId,
            @McpToolParam(required = false, description = "步骤名，如 configure_network") String stepName,
            @McpToolParam(required = false, description = "服务器 id，多机同名步骤用它区分") Long serverId,
            @McpToolParam(required = false, description = "起始字符下标，用于续读") Integer fromChar,
            @McpToolParam(required = false, description = "最多返回字符数，默认 8000，上限 40000") Integer maxChars) {

        TaskStep step = findStep(taskId, stepId, stepName, serverId);
        if (step == null) {
            return error("STEP_NOT_FOUND", "未找到步骤: taskId=" + taskId + ", stepId=" + stepId
                    + ", stepName=" + stepName + ", serverId=" + serverId);
        }
        String output = step.getOutput() == null ? "" : step.getOutput();
        int cap = maxChars == null || maxChars <= 0 ? DEFAULT_LOG_CHARS : Math.min(maxChars, 40000);

        int start;
        if (fromChar != null && fromChar >= 0) {
            start = Math.min(fromChar, output.length());
        } else {
            start = Math.max(0, output.length() - cap);
        }
        int end = Math.min(output.length(), start + cap);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("stepId", step.getId());
        out.put("stepName", step.getStepName());
        out.put("serverId", step.getServerId());
        out.put("status", step.getStatus());
        out.put("exitCode", step.getExitCode());
        out.put("errorMessage", step.getErrorMessage());
        out.put("totalChars", output.length());
        out.put("fromChar", start);
        out.put("nextFromChar", end < output.length() ? end : null);
        out.put("truncated", start > 0 || end < output.length());
        out.put("text", output.substring(start, end));
        return out;
    }

    @McpTool(name = "cancel_task", description = "取消正在执行的任务（终止远端进程）。必须显式传 confirm=true。",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = true,
                    idempotentHint = false, title = "取消任务"))
    public Map<String, Object> cancelTask(
            @McpToolParam(description = "任务 id") Long taskId,
            @McpToolParam(description = "确认取消，必须为 true") Boolean confirm) {

        if (!Boolean.TRUE.equals(confirm)) {
            return error("CONFIRM_REQUIRED", "取消任务需要显式 confirm=true");
        }
        Task task = loadTask(taskId);
        if (task == null) {
            return error("TASK_NOT_FOUND", "任务不存在: " + taskId);
        }
        if (TERMINAL_TASK.contains(nz(task.getStatus()))) {
            return error("ALREADY_TERMINAL", "任务已结束，无需取消: " + task.getStatus());
        }
        taskService.cancelTask(taskId);
        Task after = loadTask(taskId);
        return result("ok", true, "taskId", taskId, "status", after == null ? null : after.getStatus());
    }

    @McpTool(name = "retry_task", description = "整个任务重试（重置步骤状态后重新执行）。必须显式传 confirm=true。",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = true,
                    idempotentHint = false, title = "重试整个任务"))
    public Map<String, Object> retryTask(
            @McpToolParam(description = "任务 id") Long taskId,
            @McpToolParam(description = "确认重试，必须为 true") Boolean confirm) {

        if (!Boolean.TRUE.equals(confirm)) {
            return error("CONFIRM_REQUIRED", "重试任务需要显式 confirm=true");
        }
        List<Long> conflicting = findTasksUsingServers(findTaskServers(taskId));
        conflicting.removeIf(id -> id.equals(taskId));
        if (!conflicting.isEmpty()) {
            return error("BUSY", "同一批服务器上还有其它活动任务: " + conflicting);
        }
        taskService.retryTask(taskId);
        Task after = loadTask(taskId);
        return result("ok", true, "taskId", taskId, "status", after == null ? null : after.getStatus(),
                "next", "wait_for_task");
    }

    @McpTool(name = "retry_step", description = "只重试任务里的单个步骤；cascade=true 时连带重跑下游步骤。"
            + "适合定位到具体失败步骤后做最小重跑。必须显式传 confirm=true。",
            annotations = @McpTool.McpAnnotations(readOnlyHint = false, destructiveHint = true,
                    idempotentHint = false, title = "重试单个步骤"))
    public Map<String, Object> retryStep(
            @McpToolParam(description = "任务 id") Long taskId,
            @McpToolParam(description = "步骤 id，来自 get_task_result") Long stepId,
            @McpToolParam(required = false, description = "是否连带重跑下游步骤，默认 false") Boolean cascade,
            @McpToolParam(description = "确认重试，必须为 true") Boolean confirm) {

        if (!Boolean.TRUE.equals(confirm)) {
            return error("CONFIRM_REQUIRED", "重试步骤需要显式 confirm=true");
        }
        try {
            Map<String, Object> raw = taskService.retryStep(taskId, stepId, Boolean.TRUE.equals(cascade));
            return result("ok", true, "taskId", taskId, "stepId", stepId, "result", raw,
                    "next", "wait_for_task");
        } catch (Exception e) {
            return error("INTERNAL", "重试步骤失败: " + e.getMessage());
        }
    }

    // ==================== 内部方法 ====================

    private Task loadTask(Long taskId) {
        // selectById 已按 @TableName(autoResultMap = true) 应用 JSON 列的 TypeHandler，
        // sharedParams / stepParams 能直接反序列化成 Map
        return taskMapper.selectById(taskId);
    }

    private Map<String, Object> summarize(Task task) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("taskId", task.getId());
        out.put("name", task.getName());
        out.put("scriptId", task.getScriptId());
        out.put("scriptVersion", task.getScriptVersion());
        out.put("status", task.getStatus());
        out.put("startedAt", task.getStartedAt());
        out.put("finishedAt", task.getFinishedAt());

        List<Map<String, Object>> servers = new ArrayList<>();
        for (TaskServer ts : taskServerMapper.selectList(new LambdaQueryWrapper<TaskServer>()
                .eq(TaskServer::getTaskId, task.getId()))) {
            servers.add(result("serverId", ts.getServerId(), "overallStatus", ts.getOverallStatus()));
        }
        out.put("servers", servers);

        List<TaskStep> stepRows = taskStepMapper.findByTaskIdWithServer(task.getId());
        Map<String, Integer> stepSummary = new LinkedHashMap<>();
        List<Map<String, Object>> steps = new ArrayList<>();
        for (TaskStep s : stepRows) {
            stepSummary.merge(s.getStatus() == null ? "unknown" : s.getStatus(), 1, Integer::sum);
            FailureClassifier.Verdict verdict = FailureClassifier.classify(s);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("stepId", s.getId());
            item.put("stepName", s.getStepName());
            item.put("displayName", s.getDisplayName());
            item.put("serverId", s.getServerId());
            item.put("serverName", s.getServerName());
            item.put("status", s.getStatus());
            item.put("exitCode", s.getExitCode());
            item.put("reason_code", verdict.reasonCode());
            item.put("failure_phase", verdict.failurePhase());
            item.put("is_retryable", verdict.retryable());
            item.put("retry_after_seconds", verdict.retryAfterSeconds());
            item.put("error_message", s.getErrorMessage());
            item.put("parsed_result", s.getParsedResult());
            item.put("log_chars", s.getOutput() == null ? 0 : s.getOutput().length());
            List<String> artifacts = new ArrayList<>();
            if (s.getOutputFiles() != null) {
                for (Map<String, Object> f : s.getOutputFiles()) {
                    Object n = f.get("name");
                    if (n != null) {
                        artifacts.add(String.valueOf(n));
                    }
                }
            }
            item.put("artifacts", artifacts);
            steps.add(item);
        }
        out.put("stepSummary", stepSummary);
        out.put("steps", steps);
        return out;
    }

    private TaskStep findStep(Long taskId, Long stepId, String stepName, Long serverId) {
        List<TaskStep> steps = taskStepMapper.findByTaskIdWithServer(taskId);
        if (stepId == null && stepName == null && serverId == null) {
            TaskStep first = null;
            for (TaskStep s : steps) {
                if (first == null) {
                    first = s;
                }
                if ("failed".equals(s.getStatus()) || "error".equals(s.getStatus())) {
                    return s;
                }
            }
            return first;
        }
        for (TaskStep s : steps) {
            if (stepId != null && stepId.equals(s.getId())) {
                return s;
            }
            if (stepId == null && stepName != null && stepName.equals(s.getStepName())) {
                if (serverId == null || serverId.equals(s.getServerId())) {
                    return s;
                }
            }
        }
        return null;
    }

    private Task findActiveByName(String name) {
        List<Task> found = taskMapper.selectList(new LambdaQueryWrapper<Task>()
                .eq(Task::getName, name)
                .in(Task::getStatus, List.of("pending", "running")));
        return found.isEmpty() ? null : found.get(0);
    }

    /** 同一台机器上跑两个任务会争用 SSH 会话与主机状态，提交前先挡掉 */
    private List<Long> findTasksUsingServers(List<Long> serverIds) {
        List<Long> conflicts = new ArrayList<>();
        for (Task task : taskMapper.selectList(new LambdaQueryWrapper<Task>()
                .in(Task::getStatus, List.of("pending", "running")))) {
            List<TaskServer> bound = taskServerMapper.selectList(new LambdaQueryWrapper<TaskServer>()
                    .eq(TaskServer::getTaskId, task.getId()));
            for (TaskServer ts : bound) {
                if (serverIds.contains(ts.getServerId()) && !conflicts.contains(task.getId())) {
                    conflicts.add(task.getId());
                    break;
                }
            }
        }
        return conflicts;
    }

    private List<Long> findTaskServers(Long taskId) {
        List<Long> ids = new ArrayList<>();
        for (TaskServer ts : taskServerMapper.selectList(new LambdaQueryWrapper<TaskServer>()
                .eq(TaskServer::getTaskId, taskId))) {
            ids.add(ts.getServerId());
        }
        return ids;
    }

    private String resolveName(String name, Long scriptId, String idempotencyKey) {
        if (name != null && !name.isBlank()) {
            return name.trim();
        }
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            return "agent-" + scriptId + "-" + idempotencyKey.trim();
        }
        return "agent-" + scriptId + "-" + System.currentTimeMillis();
    }

    /** scripts.default_timeout 单位是秒，tasks.timeout 单位是毫秒 */
    private static int defaultTimeoutMs(Script script) {
        Integer sec = script.getDefaultTimeout();
        if (sec == null || sec <= 0) {
            return 86400000;
        }
        return sec > 86400 ? 86400000 : sec * 1000;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}

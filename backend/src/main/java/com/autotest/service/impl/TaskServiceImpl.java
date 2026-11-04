package com.autotest.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.autotest.common.PageResult;
import com.autotest.dto.request.TaskCreateRequest;
import com.autotest.dto.request.TaskQueryRequest;
import com.autotest.dto.response.TaskDetailResponse;
import com.autotest.entity.Script;
import com.autotest.entity.ScriptVersion;
import com.autotest.entity.Server;
import com.autotest.entity.PipelineRunTask;
import com.autotest.entity.ScheduledTask;
import com.autotest.entity.Task;
import com.autotest.entity.TaskServer;
import com.autotest.entity.TaskStep;
import com.autotest.entity.TestResult;
import com.autotest.exception.BusinessException;
import com.autotest.mapper.ScriptMapper;
import com.autotest.mapper.PipelineRunTaskMapper;
import com.autotest.mapper.ScriptVersionMapper;
import com.autotest.mapper.ScheduledTaskMapper;
import com.autotest.mapper.ServerMapper;
import com.autotest.mapper.TaskMapper;
import com.autotest.mapper.TaskServerMapper;
import com.autotest.mapper.TaskStepMapper;
import com.autotest.mapper.TestResultMapper;
import com.autotest.service.TaskExecutionService;
import com.autotest.service.TaskService;
import com.autotest.service.LogCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 任务服务实现
 *
 * @author auto-test-platform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

    private final TaskMapper taskMapper;
    private final TaskServerMapper taskServerMapper;
    private final TaskStepMapper taskStepMapper;
    private final TestResultMapper testResultMapper;
    private final ScriptMapper scriptMapper;
    private final ScriptVersionMapper scriptVersionMapper;
    private final ServerMapper serverMapper;
    private final ScheduledTaskMapper scheduledTaskMapper;
    private final PipelineRunTaskMapper pipelineRunTaskMapper;
    private final TaskExecutionService taskExecutionService;
    private final LogCacheService logCacheService;

    @Override
    public PageResult<Task> listTasks(TaskQueryRequest request) {
        // 使用关联查询获取脚本名称
        IPage<Task> page = taskMapper.selectPageWithScriptName(
            new Page<>(request.getPage(), request.getSize()),
            request.getName(),
            request.getStatus(),
            request.getScriptId()
        );
        
        // 填充服务器统计
        for (Task task : page.getRecords()) {
            fillTaskStatistics(task);
        }
        
        return PageResult.of(page);
    }

    /**
     * 填充任务统计数据
     */
    private void fillTaskStatistics(Task task) {
        List<TaskServer> servers = taskServerMapper.selectList(
            new LambdaQueryWrapper<TaskServer>()
                .eq(TaskServer::getTaskId, task.getId())
        );
        
        int totalCount = servers.size();
        int successCount = 0;
        int failCount = 0;
        int runningCount = 0;
        
        for (TaskServer ts : servers) {
            String overallStatus = ts.getOverallStatus();
            
            if ("completed".equals(overallStatus)) {
                successCount++;
            } else if ("failed".equals(overallStatus)) {
                failCount++;
            } else if ("running".equals(overallStatus) || 
                       "pending".equals(overallStatus) && "running".equals(task.getStatus())) {
                // 正在执行或等待执行（任务运行中）
                runningCount++;
            }
        }
        
        task.setServerCount(totalCount);
        task.setSuccessCount(successCount);
        task.setFailCount(failCount);
        task.setRunningCount(runningCount);
        
        // 更新任务状态（如果有失败的服务器）
        if (failCount > 0 && "completed".equals(task.getStatus())) {
            task.setStatus("completed_with_errors");
            taskMapper.updateById(task);
        }
    }

    @Override
    public TaskDetailResponse getTaskDetail(Long id) {
        Task task = taskMapper.selectById(id);
        if (task == null) {
            throw BusinessException.of("任务不存在");
        }
        
        TaskDetailResponse response = new TaskDetailResponse();
        response.setId(task.getId());
        response.setName(task.getName());
        response.setDescription(task.getDescription());
        response.setScriptId(task.getScriptId());
        response.setScriptVersion(task.getScriptVersion());
        response.setStatus(task.getStatus());
        response.setSharedParams(task.getSharedParams());
        response.setStepParams(task.getStepParams());
        response.setStepServerMapping(task.getStepServerMapping());
        response.setCollectConfig(task.getCollectConfig());
        response.setTimeout(task.getTimeout());
        response.setExecutionMode(task.getExecutionMode());
        response.setScheduledTime(task.getScheduledTime());
        response.setParallelMode(task.getParallelMode());
        response.setMaxParallel(task.getMaxParallel());
        response.setFailureStrategy(task.getFailureStrategy());
        response.setCollectEnabled(task.getCollectEnabled());
        response.setCreatedAt(task.getCreatedAt());
        response.setStartedAt(task.getStartedAt());
        response.setFinishedAt(task.getFinishedAt());
        
        // 获取脚本信息
        Script script = scriptMapper.selectById(task.getScriptId());
        if (script != null) {
            response.setScriptName(script.getName());
            
            TaskDetailResponse.ScriptInfo scriptInfo = new TaskDetailResponse.ScriptInfo();
            scriptInfo.setId(script.getId());
            scriptInfo.setName(script.getName());
            scriptInfo.setVersion(task.getScriptVersion());
            response.setScript(scriptInfo);
        }
        
        // 获取服务器执行状态
        LambdaQueryWrapper<TaskServer> tsWrapper = new LambdaQueryWrapper<>();
        tsWrapper.eq(TaskServer::getTaskId, id);
        List<TaskServer> taskServers = taskServerMapper.selectList(tsWrapper);
        
        List<TaskDetailResponse.ServerProgress> servers = taskServers.stream()
                .map(ts -> {
                    TaskDetailResponse.ServerProgress progress = new TaskDetailResponse.ServerProgress();
                    progress.setServerId(ts.getServerId());
                    
                    // 角色信息
                    progress.setRole(ts.getRole());
                    
                    // 当前执行信息
                    progress.setCurrentPhase(ts.getCurrentPhase());
                    progress.setCurrentCommand(ts.getCurrentCommand());
                    progress.setCommandStartedAt(ts.getCommandStartedAt());
                    
                    // 整体状态
                    progress.setOverallStatus(ts.getOverallStatus());
                    progress.setProgress(ts.getProgress());
                    
                    // 获取服务器名称
                    Server server = serverMapper.selectById(ts.getServerId());
                    if (server != null) {
                        progress.setServerName(server.getName());
                    }
                    
                    return progress;
                })
                .collect(Collectors.toList());
        
        response.setServers(servers);
        
        // 计算统计信息
        int totalCount = servers.size();
        int successCount = 0;
        int failCount = 0;
        
        for (TaskDetailResponse.ServerProgress sp : servers) {
            if ("completed".equals(sp.getOverallStatus())) {
                successCount++;
            } else if ("failed".equals(sp.getOverallStatus())) {
                failCount++;
            }
        }
        
        response.setTotalServers(totalCount);
        response.setSuccessCount(successCount);
        response.setFailCount(failCount);
        
        return response;
    }
    
    /**
     * 计算 TaskServer 的整体状态
     * 步骤执行模式下，状态由 TaskExecutionService 更新
     */
    private String calculateOverallStatus(TaskServer ts) {
        // 直接返回 TaskServer 的 overallStatus 字段
        // 步骤执行模式下，状态由 TaskExecutionService 根据步骤执行情况更新
        return ts.getOverallStatus() != null ? ts.getOverallStatus() : "pending";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Task createTask(TaskCreateRequest request) {
        // 验证脚本
        Script script = scriptMapper.selectById(request.getScriptId());
        if (script == null) {
            throw BusinessException.of("脚本不存在");
        }
        
        // 验证服务器列表
        List<Long> serverIds = request.getServerIds();
        if (serverIds == null || serverIds.isEmpty()) {
            throw BusinessException.of("服务器列表不能为空");
        }
        
        // 验证服务器（-1 表示本地执行，跳过验证）
        for (Long serverId : serverIds) {
            if (serverId == -1L) {
                continue;  // 本地执行，跳过验证
            }
            Server server = serverMapper.selectById(serverId);
            if (server == null) {
                throw BusinessException.of("服务器不存在: " + serverId);
            }
        }
        
        assertParamsValid(request.getScriptId(), request.getScriptVersion(),
                request.getSharedParams(), request.getStepParams());

        Task task = new Task();
        task.setName(request.getName());
        task.setDescription(request.getDescription());
        task.setScriptId(request.getScriptId());
        task.setScriptVersion(request.getScriptVersion());
        task.setSharedParams(request.getSharedParams());
        task.setStepParams(request.getStepParams());
        task.setStepServerMapping(request.getStepServerMapping());
        task.setExecutionMode(request.getExecutionMode());
        task.setParallelMode(request.getParallelMode());
        task.setMaxParallel(request.getMaxParallel());
        task.setFailureStrategy(request.getFailureStrategy());
        task.setCollectEnabled(request.getCollectEnabled());
        task.setTimeout(request.getTimeout());
        
        // 如果请求中没有 collectConfig，从脚本版本中获取
        if (request.getCollectConfig() != null && !request.getCollectConfig().isEmpty()) {
            task.setCollectConfig(request.getCollectConfig());
        } else {
            // outputConfig 已废弃，不再使用
        }
        
        task.setRoleExecutionStrategy(request.getRoleExecutionStrategy());
        task.setStatus("pending");
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        
        // 定时执行时间
        if ("scheduled".equals(request.getExecutionMode()) && StringUtils.hasText(request.getScheduledTime())) {
            task.setScheduledTime(LocalDateTime.parse(request.getScheduledTime()));
        }
        
        taskMapper.insert(task);
        
        // 创建任务服务器关联（统一处理，-1 表示本地执行）
        for (Long serverId : serverIds) {
            TaskServer taskServer = new TaskServer();
            taskServer.setTaskId(task.getId());
            taskServer.setServerId(serverId);  // -1 表示本地执行
            taskServer.setOverallStatus("pending");
            taskServer.setCreatedAt(LocalDateTime.now());
            taskServer.setRole(serverId == -1L ? "local" : "default");
            taskServerMapper.insert(taskServer);
        }
        
        // 如果是定时执行，创建定时任务记录
        if ("scheduled".equals(request.getExecutionMode())) {
            ScheduledTask scheduledTask = new ScheduledTask();
            scheduledTask.setName(task.getName() + "-定时任务");
            scheduledTask.setTaskId(task.getId());
            scheduledTask.setStatus("enabled");
            scheduledTask.setRunCount(0);
            scheduledTask.setFailCount(0);
            scheduledTask.setCreatedAt(LocalDateTime.now());
            scheduledTask.setUpdatedAt(LocalDateTime.now());
            
            // 判断是一次性执行还是周期执行
            if (StringUtils.hasText(request.getScheduledTime())) {
                // 指定时间执行（一次性）
                scheduledTask.setScheduleType("once");
                scheduledTask.setNextRunTime(LocalDateTime.parse(request.getScheduledTime()));
                log.info("创建一次性定时任务: taskId={}, executeAt={}", task.getId(), request.getScheduledTime());
            } else if (StringUtils.hasText(request.getCronExpression())) {
                // Cron 周期执行
                scheduledTask.setScheduleType("cron");
                scheduledTask.setCronExpression(request.getCronExpression());
                log.info("创建周期定时任务: taskId={}, cron={}", task.getId(), request.getCronExpression());
            }
            
            scheduledTaskMapper.insert(scheduledTask);
        }
        
        return task;
    }

    @Override
    public List<Map<String, Object>> checkParams(Long scriptId, String version,
                                                 Map<String, Object> sharedParams,
                                                 Map<String, Map<String, Object>> stepParams) {
        List<Map<String, Object>> problems = new ArrayList<>();
        if (scriptId == null) {
            return problems;
        }
        Script script = scriptMapper.selectById(scriptId);
        if (script == null) {
            return problems;
        }
        String useVersion = StringUtils.hasText(version) ? version : script.getCurrentVersion();
        if (!StringUtils.hasText(useVersion)) {
            return problems;
        }
        ScriptVersion sv = scriptVersionMapper.selectOne(new LambdaQueryWrapper<ScriptVersion>()
                .eq(ScriptVersion::getScriptId, scriptId)
                .eq(ScriptVersion::getVersion, useVersion));
        // 没有版本或版本没声明参数契约时无从校验，交给脚本自己处理
        if (sv == null) {
            return problems;
        }

        Map<String, Object> shared = sharedParams == null ? Map.of() : sharedParams;
        Map<String, Map<String, Object>> sharedDefs = indexByName(paramDefs(sv.getParameters()));

        // 共享参数：执行时不会注入 default（TaskExecutionService 只读任务的 sharedParams），
        // 所以必填项必须由调用方给出取值
        for (Map<String, Object> def : sharedDefs.values()) {
            String name = (String) def.get("name");
            Object value = shared.get(name);
            checkAgainstDef(def, name, null, value, isPresent(value), problems);
        }

        Map<String, Object> steps = sv.getSteps();
        if (steps == null || steps.isEmpty()) {
            return problems;
        }
        for (Map.Entry<String, Object> entry : steps.entrySet()) {
            String stepName = entry.getKey();
            if ("_meta".equals(stepName) || !(entry.getValue() instanceof Map)) {
                continue;
            }
            Object paramsObj = ((Map<?, ?>) entry.getValue()).get("params");
            Map<String, Object> supplied = userStepValues(stepParams, stepName);
            Set<String> declared = new HashSet<>();
            for (Map<String, Object> def : paramDefs(paramsObj)) {
                String name = (String) def.get("name");
                declared.add(name);
                // 执行时的优先级：用户填写 > 步骤声明的默认值 > 共享参数
                Object effective = effectiveStepValue(def, name, supplied.get(name), shared.get(name));
                checkAgainstDef(def, name, stepName, effective, isPresent(effective), problems);
            }
            // 步骤未声明、共享契约声明了的参数，用户仍可按步骤提交，取值同样受约束
            for (Map.Entry<String, Object> kv : supplied.entrySet()) {
                String name = kv.getKey();
                if (name == null || declared.contains(name)) {
                    continue;
                }
                Map<String, Object> def = sharedDefs.get(name);
                if (def != null) {
                    checkAgainstDef(def, name, stepName, kv.getValue(), isPresent(kv.getValue()), problems);
                }
            }
        }
        return problems;
    }

    /**
     * 步骤参数的生效取值：用户填写压过步骤默认值，步骤默认值压过共享参数
     */
    private Object effectiveStepValue(Map<String, Object> def, String name, Object supplied, Object sharedValue) {
        if (supplied != null) {
            return supplied;
        }
        Object declared = def.containsKey("defaultValue") ? def.get("defaultValue") : def.get("default");
        return declared != null ? declared : sharedValue;
    }

    private void checkAgainstDef(Map<String, Object> def, String name, String stepName,
                                 Object value, boolean hasValue, List<Map<String, Object>> problems) {
        List<?> options = def.get("options") instanceof List ? (List<?>) def.get("options") : List.of();
        String where = stepName == null ? "参数 " + name : "步骤 " + stepName + " 的参数 " + name;
        if (Boolean.TRUE.equals(def.get("required")) && !hasValue) {
            String tail = stepName == null && isPresent(def.get("default"))
                    ? "（脚本声明的 default 不会自动注入，请显式传值）" : "";
            problems.add(paramProblem("PARAM_REQUIRED", name, stepName,
                    where + " 是必填项但未提供取值" + tail, List.of()));
            return;
        }
        if (hasValue && !options.isEmpty() && !matchesOption(options, value)) {
            problems.add(paramProblem("PARAM_NOT_IN_OPTIONS", name, stepName,
                    where + " 的取值 " + value + " 不在可选值 " + allowedValues(options) + " 内",
                    allowedValues(options)));
        }
    }

    /**
     * 参数定义列表，只保留带非空 name 的项；_BACKGROUND 是界面开关，不是参数
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> paramDefs(Object paramsObj) {
        if (!(paramsObj instanceof List)) {
            return List.of();
        }
        List<Map<String, Object>> defs = new ArrayList<>();
        for (Object item : (List<?>) paramsObj) {
            if (!(item instanceof Map)) {
                continue;
            }
            Map<String, Object> def = (Map<String, Object>) item;
            Object name = def.get("name");
            if (name instanceof String && !((String) name).isBlank() && !"_BACKGROUND".equals(name)) {
                defs.add(def);
            }
        }
        return defs;
    }

    private Map<String, Map<String, Object>> indexByName(List<Map<String, Object>> defs) {
        Map<String, Map<String, Object>> byName = new LinkedHashMap<>();
        for (Map<String, Object> def : defs) {
            byName.put((String) def.get("name"), def);
        }
        return byName;
    }

    private Map<String, Object> userStepValues(Map<String, Map<String, Object>> stepParams, String stepName) {
        if (stepParams == null) {
            return Map.of();
        }
        Map<String, Object> values = stepParams.get(stepName);
        return values == null ? Map.of() : values;
    }

    private Map<String, Object> paramProblem(String code, String param, String stepName,
                                             String message, List<String> allowedValues) {
        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("code", code);
        problem.put("param", param);
        if (stepName != null) {
            problem.put("step", stepName);
        }
        problem.put("message", message);
        problem.put("allowedValues", allowedValues);
        return problem;
    }

    private boolean isPresent(Object value) {
        return value != null && !(value instanceof String && ((String) value).isBlank());
    }

    private boolean matchesOption(List<?> options, Object value) {
        String actual = String.valueOf(value).trim();
        for (String candidate : allowedValues(options)) {
            if (candidate.equals(actual)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 可选值列表，兼容 {label,value} 对象与裸字符串两种历史形状
     */
    private List<String> allowedValues(List<?> options) {
        List<String> values = new ArrayList<>();
        for (Object option : options) {
            if (option instanceof Map) {
                Object value = ((Map<?, ?>) option).get("value");
                if (value == null) {
                    value = ((Map<?, ?>) option).get("label");
                }
                if (isPresent(value)) {
                    values.add(String.valueOf(value).trim());
                }
            } else if (isPresent(option)) {
                values.add(String.valueOf(option).trim());
            }
        }
        return values;
    }

    private void assertParamsValid(Long scriptId, String version, Map<String, Object> sharedParams,
                                   Map<String, Map<String, Object>> stepParams) {
        List<Map<String, Object>> problems = checkParams(scriptId, version, sharedParams, stepParams);
        if (problems.isEmpty()) {
            return;
        }
        String detail = problems.stream()
                .map(problem -> String.valueOf(problem.get("message")))
                .collect(Collectors.joining("; "));
        throw BusinessException.of("参数校验失败: " + detail);
    }

    @Override
    public Task updateTask(Long id, TaskCreateRequest request) {
        Task task = taskMapper.selectById(id);
        if (task == null) {
            throw BusinessException.of("任务不存在");
        }
        
        if ("running".equals(task.getStatus())) {
            throw BusinessException.of("执行中的任务不能修改");
        }
        
        assertParamsValid(task.getScriptId(), task.getScriptVersion(),
                request.getSharedParams(), request.getStepParams());

        // 更新基本信息
        task.setName(request.getName());
        task.setDescription(request.getDescription());
        task.setSharedParams(request.getSharedParams());
        task.setStepParams(request.getStepParams());
        task.setStepServerMapping(request.getStepServerMapping());
        task.setTimeout(request.getTimeout());
        task.setCollectEnabled(request.getCollectEnabled());
        task.setCollectConfig(request.getCollectConfig());
        task.setUpdatedAt(LocalDateTime.now());
        
        taskMapper.updateById(task);
        
        // 更新服务器关联（如果有变化）
        if (request.getServerIds() != null && !request.getServerIds().isEmpty()) {
            // 删除旧的服务器关联
            LambdaQueryWrapper<TaskServer> deleteWrapper = new LambdaQueryWrapper<>();
            deleteWrapper.eq(TaskServer::getTaskId, id);
            taskServerMapper.delete(deleteWrapper);
            
            // 创建新的服务器关联
            for (Long serverId : request.getServerIds()) {
                TaskServer taskServer = new TaskServer();
                taskServer.setTaskId(task.getId());
                taskServer.setServerId(serverId);
                taskServer.setOverallStatus("pending");
                taskServer.setCreatedAt(LocalDateTime.now());
                taskServer.setRole("default");
                taskServerMapper.insert(taskServer);
            }
        }
        
        return task;
    }

    @Override
    public void deleteTask(Long id) {
        Task task = taskMapper.selectById(id);
        if (task == null) {
            throw BusinessException.of("任务不存在");
        }
        
        // 如果任务正在执行，先取消任务（清理远程进程和工作目录）
        if ("running".equals(task.getStatus())) {
            try {
                taskExecutionService.cancelTask(id);
            } catch (Exception e) {
                log.warn("取消运行中的任务失败：{}", e.getMessage());
            }
        }
        
        // 删除关联的定时任务
        LambdaQueryWrapper<ScheduledTask> scheduledWrapper = new LambdaQueryWrapper<>();
        scheduledWrapper.eq(ScheduledTask::getTaskId, id);
        scheduledTaskMapper.delete(scheduledWrapper);
        
        // 删除任务步骤（task_steps）
        LambdaQueryWrapper<TaskStep> stepWrapper = new LambdaQueryWrapper<>();
        stepWrapper.eq(TaskStep::getTaskId, id);
        taskStepMapper.delete(stepWrapper);
        
        // 删除任务服务器关联（task_servers）
        LambdaQueryWrapper<TaskServer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TaskServer::getTaskId, id);
        taskServerMapper.delete(wrapper);
        
        // 删除 Pipeline 编排任务关联（pipeline_run_tasks）
        LambdaQueryWrapper<PipelineRunTask> prtWrapper = new LambdaQueryWrapper<>();
        prtWrapper.eq(PipelineRunTask::getTaskId, id);
        pipelineRunTaskMapper.delete(prtWrapper);
        
        // 删除任务（测试结果保留）
        taskMapper.deleteById(id);
    }

    @Override
    public void executeTask(Long id) {
        Task task = taskMapper.selectById(id);
        if (task == null) {
            throw BusinessException.of("任务不存在");
        }
        
        String status = task.getStatus();
        // 不允许正在执行中的任务重复执行
        if ("running".equals(status)) {
            throw BusinessException.of("任务正在执行中，请勿重复操作");
        }
        
        // 如果任务不是待执行状态，需要重置状态后再执行
        if (!"pending".equals(status)) {
            resetTaskForReExecution(id);
        }
        
        // 获取步骤参数
        final Map<String, Map<String, Object>> stepParams = task.getStepParams();
        
        // 异步执行任务
        final Long taskId = id;
        new Thread(() -> {
            try {
                taskExecutionService.executeTask(taskId, stepParams);
            } catch (Exception e) {
                log.error("任务执行失败: {}", e.getMessage(), e);
            }
        }).start();
    }
    
    /**
     * 重置任务状态以支持重新执行
     */
    @Transactional(rollbackFor = Exception.class)
    public void resetTaskForReExecution(Long taskId) {
        // 重置任务状态
        Task task = taskMapper.selectById(taskId);
        task.setStatus("pending");
        task.setStartedAt(null);
        task.setFinishedAt(null);
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        
        // 重置任务服务器状态
        LambdaQueryWrapper<TaskServer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TaskServer::getTaskId, taskId);
        List<TaskServer> taskServers = taskServerMapper.selectList(wrapper);
        
        for (TaskServer ts : taskServers) {
            ts.setOverallStatus("pending");
            ts.setProgress(0);
            ts.setCurrentPhase(null);
            ts.setCurrentCommand(null);
            ts.setCommandStartedAt(null);
            taskServerMapper.updateById(ts);
        }
        
        // 删除旧的步骤记录
        taskStepMapper.delete(new LambdaQueryWrapper<TaskStep>().eq(TaskStep::getTaskId, taskId));
        
        log.info("任务 {} 已重置，准备重新执行", taskId);
    }

    @Override
    public void cancelTask(Long id) {
        Task task = taskMapper.selectById(id);
        if (task == null) {
            throw BusinessException.of("任务不存在");
        }
        
        if ("completed".equals(task.getStatus()) || "cancelled".equals(task.getStatus())) {
            throw BusinessException.of("任务已完成或已取消");
        }
        
        // 调用执行服务的真正取消方法（停止进程、清理工作目录）
        boolean cancelled = taskExecutionService.cancelTask(id);
        
        if (cancelled) {
            task.setStatus("cancelled");
        } else {
            task.setStatus("cancelled");
        }
        
        task.setFinishedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        
        // 更新所有关联服务器的状态为已取消
        LambdaQueryWrapper<TaskServer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TaskServer::getTaskId, id);
        List<TaskServer> taskServers = taskServerMapper.selectList(wrapper);
        for (TaskServer ts : taskServers) {
            if (!"completed".equals(ts.getOverallStatus())) {
                ts.setOverallStatus("cancelled");
                taskServerMapper.updateById(ts);
            }
        }
        
        log.info("任务 {} 已被取消", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void retryTask(Long id) {
        Task task = taskMapper.selectById(id);
        if (task == null) {
            throw BusinessException.of("任务不存在");
        }
        
        String status = task.getStatus();
        if (!"failed".equals(status) && !"completed_with_errors".equals(status)) {
            throw BusinessException.of("只有失败的任务可以重试");
        }
        
        task.setStatus("pending");
        task.setStartedAt(null);
        task.setFinishedAt(null);
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        
        // 重置任务服务器状态
        LambdaQueryWrapper<TaskServer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TaskServer::getTaskId, id);
        List<TaskServer> taskServers = taskServerMapper.selectList(wrapper);
        for (TaskServer ts : taskServers) {
            ts.setOverallStatus("pending");
            ts.setProgress(0);
            ts.setCurrentPhase(null);
            ts.setCurrentCommand(null);
            ts.setCommandStartedAt(null);
            taskServerMapper.updateById(ts);
        }
        
        // 重置任务步骤状态
        taskStepMapper.delete(new LambdaQueryWrapper<TaskStep>().eq(TaskStep::getTaskId, id));
    }

    @Override
    public Object getTaskProgress(Long id) {
        return getTaskDetail(id);
    }

    @Override
    public Object getTaskLogs(Long id, Long serverId, String stage) {
        Task task = taskMapper.selectById(id);
        if (task == null) {
            throw BusinessException.of("任务不存在");
        }
        
        // 先尝试从内存缓存获取实时日志
        String cachedLog = logCacheService.getLog(id);
        if (cachedLog != null && !cachedLog.isEmpty()) {
            return Map.of(
                "source", "cache",
                "log", cachedLog,
                "completed", logCacheService.isTaskCompleted(id)
            );
        }
        
        // 内存中没有缓存，从数据库中获取历史日志（步骤输出）
        List<TaskStep> steps = taskStepMapper.findByTaskIdWithServer(id);
        if (steps == null || steps.isEmpty()) {
            return Map.of(
                "source", "database",
                "log", "",
                "completed", true
            );
        }
        
        // 构建历史日志
        StringBuilder logBuilder = new StringBuilder();
        for (TaskStep step : steps) {
            if (step.getOutput() != null && !step.getOutput().isEmpty()) {
                logBuilder.append("=== 步骤: ").append(step.getDisplayName() != null ? step.getDisplayName() : step.getStepName())
                          .append(" ===\n");
                logBuilder.append(step.getOutput()).append("\n\n");
            }
        }
        
        return Map.of(
            "source", "database",
            "log", logBuilder.toString(),
            "completed", true
        );
    }
    
    @Override
    public Map<String, Integer> fixAllTaskStatus() {
        int fixedTasks = 0;
        return Map.of("fixedTasks", fixedTasks);
    }
    
    @Override
    public Map<String, Object> retryStep(Long taskId, Long stepId, boolean cascade) {
        return taskExecutionService.retryStep(taskId, stepId, cascade);
    }
}

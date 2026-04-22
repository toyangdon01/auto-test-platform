package com.autotest.service;

import com.autotest.dto.StepStatusCheckResult;
import com.autotest.entity.*;
import com.autotest.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 任务执行服务 - 基于步骤执行
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskExecutionService {

    private final TaskMapper taskMapper;
    private final TaskServerMapper taskServerMapper;
    private final TaskStepMapper taskStepMapper;
    private final ServerMapper serverMapper;
    private final ScriptMapper scriptMapper;
    private final ScriptVersionMapper scriptVersionMapper;
    private final TestResultMapper testResultMapper;
    private final ScriptResourceMapper scriptResourceMapper;
    private final ResourceFileMapper resourceFileMapper;
    private final ResultParseService resultParseService;
    private final LogCacheService logCacheService;
    private final LocalExecutorFactory localExecutorFactory;

    @Value("${autotest.storage.scripts-path:C:/data/auto-test/scripts}")
    private String scriptsPath;

    private static final Map<Long, ExecutionContext> runningTasks = new ConcurrentHashMap<>();

    // ==================== 公共方法 ====================

    /**
     * 执行任务（基于步骤执行）
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> executeTask(Long taskId, Map<String, Map<String, Object>> stepParams) {
        Map<String, Object> result = new LinkedHashMap<>();

        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            result.put("success", false);
            result.put("error", "任务不存在");
            return result;
        }

        // 确保 sharedParams 被正确加载（MyBatis-Plus 可能不会自动应用 TypeHandler）
        if (task.getSharedParams() == null) {
            // 通过自定义查询确保 JSONB 字段被正确加载
            task = taskMapper.selectTaskWithParams(taskId);
        }
        
        log.info("[DEBUG] Task {} sharedParams: {}", taskId, task.getSharedParams());
        log.info("[DEBUG] Task {} stepParams: {}", taskId, task.getStepParams());

        if ("running".equals(task.getStatus())) {
            result.put("success", false);
            result.put("error", "任务正在执行中");
            return result;
        }

        task.setStatus("running");
        task.setStartedAt(LocalDateTime.now());
        taskMapper.updateById(task);

        ExecutionContext context = new ExecutionContext(taskId, line -> {
            // 实时推送到 WebSocket
            logCacheService.appendLog(taskId, line);
        });
        runningTasks.put(taskId, context);

        try {
            Script script = scriptMapper.selectById(task.getScriptId());
            ScriptVersion scriptVersion = getScriptVersion(task.getScriptId(), task.getScriptVersion());
            
            if (script == null || scriptVersion == null) {
                result.put("success", false);
                result.put("error", "脚本或版本不存在");
                return result;
            }

            context.log("========== 任务执行开始 ==========");
            context.log("任务: " + task.getName());
            context.log("脚本: " + script.getName() + " v" + scriptVersion.getVersion());

            List<TaskServer> taskServers = getTaskServers(taskId);
            if (taskServers.isEmpty()) {
                result.put("success", false);
                result.put("error", "任务没有关联服务器");
                return result;
            }
            
            // 更新所有服务器状态为 running
            for (TaskServer ts : taskServers) {
                ts.setOverallStatus("running");
                ts.setProgress(0);
                taskServerMapper.updateById(ts);
            }
            
            context.log("目标服务器: " + taskServers.size() + " 台\n");

            // 资源上传阶段
            uploadResources(context, task, taskServers, script);

            // 步骤执行阶段
            Map<String, Object> stepsConfig = scriptVersion.getSteps();
            int successCount;
            
            if (stepsConfig != null && !stepsConfig.isEmpty()) {
                successCount = executeSteps(context, task, taskServers, script, scriptVersion, stepsConfig, stepParams);
            } else {
                successCount = executeDefaultStep(context, task, taskServers, script, scriptVersion);
            }

            // 检查是否有还在运行的后台步骤
            boolean hasRunningBackgroundSteps = hasRunningBackgroundSteps(task.getId());
            
            // 计算最终状态
            int totalServers = taskServers.size();
            if (hasRunningBackgroundSteps) {
                // 有后台步骤还在运行，任务保持 running 状态，由 TaskStatusCheckService 更新
                task.setStatus("running");
                context.log("[INFO] 仍有后台步骤在执行，任务保持 running 状态");
            } else if (successCount == totalServers) {
                task.setStatus("completed");
            } else if (successCount == 0) {
                task.setStatus("failed");
            } else {
                task.setStatus("completed_with_errors");
            }
            task.setFinishedAt(LocalDateTime.now());
            taskMapper.updateById(task);

            context.log("\n========== 任务执行结束 ==========");
            context.log("成功: " + successCount + "/" + totalServers);
            context.log("状态: " + task.getStatus());

            // 通知日志缓存服务任务完成
            logCacheService.completeTask(task.getId());

            result.put("success", true);
            result.put("status", task.getStatus());
            result.put("successCount", successCount);
            result.put("totalServers", totalServers);

        } catch (Exception e) {
            log.error("任务执行异常", e);
            context.log("[ERROR] 任务执行异常: " + e.getMessage());
            
            task.setStatus("failed");
            task.setFinishedAt(LocalDateTime.now());
            taskMapper.updateById(task);
            
            // 通知日志缓存服务任务失败
            logCacheService.completeTask(task.getId());
            
            result.put("success", false);
            result.put("error", e.getMessage());
        } finally {
            runningTasks.remove(taskId);
        }

        return result;
    }

    /**
     * 取消任务
     */
    public boolean cancelTask(Long taskId) {
        ExecutionContext context = runningTasks.get(taskId);
        if (context != null) {
            context.cancel();
            
            // 杀掉远程服务器上正在执行的进程
            killRunningProcesses(taskId, context);
            
            // 清理日志缓存
            logCacheService.clearCache(taskId);
            return true;
        }
        return false;
    }
    
    /**
     * 杀掉远程服务器上正在执行的进程
     */
    private void killRunningProcesses(Long taskId, ExecutionContext context) {
        try {
            Task task = taskMapper.selectById(taskId);
            if (task == null) return;
            
            List<TaskServer> taskServers = getTaskServers(taskId);
            String workDir = "/tmp/test_platform/task_" + taskId;
            
            for (TaskServer ts : taskServers) {
                Server server = serverMapper.selectById(ts.getServerId());
                if (server == null) continue;
                
                // 查找并杀掉该任务工作目录下的所有进程
                // 使用 pkill 杀掉以任务工作目录为 cwd 的进程
                String killCmd = String.format(
                    "pkill -f 'bash.*%s' 2>/dev/null || true; " +
                    "pkill -f 'sh.*%s' 2>/dev/null || true; " +
                    "pkill -f 'python.*%s' 2>/dev/null || true",
                    workDir, workDir, workDir
                );
                
                context.log("[CANCEL] 尝试停止服务器 " + server.getName() + " 上的进程");
                SshService.executeCommand(server, killCmd, null, 10000);
                
                // 清理工作目录
                context.log("[CANCEL] 清理工作目录: " + workDir);
                SshService.cleanupWorkDir(server, workDir);
            }
        } catch (Exception e) {
            log.error("取消任务时杀进程失败: {}", e.getMessage());
        }
    }

    /**
     * 获取任务日志
     */
    public String getTaskLog(Long taskId) {
        ExecutionContext context = runningTasks.get(taskId);
        return context != null ? context.getLogBuffer() : null;
    }

    // ==================== 步骤执行 ====================

    /**
     * 执行步骤模式
     */
    @SuppressWarnings("unchecked")
    private int executeSteps(ExecutionContext context, Task task, List<TaskServer> taskServers,
                              Script script, ScriptVersion scriptVersion, Map<String, Object> stepsConfig,
                              Map<String, Map<String, Object>> stepParams) {
        
        context.log("========== 步骤执行阶段 ==========");
        
        // 构建 DAG
        StepDAG dag = new StepDAG();
        for (Map.Entry<String, Object> entry : stepsConfig.entrySet()) {
            String stepName = entry.getKey();
            
            // 跳过 _meta 步骤（仅用于存储元数据，不执行）
            if ("_meta".equals(stepName)) continue;
            
            Map<String, Object> stepDef = (Map<String, Object>) entry.getValue();
            
            String displayName = (String) stepDef.getOrDefault("displayName", stepName);
            String scriptFile = (String) stepDef.get("script");
            
            // 处理 dependsOn（可能是 List 或 null）
            List<String> dependsOn = null;
            Object dependsObj = stepDef.get("dependsOn");
            if (dependsObj instanceof List) {
                dependsOn = (List<String>) dependsObj;
            }
            
            // 获取 resultParser 和 resultCollector
            Boolean resultParser = (Boolean) stepDef.get("resultParser");
            Boolean resultCollector = (Boolean) stepDef.get("resultCollector");
            
            // 如果设置了 resultParser=true，则 resultCollector 默认也为 true
            if (Boolean.TRUE.equals(resultParser)) {
                resultCollector = (resultCollector != null) ? resultCollector : true;
            } else {
                resultCollector = (resultCollector != null) ? resultCollector : false;
            }
            
            // 处理 params（可能是 List<Map> 参数定义数组，或 Map 直接参数值）
            Object params = stepDef.get("params");
            
            Map<String, Object> startupProbe = (Map<String, Object>) stepDef.get("startupProbe");
            
            // 获取解析规则配置
            Map<String, Object> parseRule = (Map<String, Object>) stepDef.get("parseRule");
            
            // 获取步骤资源配置
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> resources = (List<Map<String, Object>>) stepDef.get("resources");
            
            // 获取文件收集配置
            Boolean fileCollectEnabled = (Boolean) stepDef.get("fileCollectEnabled");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> fileCollects = (List<Map<String, Object>>) stepDef.get("fileCollects");
            
            // 修复：addStep 第 5 个参数是 resultParser，不是 resultCollector
            // resultParser 和 resultCollector 都设置为相同的值，确保结果会被收集
            boolean resultEnabled = resultCollector != null ? resultCollector : false;
            dag.addStep(stepName, displayName, scriptFile, dependsOn, 
                       resultEnabled,  // resultParser
                       params, startupProbe,
                       resources, fileCollectEnabled != null ? fileCollectEnabled : false, fileCollects, parseRule);
        }
        
        if (dag.hasCycle()) {
            context.log("[ERROR] 检测到循环依赖，任务终止");
            return 0;
        }
        
        context.log("[INFO] 步骤数量: " + dag.getAllStepNames().size());
        
        // 初始化 TaskStep 记录
        initTaskSteps(task, taskServers, dag, stepParams);
        
        ExecutorService executor = Executors.newFixedThreadPool(Math.max(4, taskServers.size()));
        // 跟踪每个服务器的失败步骤数
        Map<Long, AtomicInteger> serverFailedSteps = new ConcurrentHashMap<>();
        for (TaskServer ts : taskServers) {
            // 本地执行的 serverId 为 null，跳过
            if (ts.getServerId() != null) {
                serverFailedSteps.put(ts.getServerId(), new AtomicInteger(0));
            }
        }
        
        try {
            while (dag.hasPendingSteps()) {
                if (context.isCancelled()) {
                    context.log("[INFO] 任务已取消");
                    break;
                }
                
                List<String> readySteps = dag.getReadySteps();
                
                // 跳过被阻塞的步骤
                for (String stepName : dag.getBlockedSteps()) {
                    context.log("[WARN] 步骤 " + stepName + " 因依赖失败而被跳过");
                    dag.markAsSkipped(stepName);
                }
                
                if (readySteps.isEmpty()) {
                    try { Thread.sleep(500); } catch (InterruptedException e) { break; }
                    continue;
                }
                
                // 并行执行可执行的步骤
                List<CompletableFuture<Void>> futures = new ArrayList<>();
                
                // 获取步骤服务器映射
                Map<String, List<Long>> stepServerMapping = task.getStepServerMappingAsLong();
                
                for (String stepName : readySteps) {
                    dag.markAsRunning(stepName);
                    StepDAG.StepConfig stepConfig = dag.getStepConfig(stepName);
                    
                    // 获取该步骤分配的服务器列表
                    List<Long> assignedServerIds = stepServerMapping != null ? stepServerMapping.get(stepName) : null;
                    if (assignedServerIds == null || assignedServerIds.isEmpty()) {
                        // 如果没有配置映射，使用所有服务器（向后兼容）
                        assignedServerIds = new ArrayList<>();
                        for (TaskServer ts : taskServers) {
                            assignedServerIds.add(ts.getServerId());
                        }
                    }
                    log.info("Execute step {} on servers: {}", stepName, assignedServerIds);
                    
                    // 只在分配的服务器上执行
                    for (TaskServer taskServer : taskServers) {
                        // 检查该服务器是否被分配执行此步骤
                        if (!assignedServerIds.contains(taskServer.getServerId())) {
                            // 检查是否是本地执行
                            if (!Boolean.TRUE.equals(taskServer.getIsLocal())) {
                                continue;
                            }
                            // 本地执行：检查是否在本地执行列表中
                            if (!isLocalExecutionAssigned(taskServer, assignedServerIds)) {
                                continue;
                            }
                        }
                        
                        Server server = null;
                        boolean isLocal = Boolean.TRUE.equals(taskServer.getIsLocal());
                        if (!isLocal) {
                            server = serverMapper.selectById(taskServer.getServerId());
                            if (server == null) continue;
                        }
                        
                        final boolean localFlag = isLocal;
                        final Server finalServer = server;
                        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                            boolean success = executeStepOnServer(context, task, finalServer, 
                                                                  script, scriptVersion, stepConfig, localFlag);
                            if (!localFlag && finalServer != null && !success) {
                                serverFailedSteps.get(finalServer.getId()).incrementAndGet();
                            }
                            dag.markAsComplete(stepName, success);
                            
                            // 同步更新 task_servers 状态
                            if (!localFlag) {
                                updateTaskServerStatus(task.getId(), finalServer.getId(), success);
                            }
                        }, executor);
                        
                        futures.add(future);
                    }
                }
                
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            }
        } finally {
            executor.shutdown();
        }
        
        // 计算成功的服务器数（没有任何失败步骤的服务器）
        int successCount = 0;
        for (TaskServer ts : taskServers) {
            // 本地执行的 serverId 为 null，只计算成功状态
            if (ts.getServerId() == null) {
                // 本地执行根据任务状态判断
                if ("success".equals(ts.getOverallStatus())) {
                    successCount++;
                }
            } else if (serverFailedSteps.get(ts.getServerId()) != null 
                       && serverFailedSteps.get(ts.getServerId()).get() == 0) {
                successCount++;
            }
        }
        
        return successCount;
    }

    /**
     * 初始化任务步骤记录
     * 根据步骤服务器映射，只为指定的服务器创建步骤记录
     */
    @SuppressWarnings("unchecked")
    private void initTaskSteps(Task task, List<TaskServer> taskServers, StepDAG dag, 
                                Map<String, Map<String, Object>> stepParams) {
        // 先删除旧的步骤记录（支持再次执行）
        LambdaQueryWrapper<TaskStep> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(TaskStep::getTaskId, task.getId());
        taskStepMapper.delete(deleteWrapper);
        
        // 获取步骤服务器映射 { "step_1": [serverId1, serverId2], "step_2": [serverId3] }
        // 使用类型安全的方法获取，自动处理 Integer -> Long 转换
        Map<String, List<Long>> stepServerMapping = task.getStepServerMappingAsLong();
        log.info("Task {} stepServerMapping: {}", task.getId(), stepServerMapping);
        
        // 获取共享参数
        Map<String, Object> sharedParams = task.getSharedParams() != null ? task.getSharedParams() : new HashMap<>();
        
        // 构建服务器ID到TaskServer的映射
        Map<Long, TaskServer> serverMap = new HashMap<>();
        for (TaskServer ts : taskServers) {
            serverMap.put(ts.getServerId(), ts);
        }
        
        for (String stepName : dag.getAllStepNames()) {
            if ("_meta".equals(stepName)) continue; // 跳过 _meta 步骤
            
            StepDAG.StepConfig config = dag.getStepConfig(stepName);
            
            // 合并参数：共享参数 + 步骤参数（步骤参数覆盖共享参数）
            Map<String, Object> mergedParams = new HashMap<>();
            if (sharedParams != null) {
                mergedParams.putAll(sharedParams);
            }
            // 添加步骤特定参数
            if (stepParams != null && stepParams.get(stepName) != null) {
                mergedParams.putAll(stepParams.get(stepName));
            }
            
            // 获取该步骤分配的服务器列表
            List<Long> assignedServerIds = stepServerMapping != null ? stepServerMapping.get(stepName) : null;
            log.info("Step {} assignedServerIds: {}", stepName, assignedServerIds);
            
            if (assignedServerIds == null || assignedServerIds.isEmpty()) {
                // 如果没有配置映射，使用所有服务器（向后兼容）
                assignedServerIds = new ArrayList<>();
                for (TaskServer ts : taskServers) {
                    assignedServerIds.add(ts.getServerId());
                }
            }
            
            // 只为分配的服务器创建 TaskStep 记录
            for (Long serverId : assignedServerIds) {
                TaskServer taskServer = serverMap.get(serverId);
                if (taskServer == null) continue; // 服务器不存在，跳过
                
                TaskStep taskStep = new TaskStep();
                taskStep.setTaskId(task.getId());
                taskStep.setServerId(serverId);
                taskStep.setStepName(stepName);
                taskStep.setDisplayName(config.getDisplayName());
                taskStep.setScript(config.getScript());
                taskStep.setDependsOn(config.getDependsOn() != null && !config.getDependsOn().isEmpty() ? 
                    String.join(",", config.getDependsOn()) : null);
                taskStep.setParams(mergedParams.isEmpty() ? null : mergedParams);
                taskStep.setResultCollector(config.isResultCollector() || config.isResultParser());
                taskStep.setStartupProbe(config.getStartupProbe());
                taskStep.setStatus("pending");
                
                taskStepMapper.insert(taskStep);
            }
        }
    }

    /**
     * 在单台服务器上执行单个步骤
     */
    @SuppressWarnings("unchecked")
    private boolean executeStepOnServer(ExecutionContext context, Task task, Server server,
                                         Script script, ScriptVersion scriptVersion,
                                         StepDAG.StepConfig stepConfig, boolean isLocal) {
        
        String stepName = stepConfig.getName();
        String serverDisplay = isLocal ? "本地环境" : server.getName();
        context.log("\n--- 步骤: " + stepConfig.getDisplayName() + " @ " + serverDisplay + " ---");
        
        // 查询对应的 TaskServer 记录
        TaskServer taskServer = taskServerMapper.selectOne(
            new LambdaQueryWrapper<TaskServer>()
                .eq(TaskServer::getTaskId, task.getId())
                .eq(TaskServer::getServerId, isLocal ? null : server.getId())
                .last(isLocal ? "LIMIT 1" : "AND server_id IS NOT NULL")
        );
        if (taskServer == null) {
            taskServer = new TaskServer();
            taskServer.setTaskId(task.getId());
            if (!isLocal) {
                taskServer.setServerId(server.getId());
            }
            taskServer.setIsLocal(isLocal);
            taskServer.setOverallStatus("running");
            taskServer.setProgress(0);
            taskServerMapper.insert(taskServer);
        }
        
        // 更新步骤状态
        TaskStep taskStep;
        if (isLocal) {
            taskStep = taskStepMapper.findByTaskAndStepNameForLocal(task.getId(), stepName);
        } else {
            taskStep = taskStepMapper.findByTaskAndStepNameAndServer(task.getId(), stepName, server.getId());
        }
        if (taskStep == null) {
            taskStep = new TaskStep();
            taskStep.setTaskId(task.getId());
            if (!isLocal) {
                taskStep.setServerId(server.getId());
            }
            taskStep.setStepName(stepName);
            taskStep.setDisplayName(stepConfig.getDisplayName());
            taskStep.setScript(stepConfig.getScript());
        }
        taskStep.setStatus("running");
        taskStep.setStartedAt(LocalDateTime.now());
        
        if (taskStep.getId() == null) {
            taskStepMapper.insert(taskStep);
        } else {
            taskStepMapper.updateById(taskStep);
        }
        
        try {
            // 本地执行模式
            if (isLocal) {
                return executeStepLocally(context, task, scriptVersion, stepConfig, taskStep, taskServer);
            }
            
            // SSH 远程执行模式（原有逻辑）
            // 注意：server 参数在 isLocal=true 时为 null，需要先检查
            if (server == null) {
                context.log("[ERROR] 服务器不能为空");
                taskStep.setStatus("failed");
                taskStep.setErrorMessage("服务器不能为空");
                taskStepMapper.updateById(taskStep);
                return false;
            }
            
            String workDir = "/tmp/test_platform/task_" + task.getId();
            SshService.executeCommand(server, "mkdir -p " + workDir, null, 10000);
            
            // 上传脚本文件
            context.log("上传脚本文件...");
            if (!uploadAllScriptFiles(context, server, scriptVersion, workDir)) {
                taskStep.setStatus("failed");
                taskStep.setErrorMessage("脚本上传失败");
                taskStepMapper.updateById(taskStep);
                return false;
            }
            
            // 确定要执行的脚本
            String scriptFile = stepConfig.getScript();
            String scriptPath;
            if (scriptFile != null && !scriptFile.isEmpty()) {
                // 清理路径：移除 ./ 前缀和多余斜杠
                scriptFile = scriptFile.replaceAll("^\\./", "").replaceAll("/+", "/");
                scriptPath = workDir + "/" + scriptFile;
            } else {
                scriptPath = workDir + "/script.sh";
            }
            
            SshService.executeCommand(server, "chmod +x " + scriptPath, null, 5000);
            
            // 上传步骤专属资源
            uploadStepResources(context, server, task, stepConfig, workDir);
            
            // 构建参数
            Map<String, Object> params = new HashMap<>();
            
            // 添加内置参数
            params.put("TASK_ID", task.getId());
            params.put("SCRIPT_ID", task.getScriptId());
            params.put("TASK_NAME", task.getName() != null ? task.getName() : "");
            params.put("SCRIPT_VERSION", task.getScriptVersion() != null ? task.getScriptVersion() : "");
            params.put("SERVER_ID", server.getId());
            params.put("SERVER_NAME", server.getName() != null ? server.getName() : "");
            params.put("SERVER_HOST", server.getHost() != null ? server.getHost() : "");
            
            // 添加用户定义的共享参数
            if (task.getSharedParams() != null) {
                params.putAll(task.getSharedParams());
            }
            
            // 添加步骤专属参数（步骤参数会覆盖共享参数）
            // 注意：stepConfig.getParams() 返回的是参数定义数组，格式如：
            // [{"name": "MODE", "defaultValue": "server"}, ...]
            // 需要从中提取 name 和 defaultValue
            if (stepConfig.getParams() != null) {
                Object paramsObj = stepConfig.getParams();
                log.info("Step {} params object type: {}, value: {}", stepName, paramsObj.getClass().getName(), paramsObj);
                if (paramsObj instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> paramDefs = (List<Map<String, Object>>) paramsObj;
                    for (Map<String, Object> paramDef : paramDefs) {
                        String paramName = (String) paramDef.get("name");
                        Object paramValue = paramDef.get("defaultValue");
                        log.info("Step param: {} = {}", paramName, paramValue);
                        if (paramName != null && paramValue != null) {
                            params.put(paramName, paramValue);
                        }
                    }
                } else if (paramsObj instanceof Map) {
                    // 兼容旧格式：直接是 Map<String, Object>
                    @SuppressWarnings("unchecked")
                    Map<String, Object> paramsMap = (Map<String, Object>) paramsObj;
                    params.putAll(paramsMap);
                }
            }
            
            // 判断是否后台执行
            boolean isBackground = false;
            if (task.getStepParams() != null) {
                Map<String, Object> stepParam = task.getStepParams().get(stepName);
                if (stepParam != null && Boolean.TRUE.equals(stepParam.get("_BACKGROUND"))) {
                    isBackground = true;
                }
            }
            
            // 构建环境变量
            StringBuilder envBuilder = new StringBuilder();
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                envBuilder.append("export ").append(entry.getKey()).append("=\"").append(entry.getValue()).append("\"; ");
            }
            
            // 执行命令
            String command = String.format("cd %s && %s bash %s", workDir, envBuilder, scriptPath);
            context.log("执行: " + scriptPath);
            
            // 保存执行命令到 taskStep
            taskStep.setCommand(command);
            taskStepMapper.updateById(taskStep);
            
            // 后台执行
            if (isBackground) {
                return executeBackgroundStep(context, task, server, stepConfig, taskStep, workDir, scriptPath, envBuilder.toString());
            }
            
            // 更新 TaskServer 当前执行状态（仅远程执行时）
            TaskServer tsForUpdate = taskServerMapper.selectOne(
                new LambdaQueryWrapper<TaskServer>()
                    .eq(TaskServer::getTaskId, task.getId())
                    .eq(TaskServer::getServerId, server.getId())
            );
            if (tsForUpdate != null) {
                tsForUpdate.setCurrentPhase("run");
                tsForUpdate.setCurrentCommand(command);
                tsForUpdate.setCommandStartedAt(LocalDateTime.now());
                taskServerMapper.updateById(tsForUpdate);
            }
            
            // 用于收集完整日志（任务完成时写入数据库）
            StringBuilder logBuilder = new StringBuilder();
            // 用于处理不完整行的缓冲区（用于数据库存储）
            StringBuilder lineBuffer = new StringBuilder();
            
            // 实时日志回调：直接推送数据块 + 按行存储
            Consumer<String> logConsumer = chunk -> {
                synchronized(logBuilder) {
                    // 直接推送原始数据块到 WebSocket（实时性更好）
                    logCacheService.appendChunk(task.getId(), chunk);
                    
                    // 按行处理存储到数据库
                    lineBuffer.append(chunk);
                    int newlineIndex;
                    while ((newlineIndex = lineBuffer.indexOf("\n")) >= 0) {
                        String line = lineBuffer.substring(0, newlineIndex);
                        line = line.replace("\r", "");
                        logBuilder.append(line).append("\n");
                        lineBuffer.delete(0, newlineIndex + 1);
                    }
                }
            };
            
            int timeout = task.getTimeout() != null ? task.getTimeout() : 86400000; // 使用任务配置的超时时间，默认 24 小时
            // 使用 PTY 模式执行用户脚本，以支持 fio 等实时更新输出的程序
            SshService.ExecuteResult execResult = SshService.executeCommandPty(server, command, logConsumer, timeout);
            
            // 处理缓冲区中剩余的内容
            synchronized(logBuilder) {
                if (lineBuffer.length() > 0) {
                    String remaining = lineBuffer.toString().replace("\r", "");
                    logBuilder.append(remaining).append("\n");
                }
            }
            
            context.log("退出码: " + execResult.getExitCode());
            
            // 清除 TaskServer 当前执行状态
            TaskServer taskServerUpdate = taskServerMapper.selectOne(
                new LambdaQueryWrapper<TaskServer>()
                    .eq(TaskServer::getTaskId, task.getId())
                    .eq(TaskServer::getServerId, server.getId())
            );
            if (taskServerUpdate != null) {
                taskServerUpdate.setCurrentPhase(null);
                taskServerUpdate.setCurrentCommand(null);
                taskServerUpdate.setCommandStartedAt(null);
                taskServerMapper.updateById(taskServerUpdate);
            }
            
            // 任务完成时写入数据库（一次性）
            taskStep.setExitCode(execResult.getExitCode());
            taskStep.setOutput(logBuilder.toString());
            taskStep.setFinishedAt(LocalDateTime.now());
            
            boolean success = execResult.getExitCode() == 0;
            taskStep.setStatus(success ? "success" : "failed");
            
            // 启动探测
            if (success && stepConfig.getStartupProbe() != null) {
                if (!executeStartupProbe(context, server, stepConfig.getStartupProbe())) {
                    taskStep.setStatus("failed");
                    taskStep.setErrorMessage("启动探测失败");
                    success = false;
                }
            }
            
            // 结果收集
            if (success && (Boolean.TRUE.equals(stepConfig.isResultCollector()) || Boolean.TRUE.equals(stepConfig.isResultParser()))) {
                Map<String, Object> parseRule = stepConfig.getParseRule();
                String fileContent = null;
                
                context.log("[DEBUG] 开始结果收集，parseRule=" + (parseRule != null ? "not null" : "null"));
                
                if (parseRule != null) {
                    String inputSource = (String) parseRule.get("inputSource");
                    String filePattern = (String) parseRule.get("filePattern");
                    
                    context.log("[DEBUG] inputSource=" + inputSource + ", filePattern=" + filePattern);
                    
                    if ("file".equals(inputSource) && filePattern != null && !filePattern.isEmpty()) {
                        String actualFilePath = replaceBuiltInParams(filePattern, task, server);
                        context.log("读取结果文件：" + actualFilePath);
                        
                        SshService.ExecuteResult fileResult = SshService.executeCommand(server, "cat " + actualFilePath, null, 30000);
                        
                        if (fileResult.getExitCode() == 0 && fileResult.getOutput() != null && !fileResult.getOutput().isEmpty()) {
                            fileContent = fileResult.getOutput();
                            context.log("文件内容长度：" + fileContent.length() + " 字符");
                        } else {
                            context.log("[WARN] 读取结果文件失败：" + fileResult.getError());
                        }
                    } else if ("stdout".equals(inputSource)) {
                        fileContent = taskStep.getOutput();
                        context.log("使用标准输出，内容长度：" + (fileContent != null ? fileContent.length() : 0) + " 字符");
                    }
                }
                
                context.log("[DEBUG] 调用 createTestResult, fileContent=" + (fileContent != null ? "not null, length=" + fileContent.length() : "null"));
                createTestResult(task, server, taskStep, scriptVersion, parseRule, fileContent, context);
            }
            
            taskStepMapper.updateById(taskStep);
            return success;
            
        } catch (Exception e) {
            context.log("[ERROR] 步骤执行异常: " + e.getMessage());
            taskStep.setStatus("failed");
            taskStep.setErrorMessage(e.getMessage());
            taskStep.setFinishedAt(LocalDateTime.now());
            taskStepMapper.updateById(taskStep);
            return false;
        }
    }

    /**
     * 默认执行模式（兼容旧脚本）
     */
    @SuppressWarnings("unchecked")
    private int executeDefaultStep(ExecutionContext context, Task task, List<TaskServer> taskServers,
                                    Script script, ScriptVersion scriptVersion) {
        
        context.log("========== 默认执行模式 ==========");
        
        StepDAG.StepConfig defaultStep = new StepDAG.StepConfig();
        defaultStep.setName("default");
        defaultStep.setDisplayName("执行测试");
        defaultStep.setResultCollector(true);
        
        // 从 fileList 中自动检测入口脚本
        String scriptFile = null;
        if (scriptVersion.getFileList() != null) {
            for (Object item : scriptVersion.getFileList()) {
                Map<String, Object> fileInfo = (Map<String, Object>) item;
                String path = (String) fileInfo.get("path");
                String name = (String) fileInfo.get("name");
                String filePath = path != null ? path : name;
                // 优先检测 main.sh, main.py, script.sh 等
                if (filePath != null) {
                    if (filePath.equals("main.sh") || filePath.equals("main.py") || 
                        filePath.endsWith("/main.sh") || filePath.endsWith("/main.py")) {
                        scriptFile = filePath;
                        break;
                    }
                    if (scriptFile == null && (filePath.endsWith(".sh") || filePath.endsWith(".py"))) {
                        scriptFile = filePath;
                    }
                }
            }
        }
        
        if (scriptFile == null) {
            context.log("[ERROR] 无法确定要执行的脚本，请配置步骤");
            return 0;
        }
        
        context.log("自动检测到入口脚本: " + scriptFile);
        defaultStep.setScript(scriptFile);
        
        int successCount = 0;
        for (TaskServer taskServer : taskServers) {
            boolean isLocal = Boolean.TRUE.equals(taskServer.getIsLocal());
            Server server = null;
            if (!isLocal) {
                server = serverMapper.selectById(taskServer.getServerId());
                if (server == null) continue;
            }
            
            if (executeStepOnServer(context, task, server, script, scriptVersion, defaultStep, isLocal)) {
                successCount++;
            }
        }
        
        return successCount;
    }

    // ==================== 辅助方法 ====================

    /**
     * 资源上传
     */
    private void uploadResources(ExecutionContext context, Task task, List<TaskServer> taskServers, Script script) {
        List<ScriptResource> resources = scriptResourceMapper.findByScriptIdWithResource(script.getId());
        if (resources == null || resources.isEmpty()) {
            return;
        }
        
        // 任务工作目录
        String workDir = "/tmp/test_platform/task_" + task.getId();
        
        context.log("========== 资源上传阶段 ==========");
        for (TaskServer taskServer : taskServers) {
            Server server = serverMapper.selectById(taskServer.getServerId());
            if (server == null) continue;
            
            context.log("上传资源到服务器: " + server.getName());
            for (ScriptResource sr : resources) {
                try {
                    ResourceFile rf = resourceFileMapper.selectById(sr.getResourceId());
                    if (rf == null) continue;
                    
                    String localPath = Paths.get(scriptsPath.replace("scripts", "resources"), rf.getStoragePath()).toString();
                    
                    // 判断目标路径是绝对路径还是相对路径
                    String srTargetPath = sr.getTargetPath();
                    String targetPath;
                    if (srTargetPath != null && srTargetPath.startsWith("/")) {
                        // 绝对路径
                        // 判断是否是目录（不以文件扩展名结尾）
                        boolean isDirectory = !srTargetPath.matches(".*\\.[a-zA-Z0-9]{1,10}$");
                        if (isDirectory) {
                            // 是目录：创建目录，然后追加文件名
                            SshService.executeCommand(server, "mkdir -p " + srTargetPath, null, 10000);
                            targetPath = srTargetPath + "/" + rf.getName();
                        } else {
                            // 是文件：确保父目录存在
                            String parentDir = srTargetPath.substring(0, srTargetPath.lastIndexOf("/"));
                            if (!parentDir.isEmpty()) {
                                SshService.executeCommand(server, "mkdir -p " + parentDir, null, 10000);
                            }
                            targetPath = srTargetPath;
                        }
                    } else {
                        // 相对路径：拼接到任务工作目录
                        targetPath = workDir + "/" + (srTargetPath != null ? srTargetPath : rf.getName());
                    }
                    
                    context.log("  上传: " + rf.getName() + " -> " + targetPath);
                    SshService.uploadFile(server, localPath, targetPath);
                    SshService.executeCommand(server, "chmod " + sr.getPermissions() + " " + targetPath, null, 5000);
                } catch (Exception e) {
                    context.log("[ERROR] 上传资源失败: " + e.getMessage());
                }
            }
        }
    }

    /**
     * 上传步骤专属资源
     */
    @SuppressWarnings("unchecked")
    private void uploadStepResources(ExecutionContext context, Server server, Task task,
                                      StepDAG.StepConfig stepConfig, String workDir) {
        List<Map<String, Object>> resources = stepConfig.getResources();
        if (resources == null || resources.isEmpty()) {
            return;
        }
        
        context.log("上传步骤资源: " + resources.size() + " 个文件");
        
        for (Map<String, Object> res : resources) {
            try {
                Object resourceIdObj = res.get("resourceId");
                if (resourceIdObj == null) continue;
                
                Long resourceId;
                if (resourceIdObj instanceof Long) {
                    resourceId = (Long) resourceIdObj;
                } else if (resourceIdObj instanceof Integer) {
                    resourceId = ((Integer) resourceIdObj).longValue();
                } else if (resourceIdObj instanceof Number) {
                    resourceId = ((Number) resourceIdObj).longValue();
                } else {
                    context.log("[WARN] 无效的 resourceId 类型: " + resourceIdObj.getClass());
                    continue;
                }
                
                ResourceFile rf = resourceFileMapper.selectById(resourceId);
                if (rf == null) {
                    context.log("[WARN] 资源文件不存在: " + resourceId);
                    continue;
                }
                
                String localPath = Paths.get(scriptsPath.replace("scripts", "resources"), rf.getStoragePath()).toString();
                
                // 获取目标路径和权限
                String targetPathStr = (String) res.get("targetPath");
                String permissions = (String) res.getOrDefault("permissions", "644");
                
                // 判断目标路径是绝对路径还是相对路径
                String targetPath;
                if (targetPathStr != null && targetPathStr.startsWith("/")) {
                    // 绝对路径
                    // 判断是否是目录（不以文件扩展名结尾）
                    boolean isDirectory = !targetPathStr.matches(".*\\.[a-zA-Z0-9]{1,10}$");
                    if (isDirectory) {
                        // 是目录：创建目录，然后追加文件名
                        SshService.executeCommand(server, "mkdir -p " + targetPathStr, null, 10000);
                        targetPath = targetPathStr + "/" + rf.getName();
                    } else {
                        // 是文件：确保父目录存在
                        String parentDir = targetPathStr.substring(0, targetPathStr.lastIndexOf("/"));
                        if (!parentDir.isEmpty()) {
                            SshService.executeCommand(server, "mkdir -p " + parentDir, null, 10000);
                        }
                        targetPath = targetPathStr;
                    }
                } else {
                    // 相对路径：拼接到任务工作目录
                    targetPath = workDir + "/" + (targetPathStr != null ? targetPathStr : rf.getName());
                }
                
                context.log("  上传步骤资源: " + rf.getName() + " -> " + targetPath);
                SshService.uploadFile(server, localPath, targetPath);
                SshService.executeCommand(server, "chmod " + permissions + " " + targetPath, null, 5000);
                
            } catch (Exception e) {
                context.log("[ERROR] 上传步骤资源失败: " + e.getMessage());
            }
        }
    }

    /**
     * 上传所有脚本文件
     */
    @SuppressWarnings("unchecked")
    private boolean uploadAllScriptFiles(ExecutionContext context, Server server, 
                                          ScriptVersion scriptVersion, String workDir) {
        try {
            String storagePath = scriptVersion.getStoragePath();
            String version = scriptVersion.getVersion();
            
            if (storagePath == null || storagePath.isEmpty()) {
                context.log("[ERROR] 脚本存储路径为空");
                return false;
            }
            
            // 检查 storagePath 是否已包含版本号子目录
            Path scriptDir = Paths.get(storagePath);
            if (!Files.exists(scriptDir) || !Files.isDirectory(scriptDir)) {
                context.log("[ERROR] 脚本目录不存在: " + scriptDir);
                return false;
            }
            
            // 检查是否需要添加版本号子目录（兼容旧数据）
            Path actualScriptDir = scriptDir;
            if (version != null && !storagePath.endsWith(version)) {
                Path versionDir = scriptDir.resolve(version);
                if (Files.exists(versionDir) && Files.isDirectory(versionDir)) {
                    actualScriptDir = versionDir;
                }
            }
            
            context.log("脚本目录: " + actualScriptDir);
            
            List<Map<String, Object>> fileList = scriptVersion.getFileList();
            if (fileList == null || fileList.isEmpty()) {
                // 上传目录下所有文件
                final Path finalScriptDir = actualScriptDir;
                Files.walk(finalScriptDir)
                    .filter(path -> !Files.isDirectory(path))
                    .forEach(path -> {
                        String relativePath = finalScriptDir.relativize(path).toString().replace("\\", "/");
                        String targetPath = workDir + "/" + relativePath;
                        try {
                            SshService.uploadFile(server, path.toString(), targetPath);
                        } catch (Exception e) {
                            context.log("[ERROR] 上传文件失败: " + relativePath);
                        }
                    });
            } else {
                // 上传指定文件
                for (Map<String, Object> fileInfo : fileList) {
                    // 优先使用 path（完整相对路径），否则用 name
                    String relativePath = (String) fileInfo.get("path");
                    if (relativePath == null || relativePath.isEmpty()) {
                        relativePath = (String) fileInfo.get("name");
                    }
                    if (relativePath == null) continue;
                    
                    // 清理路径：移除 ./ 前缀和多余斜杠
                    relativePath = relativePath.replaceAll("^\\./", "").replaceAll("/+", "/");
                    
                    Path localPath = actualScriptDir.resolve(relativePath);
                    
                    // 如果文件不存在，尝试在原始目录查找
                    if (!Files.exists(localPath)) {
                        Path altPath = scriptDir.resolve(relativePath);
                        if (Files.exists(altPath)) {
                            localPath = altPath;
                        }
                    }
                    
                    String targetPath = workDir + "/" + relativePath;
                    
                    if (Files.exists(localPath)) {
                        SshService.uploadFile(server, localPath.toString(), targetPath);
                    } else {
                        context.log("[WARN] 文件不存在: " + localPath);
                    }
                }
            }
            
            return true;
        } catch (Exception e) {
            context.log("[ERROR] 脚本上传异常: " + e.getMessage());
            return false;
        }
    }

    /**
     * 后台执行步骤
     */
    private boolean executeBackgroundStep(ExecutionContext context, Task task, Server server,
                                          StepDAG.StepConfig stepConfig, TaskStep taskStep,
                                          String workDir, String scriptPath, String envBuilder) {
        String stepName = stepConfig.getName();
        String outputFile = workDir + "/" + stepName + ".log";
        String pidFile = workDir + "/" + stepName + ".pid";
        
        context.log("[BACKGROUND] " + stepName);
        
        String exitCodeFile = workDir + "/" + stepName + ".exit_code";
        
        // 构建 nohup 命令（执行完成后写入退出码）
        String command = String.format(
            "cd %s && %s nohup bash -c 'bash %s; echo $? > %s' > %s 2>&1 & echo $! > %s && sleep 1 && cat %s",
            workDir, envBuilder, scriptPath, exitCodeFile, outputFile, pidFile, pidFile
        );
        
        context.log("命令: nohup bash " + scriptPath);
        
        try {
            // 执行命令
            SshService.ExecuteResult result = SshService.executeCommand(server, command, null, SshService.getDefaultTimeout());
            
            if (result.getExitCode() == 0) {
                String pid = result.getOutput().trim();
                context.log("PID: " + pid);
                context.log("日志文件: " + outputFile);
                
                // 更新步骤状态
                taskStep.setOutput("后台执行中\nPID: " + pid + "\n日志文件: " + outputFile);
                taskStepMapper.updateById(taskStep);
                
                // 推送日志到 WebSocket
                logCacheService.appendChunk(task.getId(), "[后台执行] PID: " + pid + "\n");
                
                return true;
            } else {
                context.log("[ERROR] 启动后台任务失败: " + result.getError());
                taskStep.setStatus("failed");
                taskStep.setErrorMessage("启动后台任务失败: " + result.getError());
                taskStep.setFinishedAt(LocalDateTime.now());
                taskStepMapper.updateById(taskStep);
                return false;
            }
        } catch (Exception e) {
            context.log("[ERROR] 启动后台任务异常: " + e.getMessage());
            taskStep.setStatus("failed");
            taskStep.setErrorMessage(e.getMessage());
            taskStep.setFinishedAt(LocalDateTime.now());
            taskStepMapper.updateById(taskStep);
            return false;
        }
    }
    
    /**
     * 执行启动探测
     */
    @SuppressWarnings("unchecked")
    private boolean executeStartupProbe(ExecutionContext context, Server server, Map<String, Object> probe) {
        String type = (String) probe.getOrDefault("type", "tcp");
        Integer timeout = (Integer) probe.getOrDefault("timeoutSeconds", 60);
        
        context.log("[INFO] 执行启动探测 (" + type + "), 超时: " + timeout + "s");
        
        long startTime = System.currentTimeMillis();
        long timeoutMs = timeout * 1000L;
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                if ("tcp".equals(type)) {
                    Integer port = (Integer) probe.get("port");
                    if (port == null) return true;
                    
                    String probeCommand = String.format(
                        "bash -c 'cat < /dev/tcp/127.0.0.1/%d' 2>/dev/null && echo OK || echo FAIL", port);
                    
                    SshService.ExecuteResult result = SshService.executeCommand(server, probeCommand, null, 10000);
                    if (result.getExitCode() == 0 && result.getOutput().contains("OK")) {
                        return true;
                    }
                } else if ("http".equals(type)) {
                    String url = (String) probe.get("url");
                    Integer port = (Integer) probe.get("port");
                    String fullUrl = url;
                    if (port != null && url != null && !url.startsWith("http")) {
                        fullUrl = "http://127.0.0.1:" + port + url;
                    }
                    
                    String probeCommand = String.format("curl -sf '%s' -o /dev/null && echo OK || echo FAIL", fullUrl);
                    SshService.ExecuteResult result = SshService.executeCommand(server, probeCommand, null, 10000);
                    if (result.getExitCode() == 0 && result.getOutput().contains("OK")) {
                        return true;
                    }
                } else {
                    String probeCommand = (String) probe.get("command");
                    if (probeCommand == null) return true;
                    
                    SshService.ExecuteResult result = SshService.executeCommand(server, probeCommand, null, 10000);
                    if (result.getExitCode() == 0) {
                        return true;
                    }
                }
            } catch (Exception e) {
                log.debug("探测失败: {}", e.getMessage());
            }
            
            try { Thread.sleep(3000); } catch (InterruptedException e) { break; }
        }
        
        context.log("[WARN] 启动探测超时");
        return false;
    }

    /**
     * 获取脚本版本
     */
    private ScriptVersion getScriptVersion(Long scriptId, String version) {
        LambdaQueryWrapper<ScriptVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ScriptVersion::getScriptId, scriptId)
               .eq(ScriptVersion::getVersion, version != null ? version : "v1.0.0");
        return scriptVersionMapper.selectOne(wrapper);
    }

    /**
     * 获取任务服务器
     */
    private List<TaskServer> getTaskServers(Long taskId) {
        LambdaQueryWrapper<TaskServer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TaskServer::getTaskId, taskId);
        return taskServerMapper.selectList(wrapper);
    }

    /**
     * 更新 TaskServer 状态
     */
    private void updateTaskServerStatus(Long taskId, Long serverId, boolean stepSuccess) {
        LambdaQueryWrapper<TaskServer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TaskServer::getTaskId, taskId)
               .eq(TaskServer::getServerId, serverId);
        TaskServer taskServer = taskServerMapper.selectOne(wrapper);
        
        if (taskServer != null) {
            taskServer.setOverallStatus(stepSuccess ? "completed" : "failed");
            taskServer.setProgress(100);
            taskServerMapper.updateById(taskServer);
        }
    }

    // ==================== 内部类 ====================

    /**
     * 执行上下文
     */
    private static class ExecutionContext {
        private final Long taskId;
        private final Consumer<String> logCallback;
        private final StringBuilder logBuffer = new StringBuilder();
        private volatile boolean cancelled = false;

        public ExecutionContext(Long taskId, Consumer<String> logCallback) {
            this.taskId = taskId;
            this.logCallback = logCallback;
        }

        public void log(String message) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            String logLine = "[" + timestamp + "] " + message + "\n";
            logBuffer.append(logLine);
            if (logCallback != null) {
                logCallback.accept(logLine);
            }
        }

        public String getLogBuffer() {
            return logBuffer.toString();
        }

        public void cancel() {
            this.cancelled = true;
        }

        public boolean isCancelled() {
            return cancelled;
        }
    }
    
    /**
     * 替换字符串中的内置参数
     */
    public static String replaceBuiltInParams(String input, Task task, Server server) {
        if (input == null || input.isEmpty()) return input;
        String result = input;
        if (task != null) {
            result = result.replace("${TASK_ID}", String.valueOf(task.getId()));
            result = result.replace("${SCRIPT_ID}", String.valueOf(task.getScriptId()));
            result = result.replace("${TASK_NAME}", task.getName() != null ? task.getName() : "");
            result = result.replace("${SCRIPT_VERSION}", task.getScriptVersion() != null ? task.getScriptVersion() : "");
        }
        if (server != null) {
            result = result.replace("${SERVER_ID}", String.valueOf(server.getId()));
            result = result.replace("${SERVER_NAME}", server.getName() != null ? server.getName() : "");
            result = result.replace("${SERVER_HOST}", server.getHost() != null ? server.getHost() : "");
        }
        return result;
    }
    
    /**
     * 创建测试结果
     */
    private void createTestResult(Task task, Server server, TaskStep taskStep, ScriptVersion scriptVersion,
                                   Map<String, Object> parseRule, String fileContent, ExecutionContext context) {
        LambdaQueryWrapper<TaskServer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TaskServer::getTaskId, task.getId()).eq(TaskServer::getServerId, server.getId());
        TaskServer taskServer = taskServerMapper.selectOne(wrapper);
        
        TestResult testResult = new TestResult();
        testResult.setTaskId(task.getId());
        testResult.setTaskName(task.getName());  // 冗余字段
        testResult.setServerId(server.getId());
        testResult.setServerName(server.getName());  // 冗余字段
        testResult.setServerIp(server.getHost());  // 冗余字段
        testResult.setTaskServerId(taskServer != null ? taskServer.getId() : null);
        testResult.setResult(taskStep.getExitCode() == 0 ? "pass" : "fail");
        testResult.setExitCode(taskStep.getExitCode());
        testResult.setRawOutput(taskStep.getOutput());
        testResult.setStartedAt(taskStep.getStartedAt());
        testResult.setFinishedAt(taskStep.getFinishedAt());
        
        // 获取脚本名称（冗余）
        if (scriptVersion != null && scriptVersion.getScriptId() != null) {
            Script script = scriptMapper.selectById(scriptVersion.getScriptId());
            if (script != null) {
                testResult.setScriptName(script.getName());
            }
        }
        
        if (parseRule != null && fileContent != null && !fileContent.isEmpty()) {
            try {
                ResultRule rule = convertToResultRule(parseRule);
                context.log("开始解析，parserType=" + rule.getParserType() + ", format=" + rule.getBuiltinFormat());
                Map<String, Object> parsedData = resultParseService.parse(fileContent, rule);
                testResult.setParsedData(parsedData);
                context.log("解析成功：" + parsedData.size() + " 个字段");
            } catch (Exception e) {
                context.log("[WARN] 解析失败：" + e.getMessage());
                log.error("解析失败", e);
                testResult.setResultReason("解析失败：" + e.getMessage());
            }
        } else {
            context.log("[INFO] 跳过解析：parseRule=" + (parseRule == null) + ", fileContent=" + (fileContent == null || fileContent.isEmpty()));
        }
        
        testResultMapper.insert(testResult);
    }
    
    /**
     * 将 Map 转换为 ResultRule 对象
     */
    private ResultRule convertToResultRule(Map<String, Object> parseRule) {
        ResultRule rule = new ResultRule();
        rule.setParserType((String) parseRule.get("parserType"));
        rule.setBuiltinFormat((String) parseRule.get("builtinFormat"));
        rule.setInputSource((String) parseRule.get("inputSource"));
        rule.setFilePattern((String) parseRule.get("filePattern"));
        rule.setScriptSource((String) parseRule.get("scriptSource"));
        rule.setScriptContent((String) parseRule.get("scriptContent"));
        rule.setScriptLanguage((String) parseRule.get("scriptLanguage"));
        return rule;
    }
    
    /**
     * 在本地执行步骤（通过 LocalExecutor）
     */
    @SuppressWarnings("unchecked")
    private boolean executeStepLocally(ExecutionContext context, Task task, 
                                         ScriptVersion scriptVersion, StepDAG.StepConfig stepConfig,
                                         TaskStep taskStep, TaskServer taskServer) {
        try {
            // 获取本地执行器
            LocalExecutor executor = localExecutorFactory.getDefaultExecutor();
            if (executor == null) {
                context.log("[ERROR] 无可用的本地执行器");
                taskStep.setStatus("failed");
                taskStep.setErrorMessage("无可用的本地执行器");
                taskStepMapper.updateById(taskStep);
                return false;
            }
            
            context.log("使用本地执行器: " + executor.getType());
            
            // 构建参数
            Map<String, Object> params = new HashMap<>();
            
            // 添加内置参数
            params.put("TASK_ID", task.getId());
            params.put("SCRIPT_ID", task.getScriptId());
            params.put("TASK_NAME", task.getName() != null ? task.getName() : "");
            params.put("SCRIPT_VERSION", task.getScriptVersion() != null ? task.getScriptVersion() : "");
            
            // 添加用户定义的共享参数
            if (task.getSharedParams() != null) {
                params.putAll(task.getSharedParams());
            }
            
            // 添加步骤专属参数
            if (stepConfig.getParams() != null) {
                Object paramsObj = stepConfig.getParams();
                if (paramsObj instanceof List) {
                    List<Map<String, Object>> paramDefs = (List<Map<String, Object>>) paramsObj;
                    for (Map<String, Object> paramDef : paramDefs) {
                        String paramName = (String) paramDef.get("name");
                        Object paramValue = paramDef.get("defaultValue");
                        if (paramName != null && paramValue != null) {
                            params.put(paramName, paramValue);
                        }
                    }
                } else if (paramsObj instanceof Map) {
                    params.putAll((Map<String, Object>) paramsObj);
                }
            }
            
            // 确定要执行的脚本（优先使用步骤配置）
            String scriptFile = stepConfig.getScript();
            if (scriptFile == null || scriptFile.isEmpty()) {
                // 如果步骤没有配置脚本，尝试从 fileList 中自动检测
                if (scriptVersion.getFileList() != null) {
                    for (Object item : scriptVersion.getFileList()) {
                        if (item instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> fileInfo = (Map<String, Object>) item;
                            String path = (String) fileInfo.get("path");
                            String name = (String) fileInfo.get("name");
                            String filePath = path != null ? path : name;
                            if (filePath != null && (filePath.equals("main.sh") || filePath.equals("main.py"))) {
                                scriptFile = filePath;
                                break;
                            }
                        }
                    }
                }
            }
            
            if (scriptFile == null || scriptFile.isEmpty()) {
                context.log("[ERROR] 未指定要执行的脚本");
                taskStep.setStatus("failed");
                taskStep.setErrorMessage("未指定要执行的脚本，请在步骤配置中设置 script 字段");
                taskStepMapper.updateById(taskStep);
                return false;
            }
            
            // 保存执行命令
            taskStep.setCommand("本地执行: " + scriptFile);
            taskStepMapper.updateById(taskStep);
            
            // 日志回调
            Consumer<String> logConsumer = line -> {
                context.log(line);
            };
            
            // 执行脚本（使用步骤配置中的脚本文件）
            ExecutionResult result = executor.execute(
                scriptVersion,
                scriptFile,
                params,
                taskServer,
                null,  // 本地执行时 server 参数为 null，目标服务器信息通过参数传递
                logConsumer
            );
            
            // 更新结果
            context.log("退出码: " + result.getExitCode());
            
            taskStep.setExitCode(result.getExitCode());
            taskStep.setOutput(result.getOutput());
            taskStep.setFinishedAt(LocalDateTime.now());
            
            boolean success = result.getExitCode() == 0;
            taskStep.setStatus(success ? "success" : "failed");
            
            if (!success && result.getError() != null && !result.getError().isEmpty()) {
                taskStep.setErrorMessage(result.getError());
            }
            
            taskStepMapper.updateById(taskStep);
            
            return success;
            
        } catch (Exception e) {
            log.error("本地执行失败", e);
            context.log("[ERROR] 本地执行失败: " + e.getMessage());
            taskStep.setStatus("failed");
            taskStep.setErrorMessage("本地执行失败: " + e.getMessage());
            taskStepMapper.updateById(taskStep);
            return false;
        }
    }
    
    /**
     * 检查本地执行是否被分配到当前步骤
     */
    private boolean isLocalExecutionAssigned(TaskServer taskServer, List<Long> assignedServerIds) {
        if (!Boolean.TRUE.equals(taskServer.getIsLocal())) {
            return false;
        }
        // 本地执行使用特殊的标记（-1 表示本地）
        return assignedServerIds != null && assignedServerIds.contains(-1L);
    }
    
    // ==================== 步骤重试功能 ====================
    
    /**
     * 正在重试的步骤ID集合（防止重复重试）
     */
    private static final Set<Long> retryingSteps = ConcurrentHashMap.newKeySet();
    
    /**
     * 重试单个步骤
     * @param taskId 任务ID
     * @param stepId 步骤ID
     * @param cascade 是否级联执行下游步骤
     * @return 重试结果
     */
    public Map<String, Object> retryStep(Long taskId, Long stepId, boolean cascade) {
        Map<String, Object> result = new LinkedHashMap<>();
        
        // 防止重复重试
        if (!retryingSteps.add(stepId)) {
            result.put("success", false);
            result.put("error", "步骤正在重试中，请勿重复操作");
            return result;
        }
        
        try {
            // 1. 校验任务状态
            Task task = taskMapper.selectById(taskId);
            if (task == null) {
                result.put("success", false);
                result.put("error", "任务不存在");
                return result;
            }
            
            if (!isTaskFinished(task.getStatus())) {
                result.put("success", false);
                result.put("error", "任务未完成，无法重试步骤");
                return result;
            }
            
            // 2. 获取步骤信息
            TaskStep taskStep = taskStepMapper.selectById(stepId);
            if (taskStep == null) {
                result.put("success", false);
                result.put("error", "步骤不存在");
                return result;
            }
            
            if (!taskStep.getTaskId().equals(taskId)) {
                result.put("success", false);
                result.put("error", "步骤不属于该任务");
                return result;
            }
            
            // 3. 校验步骤状态（失败、跳过、成功的步骤都可以重试）
            if (!"failed".equals(taskStep.getStatus()) && !"skipped".equals(taskStep.getStatus()) && !"success".equals(taskStep.getStatus())) {
                result.put("success", false);
                result.put("error", "只有失败、跳过或成功的步骤可以重试");
                return result;
            }
            
            // 4. 获取服务器信息
            Server server = null;
            boolean isLocal = false;
            if (taskStep.getServerId() != null) {
                server = serverMapper.selectById(taskStep.getServerId());
                if (server == null) {
                    result.put("success", false);
                    result.put("error", "服务器不存在");
                    return result;
                }
                if (!"online".equals(server.getStatus())) {
                    result.put("success", false);
                    result.put("error", "服务器离线，无法重试");
                    return result;
                }
            } else {
                isLocal = true;
            }
            
            // 5. 获取脚本信息
            Script script = scriptMapper.selectById(task.getScriptId());
            ScriptVersion scriptVersion = getScriptVersion(task.getScriptId(), task.getScriptVersion());
            if (script == null || scriptVersion == null) {
                result.put("success", false);
                result.put("error", "脚本或版本不存在");
                return result;
            }
            
            // 6. 更新任务状态为 running
            String oldTaskStatus = task.getStatus();
            task.setStatus("running");
            taskMapper.updateById(task);
            
            // 7. 更新步骤状态为 running
            taskStep.setStatus("running");
            taskStep.setStartedAt(LocalDateTime.now());
            taskStep.setFinishedAt(null);
            taskStep.setExitCode(null);
            taskStep.setOutput(null);
            taskStep.setErrorMessage(null);
            taskStepMapper.updateById(taskStep);
            
            // 7.1 删除旧的测试结果（如果有）
            if (taskStep.getServerId() != null) {
                LambdaQueryWrapper<TestResult> query = new LambdaQueryWrapper<>();
                query.eq(TestResult::getTaskId, taskId)
                     .eq(TestResult::getServerId, taskStep.getServerId());
                testResultMapper.delete(query);
                log.debug("已删除步骤 {} 的旧测试结果", taskStep.getStepName());
            }
            
            // 8. 创建执行上下文
            ExecutionContext context = new ExecutionContext(taskId, line -> {
                logCacheService.appendLog(taskId, line);
            });
            
            context.log("========== 步骤重试开始 ==========");
            context.log("任务: " + task.getName());
            context.log("步骤: " + (taskStep.getDisplayName() != null ? taskStep.getDisplayName() : taskStep.getStepName()));
            context.log("服务器: " + (isLocal ? "本地环境" : server.getName()));
            
            try {
                // 9. 确保环境就绪
                if (!isLocal) {
                    ensureStepEnvironment(task, taskStep, server, scriptVersion, context);
                }
                
                // 10. 执行步骤
                StepDAG.StepConfig stepConfig = buildStepConfig(taskStep, scriptVersion);
                boolean success = executeStepOnServer(context, task, server, script, scriptVersion, stepConfig, isLocal);
                
                context.log("步骤执行结果: " + (success ? "成功" : "失败"));
                
                // 11. 如果需要级联且成功，执行下游步骤
                if (cascade && success) {
                    context.log("开始执行下游步骤...");
                    executeDownstreamSteps(task, taskStep, scriptVersion, context);
                }
                
                // 12. 重新计算任务状态
                recalculateTaskStatus(task);
                
                context.log("========== 步骤重试结束 ==========");
                
                // 13. 通知日志缓存服务任务完成
                logCacheService.completeTask(task.getId());
                
                result.put("success", true);
                result.put("stepStatus", taskStep.getStatus());
                result.put("taskStatus", task.getStatus());
                
            } catch (Exception e) {
                log.error("步骤重试异常", e);
                context.log("[ERROR] 步骤重试异常: " + e.getMessage());
                
                taskStep.setStatus("failed");
                taskStep.setErrorMessage(e.getMessage());
                taskStep.setFinishedAt(LocalDateTime.now());
                taskStepMapper.updateById(taskStep);
                
                recalculateTaskStatus(task);
                
                logCacheService.completeTask(task.getId());
                
                result.put("success", false);
                result.put("error", e.getMessage());
            }
            
            return result;
            
        } finally {
            retryingSteps.remove(stepId);
        }
    }
    
    /**
     * 判断任务是否已完成
     */
    private boolean isTaskFinished(String status) {
        return "completed".equals(status) 
            || "completed_with_errors".equals(status) 
            || "failed".equals(status) 
            || "cancelled".equals(status);
    }
    
    /**
     * 确保步骤执行环境就绪
     */
    private void ensureStepEnvironment(Task task, TaskStep taskStep, Server server, 
                                        ScriptVersion scriptVersion, ExecutionContext context) {
        String workDir = "/tmp/test_platform/task_" + task.getId();
        
        // 检查目录是否存在，不存在则创建
        context.log("检查工作目录: " + workDir);
        String checkDir = "test -d " + workDir + " || mkdir -p " + workDir;
        SshService.executeCommand(server, checkDir, null, 10000);
        
        // 检查脚本文件是否存在
        String scriptFile = taskStep.getScript();
        if (scriptFile != null && !scriptFile.isEmpty()) {
            scriptFile = scriptFile.replaceAll("^\\./", "").replaceAll("/+", "/");
            String scriptPath = workDir + "/" + scriptFile;
            
            String checkScript = "test -f " + scriptPath + " && echo EXISTS || echo NOT_EXISTS";
            SshService.ExecuteResult checkResult = SshService.executeCommand(server, checkScript, null, 5000);
            
            if (checkResult.getOutput() == null || !checkResult.getOutput().contains("EXISTS")) {
                context.log("脚本文件不存在，重新上传...");
                uploadAllScriptFiles(context, server, scriptVersion, workDir);
            } else {
                context.log("脚本文件已存在: " + scriptPath);
            }
        }
    }
    
    /**
     * 构建步骤配置
     */
    @SuppressWarnings("unchecked")
    private StepDAG.StepConfig buildStepConfig(TaskStep taskStep, ScriptVersion scriptVersion) {
        StepDAG.StepConfig config = new StepDAG.StepConfig();
        config.setName(taskStep.getStepName());
        config.setDisplayName(taskStep.getDisplayName());
        config.setScript(taskStep.getScript());
        config.setResultCollector(taskStep.getResultCollector() != null && taskStep.getResultCollector());
        
        // 解析依赖
        if (taskStep.getDependsOn() != null && !taskStep.getDependsOn().isEmpty()) {
            config.setDependsOn(Arrays.asList(taskStep.getDependsOn().split(",")));
        } else {
            config.setDependsOn(Collections.emptyList());
        }
        
        // 从脚本版本获取参数
        if (scriptVersion.getSteps() != null) {
            Map<String, Object> stepsConfig = scriptVersion.getSteps();
            Map<String, Object> stepDef = (Map<String, Object>) stepsConfig.get(taskStep.getStepName());
            if (stepDef != null) {
                config.setParams(stepDef.get("params"));
                config.setStartupProbe((Map<String, Object>) stepDef.get("startupProbe"));
                config.setParseRule((Map<String, Object>) stepDef.get("parseRule"));
            }
        }
        
        return config;
    }
    
    /**
     * 执行下游步骤
     */
    @SuppressWarnings("unchecked")
    private void executeDownstreamSteps(Task task, TaskStep completedStep, 
                                         ScriptVersion scriptVersion, ExecutionContext context) {
        // 查找所有依赖于此步骤的步骤
        List<TaskStep> downstreamSteps = taskStepMapper.findByTaskIdWithServer(task.getId());
        
        for (TaskStep step : downstreamSteps) {
            // 跳过已完成或正在执行的步骤
            if ("success".equals(step.getStatus()) || "running".equals(step.getStatus()) || "retrying".equals(step.getStatus())) {
                continue;
            }
            
            // 检查是否依赖已完成的步骤
            if (step.getDependsOn() != null && !step.getDependsOn().isEmpty()) {
                List<String> dependencies = Arrays.asList(step.getDependsOn().split(","));
                
                // 检查所有依赖是否都已完成
                boolean allDependenciesMet = true;
                for (String dep : dependencies) {
                    String depName = dep.trim();
                    TaskStep depStep = findStepByName(downstreamSteps, depName, step.getServerId());
                    if (depStep == null || !"success".equals(depStep.getStatus())) {
                        allDependenciesMet = false;
                        break;
                    }
                }
                
                if (allDependenciesMet) {
                    // 更新步骤状态为执行中
                    step.setStatus("running");
                    step.setStartedAt(LocalDateTime.now());
                    step.setFinishedAt(null);
                    taskStepMapper.updateById(step);
                    
                    // 获取服务器
                    Server server = null;
                    boolean isLocal = step.getServerId() == null;
                    if (!isLocal) {
                        server = serverMapper.selectById(step.getServerId());
                        if (server == null || !"online".equals(server.getStatus())) {
                            context.log("[WARN] 服务器不可用，跳过步骤: " + step.getStepName());
                            continue;
                        }
                    }
                    
                    Script script = scriptMapper.selectById(task.getScriptId());
                    StepDAG.StepConfig stepConfig = buildStepConfig(step, scriptVersion);
                    
                    context.log("执行下游步骤: " + step.getStepName());
                    boolean success = executeStepOnServer(context, task, server, script, scriptVersion, stepConfig, isLocal);
                    context.log("下游步骤 " + step.getStepName() + " 执行结果: " + (success ? "成功" : "失败"));
                    
                    // 递归执行该步骤的下游步骤（级联传递）
                    if (success) {
                        executeDownstreamSteps(task, step, scriptVersion, context);
                    }
                }
            }
        }
    }
    
    /**
     * 根据名称和服务器ID查找步骤
     */
    private TaskStep findStepByName(List<TaskStep> steps, String stepName, Long serverId) {
        for (TaskStep step : steps) {
            if (stepName.equals(step.getStepName()) && 
                (serverId == null ? step.getServerId() == null : serverId.equals(step.getServerId()))) {
                return step;
            }
        }
        return null;
    }
    
    /**
     * 重新计算任务状态
     */
    private void recalculateTaskStatus(Task task) {
        List<TaskServer> servers = getTaskServers(task.getId());
        List<TaskStep> steps = taskStepMapper.findByTaskIdWithServer(task.getId());
        
        // 统计每个服务器上所有步骤的执行情况
        int totalServers = servers.size();
        int successServers = 0;
        int failedServers = 0;
        
        for (TaskServer server : servers) {
            // 统计该服务器上所有步骤的执行情况
            List<TaskStep> serverSteps = new ArrayList<>();
            for (TaskStep step : steps) {
                if (server.getServerId() == null) {
                    // 本地执行
                    if (step.getServerId() == null) {
                        serverSteps.add(step);
                    }
                } else if (server.getServerId().equals(step.getServerId())) {
                    serverSteps.add(step);
                }
            }
            
            if (serverSteps.isEmpty()) {
                continue;
            }
            
            // 检查是否所有步骤都成功
            boolean allSuccess = serverSteps.stream()
                .allMatch(s -> "success".equals(s.getStatus()));
            
            // 检查是否有失败的步骤
            boolean hasFailed = serverSteps.stream()
                .anyMatch(s -> "failed".equals(s.getStatus()));
            
            if (allSuccess) {
                successServers++;
                server.setOverallStatus("completed");
            } else if (hasFailed) {
                failedServers++;
                server.setOverallStatus("failed");
            } else {
                // 还有 pending/running/skipped 的步骤
                server.setOverallStatus("running");
            }
            server.setProgress(100);
            taskServerMapper.updateById(server);
        }
        
        // 更新任务状态
        if (successServers == totalServers) {
            task.setStatus("completed");
        } else if (successServers == 0) {
            task.setStatus("failed");
        } else {
            task.setStatus("completed_with_errors");
        }
        
        taskMapper.updateById(task);
    }
    
    /**
     * 检查任务是否有还在运行的后台步骤
     * 
     * @param taskId 任务ID
     * @return true 表示有后台步骤还在运行
     */
    private boolean hasRunningBackgroundSteps(Long taskId) {
        // 获取任务的所有步骤
        List<TaskStep> steps = taskStepMapper.selectList(
            new LambdaQueryWrapper<TaskStep>()
                .eq(TaskStep::getTaskId, taskId)
                .eq(TaskStep::getStatus, "running")
        );
        
        if (steps.isEmpty()) {
            return false;
        }
        
        // 获取任务信息以判断哪些是后台步骤
        Task task = taskMapper.selectById(taskId);
        if (task == null || task.getStepParams() == null) {
            return false;
        }
        
        // 检查是否有 running 状态的后台步骤
        for (TaskStep step : steps) {
            Map<String, Object> stepParam = task.getStepParams().get(step.getStepName());
            if (stepParam != null && Boolean.TRUE.equals(stepParam.get("_BACKGROUND"))) {
                log.info("任务 {} 发现后台步骤仍在运行: {}", taskId, step.getStepName());
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 检查步骤状态（供 TaskStatusCheckService 和 TaskController 共用）
     */
    public StepStatusCheckResult checkStepStatus(Task task, TaskStep step, Server server) {
        String workDir = "/tmp/test_platform/task_" + task.getId();
        String stepName = step.getStepName();
        
        // 判断是否为后台执行步骤
        boolean isBackground = false;
        if (task.getStepParams() != null) {
            Map<String, Object> stepParam = task.getStepParams().get(stepName);
            if (stepParam != null && Boolean.TRUE.equals(stepParam.get("_BACKGROUND"))) {
                isBackground = true;
            }
        }
        
        if (isBackground) {
            return checkBackgroundStepStatus(server, workDir, stepName);
        } else {
            return checkNormalStepStatus(task, step, server, workDir);
        }
    }
    
    /**
     * 检查后台步骤状态
     */
    private StepStatusCheckResult checkBackgroundStepStatus(Server server, String workDir, String stepName) {
        String pidFile = workDir + "/" + stepName + ".pid";
        String outputFile = workDir + "/" + stepName + ".log";
        String exitCodeFile = workDir + "/" + stepName + ".exit_code";
        
        try {
            // 检查进程是否还在运行
            String checkCmd = String.format(
                "PID=$(cat %s 2>/dev/null); " +
                "if [ -n \"$PID\" ] && ps -p $PID > /dev/null 2>&1; then " +
                "  echo \"RUNNING\"; " +
                "else " +
                "  echo \"STOPPED\"; " +
                "fi", pidFile);
            
            SshService.ExecuteResult result = SshService.executeCommand(
                server, checkCmd, null, SshService.getDefaultTimeout());
            
            String status = result.getOutput().trim();
            
            if ("RUNNING".equals(status)) {
                return StepStatusCheckResult.running();
            }
            
            // 进程已停止，读取退出码和日志
            Integer exitCode = null;
            try {
                SshService.ExecuteResult ecResult = SshService.executeCommand(
                    server, "cat " + exitCodeFile + " 2>/dev/null", null, 5000);
                String ecStr = ecResult.getOutput().trim();
                if (!ecStr.isEmpty()) {
                    exitCode = Integer.parseInt(ecStr);
                }
            } catch (Exception e) {
                log.warn("读取后台步骤 {} 退出码失败: {}", stepName, e.getMessage());
            }
            
            // 读取完整日志
            String fullLog = readFullLog(server, outputFile);
            
            if (exitCode != null && exitCode == 0) {
                return StepStatusCheckResult.success("COMPLETED", exitCode, fullLog);
            } else {
                String reason = exitCode != null ? 
                    "EXIT_CODE_NON_ZERO (" + exitCode + ")" : "BACKGROUND_PROCESS_STOPPED";
                return StepStatusCheckResult.failed(reason, exitCode);
            }
            
        } catch (Exception e) {
            log.error("检查后台步骤 {} 状态失败: {}", stepName, e.getMessage());
            return StepStatusCheckResult.running("CHECK_FAILED: " + e.getMessage());
        }
    }
    
    /**
     * 检查正常步骤状态
     */
    private StepStatusCheckResult checkNormalStepStatus(Task task, TaskStep step, Server server, String workDir) {
        // 1. 检查超时
        if (step.getStartedAt() != null && task.getTimeout() != null) {
            long elapsed = Duration.between(step.getStartedAt(), LocalDateTime.now()).toMillis();
            if (elapsed > task.getTimeout()) {
                return StepStatusCheckResult.failed("NORMAL_TIMEOUT");
            }
        }
        
        // 2. 检查工作目录下是否有相关进程
        try {
            // 查找与该任务工作目录相关的进程
            String checkCmd = String.format(
                "ps -deo pid,ppid,stat,cmd 2>/dev/null | " +
                "grep '%s' | grep -v grep | head -10 || echo ''",
                workDir);
            
            SshService.ExecuteResult result = SshService.executeCommand(
                server, checkCmd, null, 5000);
            
            String output = result.getOutput();
            if (output != null && !output.trim().isEmpty()) {
                // 仍有相关进程在运行
                return StepStatusCheckResult.running();
            }
            
            // 进程已消失但状态还是 running，说明异常终止
            return StepStatusCheckResult.failed("NORMAL_PROCESS_DIED");
            
        } catch (Exception e) {
            log.error("检查正常步骤 {} 状态失败: {}", step.getStepName(), e.getMessage());
            return StepStatusCheckResult.running("CHECK_FAILED: " + e.getMessage());
        }
    }
    
    /**
     * 读取完整日志文件
     */
    private String readFullLog(Server server, String outputFile) {
        try {
            SshService.ExecuteResult result = SshService.executeCommand(
                server, "cat " + outputFile + " 2>/dev/null", null, 10000);
            return result.getOutput();
        } catch (Exception e) {
            log.warn("读取日志文件 {} 失败: {}", outputFile, e.getMessage());
            return null;
        }
    }
}

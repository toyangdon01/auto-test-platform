package com.autotest.service;

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

        if ("running".equals(task.getStatus())) {
            result.put("success", false);
            result.put("error", "任务正在执行中");
            return result;
        }

        task.setStatus("running");
        task.setStartedAt(LocalDateTime.now());
        taskMapper.updateById(task);

        ExecutionContext context = new ExecutionContext(taskId, null);
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

            // 计算最终状态
            int totalServers = taskServers.size();
            if (successCount == totalServers) {
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
            return true;
        }
        return false;
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
            Map<String, Object> stepDef = (Map<String, Object>) entry.getValue();
            
            String displayName = (String) stepDef.getOrDefault("displayName", stepName);
            String scriptFile = (String) stepDef.get("script");
            
            // 处理 dependsOn（可能是 List 或 null）
            List<String> dependsOn = null;
            Object dependsObj = stepDef.get("dependsOn");
            if (dependsObj instanceof List) {
                dependsOn = (List<String>) dependsObj;
            }
            
            Boolean resultCollector = (Boolean) stepDef.getOrDefault("resultCollector", true);
            
            // 处理 params（可能是 List 或 Map，这里只用默认值）
            Map<String, Object> params = null;
            Object paramsObj = stepDef.get("params");
            if (paramsObj instanceof Map) {
                params = (Map<String, Object>) paramsObj;
            }
            
            Map<String, Object> startupProbe = (Map<String, Object>) stepDef.get("startupProbe");
            
            // 获取解析规则配置
            Map<String, Object> parseRule = (Map<String, Object>) stepDef.get("parseRule");
            
            dag.addStep(stepName, displayName, scriptFile, dependsOn, 
                       resultCollector != null ? resultCollector : true, params, startupProbe,
                       null, false, null, parseRule);
        }
        
        if (dag.hasCycle()) {
            context.log("[ERROR] 检测到循环依赖，任务终止");
            return 0;
        }
        
        context.log("[INFO] 步骤数量: " + stepsConfig.size());
        
        // 初始化 TaskStep 记录
        initTaskSteps(task, taskServers, dag, stepParams);
        
        ExecutorService executor = Executors.newFixedThreadPool(Math.max(4, taskServers.size()));
        // 跟踪每个服务器的失败步骤数
        Map<Long, AtomicInteger> serverFailedSteps = new ConcurrentHashMap<>();
        for (TaskServer ts : taskServers) {
            serverFailedSteps.put(ts.getServerId(), new AtomicInteger(0));
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
                
                for (String stepName : readySteps) {
                    dag.markAsRunning(stepName);
                    StepDAG.StepConfig stepConfig = dag.getStepConfig(stepName);
                    
                    for (TaskServer taskServer : taskServers) {
                        Server server = serverMapper.selectById(taskServer.getServerId());
                        if (server == null) continue;
                        
                        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                            boolean success = executeStepOnServer(context, task, server, 
                                                                  script, scriptVersion, stepConfig);
                            if (!success) {
                                serverFailedSteps.get(server.getId()).incrementAndGet();
                            }
                            dag.markAsComplete(stepName, success);
                            
                            // 同步更新 task_servers 状态
                            updateTaskServerStatus(task.getId(), server.getId(), success);
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
            if (serverFailedSteps.get(ts.getServerId()).get() == 0) {
                successCount++;
            }
        }
        
        return successCount;
    }

    /**
     * 初始化任务步骤记录
     */
    @SuppressWarnings("unchecked")
    private void initTaskSteps(Task task, List<TaskServer> taskServers, StepDAG dag, 
                                Map<String, Map<String, Object>> stepParams) {
        // 先删除旧的步骤记录（支持再次执行）
        LambdaQueryWrapper<TaskStep> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(TaskStep::getTaskId, task.getId());
        taskStepMapper.delete(deleteWrapper);
        
        // 获取共享参数
        Map<String, Object> sharedParams = task.getSharedParams() != null ? task.getSharedParams() : new HashMap<>();
        
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
            
            for (TaskServer taskServer : taskServers) {
                TaskStep taskStep = new TaskStep();
                taskStep.setTaskId(task.getId());
                taskStep.setServerId(taskServer.getServerId());
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
                                         StepDAG.StepConfig stepConfig) {
        
        String stepName = stepConfig.getName();
        context.log("\n--- 步骤: " + stepConfig.getDisplayName() + " @ " + server.getName() + " ---");
        
        // 更新步骤状态
        TaskStep taskStep = taskStepMapper.findByTaskAndStepName(task.getId(), stepName);
        if (taskStep == null) {
            taskStep = new TaskStep();
            taskStep.setTaskId(task.getId());
            taskStep.setServerId(server.getId());
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
                scriptPath = workDir + "/" + scriptFile;
            } else {
                scriptPath = workDir + "/script.sh";
            }
            
            SshService.executeCommand(server, "chmod +x " + scriptPath, null, 5000);
            
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
            
            // 构建环境变量
            StringBuilder envBuilder = new StringBuilder();
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                envBuilder.append("export ").append(entry.getKey()).append("=\"").append(entry.getValue()).append("\"; ");
            }
            
            // 执行命令
            String command = String.format("cd %s && %s bash %s", workDir, envBuilder, scriptPath);
            context.log("执行: " + scriptPath);
            
            StringBuilder logBuilder = new StringBuilder();
            final TaskStep updateStep = taskStep; // lambda中使用的final引用
            
            // 使用定时器每秒保存输出到数据库（用于执行中实时显示）
            java.util.Timer saveTimer = new java.util.Timer(true);
            saveTimer.scheduleAtFixedRate(new java.util.TimerTask() {
                @Override
                public void run() {
                    synchronized(logBuilder) {
                        updateStep.setOutput(logBuilder.toString());
                        taskStepMapper.updateById(updateStep);
                    }
                }
            }, 1000, 1000); // 每秒保存
            
            Consumer<String> logConsumer = line -> {
                context.log("  " + line);
                synchronized(logBuilder) {
                    logBuilder.append(line).append("\n");
                }
            };
            
            int timeout = 300000; // 5分钟默认超时
            SshService.ExecuteResult execResult = SshService.executeCommand(server, command, logConsumer, timeout);
            
            // 停止定时保存器
            saveTimer.cancel();
            // 最后一次保存完整输出
            synchronized(logBuilder) {
                updateStep.setOutput(logBuilder.toString());
                taskStepMapper.updateById(updateStep);
            }
            
            context.log("退出码: " + execResult.getExitCode());
            
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
        
        // 自动检测入口脚本
        String entryFile = scriptVersion.getEntryFile();
        if (entryFile == null && scriptVersion.getFileList() != null) {
            for (Object item : scriptVersion.getFileList()) {
                Map<String, Object> fileInfo = (Map<String, Object>) item;
                String name = (String) fileInfo.get("name");
                if (name != null && name.endsWith(".sh")) {
                    entryFile = name;
                    break;
                }
            }
        }
        defaultStep.setScript(entryFile);
        
        int successCount = 0;
        for (TaskServer taskServer : taskServers) {
            Server server = serverMapper.selectById(taskServer.getServerId());
            if (server == null) continue;
            
            if (executeStepOnServer(context, task, server, script, scriptVersion, defaultStep)) {
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
                    // target_path 是相对于任务工作目录的路径
                    String targetPath = workDir + "/" + sr.getTargetPath();
                    
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
            
            // 完整路径包含版本号
            Path scriptDir = Paths.get(storagePath, version != null ? version : "v1.0.0");
            if (!Files.exists(scriptDir)) {
                context.log("[ERROR] 脚本目录不存在: " + scriptDir);
                return false;
            }
            
            context.log("脚本目录: " + scriptDir);
            
            List<Map<String, Object>> fileList = scriptVersion.getFileList();
            if (fileList == null || fileList.isEmpty()) {
                // 上传目录下所有文件
                Files.walk(scriptDir)
                    .filter(path -> !Files.isDirectory(path))
                    .forEach(path -> {
                        String relativePath = scriptDir.relativize(path).toString().replace("\\", "/");
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
                    String name = (String) fileInfo.get("name");
                    if (name == null) continue;
                    
                    Path localPath = scriptDir.resolve(name);
                    String targetPath = workDir + "/" + name;
                    
                    if (Files.exists(localPath)) {
                        SshService.uploadFile(server, localPath.toString(), targetPath);
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
        testResult.setServerId(server.getId());
        testResult.setTaskServerId(taskServer != null ? taskServer.getId() : null);
        testResult.setResult(taskStep.getExitCode() == 0 ? "pass" : "fail");
        testResult.setExitCode(taskStep.getExitCode());
        testResult.setRawOutput(taskStep.getOutput());
        testResult.setStartedAt(taskStep.getStartedAt());
        testResult.setFinishedAt(taskStep.getFinishedAt());
        
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
}

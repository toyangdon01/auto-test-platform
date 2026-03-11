package com.autotest.service;

import com.autotest.entity.*;
import com.autotest.mapper.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 任务执行服务 - 支持三阶段生命周期
 *
 * @author auto-test-platform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskExecutionService {

    private final TaskMapper taskMapper;
    private final TaskServerMapper taskServerMapper;
    private final ServerMapper serverMapper;
    private final ScriptMapper scriptMapper;
    private final ScriptVersionMapper scriptVersionMapper;
    private final TestResultMapper testResultMapper;
    private final ParseRuleService parseRuleService;
    private final ResultRuleService resultRuleService;
    private final ResultParseService resultParseService;
    private final ScriptResourceMapper scriptResourceMapper;
    private final ResourceFileMapper resourceFileMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${autotest.storage.scripts-path:C:/data/auto-test/scripts}")
    private String scriptsPath;

    // 存储执行中的任务状态
    private static final Map<Long, ExecutionContext> runningTasks = new ConcurrentHashMap<>();

    /**
     * 执行任务（支持三阶段生命周期）
     * 注意：此方法不使用 @Transactional，因为 SSH 操作可能耗时很长
     * 状态更新使用独立的数据库操作
     */
    public Map<String, Object> executeTask(Long taskId, Consumer<String> logCallback) {
        Map<String, Object> result = new LinkedHashMap<>();

        // 获取任务
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

        // 更新任务状态（独立操作，立即提交）
        task.setStatus("running");
        task.setStartedAt(LocalDateTime.now());
        taskMapper.updateById(task);

        // 创建执行上下文
        ExecutionContext context = new ExecutionContext(taskId, logCallback);
        runningTasks.put(taskId, context);

        try {
            // 获取脚本和版本
            Script script = scriptMapper.selectById(task.getScriptId());
            ScriptVersion scriptVersion = getScriptVersion(task.getScriptId(), task.getScriptVersion());
            
            if (script == null) {
                result.put("success", false);
                result.put("error", "脚本不存在");
                return result;
            }
            
            // 如果 ScriptVersion 不存在，尝试从文件系统读取脚本内容
            if (scriptVersion == null) {
                String scriptContent = getScriptContent(script, task.getScriptVersion());
                if (scriptContent == null) {
                    result.put("success", false);
                    result.put("error", "脚本内容不存在，请先上传脚本文件");
                    return result;
                }
                scriptVersion = createSimpleScriptVersion(script, scriptContent);
            }

            // 判断生命周期模式
            boolean isFullLifecycle = "full".equals(script.getLifecycleMode());
            boolean skipDeploy = Boolean.TRUE.equals(task.getSkipDeploy());
            boolean skipCleanup = Boolean.TRUE.equals(task.getSkipCleanup());

            context.log("========== 任务执行开始 ==========");
            context.log("任务: " + task.getName());
            context.log("脚本: " + script.getName() + " v" + scriptVersion.getVersion());
            context.log("生命周期模式: " + (isFullLifecycle ? "完整模式" : "简单模式"));

            // 获取任务服务器
            List<TaskServer> taskServers = getTaskServers(taskId);
            if (taskServers.isEmpty()) {
                result.put("success", false);
                result.put("error", "任务没有关联服务器");
                return result;
            }
            context.log("目标服务器: " + taskServers.size() + " 台\n");

            // ========== 阶段0：资源上传阶段 ==========
            List<ScriptResource> resources = scriptResourceMapper.findByScriptIdWithResource(script.getId());
            if (!resources.isEmpty()) {
                context.log("========== 资源上传阶段 ==========");
                for (TaskServer taskServer : taskServers) {
                    Server server = serverMapper.selectById(taskServer.getServerId());
                    if (server == null) continue;
                    
                    context.log("上传资源到服务器: " + server.getName());
                    for (ScriptResource sr : resources) {
                        try {
                            ResourceFile rf = resourceFileMapper.selectById(sr.getResourceId());
                            if (rf == null) {
                                context.log("[WARN] 资源文件不存在: " + sr.getResourceId());
                                continue;
                            }
                            
                            String localPath = Paths.get(scriptsPath.replace("scripts", "resources"), rf.getStoragePath()).toString();
                            String targetPath = sr.getTargetPath() + "/" + rf.getName();
                            
                            context.log("  上传: " + rf.getName() + " -> " + targetPath);
                            
                            // 上传文件
                            SshService.uploadFile(server, localPath, targetPath);
                            
                            // 设置权限
                            String chmodCmd = "chmod " + sr.getPermissions() + " " + targetPath;
                            SshService.executeCommand(server, chmodCmd);
                            
                            context.log("  成功: " + rf.getName());
                        } catch (Exception e) {
                            context.log("[ERROR] 上传资源失败: " + e.getMessage());
                        }
                    }
                }
            }

            // ========== 阶段1：部署阶段 ==========
            // 对于完整生命周期模式，自动执行部署阶段（不依赖 hasDeploy 字段）
            if (isFullLifecycle && !skipDeploy) {
                task.setDeployStatus("running");
                task.setDeployStartedAt(LocalDateTime.now());
                taskMapper.updateById(task);

                context.log("========== 部署阶段 ==========");
                int deploySuccess = executePhase(context, task, taskServers, scriptVersion, "deploy");

                task.setDeployFinishedAt(LocalDateTime.now());
                task.setDeployStatus(deploySuccess == taskServers.size() ? "completed" : "failed");
                taskMapper.updateById(task);

                if (deploySuccess < taskServers.size()) {
                    context.log("[WARN] 部署阶段部分失败，继续执行测试阶段");
                }
            } else if (skipDeploy) {
                task.setDeployStatus("skipped");
                taskMapper.updateById(task);
                context.log("[INFO] 跳过部署阶段\n");
            }

            // ========== 阶段2：执行阶段 ==========
            context.log("========== 执行阶段 ==========");
            int successCount = executePhase(context, task, taskServers, scriptVersion, "run");

            // ========== 阶段3：卸载阶段 ==========
            // 对于完整生命周期模式，自动执行清理阶段（不依赖 hasCleanup 字段）
            if (isFullLifecycle && !skipCleanup) {
                task.setCleanupStatus("running");
                task.setCleanupStartedAt(LocalDateTime.now());
                taskMapper.updateById(task);

                context.log("\n========== 卸载阶段 ==========");
                int cleanupSuccess = executePhase(context, task, taskServers, scriptVersion, "cleanup");

                task.setCleanupFinishedAt(LocalDateTime.now());
                task.setCleanupStatus(cleanupSuccess == taskServers.size() ? "completed" : "failed");
                taskMapper.updateById(task);
            } else if (skipCleanup) {
                task.setCleanupStatus("skipped");
                taskMapper.updateById(task);
                context.log("\n[INFO] 跳过卸载阶段");
            }

            // 计算最终状态
            long failCount = taskServers.stream()
                    .filter(ts -> !"completed".equals(ts.getRunStatus()))
                    .count();
            int totalServers = taskServers.size();

            // 状态判断：全部成功=completed，全部失败=failed，部分失败=completed_with_errors
            if (failCount == 0) {
                task.setStatus("completed");
            } else if (failCount >= totalServers) {
                task.setStatus("failed");
            } else {
                task.setStatus("completed_with_errors");
            }
            task.setFinishedAt(LocalDateTime.now());
            taskMapper.updateById(task);

            // 统计结果
            result.put("success", true);
            result.put("totalServers", taskServers.size());
            result.put("successCount", successCount);
            result.put("failCount", failCount);

            context.log("\n========== 执行完成 ==========");
            context.log("成功: " + successCount + ", 失败: " + failCount);

        } catch (Exception e) {
            log.error("任务执行异常", e);
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
     * 执行单个阶段
     */
    private int executePhase(ExecutionContext context, Task task, List<TaskServer> taskServers,
                              ScriptVersion scriptVersion, String phase) {
        int successCount = 0;
        
        // 对于 run 阶段，按角色依赖关系排序
        if ("run".equals(phase)) {
            taskServers = sortByRoleDependencies(taskServers, scriptVersion, context);
        }

        for (TaskServer taskServer : taskServers) {
            if (context.isCancelled()) {
                context.log("[INFO] 任务已取消");
                break;
            }

            Server server = serverMapper.selectById(taskServer.getServerId());
            if (server == null) {
                context.log("[ERROR] Server-" + taskServer.getServerId() + " 不存在");
                continue;
            }

            String roleInfo = taskServer.getRole() != null ? " [" + taskServer.getRole() + "]" : "";
            context.log("\n--- " + server.getName() + " (" + server.getHost() + ")" + roleInfo + " ---");

            // 更新阶段状态
            updatePhaseStatus(taskServer, phase, "running", null);
            taskServerMapper.updateById(taskServer);

            try {
                // 检查是否需要等待依赖角色的启动探测
                if ("run".equals(phase)) {
                    waitForDependencyStartup(context, task, taskServer, scriptVersion, server);
                }
                
                boolean success = executePhaseOnServer(context, task, taskServer, server, scriptVersion, phase);

                if (success) {
                    successCount++;
                    updatePhaseStatus(taskServer, phase, "completed", 0);
                } else {
                    updatePhaseStatus(taskServer, phase, "failed", 1);
                }
            } catch (Exception e) {
                context.log("[ERROR] 执行失败: " + e.getMessage());
                updatePhaseStatus(taskServer, phase, "failed", -1);
            }

            taskServerMapper.updateById(taskServer);
        }

        return successCount;
    }
    
    /**
     * 按角色依赖关系排序 TaskServers
     */
    @SuppressWarnings("unchecked")
    private List<TaskServer> sortByRoleDependencies(List<TaskServer> taskServers, ScriptVersion scriptVersion, ExecutionContext context) {
        if (scriptVersion.getRoles() == null || taskServers.size() <= 1) {
            return taskServers;
        }
        
        Map<String, Object> rolesMap = scriptVersion.getRoles();
        List<Map<String, Object>> rolesList = (List<Map<String, Object>>) rolesMap.get("roles");
        if (rolesList == null || rolesList.isEmpty()) {
            return taskServers;
        }
        
        // 构建角色的依赖深度
        Map<String, Integer> roleDepth = new java.util.HashMap<>();
        for (Map<String, Object> roleDef : rolesList) {
            String roleName = (String) roleDef.get("name");
            List<String> dependsOn = (List<String>) roleDef.get("dependsOn");
            int depth = (dependsOn == null || dependsOn.isEmpty()) ? 0 : dependsOn.size();
            roleDepth.put(roleName, depth);
        }
        
        // 按依赖深度排序（深度小的先执行）
        return taskServers.stream()
            .sorted((a, b) -> {
                int depthA = roleDepth.getOrDefault(a.getRole(), Integer.MAX_VALUE);
                int depthB = roleDepth.getOrDefault(b.getRole(), Integer.MAX_VALUE);
                return Integer.compare(depthA, depthB);
            })
            .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * 等待依赖角色的启动探测成功
     */
    @SuppressWarnings("unchecked")
    private void waitForDependencyStartup(ExecutionContext context, Task task, TaskServer taskServer,
                                           ScriptVersion scriptVersion, Server currentServer) {
        String role = taskServer.getRole();
        if (role == null || "default".equals(role)) {
            return;
        }
        
        Map<String, Object> roleDef = getRoleDefinition(scriptVersion, role);
        if (roleDef == null) {
            return;
        }
        
        List<String> dependsOn = (List<String>) roleDef.get("dependsOn");
        if (dependsOn == null || dependsOn.isEmpty()) {
            return;
        }
        
        // 查找依赖角色的服务器
        List<TaskServer> allTaskServers = taskServerMapper.selectList(
            new LambdaQueryWrapper<TaskServer>().eq(TaskServer::getTaskId, task.getId())
        );
        
        for (String depRole : dependsOn) {
            for (TaskServer ts : allTaskServers) {
                if (depRole.equals(ts.getRole())) {
                    Server depServer = serverMapper.selectById(ts.getServerId());
                    if (depServer == null) continue;
                    
                    // 获取依赖角色的启动探测配置
                    Map<String, Object> depRoleDef = getRoleDefinition(scriptVersion, depRole);
                    if (depRoleDef == null) continue;
                    
                    Map<String, Object> startupProbe = (Map<String, Object>) depRoleDef.get("startupProbe");
                    if (startupProbe == null) continue;
                    
                    Boolean probeEnabled = (Boolean) startupProbe.get("enabled");
                    if (probeEnabled == null || !probeEnabled) continue;
                    
                    String probeCommand = (String) startupProbe.get("command");
                    Integer timeout = (Integer) startupProbe.get("timeout");
                    if (timeout == null) timeout = 30;
                    
                    context.log("[INFO] 等待依赖角色 [" + depRole + "] 启动就绪...");
                    
                    // 执行启动探测
                    long startTime = System.currentTimeMillis();
                    long timeoutMs = timeout * 1000L;
                    boolean probeSuccess = false;
                    
                    while (System.currentTimeMillis() - startTime < timeoutMs) {
                        try {
                            SshService.ExecuteResult probeResult = SshService.executeCommand(
                                depServer, probeCommand, null, 10000);
                            if (probeResult.getExitCode() == 0) {
                                probeSuccess = true;
                                context.log("[INFO] 依赖角色 [" + depRole + "] 已就绪");
                                break;
                            }
                        } catch (Exception e) {
                            // 探测失败，继续等待
                        }
                        
                        try {
                            Thread.sleep(2000); // 每 2 秒探测一次
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                    
                    if (!probeSuccess) {
                        context.log("[WARN] 依赖角色 [" + depRole + "] 启动探测超时，继续执行");
                    }
                    
                    break;
                }
            }
        }
    }

    /**
     * 在单台服务器上执行单个阶段
     */
    private boolean executePhaseOnServer(ExecutionContext context, Task task, TaskServer taskServer,
                                          Server server, ScriptVersion scriptVersion, String phase) {
        // 准备工作目录
        String workDir = "/tmp/test_platform/task_" + task.getId();

        // 确保目录存在
        SshService.ExecuteResult mkdirResult = SshService.executeCommand(server, "mkdir -p " + workDir, null, 10000);
        if (mkdirResult.getExitCode() != 0) {
            context.log("[ERROR] 创建目录失败: " + mkdirResult.getError());
            return false;
        }

        // 上传所有脚本文件
        context.log("上传脚本文件...");
        Script script = scriptMapper.selectById(task.getScriptId());
        String[] scriptPathResult = new String[1];
        if (!uploadAllScriptFiles(context, server, script, scriptVersion, workDir, scriptPathResult)) {
            context.log("[ERROR] 脚本上传失败");
            return false;
        }
        
        String scriptPath = scriptPathResult[0];
        if (scriptPath == null || scriptPath.isEmpty()) {
            scriptPath = workDir + "/script.sh";
        }

        // 赋予执行权限
        SshService.executeCommand(server, "chmod +x " + scriptPath, null, 5000);

        // 构建参数（支持角色）
        Map<String, Object> params = buildRoleParams(task, taskServer, scriptVersion, phase);

        // 构建环境变量
        StringBuilder envBuilder = new StringBuilder();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            envBuilder.append("export ").append(entry.getKey()).append("=\"").append(entry.getValue()).append("\"; ");
        }

        // 获取角色的入口函数
        String role = taskServer.getRole() != null ? taskServer.getRole() : "default";
        String entryFunction = getRoleEntryFunction(phase, scriptVersion, role);
        String command;
        
        // 判断 entryFunction 是否为真正的函数名（不是文件路径）
        boolean isFunctionName = entryFunction != null && !entryFunction.isEmpty() 
                && !entryFunction.contains("/") && !entryFunction.endsWith(".sh");
        
        if (isFunctionName) {
            // 调用脚本中的函数
            command = String.format("cd %s && %s bash -c 'source %s && %s'",
                    workDir, envBuilder, scriptPath, entryFunction);
        } else {
            // 直接执行脚本，传递阶段和角色作为参数
            String phaseArg = phase;
            command = String.format("cd %s && %s bash %s %s",
                    workDir, envBuilder, scriptPath, phaseArg);
        }

        // 记录当前执行的命令
        taskServer.setCurrentPhase(phase);
        taskServer.setCurrentCommand(command);
        taskServer.setCommandStartedAt(LocalDateTime.now());
        taskServerMapper.updateById(taskServer);

        // 执行命令
        context.log("执行" + getPhaseName(phase) + "（角色: " + role + "）...");
        long startTime = System.currentTimeMillis();

        StringBuilder logBuilder = new StringBuilder();
        Consumer<String> logConsumer = line -> {
            logBuilder.append(line).append("\n");
            context.log("  " + line.trim());
        };

        int timeout = getPhaseTimeout(task, phase);
        int timeoutMs = timeout * 1000;
        SshService.ExecuteResult execResult = SshService.executeCommand(server, command, logConsumer, timeoutMs);

        long duration = System.currentTimeMillis() - startTime;

        // 清除当前命令记录
        taskServer.setCurrentPhase(null);
        taskServer.setCurrentCommand(null);
        taskServer.setCommandStartedAt(null);

        // 保存日志
        savePhaseOutput(taskServer, phase, logBuilder.toString(), execResult.getExitCode());

        // 如果是执行阶段，创建测试结果
        if ("run".equals(phase)) {
            // 检查是否应该收集结果（从角色定义或执行策略）
            boolean shouldCollectResult = shouldCollectResult(task, taskServer, scriptVersion);
            if (shouldCollectResult) {
                createTestResult(task, taskServer, server, execResult, duration);
            }
        }

        return execResult.getExitCode() == 0;
    }

    /**
     * 判断是否应该收集结果
     */
    @SuppressWarnings("unchecked")
    private boolean shouldCollectResult(Task task, TaskServer taskServer, ScriptVersion scriptVersion) {
        // 1. 检查角色定义中的 resultCollector
        String role = taskServer.getRole();
        if (role != null && !"default".equals(role)) {
            Map<String, Object> roleDef = getRoleDefinition(scriptVersion, role);
            if (roleDef != null) {
                Object resultCollector = roleDef.get("resultCollector");
                if (resultCollector != null) {
                    return Boolean.TRUE.equals(resultCollector);
                }
            }
        }
        
        // 2. 检查任务的角色执行策略
        Map<String, Object> strategy = task.getRoleExecutionStrategy();
        if (strategy != null && strategy.containsKey("resultRoles")) {
            List<String> resultRoles = (List<String>) strategy.get("resultRoles");
            if (resultRoles != null && !resultRoles.isEmpty()) {
                return resultRoles.contains(role);
            }
        }
        
        // 3. 默认收集结果
        return true;
    }

    /**
     * 创建测试结果
     */
    private void createTestResult(Task task, TaskServer taskServer, Server server,
                                   SshService.ExecuteResult execResult, long durationMs) {
        TestResult testResult = new TestResult();
        testResult.setTaskId(task.getId());
        testResult.setServerId(server.getId());
        testResult.setTaskServerId(taskServer.getId());
        testResult.setExitCode(execResult.getExitCode());
        testResult.setDurationMs((int) Math.min(durationMs, Integer.MAX_VALUE));
        testResult.setRawOutput(execResult.getStdout());
        testResult.setRawError(execResult.getStderr());
        testResult.setStartedAt(LocalDateTime.now().minusNanos(durationMs * 1_000_000));
        testResult.setFinishedAt(LocalDateTime.now());
        testResult.setCreatedAt(LocalDateTime.now());

        // 解析结果
        String judgement = parseRuleService.judgeResult(task.getScriptId(), execResult.getStdout(), execResult.getExitCode());
        testResult.setResult(judgement);

        if ("pass".equals(judgement)) {
            testResult.setResultReason("测试通过");
            testResult.setOverallScore(100);
        } else {
            testResult.setResultReason("测试失败");
            testResult.setOverallScore(0);
        }

        // 解析指标
        Map<String, Object> metrics = parseRuleService.parseOutput(task.getScriptId(), execResult.getStdout());
        testResult.setMetrics(metrics);

        // 使用解析规则解析输出
        try {
            List<ResultRule> rules = resultRuleService.listByScriptId(task.getScriptId());
            if (rules != null && !rules.isEmpty()) {
                // 只取第一个启用的规则
                ResultRule enabledRule = rules.stream()
                        .filter(r -> Boolean.TRUE.equals(r.getEnabled()))
                        .findFirst()
                        .orElse(null);
                
                if (enabledRule != null) {
                    // 确定输入来源
                    String parseInput = execResult.getStdout();
                    
                    if ("file".equals(enabledRule.getInputSource()) && enabledRule.getFilePattern() != null) {
                        // 从远程服务器读取匹配的文件
                        String workDir = "/tmp/test_platform/task_" + task.getId();
                        String fileContent = readRemoteFile(server, workDir, enabledRule.getFilePattern());
                        if (fileContent != null && !fileContent.isEmpty()) {
                            parseInput = fileContent;
                            log.info("从远程文件读取解析输入: pattern={}", enabledRule.getFilePattern());
                        }
                    }
                    
                    Map<String, Object> parsedData = resultParseService.parse(parseInput, enabledRule);
                    testResult.setParsedData(parsedData);
                    log.info("解析规则 {} 应用成功: taskId={}, serverId={}", enabledRule.getId(), task.getId(), server.getId());
                }
            }
        } catch (Exception e) {
            log.warn("解析规则执行失败: taskId={}, error={}", task.getId(), e.getMessage());
            // 解析失败不影响结果保存
        }

        testResultMapper.insert(testResult);
    }

    /**
     * 从远程服务器读取匹配正则的文件内容
     */
    private String readRemoteFile(Server server, String workDir, String filePattern) {
        try {
            // 列出目录下的所有文件
            String listCmd = String.format("find %s -type f 2>/dev/null", workDir);
            String fileList = SshService.executeCommand(server, listCmd);
            
            if (fileList == null || fileList.isEmpty()) {
                return null;
            }
            
            // 使用正则匹配文件
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(filePattern);
            String[] files = fileList.split("\n");
            
            for (String file : files) {
                file = file.trim();
                if (file.isEmpty()) continue;
                
                java.util.regex.Matcher matcher = pattern.matcher(file);
                if (matcher.find()) {
                    // 找到匹配的文件，读取内容
                    String catCmd = String.format("cat '%s' 2>/dev/null", file);
                    String content = SshService.executeCommand(server, catCmd);
                    log.info("读取远程文件: {} ({} bytes)", file, content != null ? content.length() : 0);
                    return content;
                }
            }
            
            log.warn("未找到匹配正则 {} 的文件", filePattern);
            return null;
            
        } catch (Exception e) {
            log.error("读取远程文件失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 更新阶段状态
     */
    private void updatePhaseStatus(TaskServer taskServer, String phase, String status, Integer exitCode) {
        switch (phase) {
            case "deploy":
                taskServer.setDeployStatus(status);
                if (status.equals("running")) {
                    taskServer.setDeployStartedAt(LocalDateTime.now());
                } else if (status.equals("completed") || status.equals("failed")) {
                    taskServer.setDeployFinishedAt(LocalDateTime.now());
                    if (exitCode != null) taskServer.setDeployExitCode(exitCode);
                }
                break;
            case "run":
                taskServer.setRunStatus(status);
                if (status.equals("running")) {
                    taskServer.setStartedAt(LocalDateTime.now());
                } else if (status.equals("completed") || status.equals("failed")) {
                    taskServer.setFinishedAt(LocalDateTime.now());
                    if (exitCode != null) taskServer.setExitCode(exitCode);
                }
                break;
            case "cleanup":
                taskServer.setCleanupStatus(status);
                if (status.equals("running")) {
                    taskServer.setCleanupStartedAt(LocalDateTime.now());
                } else if (status.equals("completed") || status.equals("failed")) {
                    taskServer.setCleanupFinishedAt(LocalDateTime.now());
                    if (exitCode != null) taskServer.setCleanupExitCode(exitCode);
                }
                break;
        }

        // 更新整体状态
        updateOverallStatus(taskServer);
    }

    /**
     * 更新整体状态
     */
    private void updateOverallStatus(TaskServer taskServer) {
        // 1. 如果任何阶段正在执行，整体状态为 running
        if ("running".equals(taskServer.getDeployStatus()) ||
            "running".equals(taskServer.getRunStatus()) ||
            "running".equals(taskServer.getCleanupStatus())) {
            taskServer.setOverallStatus("running");
            return;
        }
        
        // 2. 如果任何阶段失败，整体状态为 failed
        if ("failed".equals(taskServer.getDeployStatus()) ||
            "failed".equals(taskServer.getRunStatus()) ||
            "failed".equals(taskServer.getCleanupStatus())) {
            taskServer.setOverallStatus("failed");
            return;
        }
        
        // 3. 检查是否所有需要执行的阶段都已完成
        boolean deployComplete = "completed".equals(taskServer.getDeployStatus()) || 
                                "skipped".equals(taskServer.getDeployStatus()) ||
                                taskServer.getDeployStatus() == null;
        boolean runComplete = "completed".equals(taskServer.getRunStatus()) ||
                             "skipped".equals(taskServer.getRunStatus());
        boolean cleanupComplete = "completed".equals(taskServer.getCleanupStatus()) || 
                                 "skipped".equals(taskServer.getCleanupStatus()) ||
                                 taskServer.getCleanupStatus() == null;
        
        if (deployComplete && runComplete && cleanupComplete) {
            taskServer.setOverallStatus("completed");
        } else {
            // 还有阶段未执行
            taskServer.setOverallStatus("pending");
        }
    }

    /**
     * 保存阶段输出
     */
    private void savePhaseOutput(TaskServer taskServer, String phase, String output, Integer exitCode) {
        switch (phase) {
            case "deploy":
                taskServer.setDeployOutput(output);
                break;
            case "run":
                taskServer.setOutput(output);
                break;
            case "cleanup":
                taskServer.setCleanupOutput(output);
                break;
        }
    }

    /**
     * 获取入口函数名
     */
    private String getEntryFunction(String phase, ScriptVersion scriptVersion) {
        switch (phase) {
            case "deploy":
                return scriptVersion.getDeployEntry();
            case "cleanup":
                return scriptVersion.getCleanupEntry();
            default:
                return null;
        }
    }

    /**
     * 获取阶段超时时间
     */
    private int getPhaseTimeout(Task task, String phase) {
        // 从脚本获取默认超时
        Script script = scriptMapper.selectById(task.getScriptId());
        int defaultTimeout = script != null && script.getDefaultTimeout() != null ? script.getDefaultTimeout() : 1800;
        
        switch (phase) {
            case "deploy":
                return task.getDeployTimeout() != null ? task.getDeployTimeout() : 600;
            case "cleanup":
                return task.getCleanupTimeout() != null ? task.getCleanupTimeout() : 300;
            default:
                return defaultTimeout;
        }
    }

    /**
     * 获取阶段名称
     */
    private String getPhaseName(String phase) {
        switch (phase) {
            case "deploy": return "部署";
            case "run": return "测试";
            case "cleanup": return "卸载";
            default: return phase;
        }
    }

    /**
     * 获取任务服务器列表
     */
    private List<TaskServer> getTaskServers(Long taskId) {
        return taskServerMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TaskServer>()
                        .eq(TaskServer::getTaskId, taskId)
        );
    }

    /**
     * 获取单个任务服务器记录
     */
    public TaskServer getTaskServer(Long taskId, Long serverId) {
        return taskServerMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TaskServer>()
                        .eq(TaskServer::getTaskId, taskId)
                        .eq(TaskServer::getServerId, serverId)
        );
    }

    /**
     * 上传所有脚本文件到目标服务器
     * @param scriptPathResult 输出参数，返回入口脚本路径
     */
    private boolean uploadAllScriptFiles(ExecutionContext context, Server server, Script script,
                                          ScriptVersion scriptVersion, String workDir, String[] scriptPathResult) {
        try {
            // 脚本存储目录（包含版本号）
            String version = scriptVersion.getVersion() != null ? scriptVersion.getVersion() : script.getCurrentVersion();
            Path scriptDir = Paths.get(scriptsPath, script.getId().toString(), version);
            String entryFileRaw = scriptVersion.getEntryFile() != null ? scriptVersion.getEntryFile() : script.getEntryFile();
            
            // 统一转换为 Linux 路径格式（lambda 中使用的变量必须是 effectively final）
            final String entryFile = entryFileRaw != null ? entryFileRaw.replace("\\", "/") : null;
            
            if (!Files.exists(scriptDir)) {
                // 如果文件目录不存在，创建简单脚本
                context.log("[WARN] 脚本目录不存在，仅上传入口脚本");
                String entryContent = scriptVersion.getContent();
                if (entryContent == null || entryContent.isEmpty()) {
                    entryContent = "#!/bin/bash\necho 'Script content not found'\nexit 1";
                }
                scriptPathResult[0] = workDir + "/script.sh";
                return SshService.uploadContent(server, entryContent, scriptPathResult[0]);
            }
            
            // 遍历并上传所有文件
            final String[] entryPath = {null};
            Files.walk(scriptDir)
                .filter(path -> !Files.isDirectory(path))
                .forEach(path -> {
                    try {
                        String relativePath = scriptDir.relativize(path).toString().replace("\\", "/");
                        String targetPath = workDir + "/" + relativePath;
                        
                        // 创建目标目录
                        int lastSlash = targetPath.lastIndexOf('/');
                        if (lastSlash > 0) {
                            String targetDir = targetPath.substring(0, lastSlash);
                            SshService.executeCommand(server, "mkdir -p " + targetDir, null, 5000);
                        }
                        
                        // 读取文件内容并上传
                        String content = Files.readString(path);
                        if (SshService.uploadContent(server, content, targetPath)) {
                            context.log("[DEBUG] 上传: " + relativePath);
                        }
                        
                        // 记录入口脚本路径
                        if (entryFile != null && relativePath.equals(entryFile)) {
                            entryPath[0] = targetPath;
                        }
                    } catch (Exception e) {
                        context.log("[WARN] 上传文件失败: " + path + " - " + e.getMessage());
                    }
                });
            
            // 设置入口脚本路径
            if (entryPath[0] != null) {
                scriptPathResult[0] = entryPath[0];
                // 设置执行权限
                SshService.executeCommand(server, "chmod +x " + entryPath[0], null, 5000);
            } else {
                // 如果没有找到入口文件，使用默认路径
                scriptPathResult[0] = workDir + "/" + (entryFile != null ? entryFile : "script.sh");
            }
            
            context.log("[INFO] 入口脚本: " + scriptPathResult[0]);
            return true;
        } catch (Exception e) {
            context.log("[ERROR] 上传脚本文件失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 获取脚本版本
     */
    private ScriptVersion getScriptVersion(Long scriptId, String version) {
        ScriptVersion sv = scriptVersionMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ScriptVersion>()
                        .eq(ScriptVersion::getScriptId, scriptId)
                        .eq(ScriptVersion::getVersion, version)
        );
        
        // 如果 ScriptVersion 存在但内容为空，尝试从文件读取
        if (sv != null && (sv.getContent() == null || sv.getContent().isEmpty())) {
            String content = readScriptContentFromFile(scriptId, sv.getEntryFile());
            if (content != null) {
                sv.setContent(content);
            }
        }
        
        return sv;
    }

    /**
     * 获取脚本内容（支持从 ScriptVersion 或文件系统读取）
     */
    private String getScriptContent(Script script, String version) {
        // 尝试从 ScriptVersion 获取
        ScriptVersion sv = getScriptVersion(script.getId(), version);
        if (sv != null && sv.getContent() != null && !sv.getContent().isEmpty()) {
            return sv.getContent();
        }
        
        // 直接从文件系统读取
        return readScriptContentFromFile(script.getId(), script.getEntryFile());
    }

    /**
     * 从文件系统读取脚本内容
     */
    private String readScriptContentFromFile(Long scriptId, String entryFile) {
        if (entryFile == null || entryFile.isEmpty()) {
            return null;
        }
        
        try {
            Path scriptPath = Paths.get(scriptsPath, scriptId.toString(), entryFile);
            if (Files.exists(scriptPath)) {
                return Files.readString(scriptPath);
            }
        } catch (IOException e) {
            log.error("读取脚本文件失败: {}", e.getMessage());
        }
        
        return null;
    }

    /**
     * 创建简化的 ScriptVersion（用于执行）
     */
    private ScriptVersion createSimpleScriptVersion(Script script, String content) {
        ScriptVersion sv = new ScriptVersion();
        sv.setScriptId(script.getId());
        sv.setVersion(script.getCurrentVersion());
        sv.setLifecycleMode(script.getLifecycleMode());
        sv.setHasDeploy(script.getHasDeploy());
        sv.setHasCleanup(script.getHasCleanup());
        sv.setEntryFile(script.getEntryFile());
        sv.setDeployEntry(script.getDeployEntry());
        sv.setCleanupEntry(script.getCleanupEntry());
        sv.setContent(content);
        sv.setFileCount(1);
        sv.setCreatedAt(LocalDateTime.now());
        return sv;
    }

    /**
     * 获取执行日志
     */
    public String getExecutionLog(Long taskId) {
        ExecutionContext context = runningTasks.get(taskId);
        return context != null ? context.getLogBuffer() : null;
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

    // ==================== 角色相关方法 ====================

    /**
     * 获取角色定义
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getRoleDefinition(ScriptVersion scriptVersion, String roleName) {
        if (scriptVersion == null || scriptVersion.getRoles() == null) {
            return null;
        }
        
        Map<String, Object> roles = scriptVersion.getRoles();
        if (roles.containsKey("roles")) {
            List<Map<String, Object>> roleList = (List<Map<String, Object>>) roles.get("roles");
            for (Map<String, Object> role : roleList) {
                if (roleName.equals(role.get("name"))) {
                    return role;
                }
            }
        }
        
        // 也支持直接是列表格式
        if (roles instanceof List) {
            List<Map<String, Object>> roleList = (List<Map<String, Object>>) roles;
            for (Map<String, Object> role : roleList) {
                if (roleName.equals(role.get("name"))) {
                    return role;
                }
            }
        }
        
        return null;
    }

    /**
     * 获取角色的入口函数
     */
    private String getRoleEntryFunction(String phase, ScriptVersion scriptVersion, String role) {
        Map<String, Object> roleDef = getRoleDefinition(scriptVersion, role);
        
        if (roleDef != null) {
            if ("deploy".equals(phase)) {
                Object deployFunc = roleDef.get("deployFunction");
                return deployFunc != null ? deployFunc.toString() : null;
            } else if ("run".equals(phase)) {
                Object entryFunc = roleDef.get("entryFunction");
                return entryFunc != null ? entryFunc.toString() : null;
            } else if ("cleanup".equals(phase)) {
                Object cleanupFunc = roleDef.get("cleanupFunction");
                return cleanupFunc != null ? cleanupFunc.toString() : null;
            }
        }
        
        // 如果没有角色定义，使用原来的逻辑
        return getEntryFunction(phase, scriptVersion);
    }

    /**
     * 构建角色参数（包括依赖角色的参数注入）
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> buildRoleParams(Task task, TaskServer taskServer, 
                                                  ScriptVersion scriptVersion, String phase) {
        Map<String, Object> params = new LinkedHashMap<>();
        
        // 0. 注入角色基本信息
        String role = taskServer.getRole();
        if (role != null && !"default".equals(role)) {
            params.put("ROLE", role);
            params.put("ROLE_NAME", role);
        }
        
        // 1. 添加共享参数
        if (task.getSharedParams() != null) {
            params.putAll(task.getSharedParams());
        }
        
        // 2. 添加阶段特定参数
        if ("deploy".equals(phase) && task.getDeployParams() != null) {
            params.putAll(task.getDeployParams());
        } else if ("run".equals(phase) && task.getRunParams() != null) {
            params.putAll(task.getRunParams());
        }
        
        // 3. 添加角色特定参数
        if (taskServer.getRoleParams() != null) {
            params.putAll(taskServer.getRoleParams());
        }
        
        // 4. 注入依赖角色的参数
        if (role != null && !"default".equals(role)) {
            Map<String, Object> roleDef = getRoleDefinition(scriptVersion, role);
            if (roleDef != null) {
                List<String> dependsOn = (List<String>) roleDef.get("dependsOn");
                if (dependsOn != null && !dependsOn.isEmpty()) {
                    // 查找依赖角色的服务器
                    List<TaskServer> allTaskServers = taskServerMapper.selectList(
                        new LambdaQueryWrapper<TaskServer>().eq(TaskServer::getTaskId, task.getId())
                    );
                    
                    for (String depRole : dependsOn) {
                        for (TaskServer ts : allTaskServers) {
                            if (depRole.equals(ts.getRole())) {
                                Server depServer = serverMapper.selectById(ts.getServerId());
                                if (depServer != null) {
                                    // 注入目标服务器信息
                                    params.put("target_host", depServer.getHost());
                                    params.put("target_ip", depServer.getHost());
                                    params.put("target_server_name", depServer.getName());
                                    params.put("target_server_id", depServer.getId());
                                    
                                    // 如果依赖角色有端口参数，也注入
                                    if (ts.getRoleParams() != null && ts.getRoleParams().containsKey("port")) {
                                        params.put("target_port", ts.getRoleParams().get("port"));
                                    }
                                }
                                break;
                            }
                        }
                    }
                }
            }
        }
        
        return params;
    }

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
            String timestamp = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss").format(LocalDateTime.now());
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
}

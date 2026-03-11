package com.autotest.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.autotest.common.PageResult;
import com.autotest.dto.request.TaskCreateRequest;
import com.autotest.dto.request.TaskQueryRequest;
import com.autotest.dto.response.TaskDetailResponse;
import com.autotest.entity.Script;
import com.autotest.entity.Server;
import com.autotest.entity.Task;
import com.autotest.entity.TaskServer;
import com.autotest.exception.BusinessException;
import com.autotest.mapper.ScriptMapper;
import com.autotest.mapper.ServerMapper;
import com.autotest.mapper.TaskMapper;
import com.autotest.mapper.TaskServerMapper;
import com.autotest.service.TaskExecutionService;
import com.autotest.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
    private final ScriptMapper scriptMapper;
    private final ServerMapper serverMapper;
    private final TaskExecutionService taskExecutionService;

    @Override
    public PageResult<Task> listTasks(TaskQueryRequest request) {
        LambdaQueryWrapper<Task> wrapper = new LambdaQueryWrapper<>();
        
        // 名称模糊搜索
        if (StringUtils.hasText(request.getName())) {
            wrapper.like(Task::getName, request.getName());
        }
        
        // 状态筛选
        if (StringUtils.hasText(request.getStatus())) {
            wrapper.eq(Task::getStatus, request.getStatus());
        }
        
        // 脚本筛选
        if (request.getScriptId() != null) {
            wrapper.eq(Task::getScriptId, request.getScriptId());
        }
        
        // 排序
        wrapper.orderByDesc(Task::getCreatedAt);
        
        Page<Task> page = taskMapper.selectPage(request.<Task>toPage(), wrapper);
        
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
        
        for (TaskServer ts : servers) {
            // 检查所有阶段的综合状态
            boolean deploySuccess = "completed".equals(ts.getDeployStatus()) || 
                                   "skipped".equals(ts.getDeployStatus()) ||
                                   ts.getDeployStatus() == null;
            boolean runSuccess = "completed".equals(ts.getRunStatus());
            boolean cleanupSuccess = "completed".equals(ts.getCleanupStatus()) || 
                                    "skipped".equals(ts.getCleanupStatus()) ||
                                    ts.getCleanupStatus() == null;
            
            // 只有所有成功的阶段才算成功
            if (deploySuccess && runSuccess && cleanupSuccess) {
                successCount++;
            } else if (ts.getRunStatus() != null || ts.getDeployStatus() != null || ts.getCleanupStatus() != null) {
                // 有任何执行记录但不是全部成功，则算失败
                failCount++;
            }
        }
        
        task.setServerCount(totalCount);
        task.setSuccessCount(successCount);
        task.setFailCount(failCount);
        
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
        response.setLifecycleMode(task.getSkipDeploy() ? "simple" : "full");
        response.setSharedParams(task.getSharedParams());
        response.setDeployParams(task.getDeployParams());
        response.setRunParams(task.getRunParams());
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
            scriptInfo.setLifecycleMode(script.getLifecycleMode());
            scriptInfo.setHasDeploy(script.getHasDeploy());
            scriptInfo.setHasCleanup(script.getHasCleanup());
            response.setScript(scriptInfo);
        }
        
        // 获取生命周期配置
        TaskDetailResponse.LifecycleConfig lifecycleConfig = new TaskDetailResponse.LifecycleConfig();
        lifecycleConfig.setSkipDeploy(task.getSkipDeploy());
        lifecycleConfig.setSkipCleanup(task.getSkipCleanup());
        lifecycleConfig.setDeployTimeout(task.getDeployTimeout());
        lifecycleConfig.setCleanupTimeout(task.getCleanupTimeout());
        response.setLifecycleConfig(lifecycleConfig);
        
        // 获取服务器执行状态
        LambdaQueryWrapper<TaskServer> tsWrapper = new LambdaQueryWrapper<>();
        tsWrapper.eq(TaskServer::getTaskId, id);
        List<TaskServer> taskServers = taskServerMapper.selectList(tsWrapper);
        
        List<TaskDetailResponse.ServerProgress> servers = taskServers.stream()
                .map(ts -> {
                    TaskDetailResponse.ServerProgress progress = new TaskDetailResponse.ServerProgress();
                    progress.setServerId(ts.getServerId());
                    progress.setOverallStatus(ts.getOverallStatus());
                    
                    // 角色信息
                    progress.setRole(ts.getRole());
                    progress.setRoleParams(ts.getRoleParams());
                    
                    // 当前执行信息
                    progress.setCurrentPhase(ts.getCurrentPhase());
                    progress.setCurrentCommand(ts.getCurrentCommand());
                    progress.setCommandStartedAt(ts.getCommandStartedAt());
                    
                    // 部署阶段
                    TaskDetailResponse.StageDetail deploy = new TaskDetailResponse.StageDetail();
                    deploy.setStatus(ts.getDeployStatus());
                    deploy.setExitCode(ts.getDeployExitCode());
                    deploy.setStartedAt(ts.getDeployStartedAt());
                    deploy.setFinishedAt(ts.getDeployFinishedAt());
                    deploy.setOutput(ts.getDeployOutput());
                    progress.setDeploy(deploy);
                    
                    // 执行阶段
                    TaskDetailResponse.StageDetail run = new TaskDetailResponse.StageDetail();
                    run.setStatus(ts.getRunStatus());
                    run.setExitCode(ts.getExitCode());
                    run.setStartedAt(ts.getStartedAt());
                    run.setFinishedAt(ts.getFinishedAt());
                    run.setOutput(ts.getOutput());
                    progress.setRun(run);
                    
                    // 卸载阶段
                    TaskDetailResponse.StageDetail cleanup = new TaskDetailResponse.StageDetail();
                    cleanup.setStatus(ts.getCleanupStatus());
                    cleanup.setExitCode(ts.getCleanupExitCode());
                    cleanup.setStartedAt(ts.getCleanupStartedAt());
                    cleanup.setFinishedAt(ts.getCleanupFinishedAt());
                    cleanup.setOutput(ts.getCleanupOutput());
                    progress.setCleanup(cleanup);
                    
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Task createTask(TaskCreateRequest request) {
        // 验证脚本
        Script script = scriptMapper.selectById(request.getScriptId());
        if (script == null) {
            throw BusinessException.of("脚本不存在");
        }
        
        // 验证服务器
        List<Long> serverIds = request.getServerIds();
        for (Long serverId : serverIds) {
            Server server = serverMapper.selectById(serverId);
            if (server == null) {
                throw BusinessException.of("服务器不存在: " + serverId);
            }
        }
        
        Task task = new Task();
        task.setName(request.getName());
        task.setDescription(request.getDescription());
        task.setScriptId(request.getScriptId());
        task.setScriptVersion(request.getScriptVersion());
        task.setSharedParams(request.getSharedParams());
        task.setDeployParams(request.getDeployParams());
        task.setRunParams(request.getRunParams());
        task.setExecutionMode(request.getExecutionMode());
        task.setParallelMode(request.getParallelMode());
        task.setMaxParallel(request.getMaxParallel());
        task.setFailureStrategy(request.getFailureStrategy());
        task.setCollectEnabled(request.getCollectEnabled());
        task.setCollectConfig(request.getCollectConfig());
        task.setRoleExecutionStrategy(request.getRoleExecutionStrategy());
        task.setStatus("pending");
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        
        // 生命周期配置
        if (request.getLifecycleConfig() != null) {
            task.setSkipDeploy(request.getLifecycleConfig().getSkipDeploy());
            task.setSkipCleanup(request.getLifecycleConfig().getSkipCleanup());
            task.setDeployTimeout(request.getLifecycleConfig().getDeployTimeout());
            task.setCleanupTimeout(request.getLifecycleConfig().getCleanupTimeout());
        }
        
        // 定时执行时间
        if ("scheduled".equals(request.getExecutionMode()) && StringUtils.hasText(request.getScheduledTime())) {
            task.setScheduledTime(LocalDateTime.parse(request.getScheduledTime()));
        }
        
        taskMapper.insert(task);
        
        // 构建服务器ID到角色配置列表的映射（支持同一服务器多角色）
        Map<Long, List<Map<String, Object>>> serverRolesMap = new java.util.HashMap<>();
        if (request.getServerRoles() != null) {
            for (var src : request.getServerRoles()) {
                serverRolesMap.computeIfAbsent(src.getServerId(), k -> new java.util.ArrayList<>())
                    .add(Map.of(
                        "role", src.getRole() != null ? src.getRole() : "default",
                        "roleParams", src.getRoleParams() != null ? src.getRoleParams() : Map.of()
                    ));
            }
        }
        
        // 创建任务服务器关联（支持同一服务器多角色）
        for (Long serverId : serverIds) {
            List<Map<String, Object>> roleConfigs = serverRolesMap.get(serverId);
            if (roleConfigs != null && !roleConfigs.isEmpty()) {
                // 为每个角色创建一个 TaskServer 记录
                for (Map<String, Object> roleConfig : roleConfigs) {
                    TaskServer taskServer = new TaskServer();
                    taskServer.setTaskId(task.getId());
                    taskServer.setServerId(serverId);
                    taskServer.setOverallStatus("pending");
                    taskServer.setCreatedAt(LocalDateTime.now());
                    taskServer.setRole((String) roleConfig.get("role"));
                    @SuppressWarnings("unchecked")
                    Map<String, Object> roleParams = (Map<String, Object>) roleConfig.get("roleParams");
                    taskServer.setRoleParams(roleParams);
                    taskServerMapper.insert(taskServer);
                }
            } else {
                // 没有角色配置，使用默认角色
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
    @Transactional(rollbackFor = Exception.class)
    public Task updateTask(Long id, TaskCreateRequest request) {
        Task task = taskMapper.selectById(id);
        if (task == null) {
            throw BusinessException.of("任务不存在");
        }
        
        if (!"pending".equals(task.getStatus())) {
            throw BusinessException.of("只有待执行状态的任务可以修改");
        }
        
        task.setName(request.getName());
        task.setDescription(request.getDescription());
        task.setSharedParams(request.getSharedParams());
        task.setDeployParams(request.getDeployParams());
        task.setRunParams(request.getRunParams());
        task.setUpdatedAt(LocalDateTime.now());
        
        taskMapper.updateById(task);
        return task;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTask(Long id) {
        Task task = taskMapper.selectById(id);
        if (task == null) {
            throw BusinessException.of("任务不存在");
        }
        
        // 删除任务服务器关联
        LambdaQueryWrapper<TaskServer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TaskServer::getTaskId, id);
        taskServerMapper.delete(wrapper);
        
        // 删除任务
        taskMapper.deleteById(id);
    }

    @Override
    public void executeTask(Long id) {
        Task task = taskMapper.selectById(id);
        if (task == null) {
            throw BusinessException.of("任务不存在");
        }
        
        if (!"pending".equals(task.getStatus()) && !"failed".equals(task.getStatus())) {
            throw BusinessException.of("只有待执行或失败状态的任务可以执行");
        }
        
        // 异步执行任务
        final Long taskId = id;
        new Thread(() -> {
            try {
                taskExecutionService.executeTask(taskId, null);
            } catch (Exception e) {
                log.error("任务执行失败: {}", e.getMessage(), e);
            }
        }).start();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelTask(Long id) {
        Task task = taskMapper.selectById(id);
        if (task == null) {
            throw BusinessException.of("任务不存在");
        }
        
        if ("completed".equals(task.getStatus()) || "cancelled".equals(task.getStatus())) {
            throw BusinessException.of("任务已完成或已取消");
        }
        
        task.setStatus("cancelled");
        task.setFinishedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
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
            // 通用状态
            ts.setOverallStatus("pending");
            ts.setProgress(0);
            
            // 部署阶段
            ts.setDeployStatus(null);
            ts.setDeployStartedAt(null);
            ts.setDeployFinishedAt(null);
            ts.setDeployExitCode(null);
            ts.setDeployOutput(null);
            ts.setDeployError(null);
            
            // 执行阶段
            ts.setRunStatus(null);
            ts.setStartedAt(null);
            ts.setFinishedAt(null);
            ts.setExitCode(null);
            ts.setOutput(null);
            ts.setErrorMessage(null);
            ts.setParsedResult(null);
            
            // 清理阶段
            ts.setCleanupStatus(null);
            ts.setCleanupStartedAt(null);
            ts.setCleanupFinishedAt(null);
            ts.setCleanupExitCode(null);
            ts.setCleanupOutput(null);
            ts.setCleanupError(null);
            
            taskServerMapper.updateById(ts);
        }
    }

    @Override
    public Object getTaskProgress(Long id) {
        // TODO: 实现进度查询
        return getTaskDetail(id);
    }

    @Override
    public Object getTaskLogs(Long id, Long serverId, String stage) {
        Task task = taskMapper.selectById(id);
        if (task == null) {
            throw BusinessException.of("任务不存在");
        }
        
        if (serverId != null) {
            LambdaQueryWrapper<TaskServer> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(TaskServer::getTaskId, id)
                    .eq(TaskServer::getServerId, serverId);
            TaskServer taskServer = taskServerMapper.selectOne(wrapper);
            if (taskServer == null) {
                throw BusinessException.of("任务服务器不存在");
            }
            return taskServer;
        }
        
        // 返回所有服务器日志
        LambdaQueryWrapper<TaskServer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TaskServer::getTaskId, id);
        return taskServerMapper.selectList(wrapper);
    }
    
    @Override
    public Map<String, Integer> fixAllTaskStatus() {
        // 获取所有任务服务器
        List<TaskServer> allServers = taskServerMapper.selectList(null);
        
        int fixedServers = 0;
        int fixedTasks = 0;
        
        for (TaskServer ts : allServers) {
            String oldStatus = ts.getOverallStatus();
            
            // 计算正确的整体状态
            if ("running".equals(ts.getDeployStatus()) ||
                "running".equals(ts.getRunStatus()) ||
                "running".equals(ts.getCleanupStatus())) {
                ts.setOverallStatus("running");
            } else if ("failed".equals(ts.getDeployStatus()) ||
                       "failed".equals(ts.getRunStatus()) ||
                       "failed".equals(ts.getCleanupStatus())) {
                ts.setOverallStatus("failed");
            } else {
                boolean deployComplete = "completed".equals(ts.getDeployStatus()) || 
                                        "skipped".equals(ts.getDeployStatus()) ||
                                        ts.getDeployStatus() == null;
                boolean runComplete = "completed".equals(ts.getRunStatus()) ||
                                     "skipped".equals(ts.getRunStatus());
                boolean cleanupComplete = "completed".equals(ts.getCleanupStatus()) || 
                                         "skipped".equals(ts.getCleanupStatus()) ||
                                         ts.getCleanupStatus() == null;
                
                if (deployComplete && runComplete && cleanupComplete) {
                    ts.setOverallStatus("completed");
                } else {
                    ts.setOverallStatus("pending");
                }
            }
            
            if (!ts.getOverallStatus().equals(oldStatus)) {
                taskServerMapper.updateById(ts);
                fixedServers++;
            }
        }
        
        // 更新任务状态
        List<Task> allTasks = taskMapper.selectList(
            new LambdaQueryWrapper<Task>().in(Task::getStatus, "completed", "completed_with_errors")
        );
        
        for (Task task : allTasks) {
            long failCount = allServers.stream()
                .filter(ts -> ts.getTaskId().equals(task.getId()) && "failed".equals(ts.getOverallStatus()))
                .count();
            
            String expectedStatus = failCount > 0 ? "completed_with_errors" : "completed";
            if (!expectedStatus.equals(task.getStatus())) {
                task.setStatus(expectedStatus);
                taskMapper.updateById(task);
                fixedTasks++;
            }
        }
        
        return Map.of("fixedServers", fixedServers, "fixedTasks", fixedTasks);
    }
}

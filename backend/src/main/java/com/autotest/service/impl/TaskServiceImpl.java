package com.autotest.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.autotest.common.PageResult;
import com.autotest.dto.request.TaskCreateRequest;
import com.autotest.dto.request.TaskQueryRequest;
import com.autotest.dto.response.TaskDetailResponse;
import com.autotest.entity.Script;
import com.autotest.entity.ScriptVersion;
import com.autotest.entity.Server;
import com.autotest.entity.Task;
import com.autotest.entity.TaskServer;
import com.autotest.entity.TaskStep;
import com.autotest.exception.BusinessException;
import com.autotest.mapper.ScriptMapper;
import com.autotest.mapper.ScriptVersionMapper;
import com.autotest.mapper.ServerMapper;
import com.autotest.mapper.TaskMapper;
import com.autotest.mapper.TaskServerMapper;
import com.autotest.mapper.TaskStepMapper;
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
    private final TaskStepMapper taskStepMapper;
    private final ScriptMapper scriptMapper;
    private final ScriptVersionMapper scriptVersionMapper;
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
        task.setStepParams(request.getStepParams());
        task.setExecutionMode(request.getExecutionMode());
        task.setParallelMode(request.getParallelMode());
        task.setMaxParallel(request.getMaxParallel());
        task.setFailureStrategy(request.getFailureStrategy());
        task.setCollectEnabled(request.getCollectEnabled());
        
        // 如果请求中没有 collectConfig，从脚本版本中获取
        if (request.getCollectConfig() != null && !request.getCollectConfig().isEmpty()) {
            task.setCollectConfig(request.getCollectConfig());
        } else {
            // 从脚本版本获取输出收集配置
            ScriptVersion scriptVersion = scriptVersionMapper.selectOne(
                new LambdaQueryWrapper<ScriptVersion>()
                    .eq(ScriptVersion::getScriptId, request.getScriptId())
                    .eq(ScriptVersion::getVersion, request.getScriptVersion() != null ? request.getScriptVersion() : "v1.0.0")
            );
            if (scriptVersion != null && scriptVersion.getOutputConfig() != null) {
                task.setCollectConfig(scriptVersion.getOutputConfig());
                task.setCollectEnabled(true);
                log.info("从脚本版本复制输出收集配置: scriptVersion={}, config={}", scriptVersion.getVersion(), scriptVersion.getOutputConfig());
            }
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
        
        // 创建任务服务器关联
        for (Long serverId : serverIds) {
            TaskServer taskServer = new TaskServer();
            taskServer.setTaskId(task.getId());
            taskServer.setServerId(serverId);
            taskServer.setOverallStatus("pending");
            taskServer.setCreatedAt(LocalDateTime.now());
            taskServer.setRole("default");
            taskServerMapper.insert(taskServer);
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
        // 步骤执行模式下，状态由 TaskExecutionService 管理
        // 此方法主要用于修复异常中断的任务状态
        int fixedTasks = 0;
        return Map.of("fixedTasks", fixedTasks);
    }
}

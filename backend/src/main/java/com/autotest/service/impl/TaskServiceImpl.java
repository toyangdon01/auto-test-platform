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
import com.autotest.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
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
        
        Page<Task> page = taskMapper.selectPage(request.toPage(), wrapper);
        return PageResult.of(page);
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
                    
                    // 部署阶段
                    TaskDetailResponse.StageDetail deploy = new TaskDetailResponse.StageDetail();
                    deploy.setStatus(ts.getDeployStatus());
                    deploy.setExitCode(ts.getDeployExitCode());
                    progress.setDeploy(deploy);
                    
                    // 执行阶段
                    TaskDetailResponse.StageDetail run = new TaskDetailResponse.StageDetail();
                    run.setStatus(ts.getRunStatus());
                    run.setExitCode(ts.getRunExitCode());
                    progress.setRun(run);
                    
                    // 卸载阶段
                    TaskDetailResponse.StageDetail cleanup = new TaskDetailResponse.StageDetail();
                    cleanup.setStatus(ts.getCleanupStatus());
                    cleanup.setExitCode(ts.getCleanupExitCode());
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
        
        // 创建任务服务器关联
        for (Long serverId : serverIds) {
            TaskServer taskServer = new TaskServer();
            taskServer.setTaskId(task.getId());
            taskServer.setServerId(serverId);
            taskServer.setOverallStatus("pending");
            taskServer.setCreatedAt(LocalDateTime.now());
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
    @Transactional(rollbackFor = Exception.class)
    public void executeTask(Long id) {
        Task task = taskMapper.selectById(id);
        if (task == null) {
            throw BusinessException.of("任务不存在");
        }
        
        if (!"pending".equals(task.getStatus())) {
            throw BusinessException.of("只有待执行状态的任务可以执行");
        }
        
        task.setStatus("running");
        task.setStartedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        
        // TODO: 异步执行任务
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
        
        if (!"failed".equals(task.getStatus())) {
            throw BusinessException.of("只有失败的任务可以重试");
        }
        
        task.setStatus("pending");
        task.setStartedAt(null);
        task.setFinishedAt(null);
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        
        // TODO: 重置任务服务器状态
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
}

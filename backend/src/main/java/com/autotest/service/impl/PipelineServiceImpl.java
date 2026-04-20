package com.autotest.service.impl;

import com.autotest.dto.request.TaskCreateRequest;
import com.autotest.dto.response.TaskDetailResponse;
import com.autotest.entity.*;
import com.autotest.mapper.*;
import com.autotest.service.PipelineService;
import com.autotest.service.TaskService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PipelineServiceImpl implements PipelineService {

    private final PipelineMapper pipelineMapper;
    private final PipelineTaskMapper pipelineTaskMapper;
    private final PipelineRunMapper pipelineRunMapper;
    private final PipelineRunTaskMapper pipelineRunTaskMapper;
    private final TaskService taskService;
    private final ScriptMapper scriptMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public Pipeline createPipeline(Pipeline pipeline, List<PipelineTask> tasks) {
        pipeline.setEnabled(true);
        pipeline.setCreatedAt(LocalDateTime.now());
        pipeline.setUpdatedAt(LocalDateTime.now());
        pipelineMapper.insert(pipeline);

        if (tasks != null && !tasks.isEmpty()) {
            int order = 1;
            for (PipelineTask task : tasks) {
                task.setPipelineId(pipeline.getId());
                task.setOrderNum(order++);
                task.setEnabled(true);
                task.setCreatedAt(LocalDateTime.now());
                task.setUpdatedAt(LocalDateTime.now());
                pipelineTaskMapper.insert(task);
            }
        }

        return pipeline;
    }

    @Override
    public Page<Pipeline> listPipelines(int page, int size, String keyword) {
        Page<Pipeline> pageObj = new Page<>(page, size);
        LambdaQueryWrapper<Pipeline> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(Pipeline::getName, keyword);
        }
        wrapper.orderByDesc(Pipeline::getCreatedAt);
        return pipelineMapper.selectPage(pageObj, wrapper);
    }

    @Override
    public Pipeline getPipelineById(Long id) {
        return pipelineMapper.selectById(id);
    }

    @Override
    @Transactional
    public Pipeline updatePipeline(Long id, Pipeline pipeline, List<PipelineTask> tasks) {
        Pipeline existing = pipelineMapper.selectById(id);
        if (existing == null) {
            throw new RuntimeException("Pipeline not found: " + id);
        }

        existing.setName(pipeline.getName());
        existing.setDescription(pipeline.getDescription());
        existing.setMaxParallel(pipeline.getMaxParallel());
        existing.setEnabled(pipeline.getEnabled());
        existing.setUpdatedAt(LocalDateTime.now());
        pipelineMapper.updateById(existing);

        // 删除旧任务
        LambdaQueryWrapper<PipelineTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PipelineTask::getPipelineId, id);
        pipelineTaskMapper.delete(wrapper);

        // 添加新任务
        if (tasks != null && !tasks.isEmpty()) {
            int order = 1;
            for (PipelineTask task : tasks) {
                task.setId(null);
                task.setPipelineId(id);
                task.setOrderNum(order++);
                task.setEnabled(true);
                task.setCreatedAt(LocalDateTime.now());
                task.setUpdatedAt(LocalDateTime.now());
                pipelineTaskMapper.insert(task);
            }
        }

        return existing;
    }

    @Override
    @Transactional
    public void deletePipeline(Long id) {
        // 删除关联的 pipeline_run_tasks
        List<PipelineRun> runs = pipelineRunMapper.selectList(
            new LambdaQueryWrapper<PipelineRun>().eq(PipelineRun::getPipelineId, id)
        );
        for (PipelineRun run : runs) {
            pipelineRunTaskMapper.delete(
                new LambdaQueryWrapper<PipelineRunTask>().eq(PipelineRunTask::getPipelineRunId, run.getId())
            );
        }

        // 删除 pipeline_runs
        pipelineRunMapper.delete(
            new LambdaQueryWrapper<PipelineRun>().eq(PipelineRun::getPipelineId, id)
        );

        // 删除 pipeline_tasks
        pipelineTaskMapper.delete(
            new LambdaQueryWrapper<PipelineTask>().eq(PipelineTask::getPipelineId, id)
        );

        // 删除 pipeline
        pipelineMapper.deleteById(id);
    }

    @Override
    public List<PipelineTask> getPipelineTasks(Long pipelineId) {
        LambdaQueryWrapper<PipelineTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PipelineTask::getPipelineId, pipelineId);
        wrapper.orderByAsc(PipelineTask::getOrderNum);
        return pipelineTaskMapper.selectList(wrapper);
    }

    @Override
    @Transactional
    public PipelineTask addPipelineTask(Long pipelineId, PipelineTask task) {
        // 获取最大顺序
        Integer maxOrder = pipelineTaskMapper.selectMaxOrder(pipelineId);
        task.setPipelineId(pipelineId);
        task.setOrderNum(maxOrder == null ? 1 : maxOrder + 1);
        task.setEnabled(true);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        pipelineTaskMapper.insert(task);
        return task;
    }

    @Override
    @Transactional
    public PipelineTask updatePipelineTask(Long pipelineId, Long taskId, PipelineTask task) {
        PipelineTask existing = pipelineTaskMapper.selectById(taskId);
        if (existing == null || !existing.getPipelineId().equals(pipelineId)) {
            throw new RuntimeException("PipelineTask not found");
        }

        existing.setName(task.getName());
        existing.setScriptId(task.getScriptId());
        existing.setServerIds(task.getServerIds());
        existing.setStepServerMapping(task.getStepServerMapping());
        existing.setStepParams(task.getStepParams());
        existing.setSharedParams(task.getSharedParams());
        existing.setTimeout(task.getTimeout());
        existing.setDependsOn(task.getDependsOn());
        existing.setEnabled(task.getEnabled());
        existing.setUpdatedAt(LocalDateTime.now());
        pipelineTaskMapper.updateById(existing);
        return existing;
    }

    @Override
    @Transactional
    public void deletePipelineTask(Long pipelineId, Long taskId) {
        PipelineTask task = pipelineTaskMapper.selectById(taskId);
        if (task != null && task.getPipelineId().equals(pipelineId)) {
            pipelineTaskMapper.deleteById(taskId);
        }
    }

    @Override
    public PipelineRun executePipeline(Long pipelineId, Map<Long, List<Long>> serverMapping) {
        Pipeline pipeline = pipelineMapper.selectById(pipelineId);
        if (pipeline == null) {
            throw new RuntimeException("Pipeline not found: " + pipelineId);
        }

        // 创建执行记录
        PipelineRun run = new PipelineRun();
        run.setPipelineId(pipelineId);
        run.setPipelineName(pipeline.getName());
        run.setStatus("running");
        run.setStartedAt(LocalDateTime.now());
        run.setCreatedAt(LocalDateTime.now());
        run.setTriggeredBy("admin");
        pipelineRunMapper.insert(run);

        // 获取编排任务
        List<PipelineTask> tasks = getPipelineTasks(pipelineId);

        // 构建任务依赖图
        Map<Long, List<Long>> dependencyGraph = buildDependencyGraph(tasks);

        // 创建任务执行记录
        List<Long> taskIds = new ArrayList<>();
        Map<Long, Long> pipelineTaskToTaskId = new HashMap<>();

        for (PipelineTask pt : tasks) {
            List<Long> servers = serverMapping != null ? serverMapping.get(pt.getId()) : null;
            if (servers == null || servers.isEmpty()) {
                // 使用默认服务器
                servers = parseServerIds(pt.getServerIds());
            }
            
            // 如果 serverIds 为空，从 stepServerMapping 中提取服务器
            if ((servers == null || servers.isEmpty()) && pt.getStepServerMapping() != null && !pt.getStepServerMapping().isEmpty()) {
                try {
                    Map<String, List<Object>> stepServerMap = objectMapper.readValue(
                        pt.getStepServerMapping(), new TypeReference<Map<String, List<Object>>>() {});
                    Set<Long> serverSet = new HashSet<>();
                    for (List<Object> serverList : stepServerMap.values()) {
                        for (Object obj : serverList) {
                            if (obj instanceof Number) {
                                serverSet.add(((Number) obj).longValue());
                            }
                        }
                    }
                    servers = new ArrayList<>(serverSet);
                } catch (JsonProcessingException e) {
                    log.error("Failed to parse stepServerMapping: {}", e.getMessage());
                }
            }

            if (servers == null || servers.isEmpty()) {
                log.warn("No servers configured for pipeline task: {}", pt.getName());
                continue;
            }

            // 创建 TaskCreateRequest
            TaskCreateRequest taskRequest = new TaskCreateRequest();
            taskRequest.setName(pt.getName());
            taskRequest.setScriptId(pt.getScriptId());
            
            // 获取脚本的当前版本
            Script script = scriptMapper.selectById(pt.getScriptId());
            if (script != null && script.getCurrentVersion() != null) {
                taskRequest.setScriptVersion(script.getCurrentVersion());
            } else {
                taskRequest.setScriptVersion("v1.0.0");
            }
            
            taskRequest.setServerIds(servers);
            taskRequest.setExecutionMode("immediate");
            taskRequest.setCollectEnabled(false);  // 编排任务默认关闭指标采集
            
            // 设置步骤服务器映射
            if (pt.getStepServerMapping() != null && !pt.getStepServerMapping().isEmpty()) {
                try {
                    Map<String, List<Object>> stepServerMap = objectMapper.readValue(
                        pt.getStepServerMapping(), new TypeReference<Map<String, List<Object>>>() {});
                    taskRequest.setStepServerMapping(stepServerMap);
                } catch (JsonProcessingException e) {
                    log.error("Failed to parse stepServerMapping: {}", e.getMessage());
                }
            }
            
            // 设置步骤参数
            if (pt.getStepParams() != null && !pt.getStepParams().isEmpty()) {
                try {
                    Map<String, Map<String, Object>> stepParams = objectMapper.readValue(
                        pt.getStepParams(), new TypeReference<Map<String, Map<String, Object>>>() {});
                    taskRequest.setStepParams(stepParams);
                } catch (JsonProcessingException e) {
                    log.error("Failed to parse stepParams: {}", e.getMessage());
                }
            }
            
            // 设置共享参数
            if (pt.getSharedParams() != null && !pt.getSharedParams().isEmpty()) {
                try {
                    Map<String, Object> sharedParams = objectMapper.readValue(
                        pt.getSharedParams(), new TypeReference<Map<String, Object>>() {});
                    taskRequest.setSharedParams(sharedParams);
                } catch (JsonProcessingException e) {
                    log.error("Failed to parse sharedParams: {}", e.getMessage());
                }
            }
            
            // 设置超时
            if (pt.getTimeout() != null) {
                taskRequest.setTimeout(pt.getTimeout().intValue());
            }

            Task task = taskService.createTask(taskRequest);
            taskIds.add(task.getId());
            pipelineTaskToTaskId.put(pt.getId(), task.getId());

            // 创建关联记录
            PipelineRunTask runTask = new PipelineRunTask();
            runTask.setPipelineRunId(run.getId());
            runTask.setTaskId(task.getId());
            runTask.setTaskName(pt.getName());
            runTask.setStatus("pending");
            runTask.setCreatedAt(LocalDateTime.now());
            pipelineRunTaskMapper.insert(runTask);
        }

        // 异步执行 DAG 任务
        final Long runId = run.getId();
        final int maxParallel = pipeline.getMaxParallel();
        new Thread(() -> {
            try {
                executeTasksDAG(runId, tasks, taskIds, pipelineTaskToTaskId, dependencyGraph, maxParallel);
            } catch (Exception e) {
                log.error("Failed to execute pipeline: {}", e.getMessage());
            }
        }).start();

        return run;
    }

    /**
     * 构建任务依赖图
     */
    private Map<Long, List<Long>> buildDependencyGraph(List<PipelineTask> tasks) {
        Map<Long, List<Long>> graph = new HashMap<>();
        Map<String, Long> nameToId = tasks.stream()
            .collect(Collectors.toMap(PipelineTask::getName, PipelineTask::getId));
        
        for (PipelineTask task : tasks) {
            List<Long> dependencies = new ArrayList<>();
            if (task.getDependsOn() != null && !task.getDependsOn().isEmpty()) {
                try {
                    List<String> depNames = objectMapper.readValue(
                        task.getDependsOn(), new TypeReference<List<String>>() {});
                    for (String depName : depNames) {
                        Long depId = nameToId.get(depName);
                        if (depId != null) {
                            dependencies.add(depId);
                        }
                    }
                } catch (JsonProcessingException e) {
                    log.error("Failed to parse dependsOn: {}", e.getMessage());
                }
            }
            graph.put(task.getId(), dependencies);
        }
        return graph;
    }

    /**
     * DAG 方式执行任务（异步调用）
     */
    private void executeTasksDAG(Long runId, List<PipelineTask> pipelineTasks, 
            List<Long> taskIds, Map<Long, Long> pipelineTaskToTaskId,
            Map<Long, List<Long>> dependencyGraph, int maxParallel) {
        
        Set<Long> completed = new HashSet<>();
        Set<Long> running = new HashSet<>();
        Set<Long> failed = new HashSet<>();
        
        while (completed.size() + failed.size() < taskIds.size()) {
            // 找出可以执行的任务（依赖都已完成）
            List<Long> readyToRun = new ArrayList<>();
            for (Long ptId : pipelineTaskToTaskId.keySet()) {
                Long taskId = pipelineTaskToTaskId.get(ptId);
                if (completed.contains(ptId) || running.contains(ptId) || failed.contains(ptId)) {
                    continue;
                }
                List<Long> deps = dependencyGraph.getOrDefault(ptId, Collections.emptyList());
                boolean allDepsCompleted = deps.stream().allMatch(completed::contains);
                boolean anyDepFailed = deps.stream().anyMatch(failed::contains);
                
                if (anyDepFailed) {
                    // 依赖失败，跳过此任务
                    failed.add(ptId);
                    continue;
                }
                
                if (allDepsCompleted && running.size() < maxParallel) {
                    readyToRun.add(taskId);
                    running.add(ptId);
                }
            }
            
            // 执行任务
            for (Long taskId : readyToRun) {
                try {
                    taskService.executeTask(taskId);
                } catch (Exception e) {
                    log.error("Failed to execute task {}: {}", taskId, e.getMessage());
                }
            }
            
            // 检查运行中的任务状态
            for (Long ptId : new HashSet<>(running)) {
                Long taskId = pipelineTaskToTaskId.get(ptId);
                try {
                    TaskDetailResponse task = taskService.getTaskDetail(taskId);
                    if ("completed".equals(task.getStatus())) {
                        running.remove(ptId);
                        completed.add(ptId);
                    } else if ("failed".equals(task.getStatus()) || "cancelled".equals(task.getStatus())) {
                        running.remove(ptId);
                        failed.add(ptId);
                    }
                } catch (Exception e) {
                    log.error("Failed to get task status: {}", taskId);
                }
            }
            
            // 等待一段时间再检查
            if (running.size() >= maxParallel || (readyToRun.isEmpty() && !running.isEmpty())) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        // 更新编排执行状态
        PipelineRun run = pipelineRunMapper.selectById(runId);
        if (run != null) {
            run.setStatus(failed.isEmpty() ? "completed" : "failed");
            run.setFinishedAt(LocalDateTime.now());
            pipelineRunMapper.updateById(run);
        }
    }



    private List<Long> parseServerIds(String serverIds) {
        if (serverIds == null || serverIds.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(serverIds, new TypeReference<List<Long>>() {});
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    @Override
    public Page<PipelineRun> listPipelineRuns(Long pipelineId, int page, int size) {
        Page<PipelineRun> pageObj = new Page<>(page, size);
        LambdaQueryWrapper<PipelineRun> wrapper = new LambdaQueryWrapper<>();
        if (pipelineId != null) {
            wrapper.eq(PipelineRun::getPipelineId, pipelineId);
        }
        wrapper.orderByDesc(PipelineRun::getCreatedAt);
        return pipelineRunMapper.selectPage(pageObj, wrapper);
    }

    @Override
    public PipelineRun getPipelineRunById(Long runId) {
        return pipelineRunMapper.selectById(runId);
    }

    @Override
    public List<Map<String, Object>> getPipelineRunTasks(Long runId) {
        List<PipelineRunTask> runTasks = pipelineRunTaskMapper.selectList(
            new LambdaQueryWrapper<PipelineRunTask>().eq(PipelineRunTask::getPipelineRunId, runId)
        );

        return runTasks.stream().map(rt -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", rt.getId());
            map.put("taskId", rt.getTaskId());
            map.put("taskName", rt.getTaskName());
            map.put("status", rt.getStatus());

            // 获取任务详情
            try {
                TaskDetailResponse task = taskService.getTaskDetail(rt.getTaskId());
                if (task != null) {
                    map.put("status", task.getStatus());
                }
            } catch (Exception e) {
                log.error("Failed to get task: {}", rt.getTaskId());
            }

            return map;
        }).collect(Collectors.toList());
    }

    @Override
    public void cancelPipelineRun(Long runId) {
        // 不使用事务，避免与正在执行的任务写入冲突
        PipelineRun run = pipelineRunMapper.selectById(runId);
        if (run == null) {
            throw new RuntimeException("PipelineRun not found: " + runId);
        }

        // 更新状态（单独操作，避免长事务）
        run.setStatus("cancelled");
        run.setFinishedAt(LocalDateTime.now());
        
        // 重试机制
        int retries = 3;
        while (retries > 0) {
            try {
                pipelineRunMapper.updateById(run);
                break;
            } catch (Exception e) {
                retries--;
                if (retries == 0) {
                    log.error("取消 PipelineRun 失败: {}", e.getMessage());
                    throw e;
                }
                try {
                    Thread.sleep(1000);  // 等待 1 秒后重试
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        // 取消关联的任务
        List<PipelineRunTask> runTasks = pipelineRunTaskMapper.selectList(
            new LambdaQueryWrapper<PipelineRunTask>().eq(PipelineRunTask::getPipelineRunId, runId)
        );

        for (PipelineRunTask rt : runTasks) {
            try {
                taskService.cancelTask(rt.getTaskId());
            } catch (Exception e) {
                log.error("Failed to cancel task: {}", rt.getTaskId());
            }
        }
    }
}

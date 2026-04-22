package com.autotest.service;

import com.autotest.dto.StepStatusCheckResult;
import com.autotest.entity.Server;
import com.autotest.entity.Task;
import com.autotest.entity.TaskServer;
import com.autotest.entity.TaskStep;
import com.autotest.mapper.ServerMapper;
import com.autotest.mapper.TaskMapper;
import com.autotest.mapper.TaskServerMapper;
import com.autotest.mapper.TaskStepMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 任务状态检查服务
 * <p>
 * 用于检测并更新异常状态的任务和步骤：
 * 1. 平台重启后恢复假 running 状态的任务
 * 2. 网络中断后恢复后台任务的真实状态
 * 3. 超时任务标记为失败
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskStatusCheckService {

    private final TaskMapper taskMapper;
    private final TaskStepMapper taskStepMapper;
    private final TaskServerMapper taskServerMapper;
    private final ServerMapper serverMapper;
    private final TaskExecutionService taskExecutionService;

    /**
     * 检查间隔（毫秒），默认 5 分钟
     */
    @Value("${autotest.task-check.interval:300000}")
    private long checkInterval;

    /**
     * 状态检查定时任务
     */
    @Scheduled(fixedRateString = "${autotest.task-check.interval:300000}")
    public void checkTaskStatus() {
        log.info("[TaskStatusCheck] 开始检查任务状态...");
        
        try {
            // 1. 查找所有 running 状态的任务
            List<Task> runningTasks = taskMapper.selectList(
                new LambdaQueryWrapper<Task>()
                    .eq(Task::getStatus, "running")
            );
            
            if (runningTasks.isEmpty()) {
                log.info("[TaskStatusCheck] 没有运行中的任务");
                return;
            }
            
            log.info("[TaskStatusCheck] 发现 {} 个运行中的任务", runningTasks.size());
            
            int updatedCount = 0;
            for (Task task : runningTasks) {
                try {
                    boolean updated = checkTask(task);
                    if (updated) {
                        updatedCount++;
                    }
                } catch (Exception e) {
                    log.error("[TaskStatusCheck] 检查任务 {} 失败: {}", task.getId(), e.getMessage());
                }
            }
            
            log.info("[TaskStatusCheck] 检查完成，更新了 {} 个任务的状态", updatedCount);
            
        } catch (Exception e) {
            log.error("[TaskStatusCheck] 检查过程出现异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 检查单个任务的状态
     * 
     * @return 是否有状态更新
     */
    private boolean checkTask(Task task) {
        boolean updated = false;
        
        // 1. 检查任务是否超时
        if (task.getStartedAt() != null && task.getTimeout() != null) {
            long elapsed = Duration.between(task.getStartedAt(), LocalDateTime.now()).toMillis();
            if (elapsed > task.getTimeout()) {
                log.warn("[TaskStatusCheck] 任务 {} 超时（已执行 {}ms，超时 {}ms）", 
                    task.getId(), elapsed, task.getTimeout());
                taskExecutionService.cancelTask(task.getId());
                return true;
            }
        }
        
        // 2. 获取任务的步骤
        List<TaskStep> steps = taskStepMapper.selectList(
            new LambdaQueryWrapper<TaskStep>()
                .eq(TaskStep::getTaskId, task.getId())
        );
        
        if (steps.isEmpty()) {
            // 没有步骤但任务还在运行，异常状态
            log.warn("[TaskStatusCheck] 任务 {} 没有步骤记录但状态为 running", task.getId());
            task.setStatus("failed");
            task.setFinishedAt(LocalDateTime.now());
            taskMapper.updateById(task);
            return true;
        }
        
        // 3. 检查每个 running 状态的步骤
        boolean hasRunningSteps = false;
        Set<Long> failedStepIds = new HashSet<>();
        
        for (TaskStep step : steps) {
            if (!"running".equals(step.getStatus())) {
                continue;
            }
            
            hasRunningSteps = true;
            
            // 获取步骤对应的服务器
            Server server = null;
            if (step.getServerId() != null) {
                server = serverMapper.selectById(step.getServerId());
            }
            
            if (server == null) {
                // 无法确定服务器，跳过（可能是本地执行）
                log.debug("[TaskStatusCheck] 任务 {} 步骤 {} 没有关联服务器，跳过检查", 
                    task.getId(), step.getStepName());
                continue;
            }
            
            // 检查步骤状态
            StepStatusCheckResult result = taskExecutionService.checkStepStatus(task, step, server);
            
            if (!result.isStillRunning()) {
                // 步骤实际已停止，更新状态
                log.info("[TaskStatusCheck] 任务 {} 步骤 {} 状态异常: {} -> {} (原因: {})", 
                    task.getId(), step.getStepName(), step.getStatus(), result.getStatus(), result.getReason());
                
                step.setStatus(result.getStatus());
                step.setErrorMessage(result.getReason());
                if (result.getExitCode() != null) {
                    step.setExitCode(result.getExitCode());
                }
                if (result.getOutput() != null) {
                    step.setOutput(result.getOutput());
                }
                step.setFinishedAt(LocalDateTime.now());
                taskStepMapper.updateById(step);
                
                failedStepIds.add(step.getId());
                updated = true;
            }
        }
        
        // 4. 如果没有 running 状态的步骤，重新计算任务状态
        if (!hasRunningSteps) {
            String newStatus = calculateTaskStatus(task, steps);
            if (!newStatus.equals(task.getStatus())) {
                log.info("[TaskStatusCheck] 任务 {} 状态更新: {} -> {}", 
                    task.getId(), task.getStatus(), newStatus);
                task.setStatus(newStatus);
                task.setFinishedAt(LocalDateTime.now());
                taskMapper.updateById(task);
                updated = true;
            }
        }
        
        return updated;
    }

    /**
     * 计算任务状态
     */
    private String calculateTaskStatus(Task task, List<TaskStep> steps) {
        boolean hasFailed = false;
        boolean hasSuccess = false;
        
        for (TaskStep step : steps) {
            if ("success".equals(step.getStatus()) || "completed".equals(step.getStatus())) {
                hasSuccess = true;
            } else if ("failed".equals(step.getStatus())) {
                hasFailed = true;
            } else if ("running".equals(step.getStatus())) {
                // 还有步骤在运行
                return "running";
            }
        }
        
        if (hasFailed && hasSuccess) {
            return "completed_with_errors";
        } else if (hasFailed) {
            return "failed";
        } else if (hasSuccess) {
            return "completed";
        } else {
            return "unknown";
        }
    }
}

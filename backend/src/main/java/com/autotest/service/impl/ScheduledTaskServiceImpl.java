package com.autotest.service.impl;

import com.autotest.common.PageResult;
import com.autotest.entity.ScheduledTask;
import com.autotest.entity.Task;
import com.autotest.mapper.ScheduledTaskMapper;
import com.autotest.mapper.TaskMapper;
import com.autotest.service.ScheduledTaskService;
import com.autotest.service.TaskExecutionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时任务服务实现
 *
 * @author auto-test-platform
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledTaskServiceImpl implements ScheduledTaskService {

    private final ScheduledTaskMapper scheduledTaskMapper;
    private final TaskMapper taskMapper;
    private final TaskExecutionService taskExecutionService;

    @Override
    public PageResult<ScheduledTask> getPage(Integer page, Integer size, String status) {
        LambdaQueryWrapper<ScheduledTask> wrapper = new LambdaQueryWrapper<>();

        if (status != null && !status.isEmpty()) {
            wrapper.eq(ScheduledTask::getStatus, status);
        }

        wrapper.orderByDesc(ScheduledTask::getCreatedAt);

        Page<ScheduledTask> pageObj = new Page<>(page, size);
        return PageResult.of(scheduledTaskMapper.selectPage(pageObj, wrapper));
    }

    @Override
    public ScheduledTask getById(Long id) {
        return scheduledTaskMapper.selectById(id);
    }

    @Override
    @Transactional
    public ScheduledTask create(ScheduledTask task) {
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        task.setStatus("disabled");
        task.setRunCount(0);
        task.setFailCount(0);

        // 计算下次执行时间
        LocalDateTime nextRunTime = calculateNextRunTime(task);
        task.setNextRunTime(nextRunTime);

        scheduledTaskMapper.insert(task);
        return task;
    }

    @Override
    @Transactional
    public ScheduledTask update(Long id, ScheduledTask task) {
        ScheduledTask existing = scheduledTaskMapper.selectById(id);
        if (existing == null) {
            throw new RuntimeException("定时任务不存在");
        }

        task.setId(id);
        task.setUpdatedAt(LocalDateTime.now());

        // 重新计算下次执行时间
        LocalDateTime nextRunTime = calculateNextRunTime(task);
        task.setNextRunTime(nextRunTime);

        scheduledTaskMapper.updateById(task);
        return scheduledTaskMapper.selectById(id);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        scheduledTaskMapper.deleteById(id);
    }

    @Override
    @Transactional
    public void enable(Long id) {
        ScheduledTask task = scheduledTaskMapper.selectById(id);
        if (task == null) {
            throw new RuntimeException("定时任务不存在");
        }

        task.setStatus("enabled");
        task.setNextRunTime(calculateNextRunTime(task));
        task.setUpdatedAt(LocalDateTime.now());
        scheduledTaskMapper.updateById(task);
    }

    @Override
    @Transactional
    public void disable(Long id) {
        ScheduledTask task = scheduledTaskMapper.selectById(id);
        if (task == null) {
            throw new RuntimeException("定时任务不存在");
        }

        task.setStatus("disabled");
        task.setUpdatedAt(LocalDateTime.now());
        scheduledTaskMapper.updateById(task);
    }

    @Override
    public void executeNow(Long id) {
        ScheduledTask task = scheduledTaskMapper.selectById(id);
        if (task == null) {
            throw new RuntimeException("定时任务不存在");
        }

        executeTask(task);
    }

    @Override
    public List<ScheduledTask> getDueTasks() {
        return scheduledTaskMapper.selectList(
                new LambdaQueryWrapper<ScheduledTask>()
                        .eq(ScheduledTask::getStatus, "enabled")
                        .le(ScheduledTask::getNextRunTime, LocalDateTime.now())
        );
    }

    @Override
    @Transactional
    public void updateRunTime(Long id, LocalDateTime nextRunTime) {
        ScheduledTask task = scheduledTaskMapper.selectById(id);
        if (task != null) {
            task.setLastRunTime(LocalDateTime.now());
            task.setNextRunTime(nextRunTime);
            task.setUpdatedAt(LocalDateTime.now());
            scheduledTaskMapper.updateById(task);
        }
    }

    @Override
    public void executeTask(ScheduledTask scheduledTask) {
        log.info("开始执行定时任务: {} (ID: {})", scheduledTask.getName(), scheduledTask.getId());

        try {
            // 更新状态为运行中
            scheduledTask.setStatus("running");
            scheduledTaskMapper.updateById(scheduledTask);

            // 获取关联的测试任务
            Task task = taskMapper.selectById(scheduledTask.getTaskId());
            if (task == null) {
                throw new RuntimeException("关联的测试任务不存在");
            }

            // 执行测试任务
            taskExecutionService.executeTask(task.getId(), log::info);

            // 更新执行次数
            scheduledTask.setRunCount(scheduledTask.getRunCount() + 1);
            scheduledTask.setStatus("enabled");
            scheduledTask.setLastRunTime(LocalDateTime.now());
            scheduledTask.setNextRunTime(calculateNextRunTime(scheduledTask));
            scheduledTask.setUpdatedAt(LocalDateTime.now());
            scheduledTaskMapper.updateById(scheduledTask);

            log.info("定时任务执行完成: {}", scheduledTask.getName());

        } catch (Exception e) {
            log.error("定时任务执行失败: {}", scheduledTask.getName(), e);

            // 更新失败次数
            scheduledTask.setFailCount(scheduledTask.getFailCount() + 1);
            scheduledTask.setStatus("enabled");
            scheduledTask.setNextRunTime(calculateNextRunTime(scheduledTask));
            scheduledTask.setUpdatedAt(LocalDateTime.now());
            scheduledTaskMapper.updateById(scheduledTask);
        }
    }

    @Override
    public void initScheduler() {
        log.info("初始化定时任务调度器...");

        // 获取所有启用的任务
        List<ScheduledTask> enabledTasks = scheduledTaskMapper.selectList(
                new LambdaQueryWrapper<ScheduledTask>()
                        .eq(ScheduledTask::getStatus, "enabled")
        );

        for (ScheduledTask task : enabledTasks) {
            if (task.getNextRunTime() == null) {
                task.setNextRunTime(calculateNextRunTime(task));
                scheduledTaskMapper.updateById(task);
            }
        }

        log.info("已加载 {} 个定时任务", enabledTasks.size());
    }

    @Override
    public LocalDateTime calculateNextRunTime(ScheduledTask task) {
        if ("cron".equals(task.getScheduleType()) && task.getCronExpression() != null) {
            try {
                CronExpression cron = CronExpression.parse(task.getCronExpression());
                return cron.next(LocalDateTime.now());
            } catch (Exception e) {
                log.error("解析 Cron 表达式失败: {}", task.getCronExpression(), e);
                return null;
            }
        } else if ("interval".equals(task.getScheduleType()) && task.getIntervalMinutes() != null) {
            return LocalDateTime.now().plusMinutes(task.getIntervalMinutes());
        } else if ("once".equals(task.getScheduleType())) {
            return task.getNextRunTime(); // 一次性任务不重新计算
        }

        return null;
    }
}

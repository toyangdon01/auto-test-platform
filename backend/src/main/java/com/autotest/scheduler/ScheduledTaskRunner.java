package com.autotest.scheduler;

import com.autotest.entity.ScheduledTask;
import com.autotest.service.ScheduledTaskService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 定时任务执行器
 *
 * @author auto-test-platform
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledTaskRunner {

    private final ScheduledTaskService scheduledTaskService;

    /**
     * 初始化调度器
     */
    @PostConstruct
    public void init() {
        scheduledTaskService.initScheduler();
    }

    /**
     * 每分钟检查待执行任务
     */
    @Scheduled(fixedRate = 60000)
    public void checkDueTasks() {
        try {
            List<ScheduledTask> dueTasks = scheduledTaskService.getDueTasks();

            for (ScheduledTask task : dueTasks) {
                try {
                    scheduledTaskService.executeTask(task);
                } catch (Exception e) {
                    log.error("执行定时任务失败: {}", task.getName(), e);
                }
            }

            if (!dueTasks.isEmpty()) {
                log.debug("本轮执行了 {} 个定时任务", dueTasks.size());
            }
        } catch (Exception e) {
            log.error("检查定时任务失败", e);
        }
    }
}

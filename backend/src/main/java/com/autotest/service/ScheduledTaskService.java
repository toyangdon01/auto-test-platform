package com.autotest.service;

import com.autotest.common.PageResult;
import com.autotest.entity.ScheduledTask;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时任务服务接口
 *
 * @author auto-test-platform
 */
public interface ScheduledTaskService {

    /**
     * 分页查询定时任务
     */
    PageResult<ScheduledTask> getPage(Integer page, Integer size, String status);

    /**
     * 获取定时任务详情
     */
    ScheduledTask getById(Long id);

    /**
     * 创建定时任务
     */
    ScheduledTask create(ScheduledTask task);

    /**
     * 更新定时任务
     */
    ScheduledTask update(Long id, ScheduledTask task);

    /**
     * 删除定时任务
     */
    void delete(Long id);

    /**
     * 启用定时任务
     */
    void enable(Long id);

    /**
     * 禁用定时任务
     */
    void disable(Long id);

    /**
     * 立即执行
     */
    void executeNow(Long id);

    /**
     * 获取待执行的任务
     */
    List<ScheduledTask> getDueTasks();

    /**
     * 更新执行时间
     */
    void updateRunTime(Long id, LocalDateTime nextRunTime);

    /**
     * 执行定时任务
     */
    void executeTask(ScheduledTask task);

    /**
     * 初始化调度器
     */
    void initScheduler();

    /**
     * 获取下次执行时间
     */
    LocalDateTime calculateNextRunTime(ScheduledTask task);
}

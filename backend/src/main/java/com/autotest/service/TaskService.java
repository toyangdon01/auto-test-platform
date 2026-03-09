package com.autotest.service;

import com.autotest.common.PageResult;
import com.autotest.dto.request.TaskCreateRequest;
import com.autotest.dto.request.TaskQueryRequest;
import com.autotest.dto.response.TaskDetailResponse;
import com.autotest.entity.Task;

/**
 * 任务服务接口
 *
 * @author auto-test-platform
 */
public interface TaskService {

    /**
     * 分页查询任务
     */
    PageResult<Task> listTasks(TaskQueryRequest request);

    /**
     * 获取任务详情
     */
    TaskDetailResponse getTaskDetail(Long id);

    /**
     * 创建任务
     */
    Task createTask(TaskCreateRequest request);

    /**
     * 更新任务
     */
    Task updateTask(Long id, TaskCreateRequest request);

    /**
     * 删除任务
     */
    void deleteTask(Long id);

    /**
     * 执行任务
     */
    void executeTask(Long id);

    /**
     * 取消任务
     */
    void cancelTask(Long id);

    /**
     * 重试任务
     */
    void retryTask(Long id);

    /**
     * 获取任务执行进度
     */
    Object getTaskProgress(Long id);

    /**
     * 获取任务执行日志
     */
    Object getTaskLogs(Long id, Long serverId, String stage);
}

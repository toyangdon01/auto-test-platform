package com.autotest.controller;

import com.autotest.common.ApiResponse;
import com.autotest.common.PageResult;
import com.autotest.dto.request.TaskCreateRequest;
import com.autotest.dto.request.TaskQueryRequest;
import com.autotest.dto.response.TaskDetailResponse;
import com.autotest.entity.Task;
import com.autotest.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 任务管理控制器
 *
 * @author auto-test-platform
 */
@Tag(name = "tasks", description = "任务管理")
@RestController
@RequestMapping("/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @Operation(summary = "获取任务列表")
    @GetMapping
    public ApiResponse<PageResult<Task>> listTasks(TaskQueryRequest request) {
        return ApiResponse.success(taskService.listTasks(request));
    }

    @Operation(summary = "获取任务详情")
    @GetMapping("/{id}")
    public ApiResponse<TaskDetailResponse> getTask(@PathVariable Long id) {
        return ApiResponse.success(taskService.getTaskDetail(id));
    }

    @Operation(summary = "创建任务")
    @PostMapping
    public ApiResponse<Task> createTask(@Valid @RequestBody TaskCreateRequest request) {
        return ApiResponse.success(taskService.createTask(request));
    }

    @Operation(summary = "更新任务")
    @PutMapping("/{id}")
    public ApiResponse<Task> updateTask(@PathVariable Long id, @Valid @RequestBody TaskCreateRequest request) {
        return ApiResponse.success(taskService.updateTask(id, request));
    }

    @Operation(summary = "删除任务")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteTask(@PathVariable Long id) {
        taskService.deleteTask(id);
        return ApiResponse.success();
    }

    @Operation(summary = "执行任务")
    @PostMapping("/{id}/execute")
    public ApiResponse<Void> executeTask(@PathVariable Long id) {
        taskService.executeTask(id);
        return ApiResponse.success();
    }

    @Operation(summary = "取消任务")
    @PostMapping("/{id}/cancel")
    public ApiResponse<Void> cancelTask(@PathVariable Long id) {
        taskService.cancelTask(id);
        return ApiResponse.success();
    }

    @Operation(summary = "重试任务")
    @PostMapping("/{id}/retry")
    public ApiResponse<Void> retryTask(@PathVariable Long id) {
        taskService.retryTask(id);
        return ApiResponse.success();
    }

    @Operation(summary = "获取执行进度")
    @GetMapping("/{id}/progress")
    public ApiResponse<Object> getTaskProgress(@PathVariable Long id) {
        return ApiResponse.success(taskService.getTaskProgress(id));
    }

    @Operation(summary = "获取执行日志")
    @GetMapping("/{id}/logs")
    public ApiResponse<Object> getTaskLogs(
            @PathVariable Long id,
            @RequestParam(required = false) Long serverId,
            @RequestParam(defaultValue = "all") String stage) {
        return ApiResponse.success(taskService.getTaskLogs(id, serverId, stage));
    }
}

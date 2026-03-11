package com.autotest.controller;

import com.autotest.common.ApiResponse;
import com.autotest.common.PageResult;
import com.autotest.entity.ScheduledTask;
import com.autotest.service.ScheduledTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 定时任务控制器
 *
 * @author auto-test-platform
 */
@Tag(name = "定时任务管理")
@RestController
@RequestMapping("/scheduled-tasks")
@RequiredArgsConstructor
public class ScheduledTaskController {

    private final ScheduledTaskService scheduledTaskService;

    @GetMapping
    @Operation(summary = "获取定时任务列表")
    public ApiResponse<PageResult<ScheduledTask>> getPage(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") Integer size,
            @Parameter(description = "状态") @RequestParam(required = false) String status) {
        return ApiResponse.success(scheduledTaskService.getPage(page, size, status));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取定时任务详情")
    public ApiResponse<ScheduledTask> getById(@Parameter(description = "任务ID") @PathVariable Long id) {
        return ApiResponse.success(scheduledTaskService.getById(id));
    }

    @PostMapping
    @Operation(summary = "创建定时任务")
    public ApiResponse<ScheduledTask> create(@RequestBody ScheduledTask task) {
        return ApiResponse.success(scheduledTaskService.create(task));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新定时任务")
    public ApiResponse<ScheduledTask> update(
            @Parameter(description = "任务ID") @PathVariable Long id,
            @RequestBody ScheduledTask task) {
        return ApiResponse.success(scheduledTaskService.update(id, task));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除定时任务")
    public ApiResponse<Void> delete(@Parameter(description = "任务ID") @PathVariable Long id) {
        scheduledTaskService.delete(id);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/enable")
    @Operation(summary = "启用定时任务")
    public ApiResponse<Void> enable(@Parameter(description = "任务ID") @PathVariable Long id) {
        scheduledTaskService.enable(id);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/disable")
    @Operation(summary = "禁用定时任务")
    public ApiResponse<Void> disable(@Parameter(description = "任务ID") @PathVariable Long id) {
        scheduledTaskService.disable(id);
        return ApiResponse.success();
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "切换定时任务状态")
    public ApiResponse<Void> updateStatus(
            @Parameter(description = "任务ID") @PathVariable Long id,
            @RequestBody Map<String, String> request) {
        String status = request.get("status");
        if ("enabled".equals(status)) {
            scheduledTaskService.enable(id);
        } else if ("disabled".equals(status)) {
            scheduledTaskService.disable(id);
        } else {
            throw new RuntimeException("无效的状态: " + status);
        }
        return ApiResponse.success();
    }

    @PostMapping("/{id}/execute")
    @Operation(summary = "立即执行")
    public ApiResponse<Void> executeNow(@Parameter(description = "任务ID") @PathVariable Long id) {
        scheduledTaskService.executeNow(id);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/run")
    @Operation(summary = "立即执行（别名）")
    public ApiResponse<Void> run(@Parameter(description = "任务ID") @PathVariable Long id) {
        scheduledTaskService.executeNow(id);
        return ApiResponse.success();
    }
}

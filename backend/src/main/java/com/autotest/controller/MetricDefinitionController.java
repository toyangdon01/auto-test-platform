package com.autotest.controller;

import com.autotest.common.ApiResponse;
import com.autotest.common.PageResult;
import com.autotest.entity.MetricDefinition;
import com.autotest.service.MetricDefinitionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 指标定义控制器
 *
 * @author auto-test-platform
 */
@Tag(name = "指标定义管理")
@RestController
@RequestMapping("/metric-definitions")
@RequiredArgsConstructor
public class MetricDefinitionController {

    private final MetricDefinitionService metricDefinitionService;

    @GetMapping
    @Operation(summary = "获取指标定义列表")
    public ApiResponse<PageResult<MetricDefinition>> getPage(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "20") Integer size,
            @Parameter(description = "分类") @RequestParam(required = false) String category,
            @Parameter(description = "是否启用") @RequestParam(required = false) Boolean enabled) {
        return ApiResponse.success(metricDefinitionService.getPage(page, size, category, enabled));
    }

    @GetMapping("/all")
    @Operation(summary = "获取所有启用的指标定义")
    public ApiResponse<List<MetricDefinition>> getAllEnabled() {
        return ApiResponse.success(metricDefinitionService.getAllEnabled());
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取指标定义详情")
    public ApiResponse<MetricDefinition> getById(@Parameter(description = "指标ID") @PathVariable Long id) {
        return ApiResponse.success(metricDefinitionService.getById(id));
    }

    @PostMapping
    @Operation(summary = "创建指标定义")
    public ApiResponse<MetricDefinition> create(@RequestBody MetricDefinition definition) {
        return ApiResponse.success(metricDefinitionService.create(definition));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新指标定义")
    public ApiResponse<MetricDefinition> update(
            @Parameter(description = "指标ID") @PathVariable Long id,
            @RequestBody MetricDefinition definition) {
        return ApiResponse.success(metricDefinitionService.update(id, definition));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除指标定义")
    public ApiResponse<Void> delete(@Parameter(description = "指标ID") @PathVariable Long id) {
        metricDefinitionService.delete(id);
        return ApiResponse.success();
    }
}

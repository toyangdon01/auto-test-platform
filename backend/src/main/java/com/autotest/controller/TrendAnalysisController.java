package com.autotest.controller;

import com.autotest.common.ApiResponse;
import com.autotest.dto.TrendAnalysisResponse;
import com.autotest.service.TrendAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 趋势分析控制器
 *
 * @author auto-test-platform
 */
@Tag(name = "趋势分析")
@RestController
@RequestMapping("/trend")
@RequiredArgsConstructor
public class TrendAnalysisController {

    private final TrendAnalysisService trendAnalysisService;

    @GetMapping("/metric")
    @Operation(summary = "获取指标趋势")
    public ApiResponse<TrendAnalysisResponse> getMetricTrend(
            @Parameter(description = "脚本ID") @RequestParam(required = false) Long scriptId,
            @Parameter(description = "指标名称") @RequestParam String metricName,
            @Parameter(description = "天数") @RequestParam(defaultValue = "30") Integer days) {
        return ApiResponse.success(trendAnalysisService.getMetricTrend(scriptId, metricName, days));
    }

    @GetMapping("/task-execution")
    @Operation(summary = "获取任务执行趋势")
    public ApiResponse<Map<String, Object>> getTaskExecutionTrend(
            @Parameter(description = "脚本ID") @RequestParam(required = false) Long scriptId,
            @Parameter(description = "天数") @RequestParam(defaultValue = "30") Integer days) {
        return ApiResponse.success(trendAnalysisService.getTaskExecutionTrend(scriptId, days));
    }

    @GetMapping("/server-performance")
    @Operation(summary = "获取服务器性能趋势")
    public ApiResponse<Map<String, Object>> getServerPerformanceTrend(
            @Parameter(description = "服务器ID") @RequestParam Long serverId,
            @Parameter(description = "天数") @RequestParam(defaultValue = "30") Integer days) {
        return ApiResponse.success(trendAnalysisService.getServerPerformanceTrend(serverId, days));
    }

    @GetMapping("/overall")
    @Operation(summary = "获取整体趋势概览")
    public ApiResponse<Map<String, Object>> getOverallTrend(
            @Parameter(description = "天数") @RequestParam(defaultValue = "30") Integer days) {
        return ApiResponse.success(trendAnalysisService.getOverallTrend(days));
    }

    @GetMapping("/predict")
    @Operation(summary = "预测指标趋势")
    public ApiResponse<Map<String, Object>> predictMetricTrend(
            @Parameter(description = "脚本ID") @RequestParam Long scriptId,
            @Parameter(description = "指标名称") @RequestParam(required = false) String metricName,
            @Parameter(description = "预测天数") @RequestParam(defaultValue = "7") Integer days) {
        // 如果未指定指标名称，使用默认值
        if (metricName == null || metricName.isEmpty()) {
            metricName = "cpu_usage"; // 默认指标
        }
        return ApiResponse.success(trendAnalysisService.predictMetricTrend(scriptId, metricName, days));
    }

    @GetMapping("/anomalies")
    @Operation(summary = "获取异常检测报告")
    public ApiResponse<List<Map<String, Object>>> detectAnomalies(
            @Parameter(description = "脚本ID") @RequestParam Long scriptId,
            @Parameter(description = "天数") @RequestParam(defaultValue = "30") Integer days) {
        return ApiResponse.success(trendAnalysisService.detectAnomalies(scriptId, days));
    }
}
